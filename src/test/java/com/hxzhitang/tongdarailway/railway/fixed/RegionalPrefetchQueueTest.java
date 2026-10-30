package com.hxzhitang.tongdarailway.railway.fixed;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Queue order, capacity and stale requests must remain independent of worker timing. */
class RegionalPrefetchQueueTest {
 @Test void centerFirstAndBounded(){var queue=new RegionalPrefetchQueue<Integer,String>(2);var wanted=new LinkedHashMap<Integer,String>();wanted.put(0,"center");wanted.put(1,"near");wanted.put(2,"far");queue.replace(wanted);assertEquals(2,queue.size());assertEquals("center",queue.poll());assertEquals("near",queue.poll());assertNull(queue.poll());}
 @Test void movingReplacesOldNeighborhood(){var queue=new RegionalPrefetchQueue<Integer,String>(64);queue.replace(Map.of(1,"old"));queue.replace(Map.of(2,"new"));assertEquals("new",queue.poll());assertNull(queue.poll());queue.replace(Map.of(3,"pending"));queue.clear();assertEquals(0,queue.size());}
}
