package dev.wcg.buildrush;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Клиент → сервер: список позиций для массовой установки блоков из руки. */
public record BuildPayload(List<BlockPos> positions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BuildPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BuildRushMod.MOD_ID, "build"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, BuildPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(BuildRushMod.MAX_BLOCKS))
                    .map(BuildPayload::new, BuildPayload::positions);

    @Override
    public CustomPacketPayload.Type<BuildPayload> type() {
        return TYPE;
    }
}
