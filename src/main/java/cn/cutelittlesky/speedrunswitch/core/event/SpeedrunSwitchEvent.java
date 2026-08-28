package cn.cutelittlesky.speedrunswitch.core.event;

import net.minecraft.server.level.ServerPlayer;

public record SpeedrunSwitchEvent(ServerPlayer oldRunner, ServerPlayer newRunner, boolean isManual) implements SpeedrunEvent {
}



