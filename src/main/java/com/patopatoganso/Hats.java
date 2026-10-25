package com.patopatoganso;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sombreros de pato (amarillo) y ganso (blanco) hechos con block_display montados en la cabeza.
 * Son entidades vanilla: los jugadores NO necesitan resource pack ni mod.
 */
public final class Hats {
	private record Part(String block, float x, float y, float z, float sx, float sy, float sz) {}

	// Origen = encima de la cabeza, +Z = hacia delante.
	private static final List<Part> DUCK = List.of(
			new Part("yellow_concrete", -0.26f, 0.00f, -0.26f, 0.52f, 0.16f, 0.52f), // cuerpo
			new Part("yellow_concrete", -0.17f, 0.16f, -0.10f, 0.34f, 0.32f, 0.34f), // cabeza
			new Part("orange_concrete", -0.09f, 0.22f, 0.24f, 0.18f, 0.07f, 0.20f),  // pico
			new Part("black_concrete", -0.13f, 0.34f, 0.235f, 0.05f, 0.07f, 0.03f),  // ojo izq
			new Part("black_concrete", 0.08f, 0.34f, 0.235f, 0.05f, 0.07f, 0.03f),   // ojo der
			new Part("yellow_concrete", -0.08f, 0.10f, -0.36f, 0.16f, 0.14f, 0.12f)  // cola
	);

	private static final List<Part> GOOSE = List.of(
			new Part("white_concrete", -0.26f, 0.00f, -0.26f, 0.52f, 0.16f, 0.52f),  // cuerpo
			new Part("white_concrete", -0.07f, 0.16f, -0.02f, 0.14f, 0.34f, 0.14f),  // cuello
			new Part("white_concrete", -0.11f, 0.50f, -0.06f, 0.22f, 0.18f, 0.30f),  // cabeza
			new Part("orange_concrete", -0.07f, 0.54f, 0.24f, 0.14f, 0.07f, 0.20f),  // pico
			new Part("black_concrete", -0.115f, 0.60f, 0.15f, 0.03f, 0.05f, 0.05f),  // ojo izq
			new Part("black_concrete", 0.085f, 0.60f, 0.15f, 0.03f, 0.05f, 0.05f),   // ojo der
			new Part("white_concrete", -0.08f, 0.10f, -0.36f, 0.16f, 0.14f, 0.12f),  // cola
			new Part("white_concrete", -0.30f, 0.04f, -0.15f, 0.04f, 0.14f, 0.30f),  // ala izq
			new Part("white_concrete", 0.26f, 0.04f, -0.15f, 0.04f, 0.14f, 0.30f)    // ala der
	);

	private final Map<UUID, List<Display.BlockDisplay>> hats = new HashMap<>();
	private final Map<UUID, Boolean> isGoose = new HashMap<>();

	/** Pone (o cambia) el sombrero del jugador. */
	public void set(ServerPlayer p, boolean goose) {
		remove(p.getUUID());
		isGoose.put(p.getUUID(), goose);
		hats.put(p.getUUID(), spawn(p, goose));
	}

	public boolean has(UUID id) {
		return hats.containsKey(id);
	}

	/** Mantiene el sombrero pegado a la cabeza y orientado como el jugador. */
	public void tick(ServerPlayer p) {
		List<Display.BlockDisplay> list = hats.get(p.getUUID());
		if (list == null) return;
		boolean broken = false;
		for (Display.BlockDisplay d : list) {
			if (d.isRemoved() || d.getVehicle() != p) {
				broken = true;
				break;
			}
		}
		if (broken) {
			discardAll(list);
			hats.put(p.getUUID(), spawn(p, isGoose.getOrDefault(p.getUUID(), false)));
			return;
		}
		float yaw = p.getYHeadRot();
		for (Display.BlockDisplay d : list) {
			d.setYRot(yaw);
			d.setXRot(0f);
		}
	}

	public void remove(UUID id) {
		List<Display.BlockDisplay> list = hats.remove(id);
		isGoose.remove(id);
		if (list != null) discardAll(list);
	}

	public void removeAll() {
		for (List<Display.BlockDisplay> l : hats.values()) discardAll(l);
		hats.clear();
		isGoose.clear();
	}

	private static void discardAll(List<Display.BlockDisplay> list) {
		for (Display.BlockDisplay d : list) {
			if (!d.isRemoved()) d.discard();
		}
	}

	private static List<Display.BlockDisplay> spawn(ServerPlayer p, boolean goose) {
		ServerLevel lv = p.serverLevel();
		List<Display.BlockDisplay> out = new ArrayList<>();
		for (Part part : goose ? GOOSE : DUCK) {
			Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(lv);
			if (d == null) continue;
			String snbt = String.format(Locale.ROOT,
					"{block_state:{Name:\"minecraft:%s\"},"
							+ "transformation:{translation:[%.3ff,%.3ff,%.3ff],left_rotation:[0f,0f,0f,1f],"
							+ "scale:[%.3ff,%.3ff,%.3ff],right_rotation:[0f,0f,0f,1f]},"
							+ "brightness:{block:15,sky:15},shadow_radius:0f,view_range:1.0f}",
					part.block(), part.x(), part.y(), part.z(), part.sx(), part.sy(), part.sz());
			try {
				CompoundTag tag = TagParser.parseTag(snbt);
				d.load(tag);
			} catch (CommandSyntaxException e) {
				System.err.println("[PatoPatoGanso] NBT de sombrero invalido: " + e.getMessage());
				continue;
			}
			d.moveTo(p.getX(), p.getY(), p.getZ(), p.getYHeadRot(), 0f);
			d.addTag("pg_hat");
			lv.addFreshEntity(d);
			d.startRiding(p, true);
			out.add(d);
		}
		return out;
	}
}
