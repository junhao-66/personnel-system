package com.personnel.common.util;

/** Set only after server-side authentication; cleared at request completion. */
public final class AuditActorContext {

    public record Actor(Long userId, String role) {}

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    private AuditActorContext() {}

    public static void set(Long userId, String role) {
        CURRENT.set(new Actor(userId, role));
    }

    public static Actor get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
