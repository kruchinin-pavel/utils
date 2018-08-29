package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.kpa.util.algo.Amnt.woDepoAndMargin;
import static org.kpa.util.algo.Tickers.collateralCcy;

public class Position implements CryptoDto, Cloneable {
    private final String symbol;
    private final CcyPair pair;
    private Tickers.Instrument instrument;
    private final Map<String, Amnt> amntMap = new LinkedHashMap<>();
    private Function<String, Function<Double, Double>> prcSrc;
    private Double lastRefPrice = null;
    private Amnt lastRefAmnt = null;
    private Double lastQty = null;

    @JsonCreator
    public Position(@JsonProperty("symbol") String symbol, @JsonProperty("amnts") Iterable<Amnt> amnts) {
        this.symbol = symbol;
        pair = Tickers.instance.instrumentMap.get(symbol).getPair();
        amnts.forEach(v -> Preconditions.checkArgument(amntMap.put(v.getSymbol(), v) == null,
                "Already contains symbol: %s", v.getSymbol()));
        instrument = Tickers.instance.instrumentMap.get(symbol);
        Preconditions.checkNotNull(instrument, "Not found instrument for %s", symbol);
    }

    @JsonIgnore
    public CcyPair getPair() {
        return pair;
    }

    @JsonIgnore
    public Position put(Amnt... amnts) {
        Position ret = clone();
        for (Amnt amnt : amnts) {
            ret.amntMap.put(amnt.getSymbol(), amnt.prcSrc(prcSrc));
        }
        return ret;
    }

    public List<Amnt> getAmnts() {
        return new ArrayList<>(amntMap.values());
    }

    @JsonIgnore
    public Amnt get(String ccy) {
        Amnt amnt = amntMap.get(ccy);
        if (amnt == null) {
            return instrument.empty(ccy, prcSrc);
        }
        return amnt;
    }

    /**
     * Get reference amount of position (qty amount)
     *
     * @return
     */
    @JsonIgnore
    public Amnt getPosRefAmnt() {
        double qty = getQtyAmnt().total().doubleValue();
        Function<Double, Double> prcSrc = prcSrc().apply(symbol);
        Double liquidPrice = prcSrc.apply(qty);
        return getPosRefAmnt(liquidPrice);
    }

    @JsonIgnore
    public Amnt getPosRefAmnt(double marketPrice) {
        if (lastRefPrice == null || Math.abs(lastRefPrice / marketPrice - 1) > 0.01 ||
                lastQty == null || Math.abs(lastQty / getQtyAmnt().total().doubleValue() - 1) > 0.01) {
            lastRefAmnt = instrument.getPosRefAmnt(this, marketPrice);
        }
        return lastRefAmnt;
    }

    @JsonIgnore
    public Amnt getNotional() {
        return instrument.getNotional(this);
    }

    @JsonIgnore
    public Amnt getQtyAmnt() {
        return get(pair.getCcyLeft());
    }

    public String getSymbol() {
        return symbol;
    }

    @JsonIgnore
    public Position buy(double qty, double price, double leverage) {
        return instrument.buy(this, qty, price, leverage);
    }

    @JsonIgnore
    public Position buy(double qty, double price) {
        return buy(qty, price, 1.);
    }

    @JsonIgnore
    public Position fee(double fee) {
        if (fee < 0) return instrument.rebate(this, -fee);
        else return instrument.fee(this, fee);
    }

    @JsonIgnore
    public Position funding(double funding) {
        return instrument.funding(this, funding);
    }

    @JsonIgnore
    public Position sell(double qty, double price, double leverage) {
        return instrument.sell(this, qty, price, leverage);
    }

    @JsonIgnore
    public Position sell(double qty, double price) {
        return sell(qty, price, 1.);
    }

    @JsonIgnore
    public boolean isFlat() {
        return getQtyAmnt().isFlatPos();
    }

    @JsonIgnore
    public Position add(Position newPos) {
        return instrument.add(this, newPos);
    }

    @JsonIgnore
    public Position negate() {
        return new Position(symbol, amntMap.values().stream().map(Amnt::negate).collect(Collectors.toList())).prcSrc(prcSrc);
    }

    @JsonIgnore
    public Position substract(Position position) {
        Preconditions.checkArgument(getQtyAmnt().pos().compareTo(position.getQtyAmnt().pos()) == 0,
                "Qty are not equal: %s and %s", this, position);
        return negate().add(position);
    }

    @JsonIgnore
    public BigDecimal getTradedQty() {
        return getQtyAmnt().pos();
    }

    @JsonIgnore
    public BigDecimal markToMarket() {
        return markToMarket(prcSrc.apply(symbol).apply(getQtyAmnt().total().doubleValue()));
    }

    @JsonIgnore
    public BigDecimal markToMarket(double maketPrice) {
        return pnl(maketPrice).subtract(fixedPnl(maketPrice));
    }

    @JsonIgnore
    public BigDecimal fixedPnl() {
        return fixedPnl(prcSrc.apply(symbol).apply(getQtyAmnt().total().doubleValue()));
    }

    public BigDecimal fixedPnl(double marketPrice) {
        return instrument.fixedPnl(this, marketPrice);
    }

    @JsonIgnore
    public BigDecimal pnl() {
        return pnl(prcSrc.apply(symbol).apply(getQtyAmnt().total().doubleValue()));
    }

    @JsonIgnore
    public BigDecimal pnl(double marketPrice) {
        return instrument.pnl(this, marketPrice);
    }

    public static Position flat(String symbol, Function<String, Function<Double, Double>> prcSrc) {
        return new Position(symbol, Collections.EMPTY_LIST).prcSrc(prcSrc);
    }

    public Position clone(Amnt qty, Amnt sum) {
        return new Position(symbol, Arrays.asList(qty, sum)).prcSrc(prcSrc());
    }

    @Override
    public Position clone() {
        return new Position(symbol, getAmnts()).prcSrc(prcSrc());
    }

    public Position copyFlat() {
        return Position.flat(symbol, prcSrc);
    }

    @JsonIgnore
    public String pnlStr() {
        BigDecimal marketPrice = BigDecimal.valueOf(prcSrc.apply(symbol).apply(getQtyAmnt().pos().doubleValue())).stripTrailingZeros();
        return String.format("%s@%s pnl=%s(fix=%s, mtm=%s), %s",
                symbol, marketPrice.toPlainString(), pnl().setScale(0, RoundingMode.HALF_EVEN), fixedPnl().setScale(0, RoundingMode.HALF_EVEN),
                markToMarket().setScale(0, RoundingMode.HALF_EVEN),
                Joiner.on(", ").join(getAmnts()));
    }

    @JsonIgnore
    public Amnt getSumAmnt() {
        return get(pair.getCcyRight());
    }

    @JsonIgnore
    public Function<String, Function<Double, Double>> prcSrc() {
        return prcSrc;
    }

    public Position prcSrc(Function<String, Function<Double, Double>> closePriceSrc) {
        Preconditions.checkNotNull(closePriceSrc);
        this.prcSrc = closePriceSrc;
        amntMap.values().forEach(v -> v.prcSrc(closePriceSrc));
        return this;
    }

    @Override
    public String toString() {
        return "Pos{" +
                Joiner.on(", ").join(amntMap.values()) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return Objects.equals(symbol, position.symbol) &&
                Objects.equals(amntMap, position.amntMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, amntMap);
    }

    public double leverage() {
        return Math.abs(getPosRefAmnt().toRef(collateralCcy).total(woDepoAndMargin()).doubleValue() /
                getNotional().total(woDepoAndMargin()).doubleValue());
    }

    public double posToDepoLeverage() {
        return Math.abs(getPosRefAmnt().toRef(collateralCcy).total().doubleValue() /
                getNotional().total(Amnt.depo()).doubleValue());
    }

}
