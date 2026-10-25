package com.patopatoganso;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Pato, Pato, Ganso estilo Squid Craft Games 4.
 *
 * Flujo: sala -> cuenta atras -> (botella elige caminante -> el caminante dice "pato" las veces que quiera
 * hasta que dice "ganso" -> carrera caminante vs ganso: tocar el arbol y volver al asiento del ganso)
 * -> el que pierde (o ambos si se acaba el tiempo) queda eliminado -> siguiente ronda, hasta que quede uno.
 */
public final class Game {
	private enum Phase { IDLE, COUNTDOWN, SPIN, WALK, RACE, REORG, END }

	private static final int SPIN_TOTAL = 80;
	private static final int SPIN_STEPS = 14;

	private final Random rnd = new Random();
	private final Hats hats = new Hats();

	private MinecraftServer server;
	private Phase phase = Phase.IDLE;
	private int ticks;
	private int round;
	private int patoCount;
	private int tapCooldown;
	private int spinK;

	private final LinkedHashSet<UUID> lobby = new LinkedHashSet<>();
	private final List<UUID> alive = new ArrayList<>();
	private final Set<UUID> everyone = new LinkedHashSet<>();
	private final Map<UUID, ArmorStand> stands = new HashMap<>();
	private final Map<UUID, Vec3> seatPos = new HashMap<>();
	private final Map<UUID, GameType> prevMode = new HashMap<>();
	private final Set<UUID> touched = new HashSet<>();
	private final List<UUID> winners = new ArrayList<>();

	private UUID chosen, walker, goose, lastWalker;
	private Vec3 targetSeat;
	private ServerBossEvent bar;

	// ------------------------------------------------------------------ ciclo de vida

	public void setServer(MinecraftServer s) {
		this.server = s;
	}

	public void shutdown() {
		if (server != null && phase != Phase.IDLE) cleanup();
	}

	public boolean isParticipant(UUID id) {
		return phase != Phase.IDLE && alive.contains(id);
	}

	/** Borra sombreros y asientos que se quedaron colgados (por ejemplo tras un cierre brusco). */
	public static void cleanStale(MinecraftServer server) {
		for (ServerLevel lv : server.getAllLevels()) {
			List<Entity> kill = new ArrayList<>();
			for (Entity e : lv.getAllEntities()) {
				if (e.getTags().contains("pg_hat") || e.getTags().contains("pg_stand")) kill.add(e);
			}
			for (Entity e : kill) e.discard();
		}
	}

	// ------------------------------------------------------------------ utilidades

	private Config cfg() {
		return Config.INSTANCE;
	}

	private ServerLevel level() {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(cfg().arenaDimension));
		ServerLevel lv = server.getLevel(key);
		return lv != null ? lv : server.overworld();
	}

	private Vec3 center() {
		return new Vec3(cfg().arenaX, cfg().arenaY, cfg().arenaZ);
	}

	private ServerPlayer pl(UUID id) {
		return id == null ? null : server.getPlayerList().getPlayer(id);
	}

	private String name(UUID id) {
		ServerPlayer p = pl(id);
		return p != null ? p.getGameProfile().getName() : "alguien";
	}

	private void run(String command) {
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (Exception e) {
			System.err.println("[PatoPatoGanso] comando fallido: " + command + " -> " + e);
		}
	}

	private void sound(ServerPlayer p, String id, float vol, float pitch) {
		String u = p.getStringUUID();
		run(String.format(Locale.ROOT, "execute at %s run playsound %s master %s ~ ~ ~ %.2f %.2f", u, id, u, vol, pitch));
	}

	private void soundAll(String id, float vol, float pitch) {
		for (ServerPlayer p : online()) sound(p, id, vol, pitch);
	}

	private List<ServerPlayer> online() {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : everyone) {
			ServerPlayer p = pl(id);
			if (p != null) out.add(p);
		}
		return out;
	}

	private Component tag(String text, ChatFormatting color) {
		return Component.literal("[Pato Pato Ganso] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(color));
	}

	private void say(String text) {
		Component c = tag(text, ChatFormatting.YELLOW);
		for (ServerPlayer p : online()) p.sendSystemMessage(c);
	}

	private void sayLobby(String text) {
		Component c = tag(text, ChatFormatting.YELLOW);
		for (UUID id : lobby) {
			ServerPlayer p = pl(id);
			if (p != null) p.sendSystemMessage(c);
		}
	}

	private void title(ServerPlayer p, Component t, Component sub, int in, int stay, int out) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
		p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		p.connection.send(new ClientboundSetTitleTextPacket(t));
	}

	private void titleAll(Component t, Component sub, int in, int stay, int out) {
		for (ServerPlayer p : online()) title(p, t, sub, in, stay, out);
	}

	private void actionbar(ServerPlayer p, String text, ChatFormatting color) {
		p.displayClientMessage(Component.literal(text).withStyle(color), true);
	}

	private static double hdist(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private float yawToward(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * 57.29577951308232) - 90.0F;
	}

	// ------------------------------------------------------------------ sala de espera

	public String join(ServerPlayer p) {
		if (!cfg().arenaSet) return "Todavía no hay arena. Un op debe usar /pg build o /pg setcenter.";
		if (phase != Phase.IDLE) return "Ya hay una partida en curso.";
		if (lobby.contains(p.getUUID())) return "Ya estás en la sala.";
		if (lobby.size() >= cfg().players) return "La sala está llena (" + cfg().players + " jugadores).";
		lobby.add(p.getUUID());
		Vec3 c = center();
		p.teleportTo(level(), c.x, c.y + 0.1, c.z + cfg().seatRadius + 5, 180f, 0f);
		sayLobby(p.getGameProfile().getName() + " entró a la sala (" + lobby.size() + "/" + cfg().players + ").");
		if (cfg().autoStart && lobby.size() >= cfg().players) {
			start();
		}
		return null;
	}

	public void leave(ServerPlayer p) {
		if (lobby.remove(p.getUUID())) {
			p.sendSystemMessage(tag("Saliste de la sala.", ChatFormatting.YELLOW));
			return;
		}
		if (alive.contains(p.getUUID())) {
			forfeit(p);
		}
	}

	public String status() {
		if (phase == Phase.IDLE) {
			return "Sala: " + lobby.size() + "/" + cfg().players + " jugadores. Arena "
					+ (cfg().arenaSet ? "lista." : "sin construir (/pg build).");
		}
		return "Partida en curso: ronda " + round + ", quedan " + alive.size() + " jugadores.";
	}

	public void setCenter(ServerPlayer p) {
		cfg().arenaDimension = p.serverLevel().dimension().location().toString();
		cfg().arenaX = Math.floor(p.getX()) + 0.5;
		cfg().arenaY = Math.floor(p.getY());
		cfg().arenaZ = Math.floor(p.getZ()) + 0.5;
		cfg().arenaSet = true;
		Config.save();
	}

	public void buildArena(ServerPlayer p) {
		setCenter(p);
		Arena.build(p.serverLevel(), (int) Math.floor(cfg().arenaX), (int) cfg().arenaY, (int) Math.floor(cfg().arenaZ),
				cfg().players, cfg().seatRadius);
		p.teleportTo(p.serverLevel(), cfg().arenaX, cfg().arenaY + 0.1, cfg().arenaZ + cfg().seatRadius + 5, 180f, 0f);
	}

	// ------------------------------------------------------------------ empezar / parar

	public String start() {
		if (phase != Phase.IDLE) return "Ya hay una partida en curso.";
		if (!cfg().arenaSet) return "Primero construye la arena con /pg build.";
		lobby.removeIf(id -> pl(id) == null);
		if (lobby.size() < cfg().minPlayers) {
			return "Faltan jugadores: hay " + lobby.size() + " y se necesitan al menos " + cfg().minPlayers + ".";
		}
		everyone.clear();
		alive.clear();
		everyone.addAll(lobby);
		alive.addAll(lobby);
		lobby.clear();
		round = 0;
		lastWalker = null;
		winners.clear();
		for (UUID id : alive) {
			ServerPlayer p = pl(id);
			if (p == null) continue;
			prevMode.put(id, p.gameMode.getGameModeForPlayer());
			p.setGameMode(GameType.ADVENTURE);
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			clearItems(p);
		}
		bar = new ServerBossEvent(Component.literal("Carrera"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
		reseatAll();
		for (UUID id : alive) {
			ServerPlayer p = pl(id);
			if (p != null) hats.set(p, false);
		}
		phase = Phase.COUNTDOWN;
		ticks = 0;
		titleAll(Component.literal("Pato, Pato, Ganso").withStyle(ChatFormatting.GOLD),
				Component.literal(alive.size() + " jugadores. Solo uno sobrevive.").withStyle(ChatFormatting.YELLOW), 10, 50, 10);
		say("¡Empieza la partida con " + alive.size() + " jugadores! Todos son patos...");
		return null;
	}

	public void stop() {
		lobby.clear();
		if (phase != Phase.IDLE) cleanup();
	}

	private void cleanup() {
		if (bar != null) bar.removeAllPlayers();
		for (UUID id : new ArrayList<>(everyone)) {
			ServerPlayer p = pl(id);
			if (p == null) continue;
			p.stopRiding();
			clearItems(p);
			GameType m = prevMode.getOrDefault(id, GameType.SURVIVAL);
			if (p.gameMode.getGameModeForPlayer() != m) p.setGameMode(m);
		}
		hats.removeAll();
		removeAllStands();
		alive.clear();
		everyone.clear();
		seatPos.clear();
		prevMode.clear();
		touched.clear();
		winners.clear();
		walker = goose = chosen = lastWalker = null;
		phase = Phase.IDLE;
		ticks = 0;
	}

	// ------------------------------------------------------------------ asientos

	private ArmorStand makeStand(ServerLevel lv, double x, double y, double z, float yaw) {
		ArmorStand st = EntityType.ARMOR_STAND.create(lv);
		if (st == null) return null;
		try {
			st.load(TagParser.parseTag("{Marker:1b,Invisible:1b,NoGravity:1b,Small:1b}"));
		} catch (CommandSyntaxException ignored) {
			// los valores por defecto sirven igual
		}
		st.moveTo(x, y, z, yaw, 0f);
		st.addTag("pg_stand");
		lv.addFreshEntity(st);
		return st;
	}

	private void removeStand(UUID id) {
		ArmorStand st = stands.remove(id);
		if (st != null) {
			ServerPlayer p = pl(id);
			if (p != null && p.getVehicle() == st) p.stopRiding();
			if (!st.isRemoved()) st.discard();
		}
	}

	private void removeAllStands() {
		for (UUID id : new ArrayList<>(stands.keySet())) removeStand(id);
		stands.clear();
	}

	/** Reparte a los jugadores vivos en un circulo parejo alrededor del arbol y los sienta. */
	private void reseatAll() {
		removeAllStands();
		seatPos.clear();
		ServerLevel lv = level();
		Vec3 c = center();
		int n = alive.size();
		for (int i = 0; i < n; i++) {
			UUID id = alive.get(i);
			ServerPlayer p = pl(id);
			if (p == null) continue;
			double ang = Math.PI * 2 * i / n;
			double x = c.x + Math.cos(ang) * cfg().seatRadius;
			double z = c.z + Math.sin(ang) * cfg().seatRadius;
			float yaw = yawToward(new Vec3(x, c.y, z), c);
			seatPos.put(id, new Vec3(x, c.y, z));
			ArmorStand st = makeStand(lv, x, c.y + cfg().seatOffsetY, z, yaw);
			p.stopRiding();
			p.teleportTo(lv, x, c.y + 0.1, z, yaw, 10f);
			if (st != null) {
				stands.put(id, st);
				p.startRiding(st, true);
			}
		}
	}

	private void enforceSeats() {
		for (UUID id : alive) {
			if (id.equals(walker) || id.equals(goose)) continue;
			ArmorStand st = stands.get(id);
			ServerPlayer p = pl(id);
			if (st == null || p == null || st.isRemoved()) continue;
			if (p.getVehicle() != st) p.startRiding(st, true);
		}
	}

	// ------------------------------------------------------------------ objetos del caminante

	private void giveItems(ServerPlayer w) {
		ItemStack pato = new ItemStack(Items.YELLOW_DYE);
		pato.set(DataComponents.CUSTOM_NAME, Component.literal("Pato (clic a un jugador)").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
		ItemStack ganso = new ItemStack(Items.WHITE_DYE);
		ganso.set(DataComponents.CUSTOM_NAME, Component.literal("GANSO (clic a un jugador)").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE)));
		Inventory inv = w.getInventory();
		inv.add(pato);
		inv.add(ganso);
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.YELLOW_DYE) && s.has(DataComponents.CUSTOM_NAME)) {
				inv.selected = i;
				w.connection.send(new ClientboundSetCarriedItemPacket(i));
				break;
			}
		}
	}

	private void clearItems(ServerPlayer p) {
		p.getInventory().clearOrCountMatchingItems(
				s -> (s.is(Items.YELLOW_DYE) || s.is(Items.WHITE_DYE)) && s.has(DataComponents.CUSTOM_NAME),
				-1, p.inventoryMenu.getCraftSlots());
	}

	// ------------------------------------------------------------------ tick principal

	public void tick() {
		if (server == null || phase == Phase.IDLE) return;
		ticks++;
		if (tapCooldown > 0) tapCooldown--;

		for (UUID id : alive) {
			ServerPlayer p = pl(id);
			if (p != null) hats.tick(p);
		}
		enforceSeats();
		if (server.getTickCount() % 20 == 0) {
			for (UUID id : alive) {
				ServerPlayer p = pl(id);
				if (p != null) {
					p.getFoodData().setFoodLevel(20);
					p.setHealth(p.getMaxHealth());
				}
			}
		}

		switch (phase) {
			case COUNTDOWN -> tickCountdown();
			case SPIN -> tickSpin();
			case WALK -> tickWalk();
			case RACE -> tickRace();
			case REORG -> tickReorg();
			case END -> tickEnd();
			default -> { }
		}
	}

	private void tickCountdown() {
		int total = cfg().countdownSeconds * 20;
		if (ticks < total) {
			if (ticks % 20 == 0) {
				int left = (total - ticks) / 20;
				titleAll(Component.literal(String.valueOf(left)).withStyle(ChatFormatting.GOLD),
						Component.literal("Quédate en tu asiento").withStyle(ChatFormatting.YELLOW), 0, 22, 0);
				soundAll("minecraft:block.note_block.hat", 1f, 1f);
			}
			return;
		}
		startRound();
	}

	// ------------------------------------------------------------------ ronda: botella

	private void startRound() {
		round++;
		walker = null;
		goose = null;
		touched.clear();
		patoCount = 0;
		for (UUID id : alive) {
			ServerPlayer p = pl(id);
			if (p != null) hats.set(p, false);
		}
		List<UUID> cand = new ArrayList<>(alive);
		if (cand.size() > 1 && lastWalker != null) cand.remove(lastWalker);
		chosen = cand.get(rnd.nextInt(cand.size()));
		spinK = 0;
		phase = Phase.SPIN;
		ticks = 0;
		titleAll(Component.literal("Ronda " + round).withStyle(ChatFormatting.GOLD),
				Component.literal("Quedan " + alive.size() + " jugadores. Gira la botella...").withStyle(ChatFormatting.YELLOW), 5, 40, 10);
	}

	private void tickSpin() {
		int n = alive.size();
		int idxChosen = alive.indexOf(chosen);
		if (idxChosen < 0 || n == 0) {
			if (n > 0) startRound();
			return;
		}
		while (spinK <= SPIN_STEPS) {
			int when = (int) (SPIN_TOTAL * (1 - Math.pow(1 - (double) spinK / SPIN_STEPS, 2)));
			if (ticks < when) break;
			int idx = Math.floorMod(idxChosen - (SPIN_STEPS - spinK), n);
			String nm = name(alive.get(idx));
			for (ServerPlayer p : online()) {
				actionbar(p, "Botella >>> " + nm, spinK == SPIN_STEPS ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
			}
			soundAll("minecraft:ui.button.click", 0.8f, 0.8f + 0.04f * spinK);
			spinK++;
		}
		if (ticks >= SPIN_TOTAL + 25) beginWalk();
	}

	// ------------------------------------------------------------------ ronda: el caminante

	private void beginWalk() {
		ServerPlayer w = pl(chosen);
		if (w == null || !alive.contains(chosen)) {
			startRound();
			return;
		}
		walker = chosen;
		lastWalker = chosen;
		Vec3 s = seatPos.get(walker);
		Vec3 c = center();
		removeStand(walker);
		w.stopRiding();
		if (s != null) {
			Vec3 dir = new Vec3(s.x - c.x, 0, s.z - c.z).normalize();
			Vec3 o = s.add(dir.scale(2.5));
			w.teleportTo(level(), o.x, c.y + 0.1, o.z, yawToward(o, c), 0f);
		}
		giveItems(w);
		phase = Phase.WALK;
		ticks = 0;
		patoCount = 0;
		soundAll("minecraft:block.note_block.bell", 1f, 1f);
		titleAll(Component.literal(w.getGameProfile().getName()).withStyle(ChatFormatting.GOLD),
				Component.literal("camina alrededor del círculo").withStyle(ChatFormatting.YELLOW), 5, 50, 10);
		w.sendSystemMessage(tag("Tinte amarillo = PATO, tinte blanco = GANSO. Haz clic sobre un jugador sentado. "
				+ "Di pato las veces que quieras y cuando digas GANSO empieza la carrera al árbol.", ChatFormatting.AQUA));
	}

	private void tickWalk() {
		ServerPlayer w = pl(walker);
		if (w == null || !alive.contains(walker)) {
			phase = Phase.REORG;
			ticks = 0;
			return;
		}
		int limit = cfg().walkSeconds * 20;
		if (limit > 0) {
			int left = (limit - ticks) / 20;
			if (ticks % 10 == 0) actionbar(w, "Pato x" + patoCount + " | tiempo para decir GANSO: " + Math.max(left, 0) + "s", ChatFormatting.YELLOW);
			if (ticks >= limit) {
				List<UUID> opts = new ArrayList<>(alive);
				opts.remove(walker);
				if (!opts.isEmpty()) {
					say("Se acabó el tiempo de " + name(walker) + ": el ganso se elige al azar.");
					makeGoose(opts.get(rnd.nextInt(opts.size())));
				}
			}
		} else if (ticks % 10 == 0) {
			actionbar(w, "Pato x" + patoCount + " | tinte blanco = GANSO", ChatFormatting.YELLOW);
		}
	}

	/** Llamado al hacer clic o golpear a otra entidad. */
	public InteractionResult onTap(Player player, InteractionHand hand, Entity target) {
		if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (phase != Phase.WALK || !sp.getUUID().equals(walker)) return InteractionResult.PASS;
		if (!(target instanceof ServerPlayer tp)) return InteractionResult.PASS;
		UUID tid = tp.getUUID();
		if (tid.equals(walker) || !alive.contains(tid) || !stands.containsKey(tid)) return InteractionResult.PASS;
		if (tapCooldown > 0) return InteractionResult.SUCCESS;
		ItemStack held = sp.getMainHandItem();
		if (held.is(Items.YELLOW_DYE)) {
			tapCooldown = 6;
			patoCount++;
			say(sp.getGameProfile().getName() + ": ¡Pato! (" + tp.getGameProfile().getName() + ")");
			sound(tp, "minecraft:entity.chicken.ambient", 1f, 1f);
			sound(sp, "minecraft:entity.chicken.ambient", 1f, 1f);
			return InteractionResult.SUCCESS;
		}
		if (held.is(Items.WHITE_DYE)) {
			tapCooldown = 6;
			makeGoose(tid);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	// ------------------------------------------------------------------ ronda: carrera

	private void makeGoose(UUID id) {
		ServerPlayer g = pl(id);
		ServerPlayer w = pl(walker);
		if (g == null || w == null) return;
		goose = id;
		targetSeat = seatPos.get(id);
		if (targetSeat == null) targetSeat = g.position();
		Vec3 c = center();
		removeStand(id);
		g.stopRiding();
		g.teleportTo(level(), targetSeat.x, c.y + 0.1, targetSeat.z, yawToward(targetSeat, c), 0f);
		hats.set(g, true);
		clearItems(w);
		touched.clear();
		phase = Phase.RACE;
		ticks = 0;

		if (bar != null) {
			bar.removeAllPlayers();
			for (ServerPlayer p : online()) bar.addPlayer(p);
			bar.setProgress(1f);
		}
		soundAll("minecraft:entity.chicken.hurt", 1.2f, 0.6f);
		titleAll(Component.literal("¡GANSO!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.literal(g.getGameProfile().getName() + " contra " + w.getGameProfile().getName()
						+ ": toquen el árbol y vuelvan al asiento").withStyle(ChatFormatting.YELLOW), 3, 40, 10);
		say("¡" + w.getGameProfile().getName() + " dijo GANSO a " + g.getGameProfile().getName() + "! Tienen "
				+ cfg().raceSeconds + " segundos. Primero el árbol, luego al asiento del ganso.");
	}

	private ServerPlayer racer(UUID id) {
		return alive.contains(id) ? pl(id) : null;
	}

	private void tickRace() {
		int total = cfg().raceSeconds * 20;
		int left = total - ticks;
		if (bar != null) {
			bar.setProgress(Mth.clamp((float) left / (float) total, 0f, 1f));
			bar.setName(Component.literal("Carrera: " + Math.max(0, (left + 19) / 20) + " s"));
		}
		ServerPlayer w = racer(walker);
		ServerPlayer g = racer(goose);
		Vec3 c = center();

		UUID best = null;
		double bestD = Double.MAX_VALUE;
		for (UUID id : new UUID[]{walker, goose}) {
			ServerPlayer p = racer(id);
			if (p == null) continue;
			Vec3 pos = p.position();
			if (!touched.contains(id)) {
				actionbar(p, "Toca el ÁRBOL", ChatFormatting.GREEN);
				if (hdist(pos, c) <= cfg().touchRadius) {
					touched.add(id);
					sound(p, "minecraft:entity.experience_orb.pickup", 1f, 1.2f);
					say(p.getGameProfile().getName() + " tocó el árbol.");
				}
			} else {
				actionbar(p, "Vuelve al ASIENTO", ChatFormatting.AQUA);
				double d = hdist(pos, targetSeat);
				if (d <= cfg().seatTolerance && d < bestD) {
					bestD = d;
					best = id;
				}
			}
		}

		if (best != null) {
			UUID loser = best.equals(walker) ? goose : walker;
			endRace(best, loser == null ? List.of() : List.of(loser));
		} else if (w == null || g == null) {
			UUID win = w != null ? walker : (g != null ? goose : null);
			endRace(win, List.of());
		} else if (left <= 0) {
			if (cfg().bothEliminatedOnTimeout) {
				say("¡Se acabó el tiempo! Nadie llegó.");
				endRace(null, List.of(walker, goose));
			} else {
				UUID loser;
				boolean wt = touched.contains(walker), gt = touched.contains(goose);
				if (wt && !gt) loser = goose;
				else if (gt && !wt) loser = walker;
				else loser = rnd.nextBoolean() ? walker : goose;
				endRace(loser.equals(walker) ? goose : walker, List.of(loser));
			}
		}
	}

	private void endRace(UUID winner, List<UUID> losers) {
		if (bar != null) bar.removeAllPlayers();
		if (winner != null) {
			say(name(winner) + " llegó primero al asiento.");
			ServerPlayer wp = pl(winner);
			if (wp != null) sound(wp, "minecraft:entity.player.levelup", 1f, 1f);
		}
		for (UUID l : losers) {
			say(name(l) + " se quedó de pie y queda ELIMINADO.");
			eliminate(l);
		}
		walker = null;
		goose = null;
		if (checkEnd()) return;
		if (cfg().maxRounds > 0 && round >= cfg().maxRounds) {
			finish(new ArrayList<>(alive), "Se acabaron las rondas: ganan los supervivientes.");
			return;
		}
		phase = Phase.REORG;
		ticks = 0;
	}

	private void tickReorg() {
		if (ticks == 40) {
			if (alive.size() >= 2) reseatAll();
		} else if (ticks >= 80) {
			if (alive.size() >= 2) startRound();
			else checkEnd();
		}
	}

	// ------------------------------------------------------------------ eliminacion y final

	private void eliminate(UUID id) {
		ServerPlayer p = pl(id);
		alive.remove(id);
		removeStand(id);
		seatPos.remove(id);
		hats.remove(id);
		if (p == null) return;
		p.stopRiding();
		clearItems(p);
		Vec3 at = p.position();
		run(String.format(Locale.ROOT, "particle minecraft:totem_of_undying %.2f %.2f %.2f 0.4 0.8 0.4 0.3 60 force", at.x, at.y + 1, at.z));
		soundAll("minecraft:entity.lightning_bolt.thunder", 0.8f, 1f);
		title(p, Component.literal("ELIMINADO").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.literal("Gracias por jugar").withStyle(ChatFormatting.GRAY), 5, 50, 20);
		if ("kill".equalsIgnoreCase(cfg().elimination)) {
			p.setGameMode(prevMode.getOrDefault(id, GameType.SURVIVAL));
			p.kill();
		} else {
			Vec3 c = center();
			p.setGameMode(GameType.SPECTATOR);
			p.teleportTo(level(), c.x, c.y + 8, c.z, 0f, 30f);
		}
	}

	private boolean checkEnd() {
		if (alive.size() > 1) return false;
		List<UUID> w = new ArrayList<>(alive);
		if (w.isEmpty()) finish(w, "Nadie sobrevivió.");
		else finish(w, name(w.get(0)) + " es el último superviviente.");
		return true;
	}

	private void finish(List<UUID> win, String message) {
		winners.clear();
		winners.addAll(win);
		if (bar != null) bar.removeAllPlayers();
		for (UUID id : win) {
			removeStand(id);
			ServerPlayer p = pl(id);
			if (p != null) p.stopRiding();
		}
		phase = Phase.END;
		ticks = 0;
		StringBuilder sb = new StringBuilder();
		for (UUID id : win) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(name(id));
		}
		titleAll(Component.literal(win.isEmpty() ? "Sin ganador" : "¡GANADOR!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(win.isEmpty() ? message : sb.toString()).withStyle(ChatFormatting.YELLOW), 10, 100, 20);
		say(message);
		soundAll("minecraft:entity.player.levelup", 1f, 0.8f);
	}

	private void tickEnd() {
		if (ticks <= 100 && ticks % 20 == 0) {
			for (UUID id : winners) {
				ServerPlayer p = pl(id);
				if (p == null) continue;
				run(String.format(Locale.ROOT,
						"summon minecraft:firework_rocket %.2f %.2f %.2f {LifeTime:20,FireworksItem:{id:\"minecraft:firework_rocket\",count:1,"
								+ "components:{\"minecraft:fireworks\":{explosions:[{shape:\"large_ball\",colors:[I;16776960,16777215],has_trail:1b}],flight_duration:1b}}}}",
						p.getX(), p.getY() + 1, p.getZ()));
			}
		}
		if (ticks >= 200) cleanup();
	}

	// ------------------------------------------------------------------ desconexiones

	public void onDisconnect(ServerPlayer p) {
		if (server == null) return;
		lobby.remove(p.getUUID());
		if (phase == Phase.IDLE) return;
		if (alive.contains(p.getUUID())) {
			forfeit(p);
		} else if (everyone.remove(p.getUUID())) {
			GameType m = prevMode.remove(p.getUUID());
			if (m != null) p.setGameMode(m);
		}
	}

	/** El jugador abandona (o se desconecta) en plena partida. */
	private void forfeit(ServerPlayer p) {
		UUID id = p.getUUID();
		boolean wasWalker = id.equals(walker);
		alive.remove(id);
		removeStand(id);
		seatPos.remove(id);
		hats.remove(id);
		p.stopRiding();
		clearItems(p);
		GameType m = prevMode.remove(id);
		if (m != null) p.setGameMode(m);
		everyone.remove(id);
		if (bar != null) bar.removePlayer(p);
		say(p.getGameProfile().getName() + " abandonó la partida.");
		if (phase == Phase.END) return;
		if (checkEnd()) return;
		if (wasWalker && phase == Phase.WALK) {
			walker = null;
			phase = Phase.REORG;
			ticks = 0;
		}
	}
}
