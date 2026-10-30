package com.hxzhitang.tongdarailway.railway.fixed;
import com.hxzhitang.tongdarailway.railway.RegionPos;
import net.minecraft.core.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Spatial decisions must be stable before either neighboring region has generated terrain. */
class RegionalTopologyTest {
    @Test void criticalCellsRemainActiveAndOtherCellsMayBeEmpty() {
        var key=new RegionPos(-1,0);var critical=Set.of(key);
        assertTrue(RegionalTopology.active(42,key,critical,0));
        assertFalse(RegionalTopology.active(42,new RegionPos(3,3),critical,0));
        int active=0;for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)if(RegionalTopology.active(42,new RegionPos(x,z),Set.of(),.45))active++;
        assertTrue(active>100&&active<300);
    }
    @Test void eitherSideResolvesTheSameDisjointPairIncludingNegativeCoordinates() {
        for(long seed:new long[]{0,42,8675309,-4051455670688324092L})for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
            var r=new RegionPos(x,z);var unit=RegionalTopology.unit(seed,r,Set.of(),1,1);
            assertEquals(9,unit.cells().size());assertTrue(unit.cells().contains(r));
            for(var member:unit.cells())assertEquals(unit,RegionalTopology.unit(seed,member,Set.of(),1,1));
        }
    }
    @Test void pairingRequiresTwoActiveRegionsAndCanBeDisabled() {
        var critical=Set.of(new RegionPos(-1,0));
        assertEquals(9,RegionalTopology.unit(42,new RegionPos(-1,0),critical,0,1).cells().size());
        assertEquals(9,RegionalTopology.unit(42,new RegionPos(1,1),Set.of(),1,0).cells().size());
    }
    @Test void regionalCandidatesAreBoundedAndIdenticalFromEitherSide() {
        var a=new RegionPos(-1,0);var unit=RegionalTopology.unit(42,a,Set.of(a),1,1);
        var sites=List.of(new BlockPos(-976,95,141));
        var first=RegionalRailwayNetwork.candidates(42,unit,sites);
        assertTrue(first.nodes().size()<=8);assertTrue(first.nodes().stream().anyMatch(n->n.role().equals("critical")));
        for(var n:first.nodes())assertTrue(unit.contains(n.rail().getX(),n.rail().getZ(),256));
        for(var r:unit.cells())assertEquals(first,RegionalRailwayNetwork.candidates(42,RegionalTopology.unit(42,r,Set.of(a),1,1),sites));
    }
    @Test void cellCornerClampingKeepsRequiredAnchorsOutsideTheLandmark() {
        var site=new BlockPos(-3550,95,3550);var unit=new RegionalTopology.Unit(-3,0,3,3);
        var network=RegionalRailwayNetwork.candidates(42,unit,List.of(site));
        var required=network.nodes().stream().filter(n->n.role().equals("critical")).findFirst().orElseThrow();
        assertTrue(Math.abs(required.rail().getX()-site.getX())>72||Math.abs(required.rail().getZ()-site.getZ())>72);
    }
    @Test void regionalKeysDoNotHaveTheLinearHashCollisionOfRecordCoordinates() {
        assertNotEquals(RegionalTopology.key(1,0,31,7),RegionalTopology.key(1,1,0,7));
        assertEquals(new RegionPos(-1,-1),RegionalTopology.region(new BlockPos(-1,80,-1)));
    }
}
