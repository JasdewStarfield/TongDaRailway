package com.hxzhitang.tongdarailway.railway.snow;

import com.simibubi.create.content.trains.track.TrackShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnowOverlayPolicyTest {
    @Test void exposedSnowUsesTheAirSkylightSample() {
        assertEquals(15<<20,SnowOverlayPolicy.surfaceLight(0,15<<20));
    }
    @Test void skyAndTorchLightAreCombinedIndependently() {
        assertEquals((15<<20)|(14<<4),SnowOverlayPolicy.surfaceLight((3<<20)|(14<<4),(15<<20)|(2<<4)));
    }
    @Test void unlitTunnelRemainsDark() {
        assertEquals(0,SnowOverlayPolicy.surfaceLight(0,0));
    }
    @Test void visualSnowMeetsNativeOneLayerSnowWithoutAHeightStep() {
        assertEquals(1.0/8,SnowOverlayPolicy.THIN_SNOW);
    }
    @Test void nearbySnowOnlyCoversSupportedDryCells() {
        assertTrue(SnowOverlayPolicy.canCover(true,false,2));
        assertFalse(SnowOverlayPolicy.canCover(true,false,1));
        assertFalse(SnowOverlayPolicy.canCover(true,true,8));
    }
    @Test void curveHelpersInOpenAirCannotBorrowSnowFromTheGround() {
        assertFalse(SnowOverlayPolicy.canCover(false,false,8));
        assertFalse(SnowOverlayPolicy.canCover(false,true,8));
        assertTrue(SnowOverlayPolicy.canCover(true,false,6));
    }
    @Test void zeroLayerSrmStateAndUnrelatedWhiteBlocksAreNotSnow() {
        assertTrue(SnowOverlayPolicy.snowIdentifier("snowrealmagic:snowy_plant",1));
        assertFalse(SnowOverlayPolicy.snowIdentifier("snowrealmagic:snowy_plant",0));
        assertTrue(SnowOverlayPolicy.snowIdentifier("minecraft:snow",1));
        assertTrue(SnowOverlayPolicy.snowIdentifier("minecraft:snow_block",1));
        assertFalse(SnowOverlayPolicy.snowIdentifier("minecraft:quartz_block",1));
    }
    @Test void requiresSeveralSamplesAndExcludesPortalTracks() {
        assertFalse(SnowOverlayPolicy.supports(TrackShape.NONE));assertFalse(SnowOverlayPolicy.supports(TrackShape.TN));
        assertTrue(SnowOverlayPolicy.supports(TrackShape.PD));assertTrue(SnowOverlayPolicy.supports(TrackShape.CR_O));
    }
}
