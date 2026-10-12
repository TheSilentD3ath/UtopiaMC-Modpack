package net.shiro.createhp.content.tank;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.shiro.createhp.content.pressure.Pressure;

/**
 * Druck in einem Tank. Ein Tank hat in Create keinen eigenen Druck; die Rohre und Pumpen, die in ihn speisen,
 * tragen ihn an ihrem Anschluss. Der Druck eines Tanks ist darum der hoechste dieser Anschluesse, ueber den
 * ganzen Multiblock. Gilt fuer Drucktanks und fuer Creates eigene Tanks.
 */
public final class TankPressure {
	private TankPressure() {}

	public static float barAt(FluidTankBlockEntity part) {
		Level level = part.getLevel();
		FluidTankBlockEntity controller = part.getControllerBE();
		if (level == null || controller == null)
			return 0f;
		BlockPos origin = controller.getBlockPos();
		int width = controller.getWidth();
		int height = controller.getHeight();
		float max = 0f;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < width; z++) {
					BlockPos pos = origin.offset(x, y, z);
					for (Direction d : Iterate.directions) {
						int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
						// Nachbarn im selben Tank sind keine Anschluesse.
						if (nx >= 0 && nx < width && nz >= 0 && nz < width && ny >= 0 && ny < height)
							continue;
						BlockPos neighbour = pos.relative(d);
						if (!level.isLoaded(neighbour))
							continue;
						FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, neighbour);
						if (pipe == null || pipe.interfaces == null)
							continue;
						PipeConnection connection = pipe.interfaces.get(d.getOpposite());
						if (connection == null)
							continue;
						Couple<Float> pressure = connection.getPressure();
						max = Math.max(max, Math.max(pressure.getFirst(), pressure.getSecond()));
					}
				}
			}
		}
		return max / Pressure.UNITS_PER_BAR;
	}
}
