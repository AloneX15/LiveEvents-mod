package com.takumistudios.liveevents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests unitarios de la lógica pura (sin Minecraft). Todo bug corregido añade su caso aquí o en los gametests. */
class LiveEventsTest {
    @Test
    void modIdIsValid() {
        assertTrue(LiveEvents.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"));
    }
}
