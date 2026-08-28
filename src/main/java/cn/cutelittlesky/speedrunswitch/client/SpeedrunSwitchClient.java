package cn.cutelittlesky.speedrunswitch.client;

import cn.cutelittlesky.speedrunswitch.net.SpeedrunStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public class SpeedrunSwitchClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SpeedrunStatePayload.TYPE, (payload, context) ->
				ClientSpeedrunState.update(payload.json()));
		SpeedrunHud.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null || client.gameMode == null) {
				return;
			}
			GameType mode = client.gameMode.getPlayerMode();

			if (mode == GameType.SURVIVAL || mode == GameType.CREATIVE) {
				if (client.getCameraEntity() != null && client.getCameraEntity() != client.player) {
					client.setCameraEntity(client.player);
				}
				return;
			}

			if (mode == GameType.SPECTATOR) {
				if (ClientSpeedrunState.isSpectatorRole() && (ClientSpeedrunState.isRunning() || ClientSpeedrunState.isCountdown())) {
					String runnerName = ClientSpeedrunState.getActivePlayer();
					if (!runnerName.isEmpty()) {
						if (client.getCameraEntity() == null || client.getCameraEntity() == client.player) {
							for (Player p : client.level.players()) {
								if (p.getGameProfile().name().equals(runnerName) && p != client.player) {
									client.setCameraEntity(p);
									break;
								}
							}
						}
					}
				}
			}
		});
	}
}