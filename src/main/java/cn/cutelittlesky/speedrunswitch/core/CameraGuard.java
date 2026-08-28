package cn.cutelittlesky.speedrunswitch.core;

public final class CameraGuard {

	private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private CameraGuard() {}

	public static boolean isBypassed() {
		return BYPASS.get();
	}

	public static void runWithBypass(Runnable action) {
		BYPASS.set(Boolean.TRUE);
		try {
			action.run();
		} finally {
			BYPASS.set(Boolean.FALSE);
		}
	}
}
