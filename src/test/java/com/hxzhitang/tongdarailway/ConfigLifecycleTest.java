package com.hxzhitang.tongdarailway;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;

class ConfigLifecycleTest {
    // The loader constructor is package-private; a container is unnecessary for spec matching.
    private static ModConfig config(ModConfig.Type type, IConfigSpec spec) throws Exception {
        var constructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class,
                IConfigSpec.class, ModContainer.class, String.class, ReentrantLock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(type, spec, null, "test.toml", new ReentrantLock());
    }

    @Test void clientConfigEventsDoNotReadUnloadedCommonValues() throws Exception {
        var client = config(ModConfig.Type.CLIENT, VisualSnowConfig.SPEC);
        // Reproduce client-first startup while the common spec still has no loaded config.
        double previous = Config.areaGenRailwayProbability;
        try {
            // Unit-test mode permits default reads, so also detect accidental value replacement.
            Config.areaGenRailwayProbability = -1;
            assertDoesNotThrow(() -> Config.onLoad(new ModConfigEvent.Loading(client)));
            assertEquals(-1, Config.areaGenRailwayProbability);
            assertDoesNotThrow(() -> Config.onLoad(new ModConfigEvent.Reloading(client)));
            assertEquals(-1, Config.areaGenRailwayProbability);
        } finally {
            Config.areaGenRailwayProbability = previous;
        }
    }

    @Test void unloadingDoesNotReadValuesAfterTheConfigIsClosed() throws Exception {
        var common = config(ModConfig.Type.COMMON, Config.SPEC);
        double previous = Config.areaGenRailwayProbability;
        try {
            Config.areaGenRailwayProbability = -1;
            assertDoesNotThrow(() -> Config.onLoad(new ModConfigEvent.Unloading(common)));
            assertEquals(-1, Config.areaGenRailwayProbability);
        } finally {
            Config.areaGenRailwayProbability = previous;
        }
    }
}
