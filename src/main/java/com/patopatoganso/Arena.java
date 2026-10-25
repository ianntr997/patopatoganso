package com.patopatoganso;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Construye la sala: suelo de arena, arbol en el centro, asientos y borde arcoiris. */
public final class Arena {
	private static final int SAND_RADIUS = 16;
	private static final int RAINBOW_WIDTH = 7;
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private Arena() {}

	/** cy = altura de los pies de quien esta de pie (el suelo queda en cy-1). */
	public static void build(ServerLevel lv, int cx, int cy, int cz, int seats, double seatRadius) {
		int outer = SAND_RADIUS + RAINBOW_WIDTH;
		BlockState[] rainbow = {
				Blocks.RED_CONCRETE.defaultBlockState(),
				Blocks.ORANGE_CONCRETE.defaultBlockState(),
				Blocks.YELLOW_CONCRETE.defaultBlockState(),
				Blocks.LIME_CONCRETE.defaultBlockState(),
				Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
				Blocks.BLUE_CONCRETE.defaultBlockState(),
				Blocks.PURPLE_CONCRETE.defaultBlockState()
		};
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState sand = Blocks.SAND.defaultBlockState();
		BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState barrier = Blocks.BARRIER.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		int span = outer + 2;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
				if (dist > outer + 1.5) continue;
				// despejar aire encima
				for (int y = 0; y <= 14; y++) {
					lv.setBlock(pos.set(cx + dx, cy + y, cz + dz), air, FLAGS);
				}
				if (dist <= SAND_RADIUS) {
					lv.setBlock(pos.set(cx + dx, cy - 1, cz + dz), sand, FLAGS);
					lv.setBlock(pos.set(cx + dx, cy - 2, cz + dz), sandstone, FLAGS);
				} else if (dist < outer) {
					int idx = Math.max(0, Math.min(rainbow.length - 1, (int) (dist - SAND_RADIUS)));
					lv.setBlock(pos.set(cx + dx, cy - 1, cz + dz), rainbow[idx], FLAGS);
					lv.setBlock(pos.set(cx + dx, cy - 2, cz + dz), stone, FLAGS);
				} else {
					// muro invisible para que nadie se escape
					for (int y = -1; y <= 10; y++) {
						lv.setBlock(pos.set(cx + dx, cy + y, cz + dz), barrier, FLAGS);
					}
				}
			}
		}

		// Marcas de asiento (parches rojos 3x3)
		BlockState red = Blocks.RED_CONCRETE.defaultBlockState();
		for (int i = 0; i < seats; i++) {
			double ang = Math.PI * 2 * i / seats;
			int px = cx + (int) Math.round(Math.cos(ang) * seatRadius);
			int pz = cz + (int) Math.round(Math.sin(ang) * seatRadius);
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					lv.setBlock(pos.set(px + ox, cy - 1, pz + oz), red, FLAGS);
				}
			}
		}

		// Base de hierba y arbol en el centro
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int ox = -2; ox <= 2; ox++) {
			for (int oz = -2; oz <= 2; oz++) {
				lv.setBlock(pos.set(cx + ox, cy - 1, cz + oz), grass, FLAGS);
			}
		}
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		for (int y = 0; y <= 6; y++) {
			lv.setBlock(pos.set(cx, cy + y, cz), log, FLAGS);
		}
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx * dx + dy * dy + dz * dz > 10) continue;
					int y = cy + 7 + dy;
					if (dx == 0 && dz == 0 && y <= cy + 6) continue; // tronco
					if (y < cy + 4) continue;
					lv.setBlock(pos.set(cx + dx, y, cz + dz), leaves, FLAGS);
				}
			}
		}
	}
}
