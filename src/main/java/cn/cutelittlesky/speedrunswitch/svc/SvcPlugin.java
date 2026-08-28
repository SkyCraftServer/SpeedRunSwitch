package cn.cutelittlesky.speedrunswitch.svc;

import cn.cutelittlesky.speedrunswitch.SpeedrunSwitchMod;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class SvcPlugin implements VoicechatPlugin {

	private static volatile String spectatorGroupName;
	private static volatile VoicechatServerApi serverApi;

	@Override
	public String getPluginId() {
		return "speedrun-switch";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
			serverApi = event.getVoicechat();
			System.out.println("[speedrun-switch] Simple Voice Chat 已连接，观众频道与语音隔离就绪");
		});
		registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
			serverApi = null;
		});

		registration.registerEvent(MicrophonePacketEvent.class, event -> {
			if (isRunnerConnection(event.getSenderConnection())) {
				event.cancel();
			}
		});
		registration.registerEvent(EntitySoundPacketEvent.class, event -> {
			if (isRunnerConnection(event.getReceiverConnection()) || isRunnerConnection(event.getSenderConnection())) {
				event.cancel();
			}
		});
		registration.registerEvent(LocationalSoundPacketEvent.class, event -> {
			if (isRunnerConnection(event.getReceiverConnection()) || isRunnerConnection(event.getSenderConnection())) {
				event.cancel();
			}
		});
		registration.registerEvent(StaticSoundPacketEvent.class, event -> {
			if (isRunnerConnection(event.getReceiverConnection()) || isRunnerConnection(event.getSenderConnection())) {
				event.cancel();
			}
		});
	}

	private static boolean isRunnerConnection(VoicechatConnection connection) {
		if (connection == null || connection.getPlayer() == null) {
			return false;
		}
		SpeedrunManager manager = SpeedrunSwitchMod.getManager();
		if (manager == null) {
			return false;
		}
		SpeedrunManager.Mode mode = manager.getMode();
		if (mode != SpeedrunManager.Mode.RUNNING && mode != SpeedrunManager.Mode.COUNTDOWN && mode != SpeedrunManager.Mode.STARTING) {
			return false;
		}
		UUID activeId = manager.getActivePlayerId();
		return activeId != null && activeId.equals(connection.getPlayer().getUuid());
	}

	public static void onModeStart(String groupName) {
		spectatorGroupName = groupName;
		VoicechatServerApi api = serverApi;
		if (api == null) {
			System.out.println("[speedrun-switch] SVC 尚未就绪，跳过观众频道创建");
			return;
		}
		System.out.println("[speedrun-switch] 创建观众频道: " + groupName);
		findOrCreateGroup(api, groupName);
	}

	public static void onModeEnd() {
		VoicechatServerApi api = serverApi;
		String name = spectatorGroupName;
		spectatorGroupName = null;
		if (api == null || name == null) {
			return;
		}
		for (Group group : api.getGroups()) {
			if (group.getName().equals(name)) {
				api.removeGroup(group.getId());
			}
		}
	}

	public static void onRoleChanged(ServerPlayer player, boolean active) {
		VoicechatServerApi api = serverApi;
		if (api == null || spectatorGroupName == null) {
			return;
		}
		VoicechatConnection conn = connectionOf(api, player);
		if (conn == null) {
			return;
		}
		if (active) {
			if (conn.isInGroup()) {
				conn.setGroup(null);
			}
		} else if (!conn.isInGroup() || !conn.getGroup().getName().equals(spectatorGroupName)) {
			Group group = findOrCreateGroup(api, spectatorGroupName);
			conn.setGroup(group);
		}
	}

	public static void tick(MinecraftServer server, SpeedrunManager manager) {
		if (serverApi == null || spectatorGroupName == null) {
			return;
		}
		ServerPlayer active = manager.getActivePlayer();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean spectator = manager.isSpectator(player);
			VoicechatConnection conn = connectionOf(serverApi, player);
			if (conn == null) {
				continue;
			}
			if (spectator) {
				if (!conn.isInGroup() || !conn.getGroup().getName().equals(spectatorGroupName)) {
					Group group = findOrCreateGroup(serverApi, spectatorGroupName);
					conn.setGroup(group);
				}
			} else if (active != null && conn.isInGroup()) {
				conn.setGroup(null);
			}
		}
	}

	private static VoicechatConnection connectionOf(VoicechatServerApi api, ServerPlayer player) {
		try {
			return api.getConnectionOf(api.fromServerPlayer(player));
		} catch (Exception e) {
			return null;
		}
	}

	private static Group findOrCreateGroup(VoicechatServerApi api, String name) {
		for (Group group : api.getGroups()) {
			if (group.getName().equals(name)) {
				return group;
			}
		}
		return api.createGroup(name, null);
	}
}
