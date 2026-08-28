package cn.cutelittlesky.speedrunswitch.svc;

import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public final class SvcBridge {

	private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("voicechat");

	private SvcBridge() {}

	public static boolean isAvailable() {
		return AVAILABLE;
	}

	private static void invoke(String name, Class<?>[] types, Object... args) {
		if (!AVAILABLE) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("cn.cutelittlesky.speedrunswitch.svc.SvcPlugin");
			Method method = clazz.getMethod(name, types);
			method.invoke(null, args);
		} catch (Throwable t) {
			System.err.println("[speedrun-switch] SVC 调用失败(" + name + "): " + t);
		}
	}

	public static void onModeStart(String groupName) {
		invoke("onModeStart", new Class<?>[]{String.class}, groupName);
	}

	public static void onModeEnd() {
		invoke("onModeEnd", new Class<?>[]{}, new Object[]{});
	}

	public static void onRoleChanged(ServerPlayer player, boolean active) {
		invoke("onRoleChanged", new Class<?>[]{ServerPlayer.class, boolean.class}, player, active);
	}

	public static void tick(MinecraftServer server, SpeedrunManager manager) {
		invoke("tick", new Class<?>[]{MinecraftServer.class, SpeedrunManager.class}, server, manager);
	}
}



