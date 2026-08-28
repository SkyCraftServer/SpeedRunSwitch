package cn.cutelittlesky.speedrunswitch.core.event;

import cn.cutelittlesky.speedrunswitch.core.task.SpeedrunTask;

public record SpeedrunTaskEvent(SpeedrunTask task, Type type) implements SpeedrunEvent {
	public enum Type {
		ASSIGNED,
		PROGRESS,
		COMPLETED,
		TIMEOUT,
		CANCELLED
	}
}



