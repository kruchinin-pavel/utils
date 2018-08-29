package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import org.kpa.util.ChronoBased;
import org.kpa.util.FileRef;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.kpa.util.algo.Tickers.getLCcySizeConverter;

public class BidAsk implements CryptoDto, ChronoBased<BidAsk>, Cloneable {
    public static final String TYPE = "_type";
    public static final String LOCALTIME = "localtime";
    public static final String TIMESTAMP = "exchangetime";
    public static final String SYMBOL = "symbol";
    public static final String BID = "bid";
    public static final String BID_SIZE = "bidSize";
    public static final String ASK = "ask";
    public static final String ASK_SIZE = "askSize";

    private final String symbol;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd-HHmmss.SSS", timezone = "Europe/Moscow")
    private final ZonedDateTime localtime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMdd-HHmmss.SSS", timezone = "Europe/Moscow")
    private final ZonedDateTime exchangeTime;
    private final double bid;
    private final double bidSize;
    private final double ask;
    private final double askSize;
    private final FileRef srcFile;

    @JsonIgnore
    private boolean stale;
    @JsonIgnore
    private boolean reorder = false;
    @JsonIgnore
    private boolean inconsistent = false;
    @JsonIgnore
    private boolean priceLeap = false;

    public BidAsk(String symbol, double bid, double ask, ZonedDateTime localtime) {
        this(symbol, localtime, null, bid, 1., ask, 1., null);
    }

    @JsonCreator
    public BidAsk(@JsonProperty("symbol") String symbol,
                  @JsonProperty("localtime") ZonedDateTime localtime,
                  @JsonProperty("timestamp") ZonedDateTime exchangeTime,
                  @JsonProperty("bid") double bid,
                  @JsonProperty("bidSize") double bidSize,
                  @JsonProperty("ask") double ask,
                  @JsonProperty("askSize") double askSize,
                  @JsonProperty("srcFile") FileRef srcFile) {
        this.symbol = symbol;
        this.localtime = localtime;
        this.exchangeTime = exchangeTime;
        this.bid = bid;
        this.bidSize = bidSize;
        this.ask = ask;
        this.askSize = askSize;
        this.srcFile = srcFile;
    }

    @JsonIgnore
    public boolean isAlive() {
        return !isStale() && !isReorder() && !isInconsistent() && !isPriceLeap() && bidSize > 0 && askSize > 0;
    }

    @JsonIgnore
    public boolean isStale() {
        return stale;
    }

    @JsonIgnore
    public void setStale(boolean stale) {
        this.stale = stale;
    }

    @JsonIgnore
    public double mid() {
        Preconditions.checkArgument(bid > 0 && ask > 0);
        return (bid + ask) / 2;
    }

    /**
     * Liquid price source
     * currentQty>0 - sell long, currentQty<0 - close short
     */
    @JsonIgnore
    public Function<Double, Double> liquid() {
        Preconditions.checkArgument(isAlive(), "BidAsk is not alive: %s", this);
        return (currentQty) -> {
            if (currentQty > 0) {
                return bid;
            } else if (currentQty < 0) {
                return ask;
            }
            return mid();
        };
    }

    @Override
    public ZonedDateTime getLocalTime() {
        return localtime;
    }

    public FileRef getSrcFile() {
        return srcFile;
    }

    public String getSymbol() {
        return symbol;
    }

    public ZonedDateTime getExchangeTime() {
        return exchangeTime;
    }

    public double getBid() {
        return bid;
    }

    public double getBidSize() {
        return bidSize;
    }

    @JsonIgnore
    private BiFunction<BidAsk, Boolean, Double> cnv;

    @JsonIgnore
    public double getBidSize(boolean leftCcy) {
        if (!leftCcy) return getBidSize();
        if (cnv == null) {
            cnv = getLCcySizeConverter(getSymbol());
        }
        return cnv.apply(this, true);
    }

    @JsonIgnore
    public double getAskSize(boolean leftCcy) {
        if (!leftCcy) return getAskSize();
        if (cnv == null) {
            cnv = getLCcySizeConverter(getSymbol());
        }
        return cnv.apply(this, false);
    }

    public double getAsk() {
        return ask;
    }

    public double getAskSize() {
        return askSize;
    }

    @Override
    public String toString() {
        return "BidAsk{" +
                (isStale() ? " STALE " : "") +
                (isPriceLeap() ? " PRICE_LEAP " : "") +
                (isInconsistent() ? " OUT_OF_TRADES " : "") +
                (isReorder() ? " REORDER " : "") +
                symbol +
                ", b/s=" + bid + "/" + ask +
                ", sz=" + bidSize + "/" + askSize +
                ", ts='" + localtime + '\'' +
                (exchangeTime != null ? ", exch.ts='" + exchangeTime + '\'' : "") +
                (srcFile != null ? ", src=" + srcFile : "") +
                '}';
    }

    private static final DateTimeFormatter ldtF2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public Map<String, Object> toCsv() {
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put(TYPE, "BidAsk");
        vals.put(LOCALTIME, getLocalTime() != null ? ldtF2.format(getLocalTime().toInstant().atZone(ZoneId.of("UTC"))) : "");
        vals.put(TIMESTAMP, getExchangeTime() != null ? ldtF2.format(getExchangeTime().toInstant().atZone(ZoneId.of("UTC"))) : "");
        vals.put(SYMBOL, getSymbol());
        vals.put(BID, getBid());
        vals.put(BID_SIZE, getBidSize());
        vals.put(ASK, getAsk());
        vals.put(ASK_SIZE, getAskSize());
        return vals;
    }

    public static final Comparator<BidAsk> DATE_CMP = Comparator.comparing((BidAsk o) -> o.localtime)
            .thenComparing(o -> o.symbol);

    public static final Comparator<BidAsk> BID_CMP = Comparator.comparing((BidAsk o) -> o.bid);
    public static final Comparator<BidAsk> BEST_BID = Collections.reverseOrder(BidAsk.BID_CMP);
    public static final Comparator<BidAsk> ASK_CMP = Comparator.comparing((BidAsk o) -> o.ask);
    public static final Comparator<BidAsk> BEST_ASK = BidAsk.ASK_CMP;

    public static LocalDateTime last(LocalDateTime time1, LocalDateTime time2) {
        return time1.compareTo(time2) < 0 ? time2 : time1;
    }

    public void setReorder(boolean reorder) {
        this.reorder = reorder;
    }

    public void setInconsistent(boolean inconsistent) {
        this.inconsistent = inconsistent;
    }

    public void setPriceLeap(boolean priceLeap) {
        this.priceLeap = priceLeap;
    }

    public boolean isReorder() {
        return reorder;
    }

    public boolean isInconsistent() {
        return inconsistent;
    }

    public boolean isPriceLeap() {
        return priceLeap;
    }

    @Override
    public BidAsk clone() {
        try {
            BidAsk ret = (BidAsk) super.clone();
            ret.setStale(false);
            ret.setInconsistent(false);
            return ret;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

}
