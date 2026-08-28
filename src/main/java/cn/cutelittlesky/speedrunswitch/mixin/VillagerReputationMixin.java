package cn.cutelittlesky.speedrunswitch.mixin;

import cn.cutelittlesky.speedrunswitch.SpeedrunSwitchMod;
import cn.cutelittlesky.speedrunswitch.core.SpeedrunManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Villager.class)
public abstract class VillagerReputationMixin {

	@Shadow
	private GossipContainer gossips;

	@Inject(method = "getPlayerReputation", at = @At("HEAD"), cancellable = true)
	private void speedrunswitch$getSharedPlayerReputation(Player player, CallbackInfoReturnable<Integer> cir) {
		SpeedrunManager manager = SpeedrunSwitchMod.getManager();
		if (manager != null && manager.isSpeedrunActive() && this.gossips != null) {
			int teamRep = this.gossips.getReputation(SpeedrunManager.TEAM_REPUTATION_UUID, type -> true);
			int playerRep = this.gossips.getReputation(player.getUUID(), type -> true);
			int bestRep = Math.max(teamRep, playerRep);

			if (manager.getStats() != null && manager.getStats().players != null) {
				for (String uuidStr : manager.getStats().players.keySet()) {
					try {
						UUID memberUuid = UUID.fromString(uuidStr);
						int memberRep = this.gossips.getReputation(memberUuid, type -> true);
						if (memberRep > bestRep) {
							bestRep = memberRep;
						}
					} catch (Exception ignored) {
					}
				}
			}

			int effectiveRep = (teamRep != 0) ? teamRep : bestRep;
			cir.setReturnValue(effectiveRep);
		}
	}

	@Inject(method = "onReputationEventFrom", at = @At("HEAD"))
	private void speedrunswitch$onReputationEventFrom(ReputationEventType type, Entity source, CallbackInfo ci) {
		SpeedrunManager manager = SpeedrunSwitchMod.getManager();
		if (manager != null && manager.isSpeedrunActive() && source instanceof Player && this.gossips != null) {
			if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
				this.gossips.add(SpeedrunManager.TEAM_REPUTATION_UUID, GossipType.MAJOR_POSITIVE, 20);
				this.gossips.add(SpeedrunManager.TEAM_REPUTATION_UUID, GossipType.MINOR_POSITIVE, 25);
			} else if (type == ReputationEventType.TRADE) {
				this.gossips.add(SpeedrunManager.TEAM_REPUTATION_UUID, GossipType.TRADING, 2);
			} else if (type == ReputationEventType.VILLAGER_HURT) {
				this.gossips.add(SpeedrunManager.TEAM_REPUTATION_UUID, GossipType.MINOR_NEGATIVE, 25);
			} else if (type == ReputationEventType.VILLAGER_KILLED) {
				this.gossips.add(SpeedrunManager.TEAM_REPUTATION_UUID, GossipType.MAJOR_NEGATIVE, 25);
			}
		}
	}
}
