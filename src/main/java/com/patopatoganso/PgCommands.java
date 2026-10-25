package com.patopatoganso;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PgCommands {
	private PgCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("pg")
				.then(Commands.literal("join").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					String err = PatoPatoGanso.GAME.join(p);
					if (err != null) c.getSource().sendFailure(Component.literal(err));
					return err == null ? 1 : 0;
				}))
				.then(Commands.literal("leave").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					PatoPatoGanso.GAME.leave(p);
					return 1;
				}))
				.then(Commands.literal("status").executes(c -> {
					ok(c.getSource(), PatoPatoGanso.GAME.status());
					return 1;
				}))
				.then(Commands.literal("help").executes(c -> {
					ok(c.getSource(), help());
					return 1;
				}))
				.then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(c -> {
					String err = PatoPatoGanso.GAME.start();
					if (err != null) c.getSource().sendFailure(Component.literal(err));
					return err == null ? 1 : 0;
				}))
				.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(c -> {
					PatoPatoGanso.GAME.stop();
					ok(c.getSource(), "Partida detenida y arena limpia.");
					return 1;
				}))
				.then(Commands.literal("setcenter").requires(s -> s.hasPermission(2)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					PatoPatoGanso.GAME.setCenter(p);
					ok(c.getSource(), "Centro de la arena guardado en tu posición (ahí va el árbol).");
					return 1;
				}))
				.then(Commands.literal("build").requires(s -> s.hasPermission(2)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					PatoPatoGanso.GAME.buildArena(p);
					ok(c.getSource(), "Arena construida alrededor de ti. Usa /pg join para entrar.");
					return 1;
				}))
				.then(Commands.literal("config").requires(s -> s.hasPermission(2))
						.executes(c -> {
							ok(c.getSource(), Config.INSTANCE.describe());
							return 1;
						})
						.then(Commands.argument("clave", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Config.KEYS, b))
								.then(Commands.argument("valor", StringArgumentType.word())
										.executes(c -> {
											String key = StringArgumentType.getString(c, "clave");
											String val = StringArgumentType.getString(c, "valor");
											String err = Config.INSTANCE.set(key, val);
											if (err != null) {
												c.getSource().sendFailure(Component.literal(err));
												return 0;
											}
											ok(c.getSource(), "Listo: " + key + " = " + val);
											return 1;
										}))))
		);
	}

	private static void ok(CommandSourceStack src, String msg) {
		src.sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.YELLOW), false);
	}

	private static String help() {
		return "Pato, Pato, Ganso:\n"
				+ "/pg build (op) - construye la arena donde estás\n"
				+ "/pg join | leave - entrar o salir de la sala\n"
				+ "/pg start | stop (op) - empezar o parar\n"
				+ "/pg config [clave valor] (op) - ver o cambiar tiempos, rondas, jugadores...\n"
				+ "El que camina usa tinte amarillo = Pato, tinte blanco = GANSO sobre un jugador sentado.";
	}
}
