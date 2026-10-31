package com.hxzhitang.tongdarailway.railway.fixed;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Ruins keep deterministic full-width holes, while ice excavation preserves natural walls. */
class RailwayRuinGeometryTest {
 @Test void iceCoreAndThinFringeOpenWithoutRemovingBroadBanks(){
  assertTrue(RailwayTerrainPolicy.trimIce(6,6.5,false));
  assertTrue(RailwayTerrainPolicy.trimIce(-7.5,6.5,true));
  assertFalse(RailwayTerrainPolicy.trimIce(7.5,6.5,false));
  assertFalse(RailwayTerrainPolicy.trimIce(9,6.5,true));
 }
 private static RailwayDamagePlan scar(){var rows=new ListTag();var row=new CompoundTag();row.putDouble("From",100);row.putDouble("To",124);rows.add(row);return RailwayDamagePlan.fromNBT(rows);}
 @Test void floatingIceCapIsClearedEntirelyAboveTheOpening(){
  var removed=new java.util.ArrayList<Integer>();
  assertEquals(9,RailwayTerrainPolicy.clearCap(6,20,y->y>=8&&y<=16?RailwayTerrainPolicy.CapCell.FROZEN:RailwayTerrainPolicy.CapCell.EMPTY,removed::add));
  assertEquals(java.util.List.of(8,9,10,11,12,13,14,15,16),removed);
 }
 @Test void roofCleanupStopsBeforeRockAndLeavesHigherMaterialUntouched(){
  var removed=new java.util.ArrayList<Integer>();
  assertEquals(2,RailwayTerrainPolicy.clearCap(8,20,y->y==10?RailwayTerrainPolicy.CapCell.OTHER:RailwayTerrainPolicy.CapCell.FROZEN,removed::add));
  assertEquals(java.util.List.of(8,9),removed);
 }
 @Test void missingBridgeIncludesBothRailingsAndOuterColumns(){
  var p=scar();for(double z=-8;z<=8;z+=.5)assertTrue(p.bridgeMissing(112,z,42));
  assertTrue(p.bridgeMissing(100,0,42));assertFalse(p.bridgeMissing(99,0,42));assertFalse(p.bridgeMissing(125,4,42));
 }
 @Test void endsAreIrregularAndSurviveReload(){
  var p=scar();var restored=RailwayDamagePlan.fromNBT(p.toNBT());var ends=new java.util.HashSet<Integer>();
  for(double z=-7;z<=7;z+=.5){int first=100;while(!p.bridgeMissing(first,z,42))first++;ends.add(first);for(int x=95;x<130;x++)assertEquals(p.bridgeMissing(x,z,42),restored.bridgeMissing(x,z,42));}
  assertTrue(ends.size()>=3);
 }
 @Test void iceWallsRetainExcavatedAirAndFloorButRejectLining(){
  assertTrue(RailwayTerrainPolicy.naturalIce(3,0));assertFalse(RailwayTerrainPolicy.naturalIce(3,1));assertFalse(RailwayTerrainPolicy.naturalIce(0,0));
  assertTrue(RailwayTerrainPolicy.placeTunnelVoxel(true,true,5));assertTrue(RailwayTerrainPolicy.placeTunnelVoxel(true,false,-1));
  assertFalse(RailwayTerrainPolicy.placeTunnelVoxel(true,false,3));assertTrue(RailwayTerrainPolicy.placeTunnelVoxel(false,false,3));
 }
}
