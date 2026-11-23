package com.genee.psavoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Main-thread-only, process-local UI state. No transcript persistence or exported broadcasts. */
final class ConversationStore {
    static final class Turn {
        final long id;
        String user = "";
        String assistant = "";
        String action = "";
        boolean finalText;
        Turn(long id) { this.id = id; }
    }
    static boolean active;
    static boolean ended;
    static boolean failed;
    static String status = "准备连接";
    static String detail = "";
    private static final LinkedHashMap<Long, Turn> turns = new LinkedHashMap<>();
    private static final Set<Runnable> observers = new LinkedHashSet<>();
    static void observe(Runnable observer) { observers.add(observer); }
    static void remove(Runnable observer) { observers.remove(observer); }
    static void prepare() {
        ended = false; failed = false; status = "准备连接"; detail = ""; turns.clear(); changed();
    }
    static void begin() { prepare(); active = true; changed(); }
    static void state(String value, String message) { status = value; detail = message; changed(); }
    static Turn turn(long id) {
        Turn value = turns.get(id);
        if (value == null) {
            value = new Turn(id); turns.put(id, value);
            if (turns.size() > 12) turns.remove(turns.keySet().iterator().next());
        }
        return value;
    }
    static ArrayList<Turn> history() { return new ArrayList<>(turns.values()); }
    static void end(String reason, boolean error) {
        active = false; ended = true; failed = error;
        status = error ? "会话已中断" : "对话已结束"; detail = reason; changed();
    }
    static void changed() { for (Runnable observer : new ArrayList<>(observers)) observer.run(); }
}
