package com.kkllffaa.meteor_litematica_printer;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.Comparator;

import static meteordevelopment.meteorclient.utils.Utils.squaredDistance;

@SuppressWarnings("unused")
public enum SortAlgorithm {
    None(false, (a, b) -> 0),
    TopDown(true, Comparator.comparingInt(value -> value.getY() * -1)),
    DownTop(true, Comparator.comparingInt(Vec3i::getY)),
    Nearest(false, Comparator.comparingDouble(value -> MeteorClient.mc.player != null ? squaredDistance(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(), value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) : 0)),
    Furthest(false, Comparator.comparingDouble(value -> MeteorClient.mc.player != null ? (squaredDistance(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ(), value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5)) * -1 : 0));


    final boolean applySecondSorting;
    final Comparator<BlockPos> algorithm;

    SortAlgorithm(boolean applySecondSorting, Comparator<BlockPos> algorithm) {
        this.applySecondSorting = applySecondSorting;
        this.algorithm = algorithm;
    }
}
