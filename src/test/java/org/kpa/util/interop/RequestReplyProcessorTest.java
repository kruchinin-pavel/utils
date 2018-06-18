package org.kpa.util.interop;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RequestReplyProcessorTest {
    public interface Msg {

    }

    public static class Req implements Msg {
        long requestId;

        public Req(long requestId) {
            this.requestId = requestId;
        }
    }

    public static class Rep implements Msg {
        long requestId;

        public Rep(long requestId) {
            this.requestId = requestId;
        }
    }

    @Test
    public void doTest() throws InterruptedException {
        RequestReplyProcessor<Msg> processor = new RequestReplyProcessor<>((req, rep) -> ((Rep) rep).requestId == ((Req) req).requestId);
        AtomicInteger sucCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();
        processor.request(new Req(1), 1_000, r -> sucCnt.incrementAndGet(), (r, e) -> failCnt.incrementAndGet());
        processor.request(new Req(2), 1_000, r -> sucCnt.incrementAndGet(), (r, e) -> failCnt.incrementAndGet());
        processor.reply(new Rep(1));
        Thread.sleep(2_001);
        assertEquals(1, sucCnt.get());
        assertEquals(1, failCnt.get());
    }

}