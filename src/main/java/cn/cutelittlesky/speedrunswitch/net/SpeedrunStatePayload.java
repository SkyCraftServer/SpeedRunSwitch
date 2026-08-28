package cn.cutelittlesky.speedrunswitch.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SpeedrunStatePayload(String json) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SpeedrunStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("speedrun-switch", "state"));

	public static final StreamCodec<ByteBuf, SpeedrunStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			SpeedrunStatePayload::json,
			SpeedrunStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}



