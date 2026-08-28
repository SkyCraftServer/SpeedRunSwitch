package cn.cutelittlesky.speedrunswitch.core.state;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.net.GameMessages;
import net.minecraft.network.chat.Component;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;

public class StartingState implements SpeedrunState {

	@Override
	public SpeedrunManager.Mode getMode() {
		return SpeedrunManager.Mode.STARTING;
	}

	@Override
	public void onEnter(SpeedrunManager manager) {
		MinecraftServer server = manager.getServer();
		ServerPlayer runner = manager.getActivePlayer();
		if (runner == null) {
			manager.transitionTo(new IdleState());
			return;
		}

		runner.setGameMode(GameType.SURVIVAL);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != runner) {
				player.setGameMode(GameType.SPECTATOR);
				manager.getShared().onPlayerJoin(player, runner);
				manager.getDebugGuard().apply(player, true);
				cn.cutelittlesky.speedrunswitch.svc.SvcBridge.onRoleChanged(player, false);
			} else {
				manager.getDebugGuard().apply(player, false);
				cn.cutelittlesky.speedrunswitch.svc.SvcBridge.onRoleChanged(player, true);
			}
		}

		GameMessages.broadcast(server, "event.starting.broadcast",
				runner.getGameProfile().name(), manager.getCountdownRemaining());
		GameMessages.broadcastTitle(server,
				MiniMessageUtils.tr("event.starting.title"),
				MiniMessageUtils.tr("event.starting.subtitle", runner.getGameProfile().name(), manager.getCountdownRemaining()),
				5, 40, 10);
	}

	@Override
	public void tick(SpeedrunManager manager) {
	}

	@Override
	public void tickSecond(SpeedrunManager manager) {
		MinecraftServer server = manager.getServer();
		int remaining = manager.getCountdownRemaining() - 1;
		manager.setCountdownRemaining(remaining);

		if (remaining <= 0) {
			GameMessages.broadcastSound(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
			GameMessages.broadcastTitle(server,
					MiniMessageUtils.tr("event.starting.go_title"),
					MiniMessageUtils.tr("event.starting.go_subtitle"), 0, 30, 10);
			GameMessages.broadcast(server, "event.starting.go_broadcast");
			manager.transitionTo(new RunningState());
		} else {
			GameMessages.broadcastSound(server, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f + (10 - remaining) * 0.05f);
			GameMessages.broadcastTitle(server,
					MiniMessageUtils.tr("event.starting.countdown_title", remaining),
					MiniMessageUtils.tr("event.starting.countdown_subtitle"), 0, 25, 5);
		}
	}
}