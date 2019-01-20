package org.kpa.util;

import com.google.common.base.Preconditions;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 22.07.12
 * Time: 17:32
 * To change this template use File | Settings | File Templates.
 */
public class TurnoverCounter implements Cloneable {
    private DateSource dateSource = SystemDateSource.getInstance();
    private final double _maxValue;
    private final long _rangeNanos;
    private long _lastTsNanos;
    private double _lastValue;

    public TurnoverCounter(long rangeMillis, double maxValue) {
        _rangeNanos = DateSourceHelper.msToNs(rangeMillis);
        _maxValue = maxValue;
    }

    public void runIfCan(Runnable runnable) {
        if (canAddValue(1.)) {
            try {
                addValue(1.);
                runnable.run();
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public double getLastVal() {
        double amortization = (double) Math.max(_rangeNanos - (dateSource.nanos() - _lastTsNanos), 0) / _rangeNanos;
        return _lastValue * amortization;
    }

    public void addValueSec(double value) {
        try {
            addValue(value);
        } catch (ValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void addValue(double value) throws ValidationException {
        Preconditions.checkArgument(value > 0, "Value must be positive: %s", value);
        this._lastValue = getLastVal() + value;
        _lastTsNanos = dateSource.nanos();
        if (getLastVal() > _maxValue) {
            throw new ValidationException("Max turnover " + _maxValue + " reached on " +
                    _rangeNanos + " nanos. Value " + value);
        }
    }

    public boolean canAddValue(double value) {
        Preconditions.checkArgument(value > 0, "Value must be positive: %s", value);
        return getLastVal() + value <= _maxValue;
    }

    @Override
    public String toString() {
        return "TurnoverCounter{" +
                "dateSource=" + dateSource +
                ", _maxValue=" + _maxValue +
                ", _rangeNanos=" + _rangeNanos +
                ", _lastTsNanos=" + _lastTsNanos +
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
        if (dateSource == null) {
            throw new NullPointerException("dateSource is null");
        }
        this.dateSource = dateSource;
        return this;
    }
}