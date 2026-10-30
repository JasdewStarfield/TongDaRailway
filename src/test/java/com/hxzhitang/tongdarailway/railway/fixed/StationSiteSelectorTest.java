package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StationSiteSelectorTest {
    @Test void flatGroundWinsOverTheConfiguredHeightAndNearbySlope() {
        var site=StationSiteSelector.select(new BlockPos(0,150,0),24,24,63,
                (x,z)->x>=48?74:120+x/3,(x,z)->x>=48?74:120+x/3,(x,z)->x>=0);
        assertEquals(74,site.rail().getY());assertTrue(site.rail().getX()>=60);
        assertEquals(0,site.fill());assertEquals(0,site.cut());
    }
    @Test void flatWaterDoesNotHideAValidNearbyLandFootprint() {
        var site=StationSiteSelector.select(new BlockPos(0,90,0),24,24,63,
                (x,z)->x>=48?74:64,(x,z)->x>=48?74:64,(x,z)->true);
        assertEquals(74,site.rail().getY());assertTrue(site.rail().getX()>=60);
    }
    @Test void expandedSearchStillVisitsTheFirstUnexaminedCandidate() {
        // A one-cell fixture isolates candidate ordering from footprint variation.
        var site=StationSiteSelector.select(new BlockPos(0,90,0),1,1,63,
                (x,z)->75,(x,z)->x==-31&&z==33?75:64,(x,z)->true);
        assertEquals(new BlockPos(-32,75,32),site.rail());
    }
    @Test void waterAndUnavoidableCliffsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->StationSiteSelector.select(new BlockPos(0,100,0),50,40,63,(x,z)->300,(x,z)->300,(x,z)->true));
        assertThrows(IllegalArgumentException.class,()->StationSiteSelector.select(new BlockPos(0,100,0),50,40,63,(x,z)->63,(x,z)->63,(x,z)->true));
        assertThrows(IllegalArgumentException.class,()->StationSiteSelector.select(new BlockPos(0,100,0),50,40,63,(x,z)->x<0?70:110,(x,z)->x<0?70:110,(x,z)->x==0));
    }
    @Test void exactSamplingIsBoundedAndRespectsSiteReservations() {
        var count=new AtomicInteger();var site=StationSiteSelector.select(new BlockPos(0,90,0),50,40,63,
                (x,z)->75,(x,z)->{count.incrementAndGet();return 75;},(x,z)->x>=64);
        assertTrue(site.rail().getX()>=64);assertTrue(count.get()<=150);
    }
}
