package cn.cutelittlesky.speedrunswitch.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;

public final class ClientSpeedrunState {

	private static String mode = "IDLE";
	private static String activePlayer = "";
	private static long secondsUntilSwitch;
	private static int countdown;
	private static String task = "";
	private static String taskType = "";
	private static int taskTarget;
	private static int taskProgress;
	private static String taskTargetId = "";
	private static int taskSecondsLeft;
	private static String elapsed = "";
	private static boolean canResume;
	private static int resumeRound;

	private ClientSpeedrunState() {}

	public static void update(String json) {
		try {
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			mode = obj.has("mode") ? obj.get("mode").getAsString() : "IDLE";
			activePlayer = obj.has("active") ? obj.get("active").getAsString() : "";
			secondsUntilSwitch = obj.has("secondsUntilSwitch") ? obj.get("secondsUntilSwitch").getAsLong() : 0;
			countdown = obj.has("countdown") ? obj.get("countdown").getAsInt() : 0;
			task = obj.has("task") ? obj.get("task").getAsString() : "";
			taskType = obj.has("taskType") ? obj.get("taskType").getAsString() : "";
			taskTarget = obj.has("taskTarget") ? obj.get("taskTarget").getAsInt() : 0;
			taskProgress = obj.has("taskProgress") ? obj.get("taskProgress").getAsInt() : 0;
			taskTargetId = obj.has("taskTargetId") ? obj.get("taskTargetId").getAsString() : "";
			taskSecondsLeft = obj.has("taskSecondsLeft") ? obj.get("taskSecondsLeft").getAsInt() : 0;
			elapsed = obj.has("elapsed") ? obj.get("elapsed").getAsString() : "";
			canResume = obj.has("canResume") && obj.get("canResume").getAsBoolean();
			resumeRound = obj.has("resumeRound") ? obj.get("resumeRound").getAsInt() : 1;
		} catch (Exception ignored) {
		}
	}

	public static boolean isStarting() {
		return "STARTING".equals(mode);
	}

	public static boolean isRunning() {
		return "RUNNING".equals(mode);
	}

	public static boolean isCountdown() {
		return "COUNTDOWN".equals(mode);
	}

	public static boolean isFinished() {
		return "FINISHED".equals(mode);
	}

	public static boolean isIdle() {
		return "IDLE".equals(mode);
	}

	public static boolean isActiveRunner() {
		if (!isRunning() && !isCountdown() && !isStarting()) {
			return false;
		}
		if (activePlayer.isEmpty()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		String own = mc.player == null ? "" : mc.player.getGameProfile().name();
		return own.equals(activePlayer);
	}

	public static boolean isSpectatorRole() {
		if (!isRunning() && !isCountdown() && !isStarting()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		if (!activePlayer.isEmpty()) {
			String own = mc.player.getGameProfile().name();
			return !own.equals(activePlayer);
		}
		return mc.player.isSpectator();
	}

	public static String getMode() { return mode; }
	public static String getActivePlayer() { return activePlayer; }
	public static long getSecondsUntilSwitch() { return secondsUntilSwitch; }
	public static int getCountdown() { return countdown; }
	public static String getTask() { return task; }
	public static int getTaskSecondsLeft() { return taskSecondsLeft; }
	public static String getElapsed() { return elapsed; }
	public static boolean canResume() { return canResume; }
	public static int getResumeRound() { return resumeRound; }

	public static Component getTaskComponent() {
		if (taskType.isEmpty()) {
			return Component.literal(task);
		}
		return switch (taskType) {
			case "kill_hostile" -> MiniMessageUtils.tr("task.kill_hostile", taskTarget, taskProgress, taskTarget);
			case "kill_enderman" -> MiniMessageUtils.tr("task.kill_enderman", taskTarget, taskProgress, taskTarget);
			case "mine_stone" -> MiniMessageUtils.tr("task.mine_stone", taskTarget, taskProgress, taskTarget);
			case "deal_damage" -> MiniMessageUtils.tr("task.deal_damage", taskTarget, taskProgress, taskTarget);
			case "pickup_food" -> MiniMessageUtils.tr("task.pickup_food", taskTarget, taskProgress, taskTarget);
			case "enter_dimension" -> MiniMessageUtils.tr("task.enter_dimension");
			case "kill_entity" -> MiniMessageUtils.tr("task.kill_entity", taskTarget,
					BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(taskTargetId))
							.map(e -> (Component) Component.translatable(e.getDescriptionId()))
							.orElseGet(() -> Component.literal(taskTargetId)),
					taskProgress, taskTarget);
			case "collect_item" -> MiniMessageUtils.tr("task.collect_item", taskTarget,
					BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(taskTargetId))
							.map(i -> (Component) Component.translatable(i.getDescriptionId()))
							.orElseGet(() -> Component.literal(taskTargetId)),
					taskProgress, taskTarget);
			case "mine_block" -> MiniMessageUtils.tr("task.mine_block", taskTarget,
					BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(taskTargetId))
							.map(b -> (Component) Component.translatable(b.getDescriptionId()))
							.orElseGet(() -> Component.literal(taskTargetId)),
					taskProgress, taskTarget);
			default -> Component.literal(task);
		};
	}

	public static String formatSeconds(long seconds) {
		if (seconds >= 60) {
			return MiniMessageUtils.tr("hud.time.minute_second", seconds / 60, seconds % 60).getString();
		}
		return MiniMessageUtils.tr("hud.time.second", seconds).getString();
	}
}