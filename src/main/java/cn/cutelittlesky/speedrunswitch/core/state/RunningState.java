package cn.cutelittlesky.speedrunswitch.core.state;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.svc.SvcBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class RunningState implements SpeedrunState {

	@Override
	public SpeedrunManager.Mode getMode() {
		return SpeedrunManager.Mode.RUNNING;
	}

	@Override
	public void onEnter(SpeedrunManager manager) {
		manager.setPendingTargetId(null);
		manager.setCountdownRemaining(0);
		if (manager.getNextSwitchAt() <= System.currentTimeMillis()) {
			manager.resetSwitchTimer();
		}
	}

	@Override
	public void tick(SpeedrunManager manager) {
		if (manager.getTaskManager() != null) {
			manager.getTaskManager().tick();
		}
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
		if (active == null) {
			return;
		}

		manager.getShared().recordActivePosition(active);
		manager.getShared().syncSpectatorPositions(server);

		manager.getDebugGuard().tickGuard(manager);
		SvcBridge.tick(server, manager);

		manager.getStats().addActiveSecond(active);

		for (ServerPlayer spectator : manager.spectators()) {
			manager.getStats().addSpectatorSecond(spectator);
		}

		if (manager.getConfig().autoSwitchEnabled && System.currentTimeMillis() >= manager.getNextSwitchAt()) {
			manager.transitionTo(new CountdownState());
			return;
		}

		if (manager.getTaskManager() != null) {
			manager.getTaskManager().tickSecond();
		}
	}
}