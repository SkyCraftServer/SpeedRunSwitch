package cn.cutelittlesky.speedrunswitch.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SpeedrunEventBus {

	private static final Map<Class<? extends SpeedrunEvent>, List<Consumer<? extends SpeedrunEvent>>> LISTENERS = new ConcurrentHashMap<>();

	private SpeedrunEventBus() {}

	public static <T extends SpeedrunEvent> void register(Class<T> eventClass, Consumer<T> listener) {
		LISTENERS.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(listener);
	}

	@SuppressWarnings("unchecked")
	public static <T extends SpeedrunEvent> void post(T event) {
		if (event == null) {
			return;
		}
		List<Consumer<? extends SpeedrunEvent>> list = LISTENERS.get(event.getClass());
		if (list != null) {
			for (Consumer<? extends SpeedrunEvent> consumer : list) {
				try {
					((Consumer<T>) consumer).accept(event);
				} catch (Exception e) {
					System.err.println("[speedrun-switch] 事件分发异常 (" + event.getClass().getSimpleName() + "): " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
	}

	public static void clear() {
		LISTENERS.clear();
	}
}



