package cn.cutelittlesky.speedrunswitch;

import cn.cutelittlesky.speedrunswitch.command.SpeedrunCommands;
import cn.cutelittlesky.speedrunswitch.config.SpeedrunConfig;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunStats;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunEventBus;
import cn.cutelittlesky.speedrunswitch.core.task.TaskManager;
import cn.cutelittlesky.speedrunswitch.net.SpeedrunStatePayload;
import cn.cutelittlesky.speedrunswitch.svc.SvcBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

public class SpeedrunSwitchMod implements ModInitializer {

	private static SpeedrunManager manager;

	public static SpeedrunManager getManager() {
		return manager;
	}

	@Override
	public void onInitialize() {
		SpeedrunConfig config = SpeedrunConfig.load();
		System.out.println("[speedrun-switch] 接力速通模组已加载" + (SvcBridge.isAvailable() ? "（检测到 Simple Voice Chat）" : ""));

		PayloadTypeRegistry.clientboundPlay().register(SpeedrunStatePayload.TYPE, SpeedrunStatePayload.STREAM_CODEC);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SpeedrunEventBus.clear();
			manager = new SpeedrunManager(server, config, SpeedrunStats.load(server));
			manager.setTaskManager(new TaskManager(manager));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (manager != null) {
				manager.getStats().save();
			}
			SpeedrunEventBus.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (manager != null) {
				manager.tick();
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SpeedrunCommands.register(dispatcher));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!ServerPlayNetworking.canSend(player, SpeedrunStatePayload.TYPE)) {
				handler.disconnect(Component.literal("§c[Speedrun Switch 接力速通]\n\n§e进入本服务器必须在客户端安装 §bSpeedrun Switch (接力速通) §e模组！\n§7Please install the speedrun-switch client mod before joining."));
				return;
			}
			if (manager != null) {
				manager.onPlayerJoin(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (manager != null) {
				manager.onPlayerDisconnect(handler.getPlayer());
			}
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (manager != null && (manager.getMode() == SpeedrunManager.Mode.RUNNING
					|| manager.getMode() == SpeedrunManager.Mode.COUNTDOWN
					|| manager.getMode() == SpeedrunManager.Mode.STARTING)) {
				Component content = message.decoratedContent();
				sender.sendSystemMessage(Component.literal("<" + sender.getGameProfile().name() + "> ")
						.append(content != null ? content : Component.literal(message.signedContent())));
				return false;
			}
			return true;
		});

		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, damageSource) -> {
			if (manager == null) {
				return;
			}
			if (killed instanceof EnderDragon) {
				manager.onDragonKilled();
			}
			if (killed instanceof LivingEntity living && manager.getTaskManager() != null) {
				manager.getTaskManager().onEntityDeath(living, damageSource);
			}
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
			if (manager != null && entity instanceof ServerPlayer player) {
				if (manager.isSpectator(player)) {
					return false;
				}
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (manager != null) {
				if (entity instanceof EnderDragon) {
					manager.onDragonKilled();
				} else if (entity instanceof ServerPlayer sp) {
					manager.onPlayerDeath(sp);
				}
			}
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (manager != null) {
				manager.getTaskManager().onBlockBreak(level, pos, state, player);
				manager.onBlockBreak(level, pos, state, player);
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamage, damageAfterArmor, blocked) -> {
			if (manager != null) {
				manager.getTaskManager().onAfterDamage(entity, damageSource, damageAfterArmor);
			}
		});

		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (manager != null) {
				if (player.getUUID().equals(manager.getActivePlayerId())) {
					destination.getServer().execute(() -> {
						manager.getShared().syncSpectatorPositions(destination.getServer());
					});
				} else if (manager.isSpectator(player)) {
					ServerPlayer active = manager.getActivePlayer();
					if (active != null && active.isAlive() && destination == active.level()) {
						destination.getServer().execute(() -> {
							manager.getShared().bindCameraToActive(player, active);
						});
					}
				}
				if (manager.getTaskManager() != null) {
					manager.getTaskManager().onDimensionChange(player, destination);
				}
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (manager != null) {
				manager.onActivePlayerRespawn(newPlayer);
			}
		});
	}
}
