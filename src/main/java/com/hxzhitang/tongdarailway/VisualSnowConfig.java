package com.hxzhitang.tongdarailway;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only visual switch; no values are sent to or used by the server. */
public final class VisualSnowConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED=BUILDER
            .comment("Show thin visual snow on Create tracks near snow at rail height. Does not change blocks or train logic.")
            .define("visualTrackSnow",true);
    public static final ModConfigSpec.BooleanValue CURVE_SLEEPER_CLEARANCE=BUILDER
            .comment("Raise curve sleepers visually by 1/64 block to clear native thin snow. Independent of visualTrackSnow. Re-enter the world after changing.")
            .define("curveSleeperSnowClearance",true);
    public static final ModConfigSpec SPEC=BUILDER.build();
    private VisualSnowConfig() {}
}
