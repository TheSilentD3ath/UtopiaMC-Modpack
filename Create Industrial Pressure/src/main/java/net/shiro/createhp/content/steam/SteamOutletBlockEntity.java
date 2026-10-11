package net.shiro.createhp.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.lang.LangBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.CreateHP;
import net.shiro.createhp.registry.CHPFluids;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dampfauslass: wandelt die freien Kesselstufen seines Tanks in Dampf, 10 mB pro Tick und Stufe.
 *
 * Frei sind die Stufen, die keine Dampfmaschine am selben Kessel braucht; mehrere Auslaesse teilen sie sich.
 * Das Wasser verbraucht Creates Kessel selbst: Er nimmt es an und misst daran seine Wasserversorgung. Der Dampf
 * sammelt sich in einem kleinen Puffer, aus dem Pumpen ihn ueber die Vorderseite ziehen. Ist der Puffer voll,
 * geht nichts weiter verloren, es entsteht nur kein neuer Dampf.
 */
public class SteamOutletBlockEntity extends SmartBlockEntity implements SidedStorageBlockEntity, IHaveGoggleInformation {

	/** Dampf je Kesselstufe und Tick, in Tropfen (10 mB, Entscheidung des Maintainers). */
	public static final long STEAM_PER_LEVEL = 10 * 81;
	/**
	 * Puffer im Auslass. Ein Netherit-Kessel liefert bis 1500 mB pro Tick; der Puffer muss mehr als das fassen,
	 * sonst begrenzt er selbst, weil eine Pumpe nur einmal pro Tick abholt.
	 */
	private static final long BUFFER = 8 * FluidConstants.BUCKET;

	private final Buffer buffer = new Buffer();
	/** Erzeugung im letzten Tick, fuer die Brille; der Client bekommt sie mit sendData. */
	private long producedPerTick;
	private long sentProduced = -1;

	public SteamOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		producedPerTick = produce();
		if (producedPerTick != sentProduced && level.getGameTime() % 10 == 0) {
			sentProduced = producedPerTick;
			sendData();
		}
	}

	private long produce() {
		FluidTankBlockEntity tank = attachedTank();
		if (tank == null)
			return 0;
		BoilerData boiler = tank.boiler;
		int outlets = ((BoilerRules) boiler).createhp$getOutlets();
		if (outlets <= 0 || !boiler.isActive())
			return 0;
		int size = tank.getTotalTankSize();
		int level = Math.min(boiler.activeHeat,
				Math.min(boiler.getMaxHeatLevelForWaterSupply(), boiler.getMaxHeatLevelForBoilerSize(size)));
		int free = Math.max(0, level - boiler.attachedEngines);
		long share = free * STEAM_PER_LEVEL / outlets;
		long space = BUFFER - buffer.amount;
		long added = Math.min(share, space);
		if (added <= 0)
			return 0;
		if (buffer.variant.isBlank())
			buffer.variant = FluidVariant.of(CHPFluids.STEAM.get().getSource());
		buffer.amount += added;
		setChanged();
		return added;
	}

	@Nullable
	private FluidTankBlockEntity attachedTank() {
		Direction facing = SteamOutletBlock.getFacing(getBlockState());
		if (level.getBlockEntity(worldPosition.relative(facing.getOpposite())) instanceof FluidTankBlockEntity part)
			return part.getControllerBE();
		return null;
	}

	@Nullable
	@Override
	public Storage<FluidVariant> getFluidStorage(@Nullable Direction side) {
		return side == SteamOutletBlock.getFacing(getBlockState()) ? buffer : null;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		CompoundTag steam = new CompoundTag();
		steam.put("Variant", buffer.variant.toNbt());
		steam.putLong("Amount", buffer.amount);
		tag.put("Steam", steam);
		if (clientPacket)
			tag.putLong("Produced", producedPerTick);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		CompoundTag steam = tag.getCompound("Steam");
		buffer.variant = FluidVariant.fromNbt(steam.getCompound("Variant"));
		buffer.amount = steam.getLong("Amount");
		if (clientPacket)
			producedPerTick = tag.getLong("Produced");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		LangBuilder lang = new LangBuilder(CreateHP.ID);
		lang.translate("gui.goggles.steam_outlet").forGoggles(tooltip);
		lang.translate("gui.goggles.steam_rate", producedPerTick / 81)
				.style(producedPerTick > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
				.forGoggles(tooltip, 1);
		lang.translate("gui.goggles.steam_buffer", buffer.amount / 81, BUFFER / 81)
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		return true;
	}

	/** Nur Entnahme von aussen; hinein kommt Dampf allein ueber produce(). */
	private class Buffer extends SingleVariantStorage<FluidVariant> {
		@Override
		protected FluidVariant getBlankVariant() {
			return FluidVariant.blank();
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return BUFFER;
		}

		@Override
		protected boolean canInsert(FluidVariant variant) {
			return false;
		}

		@Override
		protected void onFinalCommit() {
			setChanged();
		}
	}
}
