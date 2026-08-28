package cn.cutelittlesky.speedrunswitch.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cn.cutelittlesky.speedrunswitch.config.SpeedrunConfig;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunDragonKillEvent;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunEventBus;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunRunnerDeathEvent;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunStateChangedEvent;
import cn.cutelittlesky.speedrunswitch.core.event.SpeedrunSwitchEvent;
import cn.cutelittlesky.speedrunswitch.core.state.CountdownState;
import cn.cutelittlesky.speedrunswitch.core.state.FinishedState;
import cn.cutelittlesky.speedrunswitch.core.state.IdleState;
import cn.cutelittlesky.speedrunswitch.core.state.RunningState;
import cn.cutelittlesky.speedrunswitch.core.state.SpeedrunState;
import cn.cutelittlesky.speedrunswitch.core.state.StartingState;
import cn.cutelittlesky.speedrunswitch.core.task.TaskManager;
import cn.cutelittlesky.speedrunswitch.net.GameMessages;
import cn.cutelittlesky.speedrunswitch.net.SpeedrunStatePayload;
import cn.cutelittlesky.speedrunswitch.core.CameraGuard;
import cn.cutelittlesky.speedrunswitch.svc.SvcBridge;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.UUID;

public class SpeedrunManager {

	public enum Mode { IDLE, STARTING, RUNNING, COUNTDOWN, FINISHED }

	public static final UUID TEAM_REPUTATION_UUID = UUID.nameUUIDFromBytes("speedrun_team_reputation".getBytes(StandardCharsets.UTF_8));

	private static final Gson GSON = new Gson();
	private static final Random RANDOM = new Random();

	private final MinecraftServer server;
	private final SpeedrunConfig config;
	private final SpeedrunStats stats;
	private final SharedInventory shared;
	private final DebugInfoGuard debugGuard;
	private TaskManager taskManager;

	private SpeedrunState currentState = new IdleState();
	private UUID activePlayerId;
	private UUID previousRunnerId;
	private int currentRound = 1;
	private long nextSwitchAt;
	private int countdownRemaining;
	private UUID pendingTargetId;
	private int tickCounter;

	public SpeedrunManager(MinecraftServer server, SpeedrunConfig config, SpeedrunStats stats) {
		this.server = server;
		this.config = config;
		this.stats = stats;
		this.shared = new SharedInventory(this);
		this.debugGuard = new DebugInfoGuard(server);
		if (stats.currentRound > 0) {
			this.currentRound = stats.currentRound;
		}
		if (stats.activePlayerId != null && !stats.activePlayerId.isEmpty()) {
			try {
				this.activePlayerId = UUID.fromString(stats.activePlayerId);
			} catch (IllegalArgumentException ignored) {}
		}
		if (stats.previousRunnerId != null && !stats.previousRunnerId.isEmpty()) {
			try {
				this.previousRunnerId = UUID.fromString(stats.previousRunnerId);
			} catch (IllegalArgumentException ignored) {}
		}
		if (stats.sharedRespawn != null) {
			ServerPlayer.RespawnConfig restored = stats.sharedRespawn.toRespawnConfig();
			if (restored != null) {
				shared.sharedRespawnConfig = restored;
			}
		}
		initEventBusSubsystems();
	}

	private void initEventBusSubsystems() {
		SpeedrunEventBus.register(SpeedrunSwitchEvent.class, event -> {
			ServerPlayer old = event.oldRunner();
			ServerPlayer next = event.newRunner();
			if (old != null && old != next) {
				debugGuard.apply(old, true);
				SvcBridge.onRoleChanged(old, false);
			}
			if (next != null) {
				debugGuard.apply(next, false);
				SvcBridge.onRoleChanged(next, true);
			}
			stats.save();
		});

		SpeedrunEventBus.register(SpeedrunDragonKillEvent.class, event -> {
			if (getMode() == Mode.RUNNING || getMode() == Mode.COUNTDOWN) {
				GameMessages.broadcast(server, "event.dragon_kill.broadcast");
				transitionTo(new FinishedState());
			}
		});

		SpeedrunEventBus.register(SpeedrunRunnerDeathEvent.class, event -> {
			stats.incrementDeaths(event.runner());
			stats.save();
		});
	}

	public void setTaskManager(TaskManager taskManager) {
		this.taskManager = taskManager;
	}

	public TaskManager getTaskManager() {
		return taskManager;
	}

	public synchronized void transitionTo(SpeedrunState newState) {
		if (newState == null || newState.getClass() == currentState.getClass()) {
			return;
		}
		Mode oldMode = currentState.getMode();
		currentState.onExit(this);
		currentState = newState;
		Mode newMode = currentState.getMode();
		SpeedrunEventBus.post(new SpeedrunStateChangedEvent(oldMode, newMode));
		currentState.onEnter(this);
		broadcastState();
	}

	public Mode getMode() { return currentState.getMode(); }
	public boolean isSpeedrunActive() {
		Mode m = getMode();
		return m == Mode.RUNNING || m == Mode.COUNTDOWN || m == Mode.STARTING;
	}
	public SpeedrunState getCurrentState() { return currentState; }
	public MinecraftServer getServer() { return server; }
	public SpeedrunConfig getConfig() { return config; }
	public SpeedrunStats getStats() { return stats; }
	public SharedInventory getShared() { return shared; }
	public DebugInfoGuard getDebugGuard() { return debugGuard; }
	public int getCountdownRemaining() { return countdownRemaining; }
	public void setCountdownRemaining(int countdownRemaining) { this.countdownRemaining = countdownRemaining; }
	public long getNextSwitchAt() { return nextSwitchAt; }
	public void setPendingTargetId(UUID pendingTargetId) { this.pendingTargetId = pendingTargetId; }
	public UUID getPendingTargetId() { return pendingTargetId; }
	public int getTickCounter() { return tickCounter; }
	public int getCurrentRound() { return currentRound; }

	public ServerPlayer getActivePlayer() {
		if (activePlayerId == null) {
			return null;
		}
		return server.getPlayerList().getPlayer(activePlayerId);
	}

	public UUID getActivePlayerId() {
		return activePlayerId;
	}

	public boolean isSpectator(ServerPlayer player) {
		Mode m = getMode();
		if (m != Mode.RUNNING && m != Mode.COUNTDOWN && m != Mode.STARTING) {
			return false;
		}
		return !player.getUUID().equals(activePlayerId);
	}

	public List<ServerPlayer> spectators() {
		List<ServerPlayer> list = new ArrayList<>();
		Mode m = getMode();
		if (m != Mode.RUNNING && m != Mode.COUNTDOWN && m != Mode.STARTING) {
			return list;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (isSpectator(player)) {
				list.add(player);
			}
		}
		return list;
	}

	public long secondsUntilSwitch() {
		return Math.max(0, (nextSwitchAt - System.currentTimeMillis()) / 1000);
	}

	public void resetSwitchTimer() {
		this.nextSwitchAt = System.currentTimeMillis() + config.switchIntervalMinutes * 60_000L;
	}

	public void addSwitchTime(long seconds) {
		nextSwitchAt += seconds * 1000;
	}

	public void subtractSwitchTime(long seconds) {
		nextSwitchAt -= seconds * 1000;
		if (getMode() == Mode.RUNNING && config.autoSwitchEnabled && System.currentTimeMillis() >= nextSwitchAt) {
			transitionTo(new CountdownState());
		}
	}

	public boolean setSwitchTime(CommandSourceStack source, int seconds) {
		Mode m = getMode();
		if (m != Mode.RUNNING && m != Mode.COUNTDOWN) {
			GameMessages.send(source, GameMessages.error("command.time.not_running"));
			return false;
		}

		if (seconds <= 0) {
			this.nextSwitchAt = System.currentTimeMillis();
			if (m == Mode.RUNNING) {
				if (config.autoSwitchEnabled) {
					transitionTo(new CountdownState());
				} else {
					manualSwitch(source, null);
				}
			}
			GameMessages.broadcast(server, GameMessages.warn("command.time.set_zero"));
			broadcastState();
			return true;
		}

		this.nextSwitchAt = System.currentTimeMillis() + seconds * 1000L;

		if (m == Mode.COUNTDOWN) {
			setCountdownRemaining(0);
			setPendingTargetId(null);
			transitionTo(new RunningState());
			GameMessages.broadcast(server, GameMessages.info("command.time.countdown_cancelled", seconds));
		} else {
			GameMessages.broadcast(server, GameMessages.info("command.time.set_success", seconds));
		}

		broadcastState();
		return true;
	}

	public boolean addSwitchTime(CommandSourceStack source, int seconds) {
		Mode m = getMode();
		if (m != Mode.RUNNING && m != Mode.COUNTDOWN) {
			GameMessages.send(source, GameMessages.error("command.time.not_running"));
			return false;
		}
		long current = secondsUntilSwitch();
		long target = Math.max(0, current + seconds);
		return setSwitchTime(source, (int) target);
	}

	public boolean startRun(CommandSourceStack source, ServerPlayer runner) {
		Mode m = getMode();
		if (m == Mode.RUNNING || m == Mode.COUNTDOWN || m == Mode.STARTING) {
			GameMessages.send(source, GameMessages.error("command.start.already_running"));
			return false;
		}
		if (runner == null) {
			GameMessages.send(source, GameMessages.error("command.start.no_player"));
			return false;
		}

		stats.startRun();
		server.getGameRules().set(GameRules.IMMEDIATE_RESPAWN, true, server);
		activePlayerId = runner.getUUID();
		stats.activePlayerId = runner.getUUID().toString();
		previousRunnerId = null;
		currentRound = 1;
		stats.currentRound = 1;
		stats.previousRunnerId = null;

		SpeedrunStats.PlayerEntry runnerEntry = stats.entry(runner);
		runnerEntry.lastActiveRound = 1;
		runnerEntry.spectatorSeconds = 0;
		stats.save();

		pendingTargetId = null;

		if (config.voiceChatEnabled) {
			SvcBridge.onModeStart(config.spectatorGroupName);
		}

		if (config.startCountdownSeconds > 0) {
			countdownRemaining = config.startCountdownSeconds;
			transitionTo(new StartingState());
		} else {
			applyStartInstantly(runner);
		}
		return true;
	}

	private void applyStartInstantly(ServerPlayer runner) {
		runner.setGameMode(GameType.SURVIVAL);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != runner) {
				player.setGameMode(GameType.SPECTATOR);
				shared.onPlayerJoin(player, runner);
				debugGuard.apply(player, true);
				SvcBridge.onRoleChanged(player, false);
			} else {
				debugGuard.apply(player, false);
				SvcBridge.onRoleChanged(player, true);
			}
		}
		if (taskManager != null) {
			taskManager.onRunStart();
		}
		resetSwitchTimer();
		transitionTo(new RunningState());

		GameMessages.broadcast(server, "command.start.broadcast", runner.getGameProfile().name(), config.switchIntervalMinutes);
		GameMessages.broadcastTitle(server, "command.start.title", "command.start.subtitle", 10, 70, 20, runner.getGameProfile().name());
	}

	public boolean resumeRun(CommandSourceStack source) {
		Mode m = getMode();
		if ((m == Mode.RUNNING || m == Mode.COUNTDOWN || m == Mode.STARTING) && getActivePlayer() != null) {
			GameMessages.send(source, GameMessages.error("command.resume.already_running"));
			return false;
		}
		if (stats.runStartTime == 0 || stats.finished) {
			GameMessages.send(source, GameMessages.error("command.resume.no_run"));
			return false;
		}
		List<ServerPlayer> onlinePlayers = new ArrayList<>(server.getPlayerList().getPlayers());
		if (onlinePlayers.isEmpty()) {
			GameMessages.send(source, GameMessages.error("command.resume.no_players"));
			return false;
		}

		ServerPlayer runner = getActivePlayer();
		boolean isSubstitute = false;
		if (runner == null) {
			runner = selectWeightedCandidate(onlinePlayers);
			if (runner == null) {
				GameMessages.send(source, GameMessages.error("command.resume.no_players"));
				return false;
			}
			isSubstitute = true;
			activePlayerId = runner.getUUID();
			stats.activePlayerId = runner.getUUID().toString();
			SpeedrunStats.PlayerEntry entry = stats.entry(runner);
			entry.lastActiveRound = currentRound;
			entry.spectatorSeconds = 0;
			stats.save();
		}

		server.getGameRules().set(GameRules.IMMEDIATE_RESPAWN, true, server);
		runner.setGameMode(GameType.SURVIVAL);
		if (shared.sharedRespawnConfig != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setRespawnPosition(shared.sharedRespawnConfig, false);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != runner) {
				player.setGameMode(GameType.SPECTATOR);
				shared.onPlayerJoin(player, runner);
				debugGuard.apply(player, true);
				SvcBridge.onRoleChanged(player, false);
			} else {
				debugGuard.apply(runner, false);
				SvcBridge.onRoleChanged(runner, true);
			}
		}

		if (config.voiceChatEnabled) {
			SvcBridge.onModeStart(config.spectatorGroupName);
		}
		if (taskManager != null) {
			taskManager.onRunStart();
		}

		resetSwitchTimer();
		transitionTo(new RunningState());

		if (isSubstitute) {
			GameMessages.broadcast(server, "command.resume.substitute_broadcast", runner.getGameProfile().name(), currentRound);
		} else {
			GameMessages.broadcast(server, "command.resume.broadcast", runner.getGameProfile().name(), currentRound);
		}
		GameMessages.broadcastTitle(server, "command.resume.title", "command.resume.subtitle", 10, 70, 20, runner.getGameProfile().name());

		return true;
	}

	public boolean manualSwitch(CommandSourceStack source, ServerPlayer target) {
		Mode m = getMode();
		if (m != Mode.RUNNING && m != Mode.COUNTDOWN) {
			GameMessages.send(source, GameMessages.error("command.switch.not_running"));
			return false;
		}
		ServerPlayer old = getActivePlayer();
		if (old != null) {
			shared.recordActivePosition(old);
		}
		if (target == null) {
			target = randomSpectator();
		}
		if (target == null || target == old) {
			GameMessages.send(source, GameMessages.error("command.switch.no_target"));
			return false;
		}
		doHandover(old, target, true);
		return true;
	}

	public boolean endRun(CommandSourceStack source) {
		Mode m = getMode();
		if (m == Mode.IDLE) {
			GameMessages.send(source, GameMessages.error("command.end.not_started"));
			return false;
		}
		transitionTo(new FinishedState());
		GameMessages.broadcast(server, "command.end.broadcast");
		return true;
	}

	public boolean resetRun(CommandSourceStack source) {
		transitionTo(new IdleState());
		activePlayerId = null;
		previousRunnerId = null;
		currentRound = 0;
		pendingTargetId = null;
		countdownRemaining = 0;
		if (taskManager != null) {
			taskManager.onRunEnd();
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CameraGuard.runWithBypass(() -> {
				player.setCamera(null);
			});
			player.setGameMode(GameType.SURVIVAL);
			player.setRespawnPosition(null, false);
			debugGuard.apply(player, false);
			SvcBridge.onRoleChanged(player, true);
		}
		SvcBridge.onModeEnd();
		clearSharedRespawn();
		stats.reset();
		stats.save();
		GameMessages.broadcast(server, "command.reset.broadcast");
		return true;
	}

	public void tick() {
		onServerTick();
	}

	public void onServerTick() {
		tickCounter++;
		currentState.tick(this);
		if (getMode() == Mode.RUNNING || getMode() == Mode.COUNTDOWN || getMode() == Mode.STARTING) {
			shared.tick(server);
		}
		if (tickCounter % 5 == 0) {
			currentState.tickFast(this);
		}
		if (tickCounter % 20 == 0) {
			currentState.tickSecond(this);
			broadcastState();
		}
	}

	public void onPlayerJoin(ServerPlayer player) {
		stats.entry(player);
		stats.save();

		Mode m = getMode();
		if (m == Mode.RUNNING || m == Mode.COUNTDOWN || m == Mode.STARTING) {
			ServerPlayer active = getActivePlayer();
			if (active != null && !player.getUUID().equals(activePlayerId)) {
				shared.onPlayerJoin(player, active);
				debugGuard.apply(player, true);
				SvcBridge.onRoleChanged(player, false);
			}
		}
		if (shared.sharedRespawnConfig != null) {
			player.setRespawnPosition(shared.sharedRespawnConfig, false);
		}
		broadcastState();
	}

	public void onPlayerDisconnect(ServerPlayer player) {
		if (player.getUUID().equals(activePlayerId)) {
			shared.recordActivePosition(player);
			handleActivePlayerDisconnect(player);
		}
	}

	private void handleActivePlayerDisconnect(ServerPlayer disconnected) {
		Mode m = getMode();
		if (m != Mode.RUNNING && m != Mode.COUNTDOWN) {
			return;
		}
		GameMessages.broadcast(server, "event.disconnect.active", disconnected.getGameProfile().name());
		ServerPlayer next = randomSpectator();
		if (next == null) {
			GameMessages.broadcast(server, "event.disconnect.no_player");
			activePlayerId = null;
			return;
		}
		doHandover(disconnected, next, false);
	}

	public void onPlayerDeath(ServerPlayer player) {
		if (player.getUUID().equals(activePlayerId)) {
			SpeedrunEventBus.post(new SpeedrunRunnerDeathEvent(player));
			for (ServerPlayer spectator : server.getPlayerList().getPlayers()) {
				if (isSpectator(spectator) && !spectator.hasDisconnected() && !spectator.isRemoved()) {
					CameraGuard.runWithBypass(() -> {
						spectator.setCamera(null);
					});
				}
			}
		}
	}

	public void onActivePlayerRespawn(ServerPlayer player) {
		onPlayerRespawn(player);
	}

	public void onPlayerRespawn(ServerPlayer newActive) {
		if (newActive.getUUID().equals(activePlayerId)) {
			shared.clearInventoryAfterDeath(newActive);
			shared.recordActivePosition(newActive);

			if (newActive.getRespawnConfig() == null && shared.sharedRespawnConfig != null) {
				clearSharedRespawn();
			}

			for (ServerPlayer spectator : server.getPlayerList().getPlayers()) {
				if (isSpectator(spectator) && !spectator.hasDisconnected() && !spectator.isRemoved()) {
					CameraGuard.runWithBypass(() -> {
						spectator.setCamera(null);
					});
					spectator.teleportTo(newActive.level(), newActive.getX(), newActive.getY(), newActive.getZ(),
							Set.of(), newActive.getYRot(), newActive.getXRot(), false);
				}
			}

			newActive.level().getServer().execute(() -> {
				shared.attachSpectators(server, newActive);
			});
		}
	}

	public void updateSharedRespawn(ServerPlayer.RespawnConfig config) {
		if (config == null || config.respawnData() == null) {
			return;
		}
		if (shared.sharedRespawnConfig != null && isSameRespawn(shared.sharedRespawnConfig, config)) {
			return;
		}

		shared.sharedRespawnConfig = config;
		stats.sharedRespawn = SpeedrunStats.RespawnSnapshot.fromRespawnConfig(config);
		stats.save();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setRespawnPosition(config, false);
		}
	}

	public void clearSharedRespawn() {
		if (shared.sharedRespawnConfig == null && stats.sharedRespawn == null) {
			return;
		}
		shared.sharedRespawnConfig = null;
		stats.sharedRespawn = null;
		stats.save();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setRespawnPosition(null, false);
		}
	}

	public void validateSharedRespawn() {
		ServerPlayer.RespawnConfig config = shared.sharedRespawnConfig;
		if (config == null || config.respawnData() == null) {
			return;
		}
		ServerLevel level = server.getLevel(config.respawnData().dimension());
		if (level == null) {
			return;
		}
		BlockPos pos = config.respawnData().pos();
		if (level.isLoaded(pos)) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof AbstractBedBlock) && !(state.getBlock() instanceof RespawnAnchorBlock)) {
				clearSharedRespawn();
			}
		}
	}

	public void onBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
		ServerPlayer.RespawnConfig config = shared.sharedRespawnConfig;
		if (config == null || config.respawnData() == null) {
			return;
		}
		if (!level.dimension().equals(config.respawnData().dimension())) {
			return;
		}
		BlockPos respawnPos = config.respawnData().pos();
		boolean isMatch = pos.equals(respawnPos);
		if (!isMatch && state.getBlock() instanceof AbstractBedBlock) {
			Direction connected = AbstractBedBlock.getConnectedDirection(state);
			if (pos.relative(connected).equals(respawnPos)) {
				isMatch = true;
			}
		}
		if (isMatch) {
			clearSharedRespawn();
		}
	}

	private boolean isSameRespawn(ServerPlayer.RespawnConfig a, ServerPlayer.RespawnConfig b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (a.respawnData() == null || b.respawnData() == null) return false;
		return a.respawnData().dimension().equals(b.respawnData().dimension())
				&& a.respawnData().pos().equals(b.respawnData().pos())
				&& a.forced() == b.forced();
	}

	public void onDragonKilled() {
		ServerPlayer active = getActivePlayer();
		SpeedrunEventBus.post(new SpeedrunDragonKillEvent(active));
	}

	public void onDragonDeath(ServerPlayer killer) {
		SpeedrunEventBus.post(new SpeedrunDragonKillEvent(killer));
	}

	public ServerPlayer selectPendingTarget() {
		return selectWeightedSpectator();
	}

	public void doCountdownSwitch() {
		ServerPlayer target = server.getPlayerList().getPlayer(pendingTargetId);
		ServerPlayer old = getActivePlayer();
		if (target == null || old == null || target == old || !isSpectator(target)) {
			ServerPlayer fallback = randomSpectator();
			if (fallback == null) {
				GameMessages.broadcast(server, "event.countdown.cancel_no_target");
				resetSwitchTimer();
				transitionTo(new RunningState());
				return;
			}
			target = fallback;
		}
		doHandover(old, target, false);
	}

	public ServerPlayer randomSpectator() {
		return selectWeightedSpectator();
	}

	public ServerPlayer selectWeightedSpectator() {
		List<ServerPlayer> candidates = spectators();
		if (candidates.size() < Math.max(0, config.minimumPlayersForSwitch - 1) || candidates.isEmpty()) {
			return null;
		}
		return selectWeightedCandidate(candidates);
	}

	public ServerPlayer selectWeightedCandidate(List<ServerPlayer> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		if (candidates.size() == 1) {
			return candidates.get(0);
		}

		List<ServerPlayer> eligible = new ArrayList<>();
		for (ServerPlayer player : candidates) {
			if (previousRunnerId == null || !player.getUUID().equals(previousRunnerId)) {
				eligible.add(player);
			}
		}
		List<ServerPlayer> pool = eligible.isEmpty() ? candidates : eligible;
		if (pool.size() == 1) {
			return pool.get(0);
		}

		double totalWeight = 0.0;
		double[] weights = new double[pool.size()];

		for (int i = 0; i < pool.size(); i++) {
			ServerPlayer p = pool.get(i);
			SpeedrunStats.PlayerEntry entry = stats.entry(p);

			int waitRounds;
			if (entry.lastActiveRound > 0) {
				waitRounds = Math.max(1, currentRound - entry.lastActiveRound);
			} else {
				int joinR = entry.joinRound > 0 ? entry.joinRound : 1;
				waitRounds = Math.max(1, currentRound - joinR + 1);
			}

			long waitSeconds = Math.max(0, entry.spectatorSeconds);
			long activeSeconds = Math.max(0, entry.activeSeconds);
			double activeMinutes = activeSeconds / 60.0;

			double weight = 10.0
					+ (waitRounds * 25.0)
					+ (waitSeconds / 60.0) * 5.0
					+ (300.0 / (activeMinutes + 3.0));

			weights[i] = Math.max(1.0, weight);
			totalWeight += weights[i];
		}

		double roll = RANDOM.nextDouble() * totalWeight;
		for (int i = 0; i < pool.size(); i++) {
			roll -= weights[i];
			if (roll <= 0.0) {
				return pool.get(i);
			}
		}

		return pool.get(pool.size() - 1);
	}

	public void doHandover(ServerPlayer old, ServerPlayer next, boolean isManual) {
		if (old != null) {
			previousRunnerId = old.getUUID();
			stats.previousRunnerId = old.getUUID().toString();
			SpeedrunStats.PlayerEntry oldEntry = stats.entry(old);
			oldEntry.lastActiveRound = currentRound;
			oldEntry.spectatorSeconds = 0;
		}
		currentRound++;
		stats.currentRound = currentRound;
		activePlayerId = next.getUUID();
		stats.activePlayerId = next.getUUID().toString();
		
		SpeedrunStats.PlayerEntry nextEntry = stats.entry(next);
		nextEntry.lastActiveRound = currentRound;
		nextEntry.spectatorSeconds = 0;
		stats.save();

		next.setGameMode(GameType.SURVIVAL);
		pendingTargetId = null;
		countdownRemaining = 0;
		resetSwitchTimer();
		transitionTo(new RunningState());

		shared.applyHandover(old, next);
		SpeedrunEventBus.post(new SpeedrunSwitchEvent(old, next, isManual));

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p == next) {
				p.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
			} else {
				p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
			}
		}

		GameMessages.sendTitle(next, "event.handover.active_title", "event.handover.active_subtitle", 5, 40, 15);
		GameMessages.send(next, "event.handover.active_msg");

		if (old != next && old.isAlive()) {
			GameMessages.sendTitle(old, "event.handover.spectator_title", "event.handover.spectator_subtitle", 5, 40, 15, next.getGameProfile().name());
		}

		GameMessages.broadcast(server, "event.handover.broadcast", next.getGameProfile().name(), config.switchIntervalMinutes);
	}

	public void broadcastState() {
		JsonObject obj = new JsonObject();
		Mode m = getMode();
		obj.addProperty("mode", m.name());
		ServerPlayer active = getActivePlayer();
		obj.addProperty("active", active == null ? "" : active.getGameProfile().name());
		if (m == Mode.RUNNING) {
			obj.addProperty("secondsUntilSwitch", secondsUntilSwitch());
			if (taskManager != null) {
				taskManager.appendToState(obj);
			}
		} else if (m == Mode.STARTING || m == Mode.COUNTDOWN) {
			obj.addProperty("countdown", countdownRemaining);
		} else if (m == Mode.FINISHED) {
			obj.addProperty("elapsed", SpeedrunStats.formatSeconds(stats.elapsedSeconds()));
		} else if (m == Mode.IDLE) {
			boolean canResume = (stats.totalActiveSeconds > 0 || !stats.players.isEmpty()) && !stats.finished;
			obj.addProperty("canResume", canResume);
			if (canResume) {
				obj.addProperty("resumeRound", stats.currentRound);
				obj.addProperty("elapsed", SpeedrunStats.formatSeconds(stats.elapsedSeconds()));
			}
		}
		SpeedrunStatePayload payload = new SpeedrunStatePayload(GSON.toJson(obj));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}