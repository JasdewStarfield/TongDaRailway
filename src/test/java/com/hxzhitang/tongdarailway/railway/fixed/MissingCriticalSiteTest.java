package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Startup must retain ordinary railways and later valid landmarks after a missing landmark. */
class MissingCriticalSiteTest {
    @Test void absentAndBrokenSitesDoNotPreventLaterValidSites() {
        var messages=new ArrayList<String>();var failures=new ArrayList<Throwable>();
        var valid=new BlockPos(120,80,300);
        var sites=AutomaticRailwayNetwork.resolveCriticalSites(List.of("missing","broken","valid"),id->{
            if(id.equals("broken"))throw new java.lang.reflect.InvocationTargetException(new IllegalStateException("test cause"));
            return id.equals("valid")?Optional.of(valid):Optional.empty();
        },id->"automatic placement disabled",(text,error)->{messages.add(text);failures.add(error);});
        assertEquals(List.of(valid),sites);assertEquals(2,messages.size());
        assertTrue(messages.getFirst().contains("missing"));
        assertTrue(messages.getFirst().contains("automatic placement disabled"));
        assertTrue(messages.getFirst().contains("continue ordinary railway planning"));
        assertNull(failures.getFirst());assertEquals("test cause",failures.getLast().getCause().getMessage());
    }

    @Test void noSitesRetainsOrdinaryRegionsWithoutAForcedSpawnStation() {
        var sites=AutomaticRailwayNetwork.resolveCriticalSites(List.of("radar"),id->Optional.empty(),id->"no selected location",(text,error)->{});
        var fallback=AutomaticRailwayNetwork.candidates(8675309,sites,new BlockPos(500,80,500));
        assertEquals(4,fallback.nodes().size());assertTrue(fallback.nodes().stream().allMatch(n->n.role().equals("halt")));
        var unit=new RegionalTopology.Unit(-3,0,3,3);
        var regional=NaturalRailwayLayout.create(8675309,unit,sites,(x,z)->80,63);
        assertFalse(regional.nodes().isEmpty());assertTrue(regional.nodes().stream().noneMatch(n->n.role().equals("critical")));
        var cell=unit.cells().getFirst();
        assertFalse(RegionalTopology.active(8675309,cell,Set.of(),0));
        assertTrue(RegionalTopology.active(8675309,cell,Set.of(),1));
    }
}
