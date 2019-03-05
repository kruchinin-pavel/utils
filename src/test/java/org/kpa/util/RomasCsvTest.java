package org.kpa.util;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class RomasCsvTest {
    @Test
    public void testParse() {
        RomasCsv csv = new RomasCsv("Sequence,BatchId,Time,Execution action,Is success,Client orderId,Action payload,Comment");
        Map<String, String> vals = csv.parse("1550672675765,1121645,2019-02-24 00:11:25.223,EaCanceled{cancelReason='Canceled: Canceled via API.Submitted via API.', " +
                "instrumentId=1115, avgPx=0.0, cumQty=0}," +
                "true,7-9-1126205-0-085205,instrumentId=1115 originalCliOrderId=7-9-1126132-0-981531 cancelReason=Canceled: Canceled via API.Submitted via API. price=0.0 cumQty=0,EaCanceled - for high priority order. Closing batch: Closed");
        assertEquals("7-9-1126205-0-085205", ((Map) vals).get("Client orderId"));
    }

    @Test
    public void testParse2() {
        RomasCsv csv = new RomasCsv("Sequence, BatchId, Time, Execution action, Is success, Client orderId, Action payload, Comment");
        Map<String, String> vals = csv.parse("1550672711747, 1122449, 2019-02-24 00:18:37.093, EaReplaced{instrumentId=1119, batchId=1122449, originalCliOrderId='7-1-1126939-0-516253', isLong=false, price=4111.0, size=500, instrumentId=1119, cumQty=0, cliOrderId='7-1-1126946-0-517077'}, true, 7-1-1126946-0-517077, instrumentId=1119 originalCliOrderId=7-1-1126939-0-516253 price=4111.0 size=500,");
        assertEquals("7-1-1126946-0-517077", ((Map) vals).get("Client orderId"));
    }

}