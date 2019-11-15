package org.kpa.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public class RomasCsvTest {
    private final String cols;
    private final String vals;
    private final String expOrdId;

    public RomasCsvTest(String cols, String vals, String expOrdId) {
        this.cols = cols;
        this.vals = vals;
        this.expOrdId = expOrdId;
    }

    @Test
    public void testParse() {
        RomasCsv csv = new RomasCsv(cols);
        Map<String, String> res = csv.parse(vals);
        assertEquals(expOrdId, res.get("Client orderId"));
        assertFalse(res.get("Execution action").startsWith("\""));
    }

    @Parameterized.Parameters
    public static List<Object[]> data() {
        String cols = "Sequence,BatchId,Time,Execution action,Is success,Client orderId,Action payload,Comment";
        return Arrays.asList(
                new Object[]{cols,
                        "1550672675765,1121645,2019-02-24 00:11:25.223,EaCanceled{cancelReason='Canceled: Canceled via API.Submitted via API.', " +
                                "instrumentId=1115, avgPx=0.0, cumQty=0}," +
                                "true,7-9-1126205-0-085205,instrumentId=1115 originalCliOrderId=7-9-1126132-0-981531 cancelReason=Canceled: Canceled via API.Submitted via API. price=0.0 cumQty=0,EaCanceled - for high priority order. Closing batch: Closed",
                        "7-9-1126205-0-085205"
                },
                new Object[]{cols,
                        "1550672675765,1121645,2019-02-24 00:11:25.223,\"EaCanceled{cancelReason=\"\"Canceled: Canceled via API.Submitted via API.\"\", " +
                                "instrumentId=1115, avgPx=0.0, cumQty=0}\"," +
                                "true,7-9-1126205-0-085205,instrumentId=1115 originalCliOrderId=7-9-1126132-0-981531 cancelReason=Canceled: Canceled via API.Submitted via API. price=0.0 cumQty=0,EaCanceled - for high priority order. Closing batch: Closed",
                        "7-9-1126205-0-085205"
                },
                new Object[]{cols,
                        "1550672711747, 1122449, 2019-02-24 00:18:37.093, EaReplaced{instrumentId=1119, batchId=1122449, originalCliOrderId='7-1-1126939-0-516253', isLong=false, price=4111.0, size=500, instrumentId=1119, cumQty=0, cliOrderId='7-1-1126946-0-517077'}, true, 7-1-1126946-0-517077, instrumentId=1119 originalCliOrderId=7-1-1126939-0-516253 price=4111.0 size=500,",
                        "7-1-1126946-0-517077"
                },
                new Object[]{cols,
                        "1550655574261,16560,2019-02-21 02:33:22.324,EaByTimeOut,true,t-3-batch-16560,1550716402317 EaByTimeOut cliOrderId t-3-batch-16560,Closing batch by timeout: ExecBatch{robotId=3, sequence=1550655563517, batchId=16560, statusText='null', lowPriorityHasExecuted=false, hasLowPriorityOrders=true, highPriorityFilled=false, lowPriorityFilled=false, isClosed=false, revertBatchChangesIsOn=false, revertBatchChangesSetByAlgo=true, closeOpenedByBatchPosExecuted=false, closeMismatchExecuted=false, rejectsCount=0, resendCount=0, nodeId=-1, execEntries=[ExecEntry{instrumentId=1110, side=B, price=1.209E-5, priceAfterTimeOut=1.209E-5, size=240, priority=0, remainSize=240, filledSize=0, cliOrderId='1-3-21234-0-102316', comment='b:I14 originalPrice: 1.209E-5 priceStep: 1.0E-8', batchId=0, isPartFillEntry=false, isOrderWasSent=true, forcedExecution=false, execInstruction='ParticipateDoNotInitiate'}, ExecEntry{instrumentId=1211, side=S, price=1.194E-5, priceAfterTimeOut=1.194E-5, size=1000, priority=1, remainSize=1000, filledSize=0, cliOrderId='1-3-21235-0-102316', comment='b:I14 originalPrice: 1.194E-5 priceStep: 1.0E-8', batchId=0, isPartFillEntry=false, isOrderWasSent=false, forcedExecution=false, execInstruction='null'}]}",
                        "t-3-batch-16560"
                });
    }
}