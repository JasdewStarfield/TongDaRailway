package com.hxzhitang.tongdarailway.railway.fixed;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Server-thread ticks share a small placement allowance across all nearby deferred placers. */
public final class FixedPlacementBudget {
    private static final Map<ServerLevel,Allowance> WORLDS=new WeakHashMap<>();
    private FixedPlacementBudget() { }
    public static boolean acquire(ServerLevel level,int records) {
        return WORLDS.computeIfAbsent(level,unused->new Allowance()).acquire(level.getGameTime(),records);
    }
    static final class Allowance {
        private long tick=Long.MIN_VALUE;
        private int batches,records;
        boolean acquire(long now,int count) {
            if(count<0)throw new IllegalArgumentException("Negative placement count");
            if(now!=tick){tick=now;batches=0;records=0;}
            // One unusually dense chunk can progress, but can never admit a second batch that tick.
            if(batches>=2||batches>0&&(long)records+count>32)return false;
            batches++;records+=count;return true;
        }
    }
}
