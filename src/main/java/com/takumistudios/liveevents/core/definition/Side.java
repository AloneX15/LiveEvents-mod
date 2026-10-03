package com.takumistudios.liveevents.core.definition;

/** Dónde se ejecuta un módulo. */
public enum Side {
    SERVER,
    CLIENT,
    BOTH;

    public boolean onServer() {
        return this != CLIENT;
    }

    public boolean onClient() {
        return this != SERVER;
    }
}
