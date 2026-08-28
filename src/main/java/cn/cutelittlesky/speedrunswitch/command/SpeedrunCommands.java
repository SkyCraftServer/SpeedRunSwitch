package cn.cutelittlesky.speedrunswitch.command;

import cn.cutelittlesky.speedrunswitch.SpeedrunSwitchMod;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunStats;
import cn.cutelittlesky.speedrunswitch.core.CameraGuard;
import cn.cutelittlesky.speedrunswitch.net.GameMessages;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public final class SpeedrunCommands {

	private SpeedrunCommands() {}

	private static SpeedrunManager manager() {
		return SpeedrunSwitchMod.getManager();
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("speedrun")
				.then(Commands.literal("start")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("runner", EntityArgument.player())
								.executes(ctx -> executeStart(ctx.getSource(), EntityArgument.getPlayer(ctx, "runner"))))
						.executes(ctx -> executeStart(ctx.getSource(), ctx.getSource().getPlayer())))
				.then(Commands.literal("resume")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> executeResume(ctx.getSource())))
				.then(Commands.literal("switch")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("target", EntityArgument.player())
								.executes(ctx -> executeSwitch(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
						.executes(ctx -> executeSwitch(ctx.getSource(), null)))
				.then(Commands.literal("time")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("set")
								.then(Commands.argument("seconds", IntegerArgumentType.integer(0))
										.executes(ctx -> executeSetTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
						.then(Commands.literal("add")
								.then(Commands.argument("seconds", IntegerArgumentType.integer())
										.executes(ctx -> executeAddTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(0))
								.executes(ctx -> executeSetTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
				.then(Commands.literal("settime")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(0))
								.executes(ctx -> executeSetTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
				.then(Commands.literal("stats")
						.executes(ctx -> executeStats(ctx.getSource())))
				.then(Commands.literal("unstuck")
						.then(Commands.argument("target", EntityArgument.player())
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(ctx -> executeUnstuck(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
						.executes(ctx -> executeUnstuck(ctx.getSource(), ctx.getSource().getPlayer())))
				.then(Commands.literal("end")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> executeEnd(ctx.getSource())))
				.then(Commands.literal("reset")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> executeReset(ctx.getSource())))
				.then(Commands.literal("help")
						.executes(ctx -> sendHelp(ctx.getSource())))
				.executes(ctx -> sendHelp(ctx.getSource()));

		dispatcher.register(root);
	}

	private static int executeStart(CommandSourceStack source, ServerPlayer runner) {
		manager().startRun(source, runner);
		return 1;
	}

	private static int executeResume(CommandSourceStack source) {
		manager().resumeRun(source);
		return 1;
	}

	private static int executeSwitch(CommandSourceStack source, ServerPlayer target) {
		manager().manualSwitch(source, target);
		return 1;
	}

	private static int executeSetTime(CommandSourceStack source, int seconds) {
		manager().setSwitchTime(source, seconds);
		return 1;
	}

	private static int executeAddTime(CommandSourceStack source, int seconds) {
		manager().addSwitchTime(source, seconds);
		return 1;
	}

	private static int executeStats(CommandSourceStack source) {
		sendStats(source);
		return 1;
	}

	private static int executeUnstuck(CommandSourceStack source, ServerPlayer player) {
		if (player == null) {
			GameMessages.send(source, "command.unstuck.no_player");
			return 0;
		}
		SpeedrunManager manager = manager();
		ServerPlayer active = manager.getActivePlayer();
		if (active == null) {
			GameMessages.send(source, "command.unstuck.not_running");
			return 0;
		}

		manager.getShared().clearPendingTeleport(player.getUUID());

		if (player == active) {
			CameraGuard.runWithBypass(() -> player.setCamera(null));
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			if (player.connection != null) {
				player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(player));
				player.connection.resetPosition();
			}
			GameMessages.send(player, "command.unstuck.runner");
		} else {
			CameraGuard.runWithBypass(() -> player.setCamera(null));
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			if (player.connection != null) {
				player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(player));
			}
			if (player.level() != active.level() || player.distanceToSqr(active) > 64.0) {
				player.teleportTo(active.level(), active.getX(), active.getY(), active.getZ(),
						java.util.Set.of(), active.getYRot(), active.getXRot(), false);
			}

			player.level().getServer().execute(() -> {
				manager.getShared().bindCameraToActive(player, active);
			});
			GameMessages.send(player, "command.unstuck.spectator", active.getGameProfile().name());
		}
		return 1;
	}

	private static int executeEnd(CommandSourceStack source) {
		manager().endRun(source);
		return 1;
	}

	private static int executeReset(CommandSourceStack source) {
		manager().resetRun(source);
		return 1;
	}

	private static int sendHelp(CommandSourceStack source) {
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.header"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.start"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.resume"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.switch"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.time"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.stats"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.unstuck"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.end"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.reset"));
		source.sendSystemMessage(MiniMessageUtils.tr("command.help.footer"));
		return 1;
	}

	private static void sendStats(CommandSourceStack source) {
		SpeedrunManager manager = manager();
		SpeedrunStats stats = manager.getStats();
		source.sendSystemMessage(MiniMessageUtils.tr("command.stats.header"));
		if (stats.runStartTime == 0) {
			source.sendSystemMessage(MiniMessageUtils.tr("command.stats.status.idle"));
		} else if (stats.finished) {
			source.sendSystemMessage(MiniMessageUtils.tr("command.stats.status.finished"));
			source.sendSystemMessage(MiniMessageUtils.tr("command.stats.total_time", SpeedrunStats.formatDuration(stats.elapsedMs())));
		} else {
			source.sendSystemMessage(MiniMessageUtils.tr("command.stats.status.running"));
			source.sendSystemMessage(MiniMessageUtils.tr("command.stats.total_time", SpeedrunStats.formatDuration(stats.elapsedMs())));
		}
		if (stats.players.isEmpty()) {
			source.sendSystemMessage(MiniMessageUtils.tr("command.stats.no_data"));
		} else {
			for (Map.Entry<String, SpeedrunStats.PlayerEntry> e : stats.players.entrySet()) {
				SpeedrunStats.PlayerEntry entry = e.getValue();
				source.sendSystemMessage(MiniMessageUtils.tr("command.stats.player_row",
						entry.name,
						SpeedrunStats.formatSeconds(entry.activeSeconds),
						entry.deaths));
			}
		}
		source.sendSystemMessage(MiniMessageUtils.tr("command.stats.footer"));
	}
}