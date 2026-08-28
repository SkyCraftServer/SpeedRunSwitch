package cn.cutelittlesky.speedrunswitch.core.state;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;

public interface SpeedrunState {

	SpeedrunManager.Mode getMode();

	default void onEnter(SpeedrunManager manager) {}

	default void onExit(SpeedrunManager manager) {}

	default void tick(SpeedrunManager manager) {}

	default void tickFast(SpeedrunManager manager) {}

	default void tickSecond(SpeedrunManager manager) {}
}



