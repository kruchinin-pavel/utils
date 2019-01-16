package org.kpa.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class RunOnce {
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean startCompleted = new AtomicBoolean();

    public boolean runOnce(Runnable run) {
        return runOnce(run, null);
    }

    public boolean runOnce(Runnable run, Runnable onUnable) {
        if (!started.compareAndSet(false, true)) {
            if (onUnable != null) onUnable.run();
            return false;
        }
        try {
            run.run();
            return true;
        } finally {
            startCompleted.set(true);
        }
    }

    public boolean isActed(){
        return startCompleted.get();
    }
}
