package com.hxzhitang.tongdarailway.railway.fixed;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Test long footprints and genuine cycle edges independently of terrain acceptance. */
class LongRailwayTest {
 @Test void variableCorridorsAndReturnBranchesRemainBounded() {
  int loops=0,corridors=0;var sizes=new HashSet<Integer>();
  for(long seed=0;seed<100;seed++) {
   var n=RegionalRailwayNetwork.candidates(seed,new RegionalTopology.Unit(0,0,3,3),List.of());
   assertTrue(n.nodes().size()<=8);sizes.add(n.nodes().size());
   assertTrue(n.links().stream().allMatch(l->{var a=n.nodes().stream().filter(p->p.id().equals(l.from())).findFirst().orElseThrow();var b=n.nodes().stream().filter(p->p.id().equals(l.to())).findFirst().orElseThrow();return a.rail().distSqr(b.rail())<=4096L*4096;}));
   if(n.nodes().stream().anyMatch(p->p.role().startsWith("return_to_"))){loops++;assertEquals(n.nodes().size(),n.links().size());}
   else {corridors++;assertEquals(n.nodes().size()-1,n.links().size());}
  }
  assertTrue(loops>15&&corridors>30);assertTrue(sizes.size()>=3);
 }
 @Test void coarseTerrainChangesTheCorridorWithAFixedSamplingBudget() {
  var unit=new RegionalTopology.Unit(0,0,3,3);var calls=new java.util.concurrent.atomic.AtomicInteger();
  var flat=NaturalRailwayLayout.create(42,unit,List.of(),(x,z)->80,63);
  NaturalRailwayLayout.Heights valley=(x,z)->{calls.incrementAndGet();return 80+Math.abs(z-3000)/12;};
  var terrain=NaturalRailwayLayout.create(42,unit,List.of(),valley,63);
  assertNotEquals(flat.nodes(),terrain.nodes());assertTrue(calls.get()<1500);
  assertEquals(terrain,NaturalRailwayLayout.create(42,unit,List.of(),(x,z)->80+Math.abs(z-3000)/12,63));
 }
 @Test void perpendicularRingMouthsUseAShortCornerAndLongStraightApproaches() {
  var a=new com.hxzhitang.tongdarailway.structure.StationTemplate.Exit(new net.minecraft.core.BlockPos(2500,95,0),new net.minecraft.world.phys.Vec3(0,0,1));
  var b=new com.hxzhitang.tongdarailway.structure.StationTemplate.Exit(new net.minecraft.core.BlockPos(0,70,2500),new net.minecraft.world.phys.Vec3(1,0,0));
  var route=FixedRailwayNetwork.cornerConnection(a,b);
  assertNotNull(route);assertTrue(route.trackPutInfos().stream().allMatch(NativeCurveSafety::valid));
  assertTrue(route.trackPutInfos().stream().filter(t->t.bezier()==null).count()>1000);
 }
}
