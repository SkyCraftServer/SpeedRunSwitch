package cn.cutelittlesky.speedrunswitch.core.state;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunStats;
import cn.cutelittlesky.speedrunswitch.core.CameraGuard;
import cn.cutelittlesky.speedrunswitch.net.GameMessages;
import cn.cutelittlesky.speedrunswitch.svc.SvcBridge;
import net.minecraft.network.chat.Component;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;

public class FinishedState implements SpeedrunState {

	@Override
	public SpeedrunManager.Mode getMode() {
		return SpeedrunManager.Mode.FINISHED;
	}

	@Override
	public void onEnter(SpeedrunManager manager) {
		MinecraftServer server = manager.getServer();
		ServerPlayer active = manager.getActivePlayer();

		if (manager.getTaskManager() != null) {
			manager.getTaskManager().onRunEnd();
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CameraGuard.runWithBypass(() -> {
				player.setCamera(null);
			});
			player.setGameMode(GameType.SURVIVAL);
			manager.getDebugGuard().apply(player, false);
			SvcBridge.onRoleChanged(player, true);
			if (active != null) {
				manager.getShared().syncInventoryTo(player, active);
			}
		}

		SvcBridge.onModeEnd();

		manager.getStats().finishRun();
		manager.getStats().save();

		GameMessages.broadcastTitle(server,
				MiniMessageUtils.tr("event.dragon_kill.title"),
				MiniMessageUtils.tr("event.dragon_kill.subtitle", SpeedrunStats.formatDuration(manager.getStats().elapsedMs())),
				10, 80, 20);
		GameMessages.broadcastSound(server, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
	}
}

