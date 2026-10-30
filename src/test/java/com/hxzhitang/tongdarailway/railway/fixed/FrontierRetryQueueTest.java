package com.hxzhitang.tongdarailway.railway.fixed;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierRetryQueueTest {
    @Test void partialGraphsReconnectAfterTheMissingChunkArrives() {
        var queue=new FrontierRetryQueue<Integer,Integer>();var loaded=new HashSet<Integer>();var replay=new ArrayList<Integer>();
        queue.add(2,1);queue.add(2,1);
        queue.poll(8,(chunk,anchor)->loaded.contains(chunk)&&loaded.contains(anchor),(chunk,anchor)->replay.add(anchor));
        assertTrue(replay.isEmpty());loaded.add(1);loaded.add(2);
        queue.poll(8,(chunk,anchor)->loaded.contains(chunk)&&loaded.contains(anchor),(chunk,anchor)->replay.add(anchor));
        assertEquals(List.of(1),replay);assertEquals(0,queue.size());
    }
    @Test void AnUnloadedOriginDoesNotLoseItsFrontierAndCannotStarveOthers() {
        var queue=new FrontierRetryQueue<Integer,Integer>();var replay=new ArrayList<Integer>();
        queue.add(2,1);queue.add(3,4);
        queue.poll(1,(chunk,anchor)->anchor==4,(chunk,anchor)->replay.add(anchor));
        queue.poll(1,(chunk,anchor)->anchor==4,(chunk,anchor)->replay.add(anchor));
        assertEquals(List.of(4),replay);assertEquals(1,queue.size());
        queue.poll(1,(chunk,anchor)->true,(chunk,anchor)->replay.add(anchor));assertEquals(List.of(4,1),replay);
    }
}
