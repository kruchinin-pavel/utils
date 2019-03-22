package org.kpa.util;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CachedValTest {
    private class TestItem implements AutoCloseable {
        boolean closed = false;

        @Override
        public void close() {
            closed = true;
        }

    }

    @Test
    public void valTest() throws InterruptedException {
        CachedVal<TestItem> test = CachedVal.getDisposable(TestItem::new, 100);
        TestItem item = test.get();
        Thread.sleep(1_000);
        assertTrue(item.closed);
    }

}