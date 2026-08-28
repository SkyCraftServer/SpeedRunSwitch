package cn.cutelittlesky.speedrunswitch.core;

import cn.cutelittlesky.speedrunswitch.core.CameraGuard;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SharedInventory {

	private final SpeedrunManager manager;

	public ServerLevel lastLevel;
	public double lastX;
	public double lastY;
	public double lastZ;
	public float lastYaw;
	public float lastPitch;

	public ServerPlayer.RespawnConfig sharedRespawnConfig;

	private final Set<UUID> pendingTeleportPlayers = ConcurrentHashMap.newKeySet();

	public SharedInventory(SpeedrunManager manager) {
		this.manager = manager;
	}

	public void clearPendingTeleport(UUID uuid) {
		pendingTeleportPlayers.remove(uuid);
	}

	public boolean isPendingTeleport(UUID uuid) {
		return pendingTeleportPlayers.contains(uuid);
	}

	public void syncInventoryToSpectators(MinecraftServer server) {
		ServerPlayer active = manager.getActivePlayer();
		if (active == null || active.isDeadOrDying() || !active.isAlive()) {
			return;
		}
		for (ServerPlayer spectator : server.getPlayerList().getPlayers()) {
			if (spectator != active && manager.isSpectator(spectator) && !spectator.hasDisconnected() && !spectator.isRemoved()) {
				copyInventory(active, spectator);
				copyVitals(active, spectator);
			}
		}
	}

	public void tick(MinecraftServer server) {
		ServerPlayer active = manager.getActivePlayer();
		if (active == null || !active.isAlive() || active.isDeadOrDying()) {
			return;
		}
		int tickCount = manager.getTickCounter();

		for (ServerPlayer spectator : server.getPlayerList().getPlayers()) {
			if (spectator != active && manager.isSpectator(spectator) && spectator.isAlive() && !spectator.hasDisconnected() && !spectator.isRemoved()) {
				UUID uuid = spectator.getUUID();
				if (pendingTeleportPlayers.contains(uuid)) {
					continue;
				}

				try {
					if (spectator.level() != active.level()) {
						if (tickCount % 20 == 0) {
							attachSpectator(spectator, active);
						}
					} else {
						if (spectator.getCamera() != active) {
							bindCameraToActive(spectator, active);
						}
						if (tickCount % 20 == 0 && spectator.connection != null) {
							spectator.connection.send(new ClientboundSetCameraPacket(active));
						}
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	public void attachSpectators(MinecraftServer server, ServerPlayer active) {
		if (active == null || !active.isAlive() || active.isDeadOrDying()) {
			return;
		}
		for (ServerPlayer spectator : server.getPlayerList().getPlayers()) {
			if (spectator != active && manager.isSpectator(spectator) && !spectator.hasDisconnected() && !spectator.isRemoved()) {
				attachSpectator(spectator, active);
			}
		}
	}

	public void attachSpectator(ServerPlayer spectator, ServerPlayer active) {
		if (spectator == null || active == null || spectator == active || spectator.hasDisconnected() || spectator.isRemoved()) {
			return;
		}
		UUID uuid = spectator.getUUID();
		if (pendingTeleportPlayers.contains(uuid)) {
			return;
		}

		try {
			if (spectator.level() != active.level()) {
				pendingTeleportPlayers.add(uuid);
				CameraGuard.runWithBypass(() -> spectator.setCamera(null));
				spectator.teleportTo(active.level(), active.getX(), active.getY(), active.getZ(),
						Set.of(), active.getYRot(), active.getXRot(), false);

				spectator.level().getServer().execute(() -> {
					pendingTeleportPlayers.remove(uuid);
					if (spectator.level() == active.level() && !spectator.isRemoved() && !spectator.hasDisconnected()) {
						bindCameraToActive(spectator, active);
					}
				});
			} else {
				if (spectator.distanceToSqr(active) > 64.0) {
					spectator.teleportTo(active.level(), active.getX(), active.getY(), active.getZ(),
							Set.of(), active.getYRot(), active.getXRot(), false);
				}
				spectator.level().getServer().execute(() -> {
					bindCameraToActive(spectator, active);
				});
			}
		} catch (Exception e) {
			pendingTeleportPlayers.remove(uuid);
			System.err.println("[speedrun-switch] attachSpectator 异常容错: " + e.getMessage());
		}
	}

	public void bindCameraToActive(ServerPlayer spectator, ServerPlayer active) {
		if (spectator == null || active == null || spectator.hasDisconnected() || spectator.isRemoved() || !active.isAlive() || active.isDeadOrDying()) {
			return;
		}
		if (spectator.level() != active.level()) {
			return;
		}
		try {
			CameraGuard.runWithBypass(() -> {
				spectator.setCamera(active);
			});
			if (spectator.level() instanceof ServerLevel serverLevel) {
				serverLevel.getChunkSource().move(spectator);
			}
			if (spectator.connection != null) {
				spectator.connection.send(new ClientboundSetCameraPacket(active));
				spectator.connection.resetPosition();
			}
		} catch (Exception e) {
			System.err.println("[speedrun-switch] bindCameraToActive 异常容错: " + e.getMessage());
		}
	}

	public void syncSpectatorPositions(MinecraftServer server) {
		ServerPlayer active = manager.getActivePlayer();
		if (active == null || !active.isAlive() || active.isDeadOrDying()) {
			return;
		}
		for (ServerPlayer spectator : server.getPlayerList().getPlayers()) {
			if (spectator != active && manager.isSpectator(spectator) && spectator.isAlive() && !spectator.hasDisconnected() && !spectator.isRemoved()) {
				try {
					if (spectator.level() != active.level()) {
						attachSpectator(spectator, active);
					} else {
						if (spectator.distanceToSqr(active) > 64.0) {
							spectator.teleportTo(active.level(), active.getX(), active.getY(), active.getZ(),
									Set.of(), active.getYRot(), active.getXRot(), false);
						}
						bindCameraToActive(spectator, active);
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	public void recordActivePosition(ServerPlayer active) {
		if (active == null) {
			return;
		}
		ServerPlayer.RespawnConfig config = active.getRespawnConfig();
		if (config != null) {
			manager.updateSharedRespawn(config);
		}
		manager.validateSharedRespawn();
		lastLevel = active.level();
		lastX = active.getX();
		lastY = active.getY();
		lastZ = active.getZ();
		lastYaw = active.getYRot();
		lastPitch = active.getXRot();
	}

	public void onPlayerJoin(ServerPlayer player, ServerPlayer active) {
		pendingTeleportPlayers.remove(player.getUUID());
		player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
		player.setHealth(20.0f);
		player.clearFire();
		player.removeAllEffects();
		if (sharedRespawnConfig != null) {
			player.setRespawnPosition(sharedRespawnConfig, false);
		}
		copyInventory(active, player);

		CameraGuard.runWithBypass(() -> player.setCamera(null));
		if (player.connection != null) {
			player.connection.send(new ClientboundSetCameraPacket(player));
		}

		player.level().getServer().execute(() -> {
			if (player.connection != null && !player.hasDisconnected() && !player.isRemoved()) {
				attachSpectator(player, active);
			}
		});
	}

	public void applyHandover(ServerPlayer oldActive, ServerPlayer newActive) {
		manager.validateSharedRespawn();
		for (ServerPlayer spectator : newActive.level().getServer().getPlayerList().getPlayers()) {
			if (spectator != newActive && !spectator.hasDisconnected() && !spectator.isRemoved()) {
				CameraGuard.runWithBypass(() -> spectator.setCamera(null));
			}
		}

		CameraGuard.runWithBypass(() -> newActive.setCamera(null));
		if (newActive.connection != null) {
			newActive.connection.send(new ClientboundSetCameraPacket(newActive));
			newActive.connection.resetPosition();
		}

		if (oldActive == null || oldActive.isDeadOrDying() || !oldActive.isAlive()) {
			if (sharedRespawnConfig != null) {
				newActive.setRespawnPosition(sharedRespawnConfig, false);
			}
			if (lastLevel != null) {
				newActive.teleportTo(lastLevel, lastX, lastY, lastZ, Set.of(), lastYaw, lastPitch, false);
			}
			newActive.setHealth(newActive.getMaxHealth());
			newActive.getFoodData().setFoodLevel(20);
			newActive.getFoodData().setSaturation(5.0f);
			newActive.clearFire();
			newActive.removeAllEffects();
		} else {
			if (sharedRespawnConfig != null) {
				newActive.setRespawnPosition(sharedRespawnConfig, false);
			}
			if (lastLevel != null) {
				newActive.teleportTo(lastLevel, lastX, lastY, lastZ, Set.of(), lastYaw, lastPitch, false);
			}
			copyVitals(oldActive, newActive);
			copyInventory(oldActive, newActive);
		}

		if (oldActive != null && oldActive.connection != null && !oldActive.hasDisconnected() && !oldActive.isRemoved()) {
			oldActive.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			oldActive.setHealth(20.0f);
			oldActive.clearFire();
			oldActive.removeAllEffects();
		}

		MinecraftServer server = newActive.level().getServer();
		server.execute(() -> {
			attachSpectators(server, newActive);
		});

		recordActivePosition(newActive);
	}

	public void clearInventoryAfterDeath(ServerPlayer active) {
		Inventory inv = active.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			inv.setItem(i, ItemStack.EMPTY);
		}
		sendInventorySync(active);
	}

	public void syncInventoryTo(ServerPlayer target, ServerPlayer source) {
		copyInventory(source, target);
	}

	private void copyInventory(ServerPlayer source, ServerPlayer target) {
		if (target == null || target.hasDisconnected() || target.isRemoved()) {
			return;
		}
		Inventory src = source.getInventory();
		Inventory dst = target.getInventory();
		int size = Math.min(src.getContainerSize(), dst.getContainerSize());
		for (int i = 0; i < size; i++) {
			dst.setItem(i, src.getItem(i).copy());
		}
		dst.setSelectedSlot(src.getSelectedSlot());
		sendInventorySync(target);
	}

	private void sendInventorySync(ServerPlayer player) {
		if (player == null || player.connection == null || player.hasDisconnected() || player.isRemoved()) {
			return;
		}
		try {
			player.connection.send(new ClientboundContainerSetContentPacket(
					player.inventoryMenu.containerId,
					player.inventoryMenu.incrementStateId(),
					List.copyOf(player.inventoryMenu.getItems()),
					player.inventoryMenu.getCarried()));
			player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
		} catch (Exception ignored) {
		}
	}

	private void copyVitals(ServerPlayer source, ServerPlayer target) {
		if (target == null || target.hasDisconnected() || target.isRemoved()) {
			return;
		}
		boolean isSpectatorTarget = manager.isSpectator(target);
		if (isSpectatorTarget) {
			target.setHealth(20.0f);
			target.clearFire();
			target.removeAllEffects();
		} else {
			target.setHealth(Math.max(1.0f, source.getHealth()));
			target.setRemainingFireTicks(source.getRemainingFireTicks());
			target.removeAllEffects();
			for (MobEffectInstance effect : source.getActiveEffects()) {
				target.addEffect(new MobEffectInstance(effect));
			}
		}

		target.getFoodData().setFoodLevel(source.getFoodData().getFoodLevel());
		target.getFoodData().setSaturation(source.getFoodData().getSaturationLevel());
		target.setExperienceLevels(source.experienceLevel);
		target.experienceProgress = source.experienceProgress;
		target.totalExperience = source.totalExperience;

		if (target.connection != null) {
			try {
				float healthToSend = isSpectatorTarget ? 20.0f : Math.max(1.0f, source.getHealth());
				target.connection.send(new ClientboundSetHealthPacket(
						healthToSend,
						target.getFoodData().getFoodLevel(),
						target.getFoodData().getSaturationLevel()));
				target.connection.send(new ClientboundSetExperiencePacket(
						target.experienceProgress,
						target.totalExperience,
						target.experienceLevel));
			} catch (Exception ignored) {
			}
		}
	}
}