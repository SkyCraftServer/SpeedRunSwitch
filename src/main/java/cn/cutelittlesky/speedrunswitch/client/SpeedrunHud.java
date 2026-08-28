package cn.cutelittlesky.speedrunswitch.client;

import cn.cutelittlesky.speedrunswitch.util.MiniMessageUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SpeedrunHud {

	private SpeedrunHud() {}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("speedrun-switch", "hud"), SpeedrunHud::render);
	}

	private static void render(GuiGraphicsExtractor drawContext, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) {
			return;
		}
		Font font = mc.font;
		int x = 4;
		int y = 4;

		boolean isRunner = ClientSpeedrunState.isActiveRunner();
		boolean isSpectator = ClientSpeedrunState.isSpectatorRole();

		List<Component> stateLines = buildStateLines(isRunner);
		int stateHeight = 0;
		if (!stateLines.isEmpty()) {
			int stateBorder = getStateBorderColor();
			int stateBg = isRunner ? 0xCC0F172A : 0xCC111827;
			stateHeight = renderPanel(drawContext, font, stateLines, x, y, stateBorder, stateBg);
		}

		if (ClientSpeedrunState.isRunning()) {
			String task = ClientSpeedrunState.getTask();
			if (task != null && !task.isEmpty()) {
				int taskY = y + stateHeight + 3;
				List<Component> taskLines = buildTaskLines(ClientSpeedrunState.getTaskComponent(), isRunner);
				int taskBorder = getTaskBorderColor(ClientSpeedrunState.getTaskSecondsLeft());
				int taskBg = 0xCC1C1917;
				renderPanel(drawContext, font, taskLines, x, taskY, taskBorder, taskBg);
			}
		}

		if (isSpectator && (ClientSpeedrunState.isRunning() || ClientSpeedrunState.isCountdown())) {
			renderSpectatorHotbar(drawContext, font, mc);
		}
	}

	private static void renderSpectatorHotbar(GuiGraphicsExtractor drawContext, Font font, Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();

		int slotSize = 20;
		int totalSlots = 9;
		int hotbarWidth = totalSlots * slotSize + 2;
		int hotbarHeight = slotSize + 4;
		int startX = (screenWidth - hotbarWidth) / 2;
		int startY = screenHeight - hotbarHeight - 2;

		renderSpectatorVitals(drawContext, font, mc, startX, hotbarWidth, startY);

		drawContext.fill(startX, startY, startX + hotbarWidth, startY + hotbarHeight, 0xAA0F172A);
		drawContext.fill(startX, startY, startX + hotbarWidth, startY + 1, 0x99334155);
		drawContext.fill(startX, startY + hotbarHeight - 1, startX + hotbarWidth, startY + hotbarHeight, 0x99334155);
		drawContext.fill(startX, startY + 1, startX + 1, startY + hotbarHeight, 0x99334155);
		drawContext.fill(startX + hotbarWidth - 1, startY, startX + hotbarWidth, startY + hotbarHeight, 0x99334155);

		Inventory inv = mc.player.getInventory();
		int selectedSlot = inv.getSelectedSlot();

		for (int i = 0; i < totalSlots; i++) {
			int slotX = startX + 1 + i * slotSize;
			int slotY = startY + 2;

			boolean isSelected = (i == selectedSlot);

			if (isSelected) {
				drawContext.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0x55EAB308);
				drawContext.fill(slotX, slotY, slotX + slotSize, slotY + 1, 0xFFFACC15);
				drawContext.fill(slotX, slotY + slotSize - 1, slotX + slotSize, slotY + slotSize, 0xFFFACC15);
				drawContext.fill(slotX, slotY, slotX + 1, slotY + slotSize, 0xFFFACC15);
				drawContext.fill(slotX + slotSize - 1, slotY, slotX + slotSize, slotY + slotSize, 0xFFFACC15);
			} else {
				drawContext.fill(slotX, slotY, slotX + slotSize, slotY + 1, 0x33475569);
				drawContext.fill(slotX, slotY + slotSize - 1, slotX + slotSize, slotY + slotSize, 0x33475569);
				drawContext.fill(slotX, slotY, slotX + 1, slotY + slotSize, 0x33475569);
				drawContext.fill(slotX + slotSize - 1, slotY, slotX + slotSize, slotY + slotSize, 0x33475569);
			}

			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				drawContext.item(stack, slotX + 2, slotY + 2);
				drawContext.itemDecorations(font, stack, slotX + 2, slotY + 2);
			}
		}

		ItemStack offhand = inv.getItem(40);
		int offhandX = startX - slotSize - 6;
		int offhandY = startY;
		drawContext.fill(offhandX, offhandY, offhandX + slotSize + 2, offhandY + hotbarHeight, 0xAA0F172A);
		drawContext.fill(offhandX, offhandY, offhandX + slotSize + 2, offhandY + 1, 0x99334155);
		drawContext.fill(offhandX, offhandY + hotbarHeight - 1, offhandX + slotSize + 2, offhandY + hotbarHeight, 0x99334155);
		drawContext.fill(offhandX, offhandY + 1, offhandX + 1, offhandY + hotbarHeight, 0x99334155);
		drawContext.fill(offhandX + slotSize + 1, offhandY, offhandX + slotSize + 2, offhandY + hotbarHeight, 0x99334155);

		if (!offhand.isEmpty()) {
			drawContext.item(offhand, offhandX + 2, offhandY + 2);
			drawContext.itemDecorations(font, offhand, offhandX + 2, offhandY + 2);
		} else {
			drawContext.text(font, "§8副", offhandX + 6, offhandY + 6, 0xFFFFFFFF);
		}
	}

	private static void renderSpectatorVitals(GuiGraphicsExtractor drawContext, Font font, Minecraft mc, int startX, int hotbarWidth, int hotbarY) {
		if (mc.player == null) {
			return;
		}
		int barHeight = 12;
		int barY = hotbarY - barHeight - 2;

		drawContext.fill(startX, barY, startX + hotbarWidth, barY + barHeight, 0xBB0F172A);
		drawContext.fill(startX, barY, startX + hotbarWidth, barY + 1, 0x66334155);
		drawContext.fill(startX, barY + barHeight - 1, startX + hotbarWidth, barY + barHeight, 0x66334155);
		drawContext.fill(startX, barY + 1, startX + 1, barY + barHeight, 0x66334155);
		drawContext.fill(startX + hotbarWidth - 1, barY, startX + hotbarWidth, barY + barHeight, 0x66334155);

		LivingEntity vitalsSource = (mc.getCameraEntity() instanceof LivingEntity le) ? le : mc.player;
		float health = vitalsSource.getHealth();
		float maxHealth = vitalsSource.getMaxHealth();
		int foodLevel = mc.player.getFoodData().getFoodLevel();
		int armorValue = mc.player.getArmorValue();
		int expLevel = mc.player.experienceLevel;

		String healthStr = String.format("§c❤ %.0f/%.0f", health, maxHealth);
		drawContext.text(font, healthStr, startX + 4, barY + 2, 0xFFFFFFFF);

		StringBuilder statusRight = new StringBuilder();
		if (armorValue > 0) {
			statusRight.append("§b🛡").append(armorValue).append(" ");
		}
		statusRight.append("§6🍗").append(foodLevel);
		if (expLevel > 0) {
			statusRight.append(" §aLv.").append(expLevel);
		}

		String rightStr = statusRight.toString();
		int rightW = font.width(rightStr);
		drawContext.text(font, rightStr, startX + hotbarWidth - rightW - 4, barY + 2, 0xFFFFFFFF);
	}

	private static int renderPanel(GuiGraphicsExtractor drawContext, Font font, List<Component> lines, int x, int y, int borderColor, int backgroundColor) {
		int maxLineWidth = 0;
		for (Component line : lines) {
			maxLineWidth = Math.max(maxLineWidth, font.width(line));
		}
		int panelWidth = maxLineWidth + 10;
		int panelHeight = 4 + lines.size() * (font.lineHeight + 2) + 2;

		drawContext.fill(x, y, x + panelWidth, y + panelHeight, backgroundColor);

		drawContext.fill(x, y, x + panelWidth, y + 1, borderColor);
		drawContext.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, borderColor);
		drawContext.fill(x, y + 1, x + 1, y + panelHeight, borderColor);
		drawContext.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, borderColor);

		int textY = y + 3;
		for (Component line : lines) {
			drawContext.text(font, line, x + 5, textY, 0xFFFFFFFF);
			textY += font.lineHeight + 2;
		}

		return panelHeight;
	}

	private static List<Component> buildStateLines(boolean isRunner) {
		List<Component> lines = new ArrayList<>();

		if (ClientSpeedrunState.isStarting()) {
			if (isRunner) {
				lines.add(MiniMessageUtils.tr("hud.state.starting.runner", ClientSpeedrunState.getCountdown()));
			} else {
				lines.add(MiniMessageUtils.tr("hud.state.starting.spectator.title"));
				lines.add(MiniMessageUtils.tr("hud.state.starting.spectator.runner", ClientSpeedrunState.getActivePlayer()));
				lines.add(MiniMessageUtils.tr("hud.state.starting.spectator.countdown", ClientSpeedrunState.getCountdown()));
			}
			return lines;
		}

		if (ClientSpeedrunState.isRunning()) {
			if (isRunner) {
				lines.add(MiniMessageUtils.tr("hud.state.running.runner",
						ClientSpeedrunState.formatSeconds(ClientSpeedrunState.getSecondsUntilSwitch())));
			} else {
				lines.add(MiniMessageUtils.tr("hud.state.running.spectator.title"));
				lines.add(MiniMessageUtils.tr("hud.state.running.spectator.spectating", ClientSpeedrunState.getActivePlayer()));
				lines.add(MiniMessageUtils.tr("hud.state.running.spectator.switch_in",
						ClientSpeedrunState.formatSeconds(ClientSpeedrunState.getSecondsUntilSwitch())));
			}
			return lines;
		}

		if (ClientSpeedrunState.isCountdown()) {
			if (isRunner) {
				lines.add(MiniMessageUtils.tr("hud.state.countdown.runner", ClientSpeedrunState.getCountdown()));
			} else {
				lines.add(MiniMessageUtils.tr("hud.state.countdown.spectator.title"));
				lines.add(MiniMessageUtils.tr("hud.state.countdown.spectator.current", ClientSpeedrunState.getActivePlayer()));
				lines.add(MiniMessageUtils.tr("hud.state.countdown.spectator.countdown", ClientSpeedrunState.getCountdown()));
			}
			return lines;
		}

		if (ClientSpeedrunState.isFinished()) {
			lines.add(MiniMessageUtils.tr("hud.state.finished.title"));
			lines.add(MiniMessageUtils.tr("hud.state.finished.elapsed", ClientSpeedrunState.getElapsed()));
			lines.add(MiniMessageUtils.tr("hud.state.finished.reset_tip"));
			return lines;
		}

		if (ClientSpeedrunState.canResume()) {
			lines.add(MiniMessageUtils.tr("hud.state.idle.resume_title"));
			lines.add(MiniMessageUtils.tr("hud.state.idle.resume_info", ClientSpeedrunState.getResumeRound(), ClientSpeedrunState.getElapsed()));
			lines.add(MiniMessageUtils.tr("hud.state.idle.resume_tip"));
			lines.add(MiniMessageUtils.tr("hud.state.idle.restart_tip"));
			return lines;
		}

		lines.add(MiniMessageUtils.tr("hud.state.idle.title"));
		lines.add(MiniMessageUtils.tr("hud.state.idle.start_tip"));
		return lines;
	}

	private static List<Component> buildTaskLines(Component taskComponent, boolean isRunner) {
		List<Component> lines = new ArrayList<>();
		int seconds = ClientSpeedrunState.getTaskSecondsLeft();
		String timeColor = seconds > 30 ? "<#34D399>" : (seconds > 10 ? "<#FBBF24>" : "<#F87171><b>");
		String timeEnd = seconds <= 10 ? "</b></#F87171>" : (seconds > 30 ? "</#34D399>" : "</#FBBF24>");
		String timeStr = ClientSpeedrunState.formatSeconds(seconds);

		lines.add(MiniMessageUtils.tr("hud.task.title", taskComponent.getString()));
		lines.add(MiniMessageUtils.parse(String.format("  <#94A3B8>时限：</#94A3B8> %s%s%s", timeColor, timeStr, timeEnd)));
		return lines;
	}

	private static int getStateBorderColor() {
		if (ClientSpeedrunState.isStarting()) {
			return 0xDDEAB308;
		}
		if (ClientSpeedrunState.isRunning()) {
			return 0xDD3B82F6;
		}
		if (ClientSpeedrunState.isCountdown()) {
			return 0xDDEF4444;
		}
		if (ClientSpeedrunState.isFinished()) {
			return 0xDD22C55E;
		}
		if (ClientSpeedrunState.canResume()) {
			return 0xDDF97316;
		}
		return 0x8864748B;
	}

	private static int getTaskBorderColor(int seconds) {
		if (seconds <= 10) {
			return 0xDDEF4444;
		}
		if (seconds <= 30) {
			return 0xDDEAB308;
		}
		return 0xDDF59E0B;
	}
}