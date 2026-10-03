package com.takumistudios.liveevents.core;

import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.expr.Expression;
import com.takumistudios.liveevents.core.expr.ExpressionException;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.safety.FlashLimiter;
import com.takumistudios.liveevents.core.safety.RateLimiter;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.core.timeline.Curve;
import com.takumistudios.liveevents.core.timeline.SmoothClock;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreUtilitiesTest {
    @Test
    void durationsParseAllUnits() {
        assertEquals(162, Durations.parseTicksExact("8.1s"), 1e-9);
        assertEquals(20, Durations.parseTicksExact("20t"), 1e-9);
        assertEquals(2400, Durations.parseTicksExact("2m"), 1e-9);
        assertEquals(72000, Durations.parseTicksExact("3d"), 1e-9);
        assertEquals(30, Durations.parseTicksExact("1.5"), 1e-9);
        assertEquals(Durations.PERMANENT, Durations.parseTicks("permanent"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseTicksExact("-1s"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseTicksExact("5x"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseTicksExact(""));
        assertEquals("01:15", Durations.formatClock(75.5));
    }

    @Test
    void expressionsEvaluateArithmeticAndFunctions() {
        Map<String, Double> vars = Map.of("radius", 128.0, "count", 40.0);
        assertEquals(64, Expression.compile("$radius * 0.5").evaluate(vars), 1e-9);
        assertEquals(80, Expression.compile("clamp($count * 2, 1, 80)").evaluate(vars), 1e-9);
        assertEquals(7, Expression.compile("1 + 2 * 3").evaluate(vars), 1e-9);
        assertEquals(9, Expression.compile("(1 + 2) * 3").evaluate(vars), 1e-9);
        assertEquals(-4, Expression.compile("-min(4, $count)").evaluate(vars), 1e-9);
        assertEquals(50, Expression.compile("lerp(0, 100, 0.5)").evaluate(vars), 1e-9);
    }

    @Test
    void expressionsRejectUnsafeInput() {
        assertThrows(ExpressionException.class, () -> Expression.compile("exec(1)"));
        assertThrows(ExpressionException.class, () -> Expression.compile("1 +"));
        assertThrows(ExpressionException.class, () -> Expression.compile("x".repeat(300)));
        assertThrows(ExpressionException.class, () -> Expression.compile("(".repeat(200) + "1" + ")".repeat(200)));
        assertThrows(ExpressionException.class, () -> Expression.compile("$missing + 1").evaluate(Map.of()));
        assertThrows(ExpressionException.class, () -> Expression.compile("1 / 0").evaluate(Map.of()));
        assertTrue(Expression.looksLikeExpression("$radius * 0.5"));
        assertFalse(Expression.looksLikeExpression("$cyan"));
    }

    @Test
    void flashLimiterAllowsThreeLargeFlashesPerSecond() {
        FlashLimiter limiter = new FlashLimiter();
        long t = 1_000_000_000L;
        assertTrue(limiter.tryFlash(t, 1.0));
        assertTrue(limiter.tryFlash(t + 100_000_000L, 1.0));
        assertTrue(limiter.tryFlash(t + 200_000_000L, 1.0));
        assertFalse(limiter.tryFlash(t + 300_000_000L, 1.0), "el cuarto destello en menos de 1 s se rechaza");
        assertTrue(limiter.tryFlash(t + 300_000_000L, 0.01), "los destellos pequeños no cuentan");
        assertTrue(limiter.tryFlash(t + 1_000_000_001L, 1.0), "pasado un segundo vuelve a permitirse");
    }

    @Test
    void saturatedRedIsDetected() {
        assertTrue(FlashLimiter.isSaturatedRed(0xFF0000));
        assertTrue(FlashLimiter.isSaturatedRed(0x8B0000));
        assertFalse(FlashLimiter.isSaturatedRed(0x00FFFF));
        assertFalse(FlashLimiter.isSaturatedRed(0xFFFFFF));
        assertEquals(3, FlashLimiter.maxBlinkHz(0.5, 30));
        assertEquals(30, FlashLimiter.maxBlinkHz(0.001, 30));
    }

    @Test
    void profileResolutionGivesClientLastWord() {
        assertEquals(Profile.SAFE, Profile.resolve(Profile.CINEMATIC, Profile.CINEMATIC, Profile.SAFE));
        assertEquals(Profile.GAMEPLAY, Profile.resolve(Profile.CINEMATIC, Profile.GAMEPLAY, Profile.CINEMATIC));
        assertEquals(Profile.GAMEPLAY, Profile.resolve(Profile.GAMEPLAY, Profile.CINEMATIC, Profile.CINEMATIC));
    }

    @Test
    void deterministicRandomIsReproducible() {
        DeterministicRandom a = new DeterministicRandom(42), b = new DeterministicRandom(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        assertEquals(DeterministicRandom.noise2(7, 1.5, 2.25), DeterministicRandom.noise2(7, 1.5, 2.25));
        double n = DeterministicRandom.fbm2(7, 10.3, -4.2, 4);
        assertTrue(n >= -1 && n <= 1);
        assertTrue(DeterministicRandom.derive(1, 2) != DeterministicRandom.derive(1, 3));
        DeterministicRandom r = new DeterministicRandom(9);
        for (int i = 0; i < 1000; i++) {
            int v = r.nextInt(7);
            assertTrue(v >= 0 && v < 7);
        }
    }

    @Test
    void curvesStayInRange() {
        for (Curve c : Curve.values()) {
            for (double t = 0; t <= 1.0; t += 0.05) {
                double v = c.apply(t);
                assertTrue(v >= -1e-9 && v <= 1 + 1e-9, c + " fuera de rango en " + t);
            }
        }
        assertEquals(Curve.EASE_IN, Curve.parse("ease_in", Curve.LINEAR));
        assertEquals(Curve.LINEAR, Curve.parse("nope", Curve.LINEAR));
    }

    @Test
    void smoothClockCorrectsDriftGraduallyAndSnapsWhenFar() {
        long t = 0;
        SmoothClock clock = new SmoothClock(0, t);
        t += 1_000_000_000L;
        assertEquals(20, clock.advance(t), 1e-6);
        clock.sync(25, t); // 5 ticks por detrás: no salta
        assertEquals(20, clock.ticks(), 1e-6);
        t += 1_000_000_000L;
        double after = clock.advance(t);
        assertTrue(after > 40 && after <= 42.0 + 1e-6, "corrige como mucho un 10 %: " + after);
        clock.sync(500, t); // muy lejos: salta
        assertEquals(500, clock.ticks(), 1e-6);
        clock.setPaused(true, 500, t);
        assertEquals(500, clock.advance(t + 5_000_000_000L), 1e-6);
        clock.sync(510, t + 5_000_000_000L); // en pausa, un salto corto también es exacto (bug 3)
        assertEquals(510, clock.ticks(), 1e-6);
    }

    @Test
    void rateLimiterBlocksBursts() {
        RateLimiter limiter = RateLimiter.parse("2/min", null);
        assertTrue(limiter.tryAcquire("admin", 0));
        assertTrue(limiter.tryAcquire("admin", 10));
        assertFalse(limiter.tryAcquire("admin", 20));
        assertTrue(limiter.tryAcquire("other", 20));
        assertTrue(limiter.tryAcquire("admin", 60_001));
        assertEquals(null, RateLimiter.parse("nonsense", null));
    }
}
