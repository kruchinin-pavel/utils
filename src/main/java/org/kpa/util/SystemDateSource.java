package org.kpa.util;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 22.07.12
 * Time: 14:17
 * To change this template use File | Settings | File Templates.
 */
public class SystemDateSource implements DateSource {
    private SystemDateSource() {
    }

    @Override
    public long nanos() {
        return DateSourceHelper.msToNs(System.currentTimeMillis());
    }

    public static final SystemDateSource instance = new SystemDateSource();

    public static SystemDateSource getInstance() {
        return instance;
    }
}
