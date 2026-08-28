package cn.cutelittlesky.speedrunswitch.core.event;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;

public record SpeedrunStateChangedEvent(SpeedrunManager.Mode oldMode, SpeedrunManager.Mode newMode) implements SpeedrunEvent {
}



