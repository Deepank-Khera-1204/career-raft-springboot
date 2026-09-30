package com.deepank.careerraft.discovery;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class JitteredSchedulerTest {
    @Test
    void schedulerRespectsBounds() {
        JitteredScheduler scheduler =
                new JitteredScheduler(new SourcePolicy("test",3,8,6,List.of()), new Random(7));
        for(int i=0;i<100;i++) {
            double hours=scheduler.nextDelay().toMillis()/3_600_000d;
            assertTrue(hours>=3 && hours<=8);
        }
    }

    @Test
    void nextRunIsAfterNow() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        Instant next=new JitteredScheduler(new SourcePolicy("test",3,8,6,List.of()),new Random(3)).nextRunAt(now);
        assertTrue(next.isAfter(now));
    }

    @Test
    void discreteIntervalsAreRespected() {
        JitteredScheduler scheduler=new JitteredScheduler(
                new SourcePolicy("test",3,8,6,List.of(3d,4d,6d,8d)),new Random(1));
        for(int i=0;i<100;i++) {
            double hours=scheduler.nextDelay().toMillis()/3_600_000d;
            assertTrue(List.of(3d,4d,6d,8d).contains(hours));
        }
    }
}
