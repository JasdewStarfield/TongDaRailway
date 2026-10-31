package com.hxzhitang.tongdarailway.railway.fixed;
import org.junit.jupiter.api.Test;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import static org.junit.jupiter.api.Assertions.*;
/** The same footprint margins must cover roads, station blending and complete native buildings. */
class SurfaceStructurePolicyTest {
 @Test void reservationsIncludeTheWholeBuildingAndMargin(){
  var box=new BoundingBox(10,60,20,80,100,100);
  assertTrue(SurfaceStructurePolicy.contains(box,55,70,24));
  assertTrue(SurfaceStructurePolicy.contains(box,-14,70,24));
  assertFalse(SurfaceStructurePolicy.contains(box,-15,70,24));
  assertTrue(SurfaceStructurePolicy.overlap(box,-80,20,-40,50,56));
 }
 @Test void completeRouteEnvelopeIncludesConstructionBesideTheRail(){
  var route=new com.hxzhitang.tongdarailway.railway.planner.RoutePlanner.ResultWay(new com.hxzhitang.tongdarailway.util.CurveRoute(),new java.util.ArrayList<>());
  route.addLine(new net.minecraft.world.phys.Vec3(0,80,0),new net.minecraft.world.phys.Vec3(100,80,0));
  var plan=new RegionalRailwayNetwork.Plan(new FixedRailwayNetwork.Network(1,java.util.List.of(),java.util.List.of()),java.util.List.of(),java.util.List.of(route),null);
  assertTrue(SurfaceStructurePolicy.intersects(plan,new BoundingBox(40,60,20,50,90,30)));
  assertFalse(SurfaceStructurePolicy.intersects(plan,new BoundingBox(40,60,30,50,90,40)));
 }
 @Test void unknownPolicyIsRejected(){assertThrows(IllegalArgumentException.class,()->SurfaceStructurePolicy.configure("unknown"));}
}
