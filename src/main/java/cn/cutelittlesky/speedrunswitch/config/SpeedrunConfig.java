package cn.cutelittlesky.speedrunswitch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpeedrunConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int switchIntervalMinutes = 10;
	public int countdownSeconds = 10;
	public int startCountdownSeconds = 3;
	public boolean autoSwitchEnabled = true;
	public int minimumPlayersForSwitch = 2;
	public boolean taskEnabled = true;
	public int taskIntervalSecondsMin = 0;
	public int taskIntervalSecondsMax = 300;
	public List<TaskTemplate> taskTemplates = defaultTaskTemplates();
	public int taskTimeLimitSeconds = 120;
	public int taskRewardSeconds = 30;
	public int taskPenaltySeconds = 30;
	public String taskPenaltyMode = "SLOWNESS";
	public int taskSlownessDurationSeconds = 20;
	public int taskSlownessAmplifier = 2;
	public boolean voiceChatEnabled = true;
	public String spectatorGroupName = "观众频道";
	public boolean blockDebugForSpectators = true;

	public static SpeedrunConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("speedrun-switch.json");
		SpeedrunConfig config = new SpeedrunConfig();
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
				config.switchIntervalMinutes = intField(obj, "switchIntervalMinutes", config.switchIntervalMinutes);
				config.countdownSeconds = intField(obj, "countdownSeconds", config.countdownSeconds);
				config.startCountdownSeconds = intField(obj, "startCountdownSeconds", config.startCountdownSeconds);
				config.autoSwitchEnabled = boolField(obj, "autoSwitchEnabled", config.autoSwitchEnabled);
				config.minimumPlayersForSwitch = intField(obj, "minimumPlayersForSwitch", config.minimumPlayersForSwitch);
				config.taskEnabled = boolField(obj, "taskEnabled", config.taskEnabled);
				config.taskIntervalSecondsMin = intField(obj, "taskIntervalSecondsMin", config.taskIntervalSecondsMin);
				config.taskIntervalSecondsMax = intField(obj, "taskIntervalSecondsMax", config.taskIntervalSecondsMax);
				config.taskTimeLimitSeconds = intField(obj, "taskTimeLimitSeconds", config.taskTimeLimitSeconds);
				config.taskRewardSeconds = intField(obj, "taskRewardSeconds", config.taskRewardSeconds);
				config.taskPenaltySeconds = intField(obj, "taskPenaltySeconds", config.taskPenaltySeconds);
				config.taskPenaltyMode = strField(obj, "taskPenaltyMode", config.taskPenaltyMode);
				config.taskTemplates = taskTemplatesField(obj, config.taskTemplates);
				config.taskSlownessDurationSeconds = intField(obj, "taskSlownessDurationSeconds", config.taskSlownessDurationSeconds);
				config.taskSlownessAmplifier = intField(obj, "taskSlownessAmplifier", config.taskSlownessAmplifier);
				config.voiceChatEnabled = boolField(obj, "voiceChatEnabled", config.voiceChatEnabled);
				config.spectatorGroupName = strField(obj, "spectatorGroupName", config.spectatorGroupName);
				config.blockDebugForSpectators = boolField(obj, "blockDebugForSpectators", config.blockDebugForSpectators);
			} catch (Exception e) {
				System.err.println("[speedrun-switch] 配置文件解析失败，使用默认配置: " + e.getMessage());
			}
		} else {
			save(config);
		}
		return config;
	}

	public static class TaskTemplate {
		public String id;
		public int count;
		public int weight;
		public String target = "";

		public TaskTemplate() {}

		public TaskTemplate(String id, int count, int weight) {
			this.id = id;
			this.count = count;
			this.weight = weight;
		}

		public TaskTemplate(String id, String target, int count, int weight) {
			this.id = id;
			this.target = target;
			this.count = count;
			this.weight = weight;
		}
	}

	public static List<TaskTemplate> defaultTaskTemplates() {
		List<TaskTemplate> list = new ArrayList<>();
		list.add(new TaskTemplate("kill_hostile", 3, 1));
		list.add(new TaskTemplate("kill_enderman", 1, 1));
		list.add(new TaskTemplate("mine_stone", 20, 1));
		list.add(new TaskTemplate("deal_damage", 60, 1));
		list.add(new TaskTemplate("pickup_food", 8, 1));
		list.add(new TaskTemplate("enter_dimension", 1, 1));
		return list;
	}

	public static void save(SpeedrunConfig config) {
		try {
			Path path = FabricLoader.getInstance().getConfigDir().resolve("speedrun-switch.json");
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[speedrun-switch] 写入配置文件失败: " + e.getMessage());
		}
	}

	private static int intField(JsonObject obj, String key, int def) {
		try {
			return obj.has(key) ? obj.get(key).getAsInt() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static boolean boolField(JsonObject obj, String key, boolean def) {
		try {
			return obj.has(key) ? obj.get(key).getAsBoolean() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static String strField(JsonObject obj, String key, String def) {
		try {
			return obj.has(key) ? obj.get(key).getAsString() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static List<TaskTemplate> taskTemplatesField(JsonObject obj, List<TaskTemplate> def) {
		if (!obj.has("taskTemplates") || !obj.get("taskTemplates").isJsonArray()) {
			return def;
		}
		List<TaskTemplate> list = new ArrayList<>();
		for (JsonElement el : obj.getAsJsonArray("taskTemplates")) {
			try {
				JsonObject o = el.getAsJsonObject();
				TaskTemplate t = new TaskTemplate();
				t.id = o.has("id") ? o.get("id").getAsString() : "";
				t.target = o.has("target") ? o.get("target").getAsString() : "";
				t.count = o.has("count") ? o.get("count").getAsInt() : 1;
				t.weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
				if (!t.id.isEmpty() && t.count > 0 && t.weight > 0) {
					list.add(t);
				}
			} catch (Exception ignored) {
			}
		}
		return list;
	}
}



