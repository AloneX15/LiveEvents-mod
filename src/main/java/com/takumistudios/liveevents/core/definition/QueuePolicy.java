package com.takumistudios.liveevents.core.definition;

/** Qué hacer si ya hay un evento incompatible activo (§7.12). */
public enum QueuePolicy {
    REJECT,
    QUEUE;

    public static QueuePolicy parse(String text, QueuePolicy fallback) {
        if ("queue".equalsIgnoreCase(text)) {
            return QUEUE;
        }
        if ("reject".equalsIgnoreCase(text)) {
            return REJECT;
        }
        return fallback;
    }
}
