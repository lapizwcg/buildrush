package dev.wcg.buildrush;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * BuildRush: массовая установка блоков протяжкой. Сервер валидирует список
 * позиций, списывает блоки из инвентаря (включая рюкзаки и шалкеры — любой
 * предмет с компонентом container) и ставит их.
 */
public class BuildRushMod implements ModInitializer {
    public static final String MOD_ID = "buildrush";
    public static final int MAX_BLOCKS = 128;
    public static final double MAX_DISTANCE = 40.0;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(BuildPayload.TYPE, BuildPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BuildPayload.TYPE, (payload, context) ->
                handleBuild(context.player(), payload.positions()));
    }

    private static void handleBuild(ServerPlayer player, List<BlockPos> positions) {
        if (!(player.level() instanceof ServerLevel level) || positions.isEmpty()
                || positions.size() > MAX_BLOCKS || player.isSpectator()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();

        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= MAX_DISTANCE
                    && level.getBlockState(pos).canBeReplaced()
                    && level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
                valid.add(pos);
            }
        }
        if (valid.isEmpty()) {
            return;
        }

        int available = player.isCreative() ? Integer.MAX_VALUE : countItems(player, held);
        int toPlace = Math.min(valid.size(), available);
        if (toPlace <= 0) {
            return;
        }

        int placed = 0;
        for (int i = 0; i < toPlace; i++) {
            if (level.setBlock(valid.get(i), state, 3)) {
                placed++;
            }
        }
        if (placed > 0) {
            if (!player.isCreative()) {
                consumeItems(player, held, placed);
            }
            SoundType sound = state.getSoundType();
            level.playSound(null, valid.get(0), sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        }
    }

    /** Сколько таких предметов у игрока: инвентарь + любые контейнеры-предметы (рюкзаки, шалкеры). */
    public static int countItems(net.minecraft.world.entity.player.Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack stack : player.getInventory()) {
            if (ItemStack.isSameItemSameComponents(stack, sample)) {
                count += stack.getCount();
            } else if (stack.has(DataComponents.CONTAINER)) {
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                count += contents.nonEmptyItemCopyStream()
                        .filter(inner -> ItemStack.isSameItemSameComponents(inner, sample))
                        .mapToInt(ItemStack::getCount)
                        .sum();
            }
        }
        return count;
    }

    /** Списывает n предметов: сначала из инвентаря, затем из контейнеров-предметов. */
    private static void consumeItems(ServerPlayer player, ItemStack sample, int n) {
        // Обычные слоты.
        for (ItemStack stack : player.getInventory()) {
            if (n <= 0) {
                return;
            }
            if (ItemStack.isSameItemSameComponents(stack, sample)) {
                int take = Math.min(n, stack.getCount());
                stack.shrink(take);
                n -= take;
            }
        }
        // Контейнеры-предметы (рюкзаки compact_storage, шалкеры и т.п.).
        for (ItemStack stack : player.getInventory()) {
            if (n <= 0) {
                return;
            }
            if (!stack.has(DataComponents.CONTAINER)) {
                continue;
            }
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            List<ItemStack> items = new ArrayList<>();
            contents.allItemsCopyStream().forEach(items::add);
            boolean changed = false;
            for (ItemStack inner : items) {
                if (n <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(inner, sample)) {
                    int take = Math.min(n, inner.getCount());
                    inner.shrink(take);
                    n -= take;
                    changed = true;
                }
            }
            if (changed) {
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            }
        }
    }
}
