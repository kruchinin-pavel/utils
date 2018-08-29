package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.kpa.util.CachedVal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;
import static org.kpa.util.Utils.valOrNull;

public class Amnt implements CryptoDto, Cloneable {
    private final int scale;
    private final String symbol;
    private final BigDecimal minVal;
    public static final String POS = "pos";
    public static final String MARGIN = "mgn";
    public static final String DEPO = "depo";
    public static final String PNL = "fixedPnl";
    public static final String PNL_UNREALIZED = "unrPnl";
    public static final String FEE = "fee";
    public static final String REBATE = "rbt";
    public static final String FUNDING = "fnd";
    public static final String MARKUP_PRICE = "markupPrice";
    private Function<String, Function<Double, Double>> prcSrc;
    private final Map<String, BigDecimal> valuesByCode = new HashMap<>();
    private final Map<String, BigDecimal> payload = new HashMap<>();
    private final CachedVal<BigDecimal> total = new CachedVal<>(() ->
            valuesByCode.values().stream().reduce(BigDecimal::add).orElse(ZERO).stripTrailingZeros());
    private final CachedVal<BigDecimal> rest = new CachedVal<>(() ->
            valuesByCode.entrySet().stream()
                    .filter(e -> !e.getKey().equals(POS)).map(Map.Entry::getValue)
                    .reduce(BigDecimal::add).orElse(ZERO));


    @JsonCreator
    public Amnt(@JsonProperty("symbol") String symbol,
                @JsonProperty("scale") int scale,
                @JsonProperty("valuesByCode") Map<String, BigDecimal> valuesByCode,
                @JsonProperty("payload") Map<String, BigDecimal> payload) {
        this.symbol = symbol;
        this.scale = scale;
        if (valuesByCode != null) this.valuesByCode.putAll(valuesByCode);
        if (payload != null) this.payload.putAll(payload);
        this.minVal = BigDecimal.ONE.movePointLeft(scale);
    }

    @JsonIgnore
    public Amnt prcSrc(Function<String, Function<Double, Double>> prcSrc) {
        Preconditions.checkNotNull(prcSrc, "prcSrc is null. This: %s", this);
        this.prcSrc = prcSrc;
        return this;
    }

    @JsonIgnore
    public <T extends Amnt> T rcv(double val) {
        return (T) clone().implAdd(val, POS);
    }

    public Amnt setProp(double val, String prop) {
        return clone().implSet(val, prop);
    }

    public BigDecimal getProp(String prop) {
        return payload.get(prop);
    }

    public BigDecimal getMinVal() {
        return minVal;
    }

    /**
     * @param val (>0 -we pay (pay), <0 - we paid (rcv))
     * @return
     */
    public Amnt pay(double val, String account) {
        return rcv(-val, account);
    }

    /**
     * @param val (>0 - we receive (rcv), <0 - we pay)
     * @return
     */
    public Amnt rcv(double val, String account) {
        if (val == 0.) return this;
        Preconditions.checkArgument(Math.abs(val) >= minVal.doubleValue(), "Value(%s) is less then minVal(%s) adding %s in %s",
                val, minVal, account, this);
        return clone().implAdd(val, account);
    }

    @JsonIgnore
    public Amnt set(double val, String account) {
        BigDecimal _val = MoreObjects.firstNonNull(valuesByCode.get(account), ZERO);
        if (_val.compareTo(BigDecimal.valueOf(val)) == 0) {
            return this;
        }
        return clone().implSetVal(val, account);
    }

    @JsonIgnore
    private Amnt implAdd(double val, String account) {
        return implSetVal(MoreObjects.firstNonNull(valuesByCode.get(account), ZERO).add(BigDecimal.valueOf(val)).doubleValue(), account);
    }

    @JsonIgnore
    private Amnt implSetVal(double val, String account) {
        BigDecimal bdVal = BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_EVEN).stripTrailingZeros();
        if (bdVal.compareTo(ZERO) == 0) {
            valuesByCode.remove(account);
        } else {
            valuesByCode.put(account, bdVal);
        }
        return this;
    }

    @JsonIgnore
    private Amnt implSet(double val, String account) {
        payload.put(account, BigDecimal.valueOf(val));
        return this;
    }

    @JsonIgnore
    public Amnt flatCopy() {
        return new Amnt(symbol, scale, null, payload).prcSrc(prcSrc);
    }

    @JsonIgnore
    public BigDecimal total() {
        return total.get();
    }

    /**
     * Total sum except pos
     *
     * @return
     */
    @JsonIgnore
    public BigDecimal rest() {
        return rest.get();
    }

    @JsonIgnore
    public BigDecimal get(String account) {
        return valuesByCode.getOrDefault(account, ZERO);
    }

    @JsonIgnore
    public BigDecimal pos() {
        return MoreObjects.firstNonNull(valuesByCode.get(POS), ZERO);
    }

    @JsonIgnore
    public boolean isFlat() {
        return total().compareTo(ZERO) == 0;
    }

    @JsonIgnore
    public boolean isFlatPos() {
        return pos().compareTo(ZERO) == 0;
    }

    @JsonIgnore
    public Amnt add(Amnt amnt) {
        Preconditions.checkArgument(symbol.equals(amnt.symbol), "Symbols are not equal: %s and %s", this, amnt);
        Preconditions.checkArgument(scale == amnt.scale, "Scale not equal: %s and %s", this, amnt);
        Amnt resAmount = clone();
        amnt.valuesByCode.forEach((account, bdVal) -> resAmount.implAdd(bdVal.doubleValue(), account));
        amnt.payload.forEach(resAmount.payload::put);
        return resAmount;
    }

    @JsonIgnore
    public Amnt negate() {
        Amnt amnt = clone();
        amnt.valuesByCode.replaceAll((s, bigDecimal) -> bigDecimal.negate());
        return amnt;
    }

    public int getScale() {
        return scale;
    }

    public String getSymbol() {
        return symbol;
    }

    public Map<String, BigDecimal> getValuesByCode() {
        return Collections.unmodifiableMap(valuesByCode);
    }

    public Map<String, BigDecimal> getPayload() {
        return Collections.unmodifiableMap(payload);
    }

    public BigDecimal total(Function<String, Boolean> filter) {
        return valuesByCode.entrySet().stream()
                .filter(v -> filter.apply(v.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal::add).orElse(ZERO);
    }

    public Amnt toRef(String ccy) {
        if (ccy.equals(symbol)) {
            return this;
        }
        return toRef(ccy, prcSrc.apply(symbol + ccy)).prcSrc(prcSrc);
    }

    public Amnt toRef(String ccy, double prc) {
        return toRef(ccy, val -> prc);
    }

    public Amnt toRef(String ccy, Function<Double, Double> cnv) {
        if (ccy.equals(symbol)) {
            return this;
        }
        Preconditions.checkNotNull(cnv, "Not found price converter from %s to %s", getSymbol(), ccy);
        Map<String, BigDecimal> newVals = new LinkedHashMap<>();
        valuesByCode.forEach((key, val) ->
                newVals.put(key, val.multiply(BigDecimal.valueOf(cnv.apply(val.doubleValue())))));
        return new Amnt(ccy, 8, newVals, payload).prcSrc(prcSrc);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (valuesByCode.size() > 0) {
            sb.append('{');
            if (valuesByCode.size() == 1) {
                sb.append(valuesByCode.keySet().iterator().next());
            } else {
                sb.append(Joiner.on(",").skipNulls()
                        .join(Collections.singletonList(valOrNull(Joiner.on(", ")
                                .withKeyValueSeparator("=")
                                .join(valuesByCode.entrySet().stream().collect(
                                        Collectors.toMap(Map.Entry::getKey,
                                                e -> e.getValue().toPlainString())
                                ))))));
            }
            if (payload.size() > 0) {
                sb.append(", ");
                sb.append(Joiner.on(",").skipNulls()
                        .join(Collections.singletonList(valOrNull(Joiner.on(", ")
                                .withKeyValueSeparator("@")
                                .join(payload.entrySet().stream().collect(
                                        Collectors.toMap(Map.Entry::getKey,
                                                e -> e.getValue().toPlainString())
                                ))))));
            }
            sb.append("}");
        }
        return symbol + ":" + total().toPlainString() + sb.toString();

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Amnt amnt = (Amnt) o;
        return scale == amnt.scale &&
                Objects.equals(symbol, amnt.symbol) &&
                Objects.equals(valuesByCode, amnt.valuesByCode) &&
                Objects.equals(payload, amnt.payload);
    }

    @Override
    public int hashCode() {

        return Objects.hash(scale, symbol, valuesByCode, payload);
    }

    public Amnt clone(Function<String, Boolean> filter) {
        Amnt amnt = clone();
        valuesByCode.keySet().forEach(v -> {
            if (!filter.apply(v)) {
                amnt.valuesByCode.remove(v);
            }
        });
        return amnt;
    }

    @Override
    public Amnt clone() {
        return new Amnt(symbol, scale, valuesByCode, payload).prcSrc(prcSrc);
    }

    public static Function<String, Boolean> with(String... columns) {
        return with(Arrays.asList(columns));
    }

    public static Function<String, Boolean> not(Function<String, Boolean> function) {
        return v -> !function.apply(v);
    }

    public static Function<String, Boolean> with(Collection<String> columns) {
        return columns::contains;
    }

    public static Function<String, Boolean> woDepoAndMargin() {
        return not(with(DEPO, MARGIN));
    }

    public static Function<String, Boolean> woDepo() {
        return not(depo());
    }

    public static Function<String, Boolean> woMargin() {
        return not(with(MARGIN));
    }

    public static Function<String, Boolean> depo() {
        return with(DEPO);
    }
}
