package org.kpa.util;

import com.google.common.base.Joiner;

public class ThreadStates {

    public static String printThreadState(Thread thread) {
        return printThreadState(thread, true);
    }

    public static String printThreadState(Thread thread, boolean stackTraceOnBlockedStateOnly) {
        return printThreadState(thread, stackTraceOnBlockedStateOnly, "; ");
    }

    public static String printThreadState(Thread thread, boolean stackTraceOnBlockedStateOnly, String stackTraceDelimiter) {
        StringBuilder stateStr = new StringBuilder("thread is not set");
        if (thread != null) {
            stateStr.setLength(0);
            stateStr.append("thread='").append(thread.getName()).append("':").append(thread.getId());
            Thread.State state = thread.getState();
            stateStr.append("(").append(state).append(")");
            if (!stackTraceOnBlockedStateOnly || state == Thread.State.BLOCKED) {
                stateStr.append(" stack: ").append(Joiner.on(stackTraceDelimiter).useForNull("null").join(thread.getStackTrace()));
            }
        }
        return stateStr.toString();
    }
}
