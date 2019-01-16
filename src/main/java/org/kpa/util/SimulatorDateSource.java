package org.kpa.util;

import com.google.common.base.Preconditions;
import net.jcip.annotations.NotThreadSafe;

import static org.kpa.util.DateSourceHelper.msToNs;

/**
 * Created with IntelliJ IDEA.
 * User: kruchinin
 * Date: 15.07.12
 * Time: 16:15
 * To change this template use File | Settings | File Templates.
 */
@NotThreadSafe
public class SimulatorDateSource implements DateSource {
    private long ctNanos = 0;

    public void addNanos(long nanos) {
        ctNanos += nanos;
    }

    public void addMillis(long millis) {
        ctNanos += msToNs(millis);
    }

    public SimulatorDateSource setNanos(long nanos) {
        Preconditions.checkArgument(nanos >= 0, "Time setMillis is less then unix epoch: %s", nanos);
        ctNanos = nanos;
        return this;
    }

    public SimulatorDateSource setMillis(long millis) {
        return setNanos(msToNs(millis));
    }

    @Override
    public long nanos() {
        return ctNanos;
    }
}
