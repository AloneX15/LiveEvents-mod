package com.takumistudios.liveevents.core;

import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler.Priority;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSchedulerTest {
    @Test
    void respectsBlockCapAndContinuesNextTick() {
        AtomicLong clock = new AtomicLong();
        WorkScheduler scheduler = new WorkScheduler(2.0, 100, clock::get);
        AtomicInteger remaining = new AtomicInteger(250);
        scheduler.submit("i1", Priority.BLOCKS, allowance -> {
            int n = Math.min(allowance, remaining.get());
            remaining.addAndGet(-n);
            return remaining.get() == 0 ? Result.done(n) : Result.more(n);
        });
        scheduler.tick();
        assertEquals(150, remaining.get());
        assertEquals(100, scheduler.stats().lastTickBlocks());
        scheduler.tick();
        scheduler.tick();
        assertEquals(0, remaining.get());
        assertEquals(0, scheduler.pending());
    }

    @Test
    void runsGameplayBeforeBlocksAndStopsAtTimeBudget() {
        AtomicLong clock = new AtomicLong();
        WorkScheduler scheduler = new WorkScheduler(1.0, 10_000, clock::get);
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int n = i;
            scheduler.submit("i1", Priority.BLOCKS, a -> {
                order.add("b" + n);
                clock.addAndGet(400_000); // 0,4 ms por tarea
                return Result.done(1);
            });
        }
        scheduler.submit("i1", Priority.GAMEPLAY, a -> {
            order.add("g");
            return Result.DONE;
        });
        scheduler.tick();
        assertEquals("g", order.get(0));
        assertTrue(order.size() < 6, "el presupuesto de 1 ms corta antes de terminar: " + order);
        assertTrue(scheduler.pending() > 0);
    }

    @Test
    void loadFactorReducesBudgetAndCancelRemovesOwner() {
        AtomicLong clock = new AtomicLong();
        WorkScheduler scheduler = new WorkScheduler(2.0, 100, clock::get);
        scheduler.setLoadFactor(0.1);
        AtomicInteger changed = new AtomicInteger();
        scheduler.submit("a", Priority.BLOCKS, allowance -> {
            changed.addAndGet(allowance);
            return Result.more(allowance);
        });
        scheduler.submit("b", Priority.CLEANUP, a -> Result.DONE);
        scheduler.tick();
        assertEquals(10, changed.get(), "con factor 0,1 solo caben 10 bloques");
        assertEquals(1, scheduler.cancel("a"));
        assertEquals(1, scheduler.pending("b"));
        scheduler.cancel(null);
        assertEquals(0, scheduler.pending());
    }

    /** Bug: una instancia con mucho trabajo acaparaba la cola y las demás no avanzaban (gametest scorch). */
    @Test
    void busyTasksDoNotStarveOthers() {
        AtomicLong clock = new AtomicLong();
        WorkScheduler scheduler = new WorkScheduler(2.0, 256, clock::get);
        AtomicInteger big = new AtomicInteger(), small = new AtomicInteger();
        scheduler.submit("big", Priority.BLOCKS, allowance -> {
            big.addAndGet(allowance);
            return Result.more(allowance); // nunca termina
        });
        scheduler.submit("small", Priority.BLOCKS, allowance -> {
            small.addAndGet(Math.min(allowance, 5));
            return Result.done(Math.min(allowance, 5));
        });
        scheduler.tick();
        assertEquals(5, small.get(), "la tarea pequeña debe ejecutarse en el mismo tick");
        assertTrue(big.get() > 0);
        assertTrue(big.get() + small.get() <= 256);
    }

    @Test
    void allowanceIsSliced() {
        AtomicLong clock = new AtomicLong();
        WorkScheduler scheduler = new WorkScheduler(2.0, 1000, clock::get);
        AtomicInteger maxSeen = new AtomicInteger();
        scheduler.submit("a", Priority.BLOCKS, allowance -> {
            maxSeen.accumulateAndGet(allowance, Math::max);
            return Result.more(allowance);
        });
        scheduler.tick();
        assertEquals(WorkScheduler.SLICE, maxSeen.get());
    }

    @Test
    void rechecksBudgetAfterPreemptionBeforeStartingWork() {
        AtomicInteger reads=new AtomicInteger(),runs=new AtomicInteger();
        WorkScheduler scheduler=new WorkScheduler(2,100,()->reads.getAndIncrement()<2?0:3_000_000);
        scheduler.submit("paused",Priority.BLOCKS,a->{runs.incrementAndGet();return Result.done(1);});
        scheduler.tick();
        assertEquals(0,runs.get());assertEquals(1,scheduler.pending());
        assertTrue(scheduler.stats().lastSliceStartMs()<=2);
        scheduler.tick();assertEquals(1,runs.get());
    }

    @Test
    void failuresCarryTheirOwner() {
        WorkScheduler scheduler = new WorkScheduler(2.0, 100, System::nanoTime);
        scheduler.submit("broken", Priority.GAMEPLAY, a -> {
            throw new IllegalStateException("boom");
        });
        WorkScheduler.TaskFailure failure = assertThrows(WorkScheduler.TaskFailure.class, scheduler::tick);
        assertEquals("broken", failure.owner());
    }
}
