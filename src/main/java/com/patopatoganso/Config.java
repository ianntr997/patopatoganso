package com.patopatoganso;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Configuración guardada en config/patopatoganso.json (se cambia en juego con /pg config). */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static Config INSTANCE = new Config();

	public static final List<String> KEYS = List.of(
			"players", "minPlayers", "raceSeconds", "walkSeconds", "maxRounds", "countdownSeconds",
			"seatRadius", "touchRadius", "seatTolerance", "seatOffsetY", "elimination",
			"bothEliminatedOnTimeout", "autoStart");

	/** Jugadores por círculo (y plazas de la sala de espera). */
	public int players = 6;
	/** Mínimo de jugadores para poder empezar con /pg start. */
	public int minPlayers = 2;
	/** Tiempo (segundos) que tienen el caminante y el ganso para tocar el árbol y volver. */
	public int raceSeconds = 20;
	/** Tiempo máximo (segundos) que el caminante puede decir "pato". 0 = sin límite. */
	public int walkSeconds = 0;
	/** Rondas máximas. 0 = hasta que quede un solo jugador. */
	public int maxRounds = 0;
	public int countdownSeconds = 5;
	public double seatRadius = 8.0;
	/** Distancia al árbol para contar como "tocado". */
	public double touchRadius = 2.0;
	/** Distancia al asiento para contar como "sentado". */
	public double seatTolerance = 1.8;
	/** Ajuste vertical del asiento (más negativo = más hundido en el suelo). */
	public double seatOffsetY = -0.9;
	/** "spectator" (pasa a espectador) o "kill" (muere de verdad). */
	public String elimination = "spectator";
	/** Si se acaba el tiempo y nadie volvió, caen los dos. */
	public boolean bothEliminatedOnTimeout = true;
	/** Empieza sola cuando se llena la sala. */
	public boolean autoStart = true;

	public String arenaDimension = "minecraft:overworld";
	public boolean arenaSet = false;
	public double arenaX, arenaY, arenaZ;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("patopatoganso.json");
	}

	public static void load() {
		Path p = path();
		try {
			if (Files.exists(p)) {
				Config c = GSON.fromJson(Files.readString(p), Config.class);
				if (c != null) INSTANCE = c;
			}
		} catch (Exception e) {
			System.err.println("[PatoPatoGanso] No pude leer la config, uso valores por defecto: " + e);
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(INSTANCE));
		} catch (IOException e) {
			System.err.println("[PatoPatoGanso] No pude guardar la config: " + e);
		}
	}

	public String describe() {
		return String.format(Locale.ROOT,
				"players=%d, minPlayers=%d, raceSeconds=%d, walkSeconds=%d, maxRounds=%d, countdownSeconds=%d, "
						+ "seatRadius=%.1f, touchRadius=%.1f, seatTolerance=%.1f, seatOffsetY=%.1f, elimination=%s, "
						+ "bothEliminatedOnTimeout=%b, autoStart=%b",
				players, minPlayers, raceSeconds, walkSeconds, maxRounds, countdownSeconds,
				seatRadius, touchRadius, seatTolerance, seatOffsetY, elimination,
				bothEliminatedOnTimeout, autoStart);
	}

	/** Devuelve null si todo bien, o un mensaje de error. */
	public String set(String key, String v) {
		try {
			switch (key.toLowerCase(Locale.ROOT)) {
				case "players" -> players = clampI(v, 2, 24);
				case "minplayers" -> minPlayers = clampI(v, 2, 24);
				case "raceseconds" -> raceSeconds = clampI(v, 3, 600);
				case "walkseconds" -> walkSeconds = clampI(v, 0, 3600);
				case "maxrounds" -> maxRounds = clampI(v, 0, 100);
				case "countdownseconds" -> countdownSeconds = clampI(v, 0, 60);
				case "seatradius" -> seatRadius = clampD(v, 3, 14);
				case "touchradius" -> touchRadius = clampD(v, 1, 5);
				case "seattolerance" -> seatTolerance = clampD(v, 1, 5);
				case "seatoffsety" -> seatOffsetY = clampD(v, -3, 2);
				case "elimination" -> {
					String s = v.toLowerCase(Locale.ROOT);
					if (!s.equals("spectator") && !s.equals("kill")) return "elimination solo acepta: spectator o kill";
					elimination = s;
				}
				case "botheliminatedontimeout" -> bothEliminatedOnTimeout = Boolean.parseBoolean(v);
				case "autostart" -> autoStart = Boolean.parseBoolean(v);
				default -> {
					return "Clave desconocida. Claves: " + String.join(", ", KEYS);
				}
			}
		} catch (NumberFormatException e) {
			return "Valor no válido: " + v;
		}
		save();
		return null;
	}

	private static int clampI(String v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, Integer.parseInt(v)));
	}

	private static double clampD(String v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, Double.parseDouble(v.replace(',', '.'))));
	}
}
