package cn.cutelittlesky.speedrunswitch.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SpeedrunStats {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public long runStartTime;
	public boolean finished;
	public long totalActiveSeconds;
	public long dragonKillElapsedMs;
	public int currentRound = 1;
	public String activePlayerId;
	public String previousRunnerId;
	public RespawnSnapshot sharedRespawn;
	public Map<String, PlayerEntry> players = new LinkedHashMap<>();

	public static class RespawnSnapshot {
		public String dimension = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public float yaw;
		public float pitch;
		public boolean forced;

		public RespawnSnapshot() {}

		public RespawnSnapshot(String dimension, int x, int y, int z, float yaw, float pitch, boolean forced) {
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
			this.forced = forced;
		}

		public static RespawnSnapshot fromRespawnConfig(ServerPlayer.RespawnConfig config) {
			if (config == null || config.respawnData() == null) {
				return null;
			}
			LevelData.RespawnData data = config.respawnData();
			return new RespawnSnapshot(
					data.dimension().identifier().toString(),
					data.pos().getX(),
					data.pos().getY(),
					data.pos().getZ(),
					data.yaw(),
					data.pitch(),
					config.forced()
			);
		}

		public ServerPlayer.RespawnConfig toRespawnConfig() {
			try {
				Identifier dimId = Identifier.parse(dimension);
				ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
				BlockPos pos = new BlockPos(x, y, z);
				LevelData.RespawnData data = LevelData.RespawnData.of(dimKey, pos, yaw, pitch);
				return new ServerPlayer.RespawnConfig(data, forced);
			} catch (Exception e) {
				return null;
			}
		}
	}

	public static class PlayerEntry {
		public String name = "";
		public long activeSeconds;
		public long spectatorSeconds;
		public int lastActiveRound;
		public int joinRound = 1;
		public int deaths;

		public PlayerEntry() {}

		public PlayerEntry(String name) {
			this.name = name;
		}
	}

	private transient Path filePath;

	public static SpeedrunStats load(MinecraftServer server) {
		SpeedrunStats stats = new SpeedrunStats();
		stats.filePath = stats.path(server);
		if (Files.exists(stats.filePath)) {
			try {
				SpeedrunStats loaded = GSON.fromJson(Files.readString(stats.filePath, StandardCharsets.UTF_8), SpeedrunStats.class);
				if (loaded != null) {
					loaded.filePath = stats.filePath;
					if (loaded.players == null) {
						loaded.players = new LinkedHashMap<>();
					}
					return loaded;
				}
			} catch (Exception e) {
				System.err.println("[speedrun-switch] 读取统计文件失败: " + e.getMessage());
			}
		}
		return stats;
	}

	private Path path(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("speedrun_switch").resolve("stats.json");
	}

	public void save() {
		if (filePath == null) {
			return;
		}
		try {
			Files.createDirectories(filePath.getParent());
			Files.writeString(filePath, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[speedrun-switch] 写入统计文件失败: " + e.getMessage());
		}
	}

	public PlayerEntry entry(ServerPlayer player) {
		PlayerEntry e = entry(player.getUUID(), player.getGameProfile().name());
		e.name = player.getGameProfile().name();
		return e;
	}

	public PlayerEntry entry(UUID uuid, String name) {
		return players.computeIfAbsent(uuid.toString(), k -> {
			PlayerEntry pe = new PlayerEntry(name);
			pe.joinRound = Math.max(1, currentRound);
			return pe;
		});
	}

	public void addActiveSecond(ServerPlayer player) {
		entry(player).activeSeconds++;
		totalActiveSeconds++;
	}

	public void addSpectatorSecond(ServerPlayer player) {
		entry(player).spectatorSeconds++;
	}

	public void resetSpectatorSeconds(ServerPlayer player) {
		entry(player).spectatorSeconds = 0;
	}

	public void incrementDeaths(ServerPlayer player) {
		entry(player).deaths++;
	}

	public void startRun() {
		players.clear();
		runStartTime = System.currentTimeMillis();
		totalActiveSeconds = 0;
		finished = false;
		dragonKillElapsedMs = 0;
		currentRound = 1;
		activePlayerId = null;
		previousRunnerId = null;
		sharedRespawn = null;
	}

	public void reset() {
		startRun();
	}

	public void finishRun() {
		if (!finished) {
			finished = true;
			dragonKillElapsedMs = elapsedSeconds() * 1000L;
		}
	}

	public long elapsedSeconds() {
		if (totalActiveSeconds > 0) {
			return totalActiveSeconds;
		}
		long sum = 0;
		for (PlayerEntry p : players.values()) {
			sum += p.activeSeconds;
		}
		return sum;
	}

	public long elapsedMs() {
		if (runStartTime == 0 && totalActiveSeconds == 0 && players.isEmpty()) {
			return 0;
		}
		return elapsedSeconds() * 1000L;
	}

	public static String formatDuration(long ms) {
		long totalSeconds = ms / 1000;
		return formatSeconds(totalSeconds);
	}

	public static String formatSeconds(long seconds) {
		return (seconds / 60) + "分" + (seconds % 60) + "秒";
	}
}