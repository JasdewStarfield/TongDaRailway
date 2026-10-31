package com.hxzhitang.tongdarailway.railway.fixed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Near-site ranking keeps grounding constraints while respecting the landmark's actual rotated doorway. */
class CriticalStationSitingTest {
 @Test void nearbyUsableGroundWinsOverAFlatRemoteField(){
  var gate=new BlockPos(-700,96,1000);
  java.util.function.IntBinaryOperator heights=(x,z)->x<-620?96+Math.floorMod(x+z,3):96;
  var site=StationSiteSelector.selectNearby(gate,17,13,63,heights,heights,(x,z)->x>=-652);
  assertTrue(Math.hypot(site.rail().getX()-gate.getX(),site.rail().getZ()-gate.getZ())<=64);
  assertTrue(site.fill()<=5&&site.cut()<=5);
 }
 @Test void rejectedWaterDoesNotBecomeANearbyStation(){
  var gate=new BlockPos(0,96,0);
  var site=StationSiteSelector.selectNearby(gate,13,13,63,(x,z)->x<80?63:96,(x,z)->x<80?63:96,(x,z)->x>16);
  assertTrue(site.minHeight()>64);assertTrue(site.rail().getX()>=80);
 }
 @Test void unsuitableNearbyCliffsStillFail(){
  assertThrows(IllegalArgumentException.class,()->StationSiteSelector.selectNearby(new BlockPos(0,90,0),33,33,63,
    (x,z)->x<0?80:120,(x,z)->x<0?80:120,(x,z)->x==0));
 }
 @Test void doorwayUsesTheSharedCornerRotation(){
  var origin=new BlockPos(-800,95,1050);var local=new BlockPos(13,3,42);
  assertEquals(new BlockPos(-787,98,1092),CriticalStationSiting.entrance(origin,local,Rotation.NONE));
  assertEquals(new BlockPos(-813,98,1008),CriticalStationSiting.entrance(origin,local,Rotation.CLOCKWISE_180));
  assertEquals(new BlockPos(-842,98,1063),CriticalStationSiting.entrance(origin,local,Rotation.CLOCKWISE_90));
 }
}
