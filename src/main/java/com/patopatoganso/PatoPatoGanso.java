package com.patopatoganso;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public class PatoPatoGanso implements ModInitializer {
	public static final String MOD_ID = "patopatoganso";
	public static Game GAME;

	@Override
	public void onInitialize() {
		Config.load();
		GAME = new Game();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GAME.setServer(server);
			Game.cleanStale(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> GAME.shutdown());
		ServerTickEvents.END_SERVER_TICK.register(server -> GAME.tick());

		// Nadie recibe daño mientras juega (la eliminación la decide el juego).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer sp && GAME.isParticipant(sp.getUUID())));

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> GAME.onTap(player, hand, entity));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> GAME.onTap(player, hand, entity));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GAME.onDisconnect(handler.player));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				PgCommands.register(dispatcher));
	}
}
