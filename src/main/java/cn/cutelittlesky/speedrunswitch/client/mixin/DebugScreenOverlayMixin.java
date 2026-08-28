package cn.cutelittlesky.speedrunswitch.client.mixin;

import cn.cutelittlesky.speedrunswitch.client.ClientSpeedrunState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.ListIterator;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

	@Inject(method = "extractLines", at = @At("HEAD"))
	private void speedrunswitch$obfuscateDebugCoordinates(GuiGraphicsExtractor drawContext, List<String> lines, boolean left, int row, CallbackInfo ci) {
		if (lines == null || !ClientSpeedrunState.isSpectatorRole()) {
			return;
		}
		ListIterator<String> it = lines.listIterator();
		while (it.hasNext()) {
			String line = it.next();
			if (line == null) {
				continue;
			}
			if (line.startsWith("XYZ:")) {
				it.set("XYZ: §c[已伪造/Fake] §e~8848.250 / ~64.000 / ~-1314.520");
			} else if (line.startsWith("Block:")) {
				it.set("Block: §c[已伪造/Fake] §e8848 64 -1314 [8 0 6]");
			} else if (line.startsWith("Chunk:")) {
				it.set("Chunk: §c[已伪造/Fake] §e553 4 -82 [8 0 6]");
			} else if (line.startsWith("Facing:")) {
				it.set("Facing: §c[已伪造/Fake] §enorth (Towards negative Z)");
			} else if (line.startsWith("Targeted Block:")) {
				it.set("Targeted Block: §c[已伪造/Fake] §e8848, 64, -1314");
			} else if (line.startsWith("Targeted Fluid:")) {
				it.set("Targeted Fluid: §c[已伪造/Fake] §e8848, 64, -1314");
			} else if (line.startsWith("Targeted Entity:")) {
				it.set("Targeted Entity: §c[已隐藏/Hidden]");
			} else if (line.startsWith("Biome:")) {
				it.set("Biome: §c[已伪造/Fake] §eminecraft:plains");
			} else if (line.startsWith("SH:") || line.startsWith("SC:")) {
				it.set("SH: §c[已隐藏/Hidden]");
			}
		}
	}
}
