package cn.cutelittlesky.speedrunswitch.core.task;

import cn.cutelittlesky.speedrunswitch.config.SpeedrunConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;

public abstract class SpeedrunTask {

	protected final String type;
	protected final int target;
	protected int progress;

	protected SpeedrunTask(String type, int target) {
		this.type = type;
		this.target = Math.max(1, target);
		this.progress = 0;
	}

	public String getType() {
		return type;
	}

	public int getTarget() {
		return target;
	}

	public int getProgress() {
		return progress;
	}

	public String getTargetIdentifier() {
		return "";
	}

	public abstract Component getDisplayComponent();

	public String getDisplayText() {
		return getDisplayComponent().getString();
	}

	public boolean isComplete() {
		return progress >= target;
	}

	public void addProgress(int amount) {
		progress = Math.min(target, progress + amount);
	}

	public static SpeedrunTask randomTask(Random random, List<SpeedrunConfig.TaskTemplate> templates) {
		List<SpeedrunConfig.TaskTemplate> pool = (templates == null || templates.isEmpty()) ? SpeedrunConfig.defaultTaskTemplates() : templates;
		int total = 0;
		for (SpeedrunConfig.TaskTemplate t : pool) {
			total += t.weight;
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (SpeedrunConfig.TaskTemplate t : pool) {
			roll -= t.weight;
			if (roll < 0) {
				SpeedrunTask task = createTask(t.id, t.count, t.target);
				if (task != null) {
					return task;
				}
			}
		}
		if (pool != templates) {
			return null;
		}
		return randomTask(random, null);
	}

	private static SpeedrunTask createTask(String id, int target, String targetId) {
		return switch (id) {
			case "kill_hostile" -> new KillHostileTask(target);
			case "kill_enderman" -> new KillEndermanTask(target);
			case "mine_stone" -> new MineStoneTask(target);
			case "deal_damage" -> new DealDamageTask(target);
			case "pickup_food" -> new PickupFoodTask(target);
			case "enter_dimension" -> new EnterDimensionTask(target);
			case "kill_entity" -> parseEntityType(targetId) == null ? null : new KillEntityTask(parseEntityType(targetId), target);
			case "collect_item" -> parseItem(targetId) == null ? null : new CollectItemTask(parseItem(targetId), target);
			case "mine_block" -> parseBlock(targetId) == null ? null : new MineBlockTask(parseBlock(targetId), target);
			default -> null;
		};
	}

	public static class KillHostileTask extends SpeedrunTask {
		public KillHostileTask(int target) { super("kill_hostile", target); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.kill_hostile", target, progress, target); }
	}

	public static class KillEndermanTask extends SpeedrunTask {
		public KillEndermanTask(int target) { super("kill_enderman", target); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.kill_enderman", target, progress, target); }
	}

	public static class MineStoneTask extends SpeedrunTask {
		public MineStoneTask(int target) { super("mine_stone", target); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.mine_stone", target, progress, target); }
	}

	public static class DealDamageTask extends SpeedrunTask {
		public DealDamageTask(int target) { super("deal_damage", target); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.deal_damage", target, progress, target); }
	}

	public static class PickupFoodTask extends SpeedrunTask {
		public PickupFoodTask(int target) { super("pickup_food", target); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.pickup_food", target, progress, target); }
	}

	public static class EnterDimensionTask extends SpeedrunTask {
		public EnterDimensionTask(int target) { super("enter_dimension", target); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.enter_dimension"); }
	}

	public static class KillEntityTask extends SpeedrunTask {
		private final EntityType<?> entityType;
		public KillEntityTask(EntityType<?> entityType, int target) { super("kill_entity", target); this.entityType = entityType; }
		public boolean matches(LivingEntity e) { return e.getType() == entityType; }
		@Override public String getTargetIdentifier() { return BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString(); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.kill_entity", target, Component.translatable(entityType.getDescriptionId()).getString(), progress, target); }
	}

	public static class CollectItemTask extends SpeedrunTask {
		private final Item item;
		public CollectItemTask(Item item, int target) { super("collect_item", target); this.item = item; }
		public boolean matches(ItemStack stack) { return stack.is(item); }
		@Override public String getTargetIdentifier() { return BuiltInRegistries.ITEM.getKey(item).toString(); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.collect_item", target, Component.translatable(item.getDescriptionId()).getString(), progress, target); }
	}

	public static class MineBlockTask extends SpeedrunTask {
		private final Block block;
		public MineBlockTask(Block block, int target) { super("mine_block", target); this.block = block; }
		public boolean matches(BlockState state) { return state.is(block); }
		@Override public String getTargetIdentifier() { return BuiltInRegistries.BLOCK.getKey(block).toString(); }
		@Override public Component getDisplayComponent() { return MiniMessageUtils.tr("task.mine_block", target, Component.translatable(block.getDescriptionId()).getString(), progress, target); }
	}

	private static EntityType<?> parseEntityType(String target) {
		if (target == null || target.isEmpty()) { return null; }
		Identifier id = Identifier.tryParse(target);
		return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
	}

	private static Item parseItem(String target) {
		if (target == null || target.isEmpty()) { return null; }
		Identifier id = Identifier.tryParse(target);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	private static Block parseBlock(String target) {
		if (target == null || target.isEmpty()) { return null; }
		Identifier id = Identifier.tryParse(target);
		return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
	}
}