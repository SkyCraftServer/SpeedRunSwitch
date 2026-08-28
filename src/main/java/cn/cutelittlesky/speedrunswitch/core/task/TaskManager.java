package cn.cutelittlesky.speedrunswitch.core.task;

import com.google.gson.JsonObject;
import cn.cutelittlesky.speedrunswitch.config.SpeedrunConfig;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunEventBus;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunStateChangedEvent;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunSwitchEvent;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunTaskEvent;
import cn.cutelittlesky.speedrunswitch.net.GameMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;
import java.util.UUID;

public class TaskManager {

	private final SpeedrunManager manager;
	private final Random random = new Random();

	private SpeedrunTask currentTask;
	private long nextTaskAt;
	private boolean roundTaskIssued;
	private int taskSecondsLeft;
	private int lastFoodCount = -1;
	private UUID lastFoodOwner;

	public TaskManager(SpeedrunManager manager) {
		this.manager = manager;
		initEventBusListeners();
	}

	private void initEventBusListeners() {
		SpeedrunEventBus.register(SpeedrunStateChangedEvent.class, event -> {
			if (event.newMode() == SpeedrunManager.Mode.RUNNING && event.oldMode() == SpeedrunManager.Mode.STARTING) {
				onRunStart();
			} else if (event.newMode() == SpeedrunManager.Mode.IDLE || event.newMode() == SpeedrunManager.Mode.FINISHED) {
				onRunEnd();
			}
		});

		SpeedrunEventBus.register(SpeedrunSwitchEvent.class, event -> {
			onSwitch();
		});
	}

	public void onRunStart() {
		currentTask = null;
		lastFoodCount = -1;
		roundTaskIssued = false;
		scheduleNext();
	}

	public void onRunEnd() {
		if (currentTask != null) {
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.CANCELLED));
		}
		currentTask = null;
		lastFoodCount = -1;
		roundTaskIssued = true;
	}

	public void onSwitch() {
		if (currentTask != null) {
			String text = currentTask.getDisplayText();
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.CANCELLED));
			currentTask = null;
			lastFoodCount = -1;
			GameMessages.broadcast(manager.getServer(), "task.switch_cancelled", text);
		}
		roundTaskIssued = false;
		scheduleNext();
	}

	public void tick() {
		if (currentTask == null) {
			return;
		}
		java.util.function.Predicate<ItemStack> matcher;
		if (currentTask instanceof SpeedrunTask.PickupFoodTask) {
			matcher = s -> s.get(DataComponents.FOOD) != null;
		} else if (currentTask instanceof SpeedrunTask.CollectItemTask collect) {
			matcher = collect::matches;
		} else {
			return;
		}
		ServerPlayer active = manager.getActivePlayer();
		if (active == null) {
			return;
		}
		if (!active.getUUID().equals(lastFoodOwner)) {
			lastFoodOwner = active.getUUID();
			lastFoodCount = -1;
		}
		int count = countMatchingItems(active, matcher);
		if (lastFoodCount >= 0 && count > lastFoodCount) {
			currentTask.addProgress(count - lastFoodCount);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
			checkComplete();
		}
		lastFoodCount = count;
	}

	private int countMatchingItems(ServerPlayer player, java.util.function.Predicate<ItemStack> matcher) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && matcher.test(stack)) {
				count += stack.getCount();
			}
		}
		ItemStack offhand = player.getInventory().getItem(Inventory.SLOT_OFFHAND);
		if (!offhand.isEmpty() && matcher.test(offhand)) {
			count += offhand.getCount();
		}
		return count;
	}

	public void tickSecond() {
		if (!manager.getConfig().taskEnabled) {
			return;
		}
		if (manager.getMode() != SpeedrunManager.Mode.RUNNING) {
			return;
		}
		if (currentTask != null) {
			taskSecondsLeft--;
			if (taskSecondsLeft <= 0) {
				penalize();
			}
			return;
		}
		if (!roundTaskIssued && System.currentTimeMillis() >= nextTaskAt) {
			issueTask();
		}
	}

	private void issueTask() {
		roundTaskIssued = true;
		SpeedrunConfig config = manager.getConfig();

		int remainingSwitchSeconds = (int) manager.secondsUntilSwitch();
		if (remainingSwitchSeconds <= 15) {
			return;
		}

		SpeedrunTask task = SpeedrunTask.randomTask(random, config.taskTemplates);
		if (task == null) {
			return;
		}
		currentTask = task;

		lastFoodCount = -1;
		lastFoodOwner = null;

		int baseTimeLimit = Math.max(10, config.taskTimeLimitSeconds);
		taskSecondsLeft = Math.min(baseTimeLimit, Math.max(10, remainingSwitchSeconds - 5));

		SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.ASSIGNED));

		GameMessages.broadcast(manager.getServer(), GameMessages.info("新任务发放！"
				+ currentTask.getDisplayText() + "（时限：" + taskSecondsLeft + " 秒）"));
	}

	private void completeTask() {
		if (currentTask == null) {
			return;
		}
		SpeedrunConfig config = manager.getConfig();
		manager.addSwitchTime(config.taskRewardSeconds);
		GameMessages.broadcast(manager.getServer(), GameMessages.success("任务完成！" + currentTask.getDisplayText()
				+ "，奖励 +" + config.taskRewardSeconds + " 秒操纵时间"));
		SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.COMPLETED));
		currentTask = null;
		lastFoodCount = -1;
		manager.getStats().save();
	}

	private void penalize() {
		if (currentTask == null) {
			return;
		}
		SpeedrunConfig config = manager.getConfig();
		String text = currentTask.getDisplayText();
		SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.TIMEOUT));
		currentTask = null;
		lastFoodCount = -1;

		if ("TIME".equalsIgnoreCase(config.taskPenaltyMode)) {
			manager.subtractSwitchTime(config.taskPenaltySeconds);
			GameMessages.broadcast(manager.getServer(), GameMessages.error("任务超时：" + text
					+ "，惩罚：下次切换提前 " + config.taskPenaltySeconds + " 秒"));
		} else {
			ServerPlayer active = manager.getActivePlayer();
			if (active != null) {
				active.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
						config.taskSlownessDurationSeconds * 20, config.taskSlownessAmplifier));
			}
			GameMessages.broadcast(manager.getServer(), GameMessages.error("任务超时：" + text
					+ "，惩罚：活动玩家获得缓慢效果 " + config.taskSlownessDurationSeconds + " 秒"));
		}
		manager.getStats().save();
	}

	private void scheduleNext() {
		SpeedrunConfig config = manager.getConfig();
		long min = Math.max(0, config.taskIntervalSecondsMin);
		long max = Math.max(min, config.taskIntervalSecondsMax);
		nextTaskAt = System.currentTimeMillis()
				+ (min + random.nextInt((int) (max - min) + 1)) * 1000L;
	}

	public void onEntityDeath(LivingEntity entity, DamageSource source) {
		if (currentTask == null || entity == manager.getActivePlayer()) {
			return;
		}
		if (!(source.getEntity() instanceof ServerPlayer killer) || killer != manager.getActivePlayer()) {
			return;
		}
		if (currentTask instanceof SpeedrunTask.KillHostileTask && entity instanceof Monster) {
			currentTask.addProgress(1);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
		}
		if (currentTask instanceof SpeedrunTask.KillEndermanTask && entity instanceof Enderman) {
			currentTask.addProgress(1);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
		}
		if (currentTask instanceof SpeedrunTask.KillEntityTask kill && kill.matches(entity)) {
			currentTask.addProgress(1);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
		}
		checkComplete();
	}

	public void onBlockBreak(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
		if (!(player instanceof ServerPlayer sp) || sp != manager.getActivePlayer()) {
			return;
		}
		if (currentTask instanceof SpeedrunTask.MineStoneTask
				&& (state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES))) {
			currentTask.addProgress(1);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
			checkComplete();
		} else if (currentTask instanceof SpeedrunTask.MineBlockTask mine && mine.matches(state)) {
			currentTask.addProgress(1);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
			checkComplete();
		}
	}

	public void onAfterDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(currentTask instanceof SpeedrunTask.DealDamageTask)) {
			return;
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || sp != manager.getActivePlayer()) {
			return;
		}
		currentTask.addProgress((int) amount);
		SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
		checkComplete();
	}

	public void onDimensionChange(ServerPlayer player, ServerLevel level) {
		if (!(currentTask instanceof SpeedrunTask.EnterDimensionTask) || player != manager.getActivePlayer()) {
			return;
		}
		if (level.dimension() == Level.NETHER || level.dimension() == Level.END) {
			currentTask.addProgress(1);
			SpeedrunEventBus.post(new SpeedrunTaskEvent(currentTask, SpeedrunTaskEvent.Type.PROGRESS));
			checkComplete();
		}
	}

	private void checkComplete() {
		if (currentTask != null && currentTask.isComplete()) {
			completeTask();
		}
	}

	public void appendToState(JsonObject obj) {
		if (currentTask != null) {
			obj.addProperty("task", currentTask.getDisplayText());
			obj.addProperty("taskType", currentTask.getType());
			obj.addProperty("taskTarget", currentTask.getTarget());
			obj.addProperty("taskProgress", currentTask.getProgress());
			obj.addProperty("taskTargetId", currentTask.getTargetIdentifier());
			obj.addProperty("taskSecondsLeft", Math.max(0, taskSecondsLeft));
		} else {
			obj.addProperty("task", "");
			obj.addProperty("taskType", "");
			obj.addProperty("taskTarget", 0);
			obj.addProperty("taskProgress", 0);
			obj.addProperty("taskTargetId", "");
			obj.addProperty("taskSecondsLeft", 0);
		}
	}

	public MinecraftServer getServer() {
		return manager.getServer();
	}
}


