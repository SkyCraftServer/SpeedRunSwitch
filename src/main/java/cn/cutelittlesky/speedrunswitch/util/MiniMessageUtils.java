package cn.cutelittlesky.speedrunswitch.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MiniMessageUtils {

	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
	private static final Map<String, Map<String, String>> LANG_MAPS = new ConcurrentHashMap<>();

	static {
		loadLanguage("zh_cn", "/assets/speedrun-switch/lang/zh_cn.json");
		loadLanguage("en_us", "/assets/speedrun-switch/lang/en_us.json");
	}

	private MiniMessageUtils() {}

	private static void loadLanguage(String langCode, String resourcePath) {
		try (InputStream is = MiniMessageUtils.class.getResourceAsStream(resourcePath)) {
			if (is != null) {
				JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
				Map<String, String> map = new ConcurrentHashMap<>();
				for (String key : obj.keySet()) {
					map.put(key, obj.get(key).getAsString());
				}
				LANG_MAPS.put(langCode.toLowerCase(Locale.ROOT), map);
			}
		} catch (Exception e) {
			System.err.println("[speedrun-switch] 加载语言文件失败 (" + langCode + "): " + e.getMessage());
		}
	}

	public static MutableComponent parse(String miniMessageText) {
		if (miniMessageText == null || miniMessageText.isEmpty()) {
			return Component.empty();
		}
		try {
			net.kyori.adventure.text.Component adventureComp = MINI_MESSAGE.deserialize(miniMessageText);
			return toNative(adventureComp);
		} catch (Exception e) {
			return Component.literal(miniMessageText);
		}
	}

	public static MutableComponent trFor(String langCode, String key, Object... args) {
		String lang = (langCode == null || langCode.isEmpty()) ? "zh_cn" : langCode.toLowerCase(Locale.ROOT);
		Map<String, String> dict = LANG_MAPS.get(lang);
		String template = dict != null ? dict.get(key) : null;

		if (template == null && !"zh_cn".equals(lang)) {
			Map<String, String> zh = LANG_MAPS.get("zh_cn");
			if (zh != null) {
				template = zh.get(key);
			}
		}
		if (template == null) {
			Map<String, String> en = LANG_MAPS.get("en_us");
			if (en != null) {
				template = en.get(key);
			}
		}
		if (template == null) {
			template = Language.getInstance().getOrDefault(key);
		}
		if (template == null || template.isEmpty()) {
			return Component.literal(key);
		}

		try {
			String formatted = (args == null || args.length == 0) ? template : String.format(template, args);
			return parse(formatted);
		} catch (Exception e) {
			return Component.literal(template);
		}
	}

	public static MutableComponent trForPlayer(ServerPlayer player, String key, Object... args) {
		String lang = "zh_cn";
		if (player != null && player.clientInformation() != null && player.clientInformation().language() != null) {
			lang = player.clientInformation().language();
		}
		return trFor(lang, key, args);
	}

	public static MutableComponent tr(String key, Object... args) {
		String lang = "zh_cn";
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc != null && mc.options != null && mc.options.languageCode != null) {
				lang = mc.options.languageCode;
			}
		} catch (Throwable ignored) {
		}
		return trFor(lang, key, args);
	}

	public static MutableComponent toNative(net.kyori.adventure.text.Component adventure) {
		if (adventure == null) {
			return Component.empty();
		}
		MutableComponent result;
		if (adventure instanceof net.kyori.adventure.text.TextComponent tc) {
			result = Component.literal(tc.content());
		} else {
			result = Component.empty();
		}

		net.kyori.adventure.text.format.Style advStyle = adventure.style();
		Style mcStyle = Style.EMPTY;

		if (advStyle.color() != null) {
			mcStyle = mcStyle.withColor(TextColor.fromRgb(advStyle.color().value()));
		}
		if (advStyle.hasDecoration(TextDecoration.BOLD)) {
			mcStyle = mcStyle.withBold(advStyle.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE);
		}
		if (advStyle.hasDecoration(TextDecoration.ITALIC)) {
			mcStyle = mcStyle.withItalic(advStyle.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE);
		}
		if (advStyle.hasDecoration(TextDecoration.UNDERLINED)) {
			mcStyle = mcStyle.withUnderlined(advStyle.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE);
		}
		if (advStyle.hasDecoration(TextDecoration.STRIKETHROUGH)) {
			mcStyle = mcStyle.withStrikethrough(advStyle.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE);
		}
		if (advStyle.hasDecoration(TextDecoration.OBFUSCATED)) {
			mcStyle = mcStyle.withObfuscated(advStyle.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE);
		}

		result.setStyle(mcStyle);

		for (net.kyori.adventure.text.Component child : adventure.children()) {
			result.append(toNative(child));
		}

		return result;
	}
}