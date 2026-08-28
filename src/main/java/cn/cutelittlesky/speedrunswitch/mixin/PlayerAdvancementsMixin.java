package cn.cutelittlesky.speedrunswitch.mixin;

import cn.cutelittlesky.speedrunswitch.SpeedrunSwitchMod;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At("HEAD"), cancellable = true)
	private void speedrunswitch$blockSpectatorAdvancements(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
		SpeedrunManager manager = SpeedrunSwitchMod.getManager();
		if (manager != null && manager.isSpectator(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
