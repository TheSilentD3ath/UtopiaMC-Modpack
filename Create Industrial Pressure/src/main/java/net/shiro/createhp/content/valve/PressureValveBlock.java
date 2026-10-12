package net.shiro.createhp.content.valve;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureRated;

import java.util.List;
import java.util.function.Supplier;

/**
 * Absperrventil, Druckregler und Ueberdruckventil. Alle drei sind Creates Fluessigkeitsventil mit anderem
 * Verhalten: gleiche Form, gleiche Ausrichtung, gleiche Rohranschluesse. So verbinden sie sich mit Create-Rohren
 * genau wie Creates Ventil, und Creates Modelle lassen sich als Vorlage verwenden.
 *
 * SMART zeigt die Aufwertung mit einer Elektronenroehre an (wie Creates "schlaue" Teile). Bei Absperrventil und
 * Regler steuert dann Redstone; das Ueberdruckventil gibt beim Ablassen Redstone aus.
 */
public class PressureValveBlock extends FluidValveBlock implements PressureRated, SpecialBlockItemRequirement {

	public static final BooleanProperty SMART = BooleanProperty.create("smart");

	private final ValveKind kind;
	private final PipeMaterial material;
	private final Supplier<? extends BlockEntityType<? extends PressureValveBlockEntity>> blockEntityType;

	public PressureValveBlock(Properties properties, ValveKind kind, PipeMaterial material,
			Supplier<? extends BlockEntityType<? extends PressureValveBlockEntity>> blockEntityType) {
		super(properties);
		this.kind = kind;
		this.material = material;
		this.blockEntityType = blockEntityType;
		// BooleanProperty beginnt mit true; ohne das waere jedes neue Ventil schon schlau.
		registerDefaultState(defaultBlockState().setValue(SMART, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SMART);
	}

	public ValveKind getKind() {
		return kind;
	}

	@Override
	public PipeMaterial getMaterial() {
		return material;
	}

	@Override
	public BlockEntityType<? extends FluidValveBlockEntity> getBlockEntityType() {
		return blockEntityType.get();
	}

	/** Nur das normale Absperrventil hat eine Welle; alle anderen werden per Redstone oder Sollwert gestellt. */
	public boolean isRotationDriven(BlockState state) {
		return kind == ValveKind.SHUTOFF && !state.getValue(SMART);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return isRotationDriven(state) && super.hasShaftTowards(world, pos, state, face);
	}

	// Die Aufwertung nimmt die Welle weg. Ohne diesen Vergleich haelt Create die Zustaende fuer gleichwertig und
	// laesst das Ventil am alten Wellenstrang haengen.
	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState)
				&& oldState.getValue(SMART) == newState.getValue(SMART);
	}

	// Beim Setzen richtet sich Creates Ventil auch nach Wellen in der Naehe. Regler und Ueberdruckventil haben
	// keine; fuer sie zaehlen nur die Rohre.
	@Override
	protected boolean prefersConnectionTo(LevelReader reader, BlockPos pos, Direction facing, boolean shaftAxis) {
		if (shaftAxis && kind != ValveKind.SHUTOFF)
			return false;
		return super.prefersConnectionTo(reader, pos, facing, shaftAxis);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		// Bei Regler und Ueberdruckventil zeigt ENABLED nur an, ob sie gerade regeln oder ablassen. Die Anschluesse
		// bleiben gleich. Creates Ventil wuerde bei jedem Wechsel das Netz neu aufbauen und den Durchfluss kurz
		// unterbrechen; das Neuverteilen danach kaeme ausserdem wieder hier an.
		if (kind.hasSetpoint() && oldState.is(this)
				&& state == oldState.setValue(ENABLED, state.getValue(ENABLED)))
			return;
		super.onPlace(state, level, pos, oldState, isMoving);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (state.getValue(SMART) || !AllItems.ELECTRON_TUBE.isIn(held))
			return super.use(state, level, pos, player, hand, hit);
		if (!level.isClientSide) {
			KineticBlockEntity.switchToBlockState(level, pos, state.setValue(SMART, true));
			if (!player.getAbilities().instabuild)
				held.shrink(1);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	// Das schlaue Ueberdruckventil meldet das Ablassen; schlaue Absperrventile und Regler gelten ebenfalls als
	// Redstone-Bauteil, damit Redstone-Staub sich sichtbar mit ihnen verbindet.
	@Override
	public boolean isSignalSource(BlockState state) {
		return state.getValue(SMART);
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return kind == ValveKind.RELIEF && state.getValue(SMART) && state.getValue(ENABLED) ? 15 : 0;
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
		if (!state.getValue(SMART))
			return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, asItem());
		return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME,
				List.of(new ItemStack(asItem()), AllItems.ELECTRON_TUBE.asStack()));
	}
}
