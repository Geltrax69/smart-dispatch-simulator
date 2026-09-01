package com.dispatch.sim;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe event log. Producers (any thread) call {@link #log}; the
 * UI thread polls via {@link #snapshot()}.
 */
public final class EventLogger {
    public interface Listener { void onNewEvent(String line); }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LINES = 500;

    private final Deque<String> lines = new ArrayDeque<>(MAX_LINES + 16);
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void log(String msg) {
        String line = "[" + LocalTime.now().format(FMT) + "] " + msg;
        synchronized (lock) {
            if (lines.size() >= MAX_LINES) lines.pollFirst();
            lines.addLast(line);
        }
        for (Listener l : listeners) l.onNewEvent(line);
    }

    public List<String> snapshot() {
        synchronized (lock) { return List.copyOf(lines); }
    }
}
