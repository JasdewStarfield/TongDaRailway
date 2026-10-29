package com.hxzhitang.tongdarailway.railway.fixed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A player approaching many generated chunks must not finish their entire railway in one tick. */
class FixedPlacementBudgetTest {
    @Test void nearbyPlacersShareRecordAndBatchLimitsAndResumeNextTick() {
        var budget=new FixedPlacementBudget.Allowance();
        assertTrue(budget.acquire(100,20));assertFalse(budget.acquire(100,20));
        assertTrue(budget.acquire(100,12));assertFalse(budget.acquire(100,0));
        assertTrue(budget.acquire(101,20));assertTrue(budget.acquire(101,10));
    }
    @Test void oneDenseChunkProgressesWithoutLettingItsNeighboursBurst() {
        var budget=new FixedPlacementBudget.Allowance();
        assertTrue(budget.acquire(200,64));assertFalse(budget.acquire(200,1));
        assertTrue(budget.acquire(201,1));assertTrue(budget.acquire(201,1));
        assertFalse(budget.acquire(201,1));
        assertThrows(IllegalArgumentException.class,()->budget.acquire(202,-1));
    }
}
