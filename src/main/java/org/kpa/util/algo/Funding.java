package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kpa.util.ChronoBased;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class Funding implements ChronoBased<Funding> {
    private final ZonedDateTime timestamp;
    private final String symbol;
    private final double fundingRate;

    @JsonCreator
    public Funding(@JsonProperty("timestamp") ZonedDateTime timestamp,
                   @JsonProperty("symbol") String symbol,
                   @JsonProperty("fundingRate") double fundingRate) {
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.fundingRate = fundingRate;
    }

    @Override
    @JsonIgnore
    public ZonedDateTime getLocalTime() {
        return getTimestamp();
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getFundingRate() {
        return fundingRate;
    }

    @Override
    public String toString() {
        return "Funding{" +
                "timestamp=" + timestamp +
                ", symbol='" + symbol + '\'' +
                ", fundingRate=" + fundingRate +
                '}';
    }

    public Position eval(Position position) {
        Amnt qtyAmnt = position.getQtyAmnt();
        if (qtyAmnt.total().compareTo(BigDecimal.ZERO) == 0) return position;
        return position.copyFlat().funding(qtyAmnt.total().doubleValue() /
                qtyAmnt.getProp(Amnt.MARKUP_PRICE).doubleValue() * getFundingRate());
    }
}
