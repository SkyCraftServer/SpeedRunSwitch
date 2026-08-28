package cn.cutelittlesky.speedrunswitch.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class DebugInfoGuard {

	private final MinecraftServer server;

	public DebugInfoGuard(MinecraftServer server) {
		this.server = server;
	}

	public void apply(ServerPlayer player, boolean spectator) {
		player.setReducedDebugInfo(spectator);
	}

	public void tickGuard(SpeedrunManager manager) {
		if (manager.getActivePlayer() == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (manager.isSpectator(player)) {
				if (!player.isReducedDebugInfo()) {
					apply(player, true);
				}
			} else if (player.isReducedDebugInfo()) {
				apply(player, false);
			}
		}
	}
}



