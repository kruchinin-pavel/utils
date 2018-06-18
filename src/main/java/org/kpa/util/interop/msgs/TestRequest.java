package org.kpa.util.interop.msgs;

public class TestRequest implements Message {
    public long reqId;

    public TestRequest() {
        this.reqId = System.currentTimeMillis();
    }
}
