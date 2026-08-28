package cn.cutelittlesky.speedrunswitch.core.event;

import net.minecraft.server.level.ServerPlayer;

public record SpeedrunDragonKillEvent(ServerPlayer killer) implements SpeedrunEvent {
}



