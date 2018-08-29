package org.kpa.util.algo;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.kpa.util.ChronoBased;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.valueOf;

public class Ohlc implements ChronoBased<Ohlc>, Cloneable {
    public static final String SYMBOL = "symbol";
    public static final String TIME = "time";
    public static final String OPEN = "open";
    public static final String HIGH = "high";
    public static final String LOW = "low";
    public static final String CLOSE = "close";
    public final String symbol;
    public final ZonedDateTime time;
    public final BigDecimal open;
    public final BigDecimal high;
    public final BigDecimal low;
    public final BigDecimal close;
    public final Map<String, Object> payload = new LinkedHashMap<>();
    private static final DateTimeFormatter ldtF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public Ohlc(String symbol, ZonedDateTime time, double price) {
        this(symbol, time, price, price, price, price);
    }

    public Ohlc(String symbol, ZonedDateTime time, double open, double high, double low, double close) {
        this(symbol, time, valueOf(open), valueOf(high), valueOf(low), valueOf(close));
    }

    public Ohlc(String symbol, ZonedDateTime time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        this.symbol = symbol;
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        Preconditions.checkArgument(high.compareTo(open) >= 0);
        Preconditions.checkArgument(high.compareTo(close) >= 0);
        Preconditions.checkArgument(low.compareTo(open) <= 0);
        Preconditions.checkArgument(low.compareTo(close) <= 0);
    }

    @Override
    public ZonedDateTime getLocalTime() {
        return time;
    }

    @Override
    public String toString() {
        return "Ohlc{" +
                symbol +
                " at=" + time +
                ", o=" + open.toPlainString() +
                ", h=" + high.toPlainString() +
                ", l=" + low.toPlainString() +
                ", c=" + close.toPlainString() +
                (payload.size() > 0 ? ", " + Joiner.on(", ").withKeyValueSeparator("=").join(payload) : "") +
                '}';
    }

    public Ohlc add(Ohlc ohlc) {
        Preconditions.checkArgument(symbol.equals(ohlc.symbol));
        return new Ohlc(symbol, ohlc.time, open, high.max(ohlc.high), low.min(ohlc.low), ohlc.close);
    }

    public Map<String, Object> toMap() {
        return new ImmutableMap.Builder<String, Object>()
                .put(SYMBOL, symbol)
                .put(TIME, time.format(ldtF))
                .put(OPEN, open.toPlainString())
                .put(HIGH, high.toPlainString())
                .put(LOW, low.toPlainString())
                .put(CLOSE, close.toPlainString())
                .putAll(payload)
                .build();
    }

    public boolean isRaise(double targetPnl) {
        return raisePnl() > targetPnl;
    }

    public boolean isDown(double targetPnl) {
        return downPnl() > targetPnl;
    }

    public double raisePnl() {
        return close.divide(open, 8, RoundingMode.HALF_EVEN)
                .subtract(ONE).doubleValue();
    }

    public double downPnl() {
        return open.divide(close, 8, RoundingMode.HALF_EVEN)
                .subtract(ONE).doubleValue();
    }

    public BigDecimal range() {
        return close.subtract(open).abs();
    }

    @Override
    public Ohlc clone() {
        Ohlc ohlc = new Ohlc(symbol, time, open, high, low, close);
        ohlc.payload.putAll(payload);
        return ohlc;
    }

    public OhlcType getType(double margin) {
        if (isRaise(margin)) {
            return OhlcType.RAISE;
        } else if (isDown(margin)) {
            return OhlcType.DOWN;
        }
        return OhlcType.NEUTRAL;
    }

    public double getPredictedPnl() {
        double pnl = raisePnl();
        if (pnl < 0) {
            pnl = downPnl();
        }
        if (pnl < 0) {
            pnl = -1.0;
        }
        return pnl;
    }

    public static Ohlc fromMap(Map<String, String> map) {
        return new Ohlc(map.get(SYMBOL),
                ZonedDateTime.of(LocalDateTime.parse(map.get(TIME), ldtF), ZoneId.of("UTC")),
                new BigDecimal(map.get(OPEN)),
                new BigDecimal(map.get(HIGH)),
                new BigDecimal(map.get(LOW)),
                new BigDecimal(map.get(CLOSE)));
    }
}
