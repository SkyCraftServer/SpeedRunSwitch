package cn.cutelittlesky.speedrunswitch.net;

import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

public final class GameMessages {

	private GameMessages() {}

	public static MutableComponent prefix(ServerPlayer player) {
		return MiniMessageUtils.trForPlayer(player, "mod.prefix");
	}

	public static MutableComponent prefix() {
		return MiniMessageUtils.tr("mod.prefix");
	}

	public static Component msgFor(ServerPlayer player, String key, Object... args) {
		return prefix(player).append(MiniMessageUtils.trForPlayer(player, key, args));
	}

	public static Component msg(String key, Object... args) {
		return prefix().append(MiniMessageUtils.tr(key, args));
	}

	public static Component msg(Component component) {
		return prefix().append(component);
	}

	public static Component info(String key, Object... args) {
		return prefix().append(MiniMessageUtils.tr(key, args));
	}

	public static Component info(Component component) {
		return prefix().append(component);
	}

	public static Component success(String key, Object... args) {
		return prefix().append(MiniMessageUtils.tr(key, args));
	}

	public static Component success(Component component) {
		return prefix().append(component);
	}

	public static Component warn(String key, Object... args) {
		return prefix().append(MiniMessageUtils.tr(key, args));
	}

	public static Component warn(Component component) {
		return prefix().append(component);
	}

	public static Component error(String key, Object... args) {
		return prefix().append(MiniMessageUtils.tr(key, args));
	}

	public static Component error(Component component) {
		return prefix().append(component);
	}

	public static Component miniMessage(String miniMessageText) {
		return MiniMessageUtils.parse(miniMessageText);
	}

	public static void send(CommandSourceStack source, Component component) {
		source.sendSystemMessage(component);
	}

	public static void send(CommandSourceStack source, String key, Object... args) {
		ServerPlayer player = source.getPlayer();
		source.sendSystemMessage(prefix(player).append(MiniMessageUtils.trForPlayer(player, key, args)));
	}

	public static void send(ServerPlayer player, Component component) {
		player.sendSystemMessage(component);
	}

	public static void send(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(prefix(player).append(MiniMessageUtils.trForPlayer(player, key, args)));
	}

	public static void broadcast(MinecraftServer server, Component component) {
		server.getPlayerList().broadcastSystemMessage(component, false);
	}

	public static void broadcast(MinecraftServer server, String key, Object... args) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(prefix(player).append(MiniMessageUtils.trForPlayer(player, key, args)));
		}
		server.sendSystemMessage(prefix(null).append(MiniMessageUtils.trFor("zh_cn", key, args)));
	}

	public static void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		if (title != null) {
			player.connection.send(new ClientboundSetTitleTextPacket(title));
		}
		if (subtitle != null) {
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
	}

	public static void sendTitle(ServerPlayer player, String titleKey, String subtitleKey, int fadeIn, int stay, int fadeOut, Object... subtitleArgs) {
		Component title = titleKey != null ? MiniMessageUtils.trForPlayer(player, titleKey) : null;
		Component subtitle = subtitleKey != null ? MiniMessageUtils.trForPlayer(player, subtitleKey, subtitleArgs) : null;
		sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
	}

	public static void broadcastTitle(MinecraftServer server, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
		}
	}

	public static void broadcastTitle(MinecraftServer server, String titleKey, String subtitleKey, int fadeIn, int stay, int fadeOut, Object... subtitleArgs) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Component title = titleKey != null ? MiniMessageUtils.trForPlayer(player, titleKey) : null;
			Component subtitle = subtitleKey != null ? MiniMessageUtils.trForPlayer(player, subtitleKey, subtitleArgs) : null;
			sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
		}
	}

	public static void broadcastSound(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.playSound(sound, volume, pitch);
		}
	}
}