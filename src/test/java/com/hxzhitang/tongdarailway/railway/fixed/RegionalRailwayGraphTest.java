package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionalRailwayGraphTest {
    private static FixedRailwayNetwork.Node node(String id,int x,int z) {
        return new FixedRailwayNetwork.Node(id,new BlockPos(x,90,z),Direction.EAST,new BlockPos(x-200,90,z),12);
    }
    @Test void graphIsConnectedAndIndependentOfCandidateOrdering() {
        var nodes=new ArrayList<>(List.of(node("radar",0,0),node("mine_a",800,200),node("mine_b",-900,150),node("mine_c",900,1300)));
        var first=RegionalRailwayGraph.connect(nodes);Collections.reverse(nodes);
        assertEquals(first,RegionalRailwayGraph.connect(nodes));assertEquals(3,first.size());
        var degree=new HashMap<String,Integer>();for(var edge:first){degree.merge(edge.from(),1,Integer::sum);degree.merge(edge.to(),1,Integer::sum);}
        assertEquals(4,degree.size());assertTrue(degree.values().stream().allMatch(d->d<=3));
    }
    @Test void formatTwoBuildsLinksFromNamedPoiCandidates() {
        String text="""
                {"format":2,"seed":42,"auto_connect":true,"nodes":[
                {"id":"radar","rail":[0,90,0],"facing":"east","protected_center":[-200,90,0],"protected_radius":12,"role":"radar","station":"small_stone_station_0"},
                {"id":"mine","rail":[800,90,0],"facing":"west","protected_center":[1000,90,0],"protected_radius":12,"role":"mine","station":"small_wood_station_2"}]}
                """;
        var network=FixedRailwayNetwork.parse(text);assertEquals(1,network.links().size());
        assertEquals("radar",network.nodes().getFirst().role());
        assertEquals("small_stone_station_0",network.nodes().getFirst().station());
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(text.replace("\"auto_connect\":true","\"auto_connect\":true,\"links\":[{\"from\":\"radar\",\"to\":\"mine\"}]")));
    }
    @Test void distantCandidatesFailWithoutTryingToLoadTerrain() {
        assertThrows(IllegalArgumentException.class,()->RegionalRailwayGraph.connect(List.of(node("radar",0,0),node("far",5000,0))));
    }
    @Test void ruinKindMatchesTheLocalStructureAndPilesAreNotUniform() {
        assertEquals(RailwayRuin.Kind.BROKEN_BRIDGE,RailwayRuin.kind(true,false));
        assertEquals(RailwayRuin.Kind.TUNNEL_COLLAPSE,RailwayRuin.kind(false,true));
        assertEquals(RailwayRuin.Kind.LANDSLIDE,RailwayRuin.kind(false,false));
        var heights=new HashSet<Integer>();
        for(int x=0;x<20;x++)for(int z=-3;z<=3;z++) {
            var pos=new BlockPos(x,90,z);int h=RailwayRuin.rubbleHeight(42,pos,z,false);
            assertEquals(h,RailwayRuin.rubbleHeight(42,pos,z,false));assertTrue(h>=0&&h<=4);heights.add(h);
        }
        assertTrue(heights.size()>=3);
    }
}
