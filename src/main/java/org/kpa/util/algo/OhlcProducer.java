package org.kpa.util.algo;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.kpa.util.ChronoBased;
import org.kpa.util.Utils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 14.10.12
 * Time: 12:01
 * To change this template use File | Settings | File Templates.
 */
public class OhlcProducer {
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private final String symbol;
    private ZonedDateTime curBarTime = null;

    private Consumer<Ohlc> onBarCompleted;
    private Consumer<Ohlc> onBarCreated;
    private final Function<ZonedDateTime, ZonedDateTime> aliner;

    public OhlcProducer(String symbol, Function<ZonedDateTime, ZonedDateTime> aliner) {
        this.symbol = symbol;
        this.aliner = aliner;
    }

    public Ohlc update(ZonedDateTime barTime, BigDecimal price) {
        Ohlc ret = null;
        ZonedDateTime barTimeAligned = aliner.apply(barTime);
        if (curBarTime != null && barTimeAligned.isAfter(curBarTime)) {
            ret = new Ohlc(symbol, curBarTime, open, high, low, close);
            curBarTime = null;
            open = close;
            if (onBarCompleted != null) onBarCompleted.accept(ret);
        }
        if (curBarTime == null) {
            if (barTimeAligned.equals(barTime)) {
                addPrice(price, true);
            } else {
                addPrice(MoreObjects.firstNonNull(close, price), true);
            }
            curBarTime = barTimeAligned;
            if (onBarCreated != null)
                onBarCreated.accept(new Ohlc(symbol, curBarTime, open, high, low, close));
            if (!barTimeAligned.equals(barTime)) addPrice(price, false);
        } else {
            addPrice(price, false);
        }
        return ret;
    }

    private void addPrice(BigDecimal price, boolean clear) {
        if (open == null || clear) {
            open = price;
            high = price;
            low = price;
        }
        if (high.compareTo(price) < 0) {
            high = price;
        }
        if (low.compareTo(price) > 0) {
            low = price;
        }
        close = price;
    }

    public static Iterable<Ohlc> wrap(Function<ZonedDateTime, ZonedDateTime> aliner, Iterable<? extends ChronoBased> vals) {
        final Map<String, OhlcProducer> barMakers = new HashMap<>();
        return Utils.convert(vals, v -> {
            if (v instanceof BidAsk) {
                BidAsk bidAsk = (BidAsk) v;
                return barMakers.computeIfAbsent(bidAsk.getSymbol(), s -> new OhlcProducer(s, aliner))
                        .update(bidAsk.getLocalTime(), BigDecimal.valueOf(bidAsk.mid()));
            }
            return null;
        });
    }

    public void setOnBarCreated(Consumer<Ohlc> onBarCreated) {
        this.onBarCreated = onBarCreated;
    }

    public void setOnBarCompleted(Consumer<Ohlc> onBarCompleted) {
        this.onBarCompleted = onBarCompleted;
    }

    public static Function<ZonedDateTime, ZonedDateTime> minutesAligned(int minutes) {
        Preconditions.checkArgument(60 % minutes == 0, "Not even to 60minutes");
        return t -> {
            ZonedDateTime aligned = t.truncatedTo(ChronoUnit.MINUTES);
            return aligned.minusMinutes(aligned.getMinute() % minutes);
        };
    }

}