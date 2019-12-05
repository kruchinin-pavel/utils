package org.kpa.util;

import com.google.common.base.Preconditions;

import java.util.function.Supplier;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 22.07.12
 * Time: 17:32
 * To change this template use File | Settings | File Templates.
 */
public class TurnoverCounter implements Cloneable {
    private Supplier<Long> millis = System::currentTimeMillis;
    private final double _maxValue;
    private final long _rangeMillis;
    private long _lastTsMillis;
    private double _lastValue;
    private double rateToMillis;

    public TurnoverCounter(long rangeMillis, double maxValue) {
        this(rangeMillis, maxValue, false);
    }

    public TurnoverCounter(long rangeMillis, double maxValue, boolean linear) {
        _rangeMillis = rangeMillis;
        _maxValue = maxValue;
        if (linear) {
            rateToMillis = maxValue / rangeMillis;
        }
    }

    public void reset() {
        _lastTsMillis = 0;
        _lastValue = 0;
    }

    public void runIfCan(Runnable runnable) {
        runIfCan(runnable, null);
    }

    public void runIfCan(Runnable runnable, Runnable otherwise) {
        boolean canAddValue = false;
        synchronized (this) {
            try {
                if (canAddValue(1.0)) {
                    addValue(1.);
                    canAddValue = true;
                }
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        }
        if (canAddValue) {
            runnable.run();
        } else if (otherwise != null) {
            otherwise.run();
        }
    }

    public double getLastVal() {
        if (rateToMillis > 0) {
            return Math.max(_lastValue - rateToMillis * (millis.get() - _lastTsMillis), 0.);
        } else {
            return _lastValue * ((double) Math.max(_rangeMillis - (millis.get() - _lastTsMillis), 0) / _rangeMillis);
        }
    }

    public void addValueSec(double value) {
        try {
            addValue(value);
        } catch (ValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setValue(double newValue) {
        _lastTsMillis = millis.get();
        _lastValue = newValue;
    }

    public void addValue(double value) throws ValidationException {
        synchronized (this) {
            Preconditions.checkArgument(value > 0, "Value must be positive: %s", value);
            this._lastValue = getLastVal() + value;
            _lastTsMillis = millis.get();
            if (getLastVal() > _maxValue) {
                throw new ValidationException("Max turnover " + _maxValue + " reached on " +
                        _rangeMillis + " milis. Value " + value);
            }
        }
    }

    public boolean canAddValue(double value) {
        synchronized (this) {
            Preconditions.checkArgument(value > 0, "Value must be positive: %s", value);
            return getLastVal() + value <= _maxValue;
        }
    }

    @Override
    public String toString() {
        return "TurnoverCounter{" +
                "dateSource=" + millis +
                ", _maxValue=" + _maxValue +
                ", _rangeMillis=" + _rangeMillis +
                ", _lastTsMillis=" + _lastTsMillis +
                ", _lastValue=" + _lastValue +
                '}';
    }

    @Override
    protected TurnoverCounter clone() {
        try {
            return (TurnoverCounter) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

    }

    public TurnoverCounter setDateSource(DateSource dateSource) {
        return setMillis(dateSource::millis);
    }

    public TurnoverCounter setMillis(Supplier<Long> millis) {
        if (millis == null) {
            throw new NullPointerException("dateSource is null");
        }
        this.millis = millis;
        return this;
    }
}
