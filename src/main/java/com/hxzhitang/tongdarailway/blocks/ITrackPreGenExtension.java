package com.hxzhitang.tongdarailway.blocks;

import com.simibubi.create.content.trains.track.BezierConnection;

public interface ITrackPreGenExtension {
    void addConnectionToPreGen(BezierConnection connection);
    void queueGraphBootstrap(java.util.List<net.minecraft.core.BlockPos> positions);
}
