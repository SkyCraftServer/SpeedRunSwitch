package cn.cutelittlesky.speedrunswitch.core.state;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.net.GameMessages;
import cn.cutelittlesky.speedrunswitch.svc.SvcBridge;
import net.minecraft.network.chat.Component;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

public class CountdownState implements SpeedrunState {

	@Override
	public SpeedrunManager.Mode getMode() {
		return SpeedrunManager.Mode.COUNTDOWN;
	}

	@Override
	public void onEnter(SpeedrunManager manager) {
		ServerPlayer target = manager.selectPendingTarget();
		if (target == null) {
			GameMessages.broadcast(manager.getServer(), GameMessages.warn("event.countdown.skip_insufficient",
					manager.getConfig().minimumPlayersForSwitch));
			manager.resetSwitchTimer();
			manager.transitionTo(new RunningState());
			return;
		}

		manager.setCountdownRemaining(manager.getConfig().countdownSeconds);
		manager.setPendingTargetId(target.getUUID());
		GameMessages.broadcast(manager.getServer(), GameMessages.warn("event.countdown.start_broadcast",
				manager.getCountdownRemaining(), target.getGameProfile().name()));
	}

	@Override
	public void tick(SpeedrunManager manager) {
	}

	@Override
	public void tickFast(SpeedrunManager manager) {
		MinecraftServer server = manager.getServer();
		ServerPlayer active = manager.getActivePlayer();
		if (active == null) {
			return;
		}
		manager.getShared().syncInventoryToSpectators(server);
	}

	@Override
	public void tickSecond(SpeedrunManager manager) {
		MinecraftServer server = manager.getServer();
		ServerPlayer active = manager.getActivePlayer();
		if (active != null) {
			manager.getShared().recordActivePosition(active);
			manager.getShared().syncSpectatorPositions(server);
			manager.getStats().addActiveSecond(active);
		}

		for (ServerPlayer spectator : manager.spectators()) {
			manager.getStats().addSpectatorSecond(spectator);
		}

		manager.getDebugGuard().tickGuard(manager);
		SvcBridge.tick(server, manager);

		int remaining = manager.getCountdownRemaining() - 1;
		manager.setCountdownRemaining(remaining);

		if (remaining <= 0) {
			manager.doCountdownSwitch();
		} else {
			GameMessages.broadcastSound(server, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f + (10 - remaining) * 0.05f);
			GameMessages.broadcastTitle(server,
					MiniMessageUtils.tr("event.countdown.title", remaining),
					MiniMessageUtils.tr("event.countdown.subtitle"), 0, 25, 5);
		}
	}
}