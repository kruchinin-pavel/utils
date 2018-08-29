package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.apache.commons.math3.util.Precision;
import org.kpa.util.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kpa.util.algo.Amnt.FEE;
import static org.kpa.util.algo.Amnt.POS;
import static org.kpa.util.algo.Amnt.with;
import static org.kpa.util.algo.Tickers.collateralCcy;

// TODO joins Portfolio (mutable) class with Position (immutable)
public class Portfolio implements Iterable<Position>, CryptoDto {
    private final Map<String, Position> positionMap = new HashMap<>();
    private boolean isActual = true;

    @JsonIgnore
    private Function<String, Function<Double, Double>> prcSrc;

    public Portfolio() {

    }

    @JsonCreator()
    public Portfolio(@JsonProperty("positionMap") Map<String, Position> positionMap, @JsonProperty("positionLimit") double positionLimit) {
        Preconditions.checkNotNull(positionMap);
        Preconditions.checkNotNull(positionLimit);
        this.positionMap.putAll(positionMap);
    }

    public Portfolio(Function<String, Function<Double, Double>> prcSrc) {
        Preconditions.checkNotNull(prcSrc);
        this.prcSrc = prcSrc;
    }

    public void setPrcSrc(Function<String, Function<Double, Double>> closePriceSrc) {
        Preconditions.checkNotNull(closePriceSrc, "prcSrc being set is null");
        this.prcSrc = closePriceSrc;
    }

    @JsonIgnore
    public Amnt getPosRefAmnt() {
        return positionMap.values().stream()
                .map(Position::getPosRefAmnt)
                .reduce(Amnt::add)
                .get();
    }

    @JsonIgnore
    public Amnt getSumAmnt() {
        return positionMap.values().stream()
                .map(Position::getNotional)
                .reduce(Amnt::add)
                .get();
    }


    @JsonIgnore
    public Amnt getRefSumAmnt() {
        List<Amnt> amntStream = positionMap.values().stream()
                .map(Position::getNotional).collect(Collectors.toList());
        return amntStream.stream()
                .reduce(Amnt::add)
                .get();
    }

    public Map<String, Position> getPositionMap() {
        return positionMap;
    }

    public Function<String, Function<Double, Double>> prcSrc() {
        return prcSrc;
    }

    @JsonIgnore
    public void add(Position position) {
        Position add = get(position.getSymbol()).add(position);
        positionMap.put(add.getSymbol(), add);
    }

    @JsonIgnore
    public void setFrom(Portfolio portfolio) {
        positionMap.clear();
        positionMap.putAll(portfolio.positionMap);
        positionMap.values().forEach(v -> v.prcSrc(prcSrc()));
    }

    /**
     * @param portfolio
     * @return Return changes
     */
    @JsonIgnore
    public List<Trade> syncWith(Portfolio portfolio) {
        List<Trade> changes = new ArrayList<>();
        delta(portfolio).forEach((ticker, delta) -> {
            Position position = null;

            Double price = prcSrc().apply(ticker).apply(delta.doubleValue());
            double size = delta.abs().doubleValue();
            boolean isBuy = false;
            if (delta.signum() > 0) {
                position = Position.flat(ticker, prcSrc()).buy(size, price);
                isBuy = true;
            } else if (delta.signum() < 0) {
                position = Position.flat(ticker, prcSrc()).sell(size, price);
                isBuy = false;
            }
            add(position);
            changes.add(new Trade(ZonedDateTime.now(), ticker, price, size, isBuy, position));
        });
        return changes;
    }

    @JsonIgnore
    public Position get(String ticker) {
        return positionMap.computeIfAbsent(ticker, k -> Position.flat(k, prcSrc));
    }

    @JsonIgnore
    public boolean has(String ticker) {
        return positionMap.containsKey(ticker);
    }

    @Override
    public String toString() {
        return toString(" ", true);
    }

    @JsonIgnore
    public BigDecimal getNotional() {
        return positionMap.values().stream()
                .map(e -> e.getNotional().total().abs())
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
    }

    @JsonIgnore
    public boolean isActual() {
        return isActual;
    }

    @JsonIgnore
    public void setActual(boolean actual) {
        isActual = actual;
    }

    public String toString(String nlDelim, boolean fee) {
        List<BigDecimal> fixedPnl = new ArrayList<>();
        List<BigDecimal> mtm = new ArrayList<>();
        List<BigDecimal> fees = new ArrayList<>();
        List<String> pnlStrs = positionMap.entrySet().stream()
                .map(e -> {
                    fixedPnl.add(e.getValue().fixedPnl());
                    mtm.add(e.getValue().markToMarket());
                    if (fee) {
                        fees.add(e.getValue().getAmnts().stream().map(a -> a.toRef(collateralCcy).total(with(FEE)))
                                .reduce(BigDecimal::add)
                                .orElse(BigDecimal.ZERO));
                    }
                    return "(" + e.getValue().pnlStr() + ")";
                })
                .collect(Collectors.toList());
        BigDecimal fixedTot = fixedPnl.stream().reduce(BigDecimal::add).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros();
        BigDecimal mtmTot = mtm.stream().reduce(BigDecimal::add).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros();
        BigDecimal sumTot = getNotional().setScale(0, RoundingMode.HALF_EVEN);
        BigDecimal pnl = BigDecimal.valueOf(getPnl()).setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros();
        String pnlBp = "-";
        String feeStr = "";
        if (sumTot.compareTo(BigDecimal.ZERO) > 0) {
            pnlBp = fixedTot.add(mtmTot).multiply(BigDecimal.valueOf(10_000)).divide(sumTot, 1, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
            if (fee) {
                feeStr = "(incl.fee(bp)=" + fees.stream().reduce(BigDecimal::add).orElse(BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(10_000)).divide(sumTot, 1, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString() + ")";
            }
        }
        return String.format("pnl=%s(fix=%s, mtm=%s, bp=%s%s), |pos|=%s: %s%s", pnl,
                fixedTot,
                mtmTot,
                pnlBp, feeStr,
                sumTot, nlDelim,
                Joiner.on("," + nlDelim).join(pnlStrs));
    }

    @JsonIgnore
    public double getPnl() {
        Preconditions.checkNotNull(prcSrc, "prcSrc is null");
        return Precision.round(positionMap.entrySet().stream()
                .mapToDouble(e -> e.getValue().pnl().doubleValue())
                .sum(), 2);
    }

    @JsonIgnore
    public BigDecimal getPnlBD() {
        Preconditions.checkNotNull(prcSrc, "prcSrc is null");
        return positionMap.entrySet().stream()
                .map(e -> e.getValue().pnl()).reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
    }

    @JsonIgnore
    @Override
    public Portfolio clone() {
        Portfolio ret = new Portfolio(prcSrc);
        positionMap.values().forEach(v -> ret.positionMap.put(v.getSymbol(), v));
        return ret;
    }

    @Override
    public Iterator<Position> iterator() {
        return positionMap.values().iterator();
    }

    @JsonIgnore
    public Stream<Position> stream() {
        return Utils.stream(this);
    }

    public double leverage() {
        return Math.abs(getPosRefAmnt().toRef(collateralCcy).total(Amnt.woDepo()).doubleValue() /
                getRefSumAmnt().total(Amnt.woDepoAndMargin()).doubleValue());

    }

    public double posToDepoLeverage() {
        return Math.abs(getPosRefAmnt().toRef(collateralCcy).total().doubleValue() /
                getRefSumAmnt().total(Amnt.depo()).doubleValue());
    }

    public double totLeverage() {
        return Math.abs(getPosRefAmnt().toRef(collateralCcy).total().doubleValue() /
                getRefSumAmnt().total().doubleValue());
    }

    public Map<String, BigDecimal> delta(Portfolio portfolio) {
        Map<String, BigDecimal> deltasByTicker = new LinkedHashMap<>();
        Set<String> tickers = new HashSet<>();
        tickers.addAll(portfolio.positionMap.keySet());
        tickers.addAll(positionMap.keySet());
        tickers.forEach(v -> {
            Position existingPosition = get(v);
            Position newPosition = portfolio.get(v);
            BigDecimal delta = newPosition.getQtyAmnt().get(POS).subtract(existingPosition.getQtyAmnt().get(POS));
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                deltasByTicker.put(v, delta);
            }
        });
        return deltasByTicker;
    }

    public static String diffStr(Portfolio before, Portfolio after) {
        StringBuilder sb = new StringBuilder();
        Map<String, BigDecimal> deltaMap;
        if (before == null) {
            deltaMap = new LinkedHashMap<>();
            after.positionMap.values().forEach(v -> {
                deltaMap.put(v.getSymbol(), v.getQtyAmnt().total());
            });
        } else {
            deltaMap = before.delta(after);
        }
        deltaMap.keySet().stream().sorted().forEach(v -> {
            if (sb.length() > 0) sb.append("\n");
            BigDecimal delta = deltaMap.get(v);
            Amnt qty = after.get(v).getQtyAmnt();
            sb.append(delta.signum() > 0 ? "+" : "-")
                    .append(v)
                    .append(" ")
                    .append(delta.abs().toPlainString())
                    .append(" => ");

            Amnt ref = after.get(v).getPosRefAmnt();
            if (!qty.getSymbol().equals(ref.getSymbol())) {
                sb.append(ref.total().toPlainString()).append(" ").append(ref.getSymbol());
                sb.append("(").append(qty.total().toPlainString()).append(" ").append(qty.getSymbol()).append(")");
            } else {
                sb.append(qty.get(POS).toPlainString()).append(" ").append(qty.getSymbol());
            }
        });
        return sb.insert(0, String.format(before == null ? "New(pl=%s %s):\n" : "Diff(pl=%s %s):\n", after.getPnl(), collateralCcy)).toString();
    }

    public static Portfolio sum(Function<String, Function<Double, Double>> prcSrc, Position... positions) {
        Portfolio portfolio = new Portfolio(prcSrc);
        Arrays.asList(positions).forEach(portfolio::add);
        return portfolio;
    }

}
