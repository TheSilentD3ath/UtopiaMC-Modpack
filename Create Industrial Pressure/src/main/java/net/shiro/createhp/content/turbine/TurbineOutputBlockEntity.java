package net.shiro.createhp.content.turbine;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.shiro.createhp.content.pressure.PipeMaterial;
import net.shiro.createhp.content.pressure.PressureTooltip;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Abtrieb und Steuerung der Dampfturbine.
 *
 * Alle 20 Ticks prueft er den Aufbau (TurbineStructure). Steht die Turbine, nimmt er jeden Tick Dampf aus ihren
 * Einlaessen, hoechstens so viel, wie ihre Rotorlagen verarbeiten, und legt die Haelfte davon als Wasser in die
 * Kondensatauslaesse (Entscheidung des Maintainers: teilweise Wasser zurueck). Ist dort kein Platz, geht das
 * Wasser verloren; die Turbine laeuft weiter.
 *
 * Die Leistung richtet sich nach dem Dampf, der im Mittel der letzten 20 Ticks durchging: je 10 mB pro Tick das
 * 1,5-fache (Kupfer) oder 2,5-fache (Netherit) einer Create-Dampfmaschine unter Volllast, bei festen 64 RPM wie
 * die Dampfmaschine. Gemittelt wird, weil Pumpen den Dampf nicht gleichmaessig liefern; ohne Mittel wuerde das
 * Wellennetz jeden Tick neu rechnen.
 */
public class TurbineOutputBlockEntity extends GeneratingKineticBlockEntity {

	private static final int SCAN_TICKS = 20;
	private static final int WINDOW_TICKS = 20;
	/** Drehzahl einer Create-Dampfmaschine unter Volllast. */
	public static final float SPEED = 64f;
	/** Leistung einer Create-Dampfmaschine unter Volllast in SU: 1024 je vier Stufen Drehzahl, mal 64 RPM. */
	public static final float ENGINE_SU = 16384f;
	/** Dampf je Leistungsstufe in mB pro Tick, wie beim Dampfauslass. */
	public static final float STEAM_PER_LEVEL_MB = 10f;

	private TurbineStructure.Result structure = TurbineStructure.Result.broken(null, BlockPos.ZERO);
	private boolean scanned;

	/** Mittlerer Dampfverbrauch des letzten Fensters in mB pro Tick; bestimmt Drehzahl und Leistung. */
	private int steamRate;
	private long windowSteam;
	private int windowTicks;
	/** Tropfen Dampf, deren Wasser noch nicht ausgegeben wurde (die Haelfte eines ungeraden Rests). */
	private long condensateCarry;

	// Fuer Brille und Client; der Server schickt sie mit sendData.
	private boolean formed;
	private int length;
	@Nullable
	private PipeMaterial material;
	@Nullable
	private BlockPos problem;
	private boolean tooLong;

	public TurbineOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void initialize() {
		super.initialize();
		// Wie Creates Motor: nach dem Laden mit der gespeicherten Leistung weiterlaufen, statt eine Sekunde zu stehen.
		if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed())
			updateGeneratedRotation();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;

		if (!scanned || level.getGameTime() % SCAN_TICKS == 0)
			rescan();

		if (formed)
			windowSteam += runTurbine();

		if (++windowTicks >= WINDOW_TICKS) {
			int rate = formed ? Math.round(windowSteam / (float) windowTicks / 81f) : 0;
			windowSteam = 0;
			windowTicks = 0;
			if (rate != steamRate) {
				steamRate = rate;
				updateGeneratedRotation();
			}
		}
	}

	private void rescan() {
		scanned = true;
		Direction facing = getBlockState().getValue(DirectionalKineticBlock.FACING);
		structure = TurbineStructure.scan(level, worldPosition, facing);
		for (BlockPos pos : structure.inlets())
			if (portAt(pos) instanceof TurbinePortBlockEntity port)
				port.link();
		for (BlockPos pos : structure.condensateOutlets())
			if (portAt(pos) instanceof TurbinePortBlockEntity port)
				port.link();

		boolean changed = formed != structure.formed() || length != structure.length()
				|| material != structure.material() || !Objects.equals(problem, structure.problem())
				|| tooLong != structure.tooLong();
		formed = structure.formed();
		length = structure.length();
		material = structure.material();
		problem = structure.problem();
		tooLong = structure.tooLong();
		if (!formed && steamRate != 0) {
			// Sofort stehen bleiben, nicht erst am Ende des Fensters.
			steamRate = 0;
			windowSteam = 0;
			updateGeneratedRotation();
		} else if (changed) {
			sendData();
		}
	}

	/** Ein Tick der Turbine: Dampf aus den Einlaessen, Wasser in die Auslaesse. Gibt den verbrauchten Dampf zurueck. */
	private long runTurbine() {
		long want = maxSteamPerTick() * 81L;
		long taken = 0;
		for (BlockPos pos : structure.inlets()) {
			if (taken >= want)
				break;
			if (portAt(pos) instanceof SteamInletBlockEntity inlet)
				taken += inlet.drain(want - taken);
		}
		if (taken <= 0)
			return 0;

		condensateCarry += taken;
		long water = condensateCarry / 2;
		condensateCarry -= water * 2;
		List<CondensateOutletBlockEntity> outlets = new ArrayList<>();
		for (BlockPos pos : structure.condensateOutlets())
			if (portAt(pos) instanceof CondensateOutletBlockEntity outlet)
				outlets.add(outlet);
		// Gleichmaessig verteilen: Jeder Auslass bekommt seinen Teil vom Rest, der letzte alles, was uebrig ist.
		for (int i = 0; i < outlets.size() && water > 0; i++)
			water -= outlets.get(i).fill(water / (outlets.size() - i));
		// Was ein voller Auslass weiter vorn nicht nahm, bekommen die anderen; sonst ginge es verloren, obwohl
		// noch Platz ist.
		for (int i = 0; i < outlets.size() && water > 0; i++)
			water -= outlets.get(i).fill(water);
		return taken;
	}

	/**
	 * Block-Entity eines Anschlusses. Die Teile wurden bei der letzten Pruefung geladen gefunden; bis zur naechsten
	 * kann ein Chunk entladen sein, und getBlockEntity wuerde ihn dann synchron nachladen.
	 */
	@Nullable
	private BlockEntity portAt(BlockPos pos) {
		return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
	}

	/** Hoechster Dampfdurchsatz der Turbine in mB pro Tick. */
	public int maxSteamPerTick() {
		return material == null ? 0 : length * material.turbineSteamPerSlice;
	}

	@Override
	public float getGeneratedSpeed() {
		if (!(getBlockState().getBlock() instanceof TurbineOutputBlock) || steamRate <= 0)
			return 0;
		return convertToDirection(SPEED, getBlockState().getValue(DirectionalKineticBlock.FACING));
	}

	@Override
	public float calculateAddedStressCapacity() {
		float capacity = 0;
		if (steamRate > 0 && material != null)
			capacity = steamRate / STEAM_PER_LEVEL_MB * ENGINE_SU * material.turbinePowerFactor / SPEED;
		this.lastCapacityProvided = capacity;
		return capacity;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("SteamRate", steamRate);
		if (material != null)
			tag.putString("Material", material.name());
		tag.putInt("Length", length);
		tag.putBoolean("Formed", formed);
		if (clientPacket && problem != null)
			tag.put("Problem", NbtUtils.writeBlockPos(problem));
		if (clientPacket)
			tag.putBoolean("TooLong", tooLong);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		steamRate = tag.getInt("SteamRate");
		material = tag.contains("Material") ? materialOf(tag.getString("Material")) : null;
		length = tag.getInt("Length");
		formed = tag.getBoolean("Formed");
		if (clientPacket) {
			problem = tag.contains("Problem") ? NbtUtils.readBlockPos(tag.getCompound("Problem")) : null;
			tooLong = tag.getBoolean("TooLong");
		}
	}

	@Nullable
	private static PipeMaterial materialOf(String name) {
		for (PipeMaterial m : PipeMaterial.values())
			if (m.name().equals(name))
				return m;
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		PressureTooltip.lang().translate("gui.goggles.turbine").forGoggles(tooltip);
		if (!formed) {
			PressureTooltip.lang().translate("gui.goggles.turbine_incomplete")
					.style(ChatFormatting.RED)
					.forGoggles(tooltip, 1);
			if (tooLong)
				PressureTooltip.lang().translate("gui.goggles.turbine_too_long", TurbineStructure.MAX_LENGTH)
						.style(ChatFormatting.GRAY)
						.forGoggles(tooltip, 1);
			if (problem != null)
				PressureTooltip.lang().translate("gui.goggles.turbine_problem", problem.getX(), problem.getY(), problem.getZ())
						.style(ChatFormatting.GRAY)
						.forGoggles(tooltip, 1);
			return true;
		}
		String kind = material == PipeMaterial.NETHERITE ? "gui.goggles.turbine_netherite" : "gui.goggles.turbine_copper";
		PressureTooltip.lang().translate(kind, length)
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		PressureTooltip.lang().translate("gui.goggles.turbine_steam", steamRate, maxSteamPerTick())
				.style(steamRate > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
				.forGoggles(tooltip, 1);
		// Die Haelfte des Dampfs; bei ungerader Rate mit einer halben Einheit, nicht als Gleitkommazahl.
		String condensate = steamRate / 2 + (steamRate % 2 == 0 ? "" : ".5");
		PressureTooltip.lang().translate("gui.goggles.turbine_condensate", condensate)
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}
}
