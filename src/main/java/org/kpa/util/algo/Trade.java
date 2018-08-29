package org.kpa.util.algo;

import org.kpa.util.ChronoBased;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class Trade implements ChronoBased<Trade> {
    public final ZonedDateTime time;
    public final String ticker;
    public final BigDecimal price;
    public final BigDecimal size;
    public final String side;
    public final Position pos;

    public Trade(ZonedDateTime time, String ticker, double price, double size, boolean isBuy, Position pos) {
        this(time, ticker, new BigDecimal("" + price).stripTrailingZeros(),
                new BigDecimal("" + size).stripTrailingZeros(), isBuy ? "buy" : "sell", pos);
    }

    public Trade(ZonedDateTime time, String ticker, BigDecimal price, BigDecimal size, String side, Position pos) {
        this.time = time;
        this.ticker = ticker;
        this.price = price;
        this.size = size;
        this.side = side;
        this.pos = pos;
    }

    public boolean isBuy() {
        return "buy".equals(side);
    }

    @Override
    public String toString() {
        return toString("", true);
    }

    public String toString(String delim, boolean withPos) {
        StringBuilder format = new StringBuilder(String.format("trd: %s %s %s@%s at %s",
                side, ticker, size.stripTrailingZeros().toPlainString(), price.stripTrailingZeros().toPlainString(), getLocalTime()));
        if (pos != null && withPos) {
            format.append(",").append(delim).append(" pos=").append(pos);
        }
        return format.toString();
    }

    @Override
    public ZonedDateTime getLocalTime() {
        return time;
    }
}
