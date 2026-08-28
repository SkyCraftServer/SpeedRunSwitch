package cn.cutelittlesky.speedrunswitch.core.event;

import net.minecraft.server.level.ServerPlayer;

public record SpeedrunRunnerDeathEvent(ServerPlayer runner) implements SpeedrunEvent {
}



