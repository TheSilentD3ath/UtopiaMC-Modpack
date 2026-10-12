package net.shiro.createhp.content.turbine;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Gemeinsame Grundlage von Dampfeinlass und Kondensatauslass: ein Puffer fuer eine Fluessigkeit, an allen Seiten
 * fuer Rohre erreichbar, und die Verbindung zur Turbine.
 *
 * Die Turbine haelt keine Speicher fremder Bloecke fest. Sie nimmt bei jedem Tick aus den Einlasspuffern und legt
 * in die Auslasspuffer; so bleibt jeder Block fuer sich gueltig, auch wenn die Turbine gerade unvollstaendig ist
 * oder ein Chunk entladen wird.
 */
public abstract class TurbinePortBlockEntity extends SmartBlockEntity implements SidedStorageBlockEntity, IHaveGoggleInformation {

	/** Puffer je Anschluss. Ein Netherit-Kessel liefert 1500 mB pro Tick; eine Pumpe holt nur einmal pro Tick ab. */
	public static final long BUFFER = 8 * FluidConstants.BUCKET;
	/** So lange gilt die Verbindung; die Turbine erneuert sie bei jeder Pruefung, also alle 20 Ticks. */
	private static final int LINK_TICKS = 50;

	protected final Buffer buffer = new Buffer();
	private long linkedUntil;
	private boolean linked;
	private long sentAmount = -1;

	protected TurbinePortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	/** Ruft die Turbine bei jeder Pruefung, solange dieser Block zu ihr gehoert. */
	public void link() {
		if (level != null)
			linkedUntil = level.getGameTime() + LINK_TICKS;
	}

	/** Ob dieser Block zu einer vollstaendigen Turbine gehoert; auf dem Client aus dem letzten Paket. */
	public boolean isLinked() {
		return linked;
	}

	public long getAmount() {
		return buffer.amount;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		boolean now = level.getGameTime() < linkedUntil;
		if (now != linked) {
			linked = now;
			sendData();
		} else if (level.getGameTime() % 10 == 0 && buffer.amount != sentAmount) {
			sendData();
		}
	}

	/** Was Rohre hineinfuellen duerfen; der Einlass nimmt Dampf, der Kondensatauslass nichts. */
	protected abstract boolean acceptsFromPipes(FluidVariant variant);

	/** Ob Rohre etwas herausziehen duerfen; nur beim Kondensatauslass. */
	protected abstract boolean givesToPipes();

	@Nullable
	@Override
	public Storage<FluidVariant> getFluidStorage(@Nullable Direction side) {
		return buffer;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		CompoundTag content = new CompoundTag();
		content.put("Variant", buffer.variant.toNbt());
		content.putLong("Amount", buffer.amount);
		tag.put("Buffer", content);
		if (clientPacket) {
			tag.putBoolean("Linked", linked);
			sentAmount = buffer.amount;
		}
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		CompoundTag content = tag.getCompound("Buffer");
		buffer.variant = FluidVariant.fromNbt(content.getCompound("Variant"));
		buffer.amount = content.getLong("Amount");
		if (clientPacket)
			linked = tag.getBoolean("Linked");
	}

	protected class Buffer extends SingleVariantStorage<FluidVariant> {
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
			return acceptsFromPipes(variant);
		}

		@Override
		protected boolean canExtract(FluidVariant variant) {
			return givesToPipes();
		}

		@Override
		protected void onFinalCommit() {
			setChanged();
		}

		/** Fuer die Turbine, ausserhalb jeder Transaktion; sie laeuft auf dem Server-Thread im Tick. */
		long take(long max) {
			long taken = Math.min(max, amount);
			if (taken <= 0)
				return 0;
			amount -= taken;
			if (amount == 0)
				variant = FluidVariant.blank();
			setChanged();
			return taken;
		}

		long put(FluidVariant fluid, long max) {
			if (!variant.isBlank() && !variant.equals(fluid))
				return 0;
			long added = Math.min(max, BUFFER - amount);
			if (added <= 0)
				return 0;
			variant = fluid;
			amount += added;
			setChanged();
			return added;
		}
	}
}
