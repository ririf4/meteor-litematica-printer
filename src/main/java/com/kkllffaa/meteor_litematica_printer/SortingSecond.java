package com.kkllffaa.meteor_litematica_printer;

import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

@SuppressWarnings("unused")
public enum SortingSecond {
    None(SortAlgorithm.None.algorithm),
    Nearest(SortAlgorithm.Nearest.algorithm),
    Furthest(SortAlgorithm.Furthest.algorithm);

    final Comparator<BlockPos> algorithm;

    SortingSecond(Comparator<BlockPos> algorithm) {
        this.algorithm = algorithm;
    }
}
