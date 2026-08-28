package cn.cutelittlesky.speedrunswitch.mixin;

import cn.cutelittlesky.speedrunswitch.SpeedrunSwitchMod;
import cn.cutelittlesky.speedrunswitch.core.CameraGuard;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCameraMixin {

	@Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
	private void speedrunswitch$onSetCamera(Entity target, CallbackInfo ci) {
		if (CameraGuard.isBypassed()) {
			return;
		}
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (self.hasDisconnected() || self.isRemoved() || self.connection == null) {
			return;
		}
		SpeedrunManager manager = SpeedrunSwitchMod.getManager();
		if (manager == null) {
			return;
		}
		SpeedrunManager.Mode mode = manager.getMode();
		if (mode != SpeedrunManager.Mode.RUNNING && mode != SpeedrunManager.Mode.COUNTDOWN) {
			return;
		}
		if (manager.isSpectator(self)) {
			ServerPlayer active = manager.getActivePlayer();
			if (active != null && active.isAlive() && !active.isDeadOrDying()) {
				if (self.level() != active.level()) {
					return;
				}
				if (target == null || target == self || target != active) {
					if (self.connection != null) {
						self.connection.send(new ClientboundSetCameraPacket(active));
					}
					ci.cancel();
				}
			}
		}
	}
}
