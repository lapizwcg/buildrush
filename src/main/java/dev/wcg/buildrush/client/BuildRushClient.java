package dev.wcg.buildrush.client;

import dev.wcg.buildrush.BuildPayload;
import dev.wcg.buildrush.BuildRushMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Протяжка: короткий ПКМ — ванильная установка; удержание + движение прицела —
 * линия призраков от первого блока. Зелёные — хватает блоков, красные — нет.
 * Отпускание ставит все зелёные (сервер валидирует и списывает).
 */
public class BuildRushClient implements ClientModInitializer {
    private static final GizmoStyle GHOST_OK = GizmoStyle.strokeAndFill(0xFF44DD66, 2.0F, 0x4033CC55);
    private static final GizmoStyle GHOST_BAD = GizmoStyle.strokeAndFill(0xFFDD4444, 2.0F, 0x40CC3333);

    private static BlockPos anchor;
    private static ItemStack anchorItem = ItemStack.EMPTY;
    private static boolean dragging;
    private static final List<BlockPos> ghosts = new ArrayList<>();
    private static int available;

    @Override
    public void onInitializeClient() {
        // Первый клик — ванилла (запоминаем якорь); повторы при удержании — глушим.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BlockItem)) {
                return InteractionResult.PASS;
            }
            if (anchor == null) {
                anchor = hit.getBlockPos().relative(hit.getDirection());
                anchorItem = held.copyWithCount(1);
                return InteractionResult.PASS; // ваниллa ставит первый блок
            }
            return InteractionResult.FAIL; // удержание: авто-повтор ваниллы выключен
        });

        ClientTickEvents.END_CLIENT_TICK.register(BuildRushClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        boolean useDown = minecraft.options.keyUse.isDown();

        if (!useDown) {
            // Отпустили: коммитим призраки (если протяжка была).
            if (dragging && !ghosts.isEmpty() && available > 0) {
                List<BlockPos> toSend = ghosts.subList(0, Math.min(available, ghosts.size()));
                ClientPlayNetworking.send(new BuildPayload(new ArrayList<>(toSend)));
            }
            reset();
            return;
        }
        if (anchor == null) {
            return;
        }
        // Якорь валиден, только если ванилла реально поставила блок: клик по
        // двери/люку/сундуку открывает их, блок не ставится — протяжку не
        // начинаем и авто-повтор не глушим.
        if (!dragging && !minecraft.level.getBlockState(anchor)
                .is(((BlockItem) anchorItem.getItem()).getBlock())) {
            reset();
            return;
        }
        // Пока держим кнопку после первого блока — полностью замораживаем
        // ванильный авто-повтор установки (надёжнее, чем отмена в колбэке).
        ((dev.wcg.buildrush.mixin.MinecraftAccessor) minecraft).buildrush$setRightClickDelay(10);
        // Предмет сменили — отмена.
        if (!ItemStack.isSameItemSameComponents(
                minecraft.player.getMainHandItem().copyWithCount(1), anchorItem)) {
            reset();
            return;
        }

        // Точка протяжки: куда смотрим.
        Vec3 targetPoint = null;
        if (minecraft.hitResult instanceof BlockHitResult blockHit
                && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            // Прицел на самом якоре — протяжки нет (обычная установка одного блока).
            if (blockHit.getBlockPos().equals(anchor)) {
                ghosts.clear();
                return;
            }
            targetPoint = blockHit.getLocation();
        } else if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.MISS) {
            targetPoint = minecraft.player.getEyePosition()
                    .add(minecraft.player.getLookAngle().scale(8.0));
        }
        if (targetPoint == null) {
            return;
        }

        // Линия по доминирующей оси; увод прицела по второй оси — плоскость
        // (прямоугольник от якоря по двум доминирующим осям).
        Vec3 delta = targetPoint.subtract(Vec3.atCenterOf(anchor));
        // floor: мёртвая зона в полблока, чтобы клик по соседней грани
        // не считался протяжкой.
        Direction.Axis[] axes = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
        int[] lens = new int[3];
        for (int i = 0; i < 3; i++) {
            lens[i] = (int) Math.floor(Math.abs(component(delta, axes[i])));
        }
        int p = 0;
        for (int i = 1; i < 3; i++) {
            if (lens[i] > lens[p]) {
                p = i;
            }
        }
        int s = -1;
        for (int i = 0; i < 3; i++) {
            if (i != p && (s < 0 || lens[i] > lens[s])) {
                s = i;
            }
        }
        if (lens[s] < 1) {
            s = -1; // вторая ось не задействована — обычная линия
        }
        int lenP = Math.min(lens[p], BuildRushMod.MAX_BLOCKS - 1);
        // Прямоугольник целиком в лимите: вторая сторона ужимается.
        int lenS = s < 0 ? 0 : Math.min(lens[s], BuildRushMod.MAX_BLOCKS / (lenP + 1) - 1);
        Direction dirP = dirAlong(axes[p], component(delta, axes[p]));
        Direction dirS = s < 0 ? Direction.UP : dirAlong(axes[s], component(delta, axes[s]));

        ghosts.clear();
        for (int i = 0; i <= lenP; i++) {
            for (int j = 0; j <= lenS; j++) {
                if (i == 0 && j == 0) {
                    continue; // якорь уже стоит
                }
                BlockPos pos = anchor.relative(dirP, i).relative(dirS, j);
                if (minecraft.level.getBlockState(pos).canBeReplaced()) {
                    ghosts.add(pos);
                }
            }
        }
        if (!ghosts.isEmpty()) {
            dragging = true;
        }
        if (!dragging) {
            return;
        }

        available = minecraft.player.isCreative() ? Integer.MAX_VALUE
                : BuildRushMod.countItems(minecraft.player, anchorItem);

        for (int i = 0; i < ghosts.size(); i++) {
            Gizmos.cuboid(ghosts.get(i), -0.01F, i < available ? GHOST_OK : GHOST_BAD);
        }
    }

    private static Direction dirAlong(Direction.Axis axis, double component) {
        return Direction.fromAxisAndDirection(axis, component >= 0
                ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    private static double component(Vec3 vec, Direction.Axis axis) {
        return switch (axis) {
            case X -> vec.x;
            case Y -> vec.y;
            case Z -> vec.z;
        };
    }

    private static void reset() {
        anchor = null;
        anchorItem = ItemStack.EMPTY;
        dragging = false;
        ghosts.clear();
        available = 0;
    }
}
