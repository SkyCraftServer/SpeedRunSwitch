package cn.cutelittlesky.speedrunswitch.core.state;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;

public class IdleState implements SpeedrunState {

	@Override
	public SpeedrunManager.Mode getMode() {
		return SpeedrunManager.Mode.IDLE;
	}

	@Override
	public void onEnter(SpeedrunManager manager) {
		manager.setCountdownRemaining(0);
	}
}



