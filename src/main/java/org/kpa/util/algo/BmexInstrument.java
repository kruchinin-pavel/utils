package org.kpa.util.algo;

import com.google.common.base.Preconditions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.kpa.util.algo.Amnt.*;

public class BmexInstrument implements Tickers.Instrument {
    private final CcyPair pair;
    private final String marginCcy;
    private final int margingScale;
    private static final Function<String, Boolean> withMargin = with(MARGIN);
    private static final Function<String, Boolean> allPlFltr = not(with(MARGIN, DEPO));
    private static final Function<String, Boolean> fxdPlFltr = not(with(MARGIN, PNL_UNREALIZED, DEPO));
    private final BigDecimal priceStep;

    @Override
    public BigDecimal priceStep() {
        return priceStep;
    }

    @Override
    public CcyPair getPair() {
        return pair;
    }

    public BmexInstrument(String symbol, String exchange, String ccyLeft, String marginCcy, int margingCcyScale, BigDecimal priceStep) {
        this.priceStep = priceStep;
        pair = new CcyPair(symbol, exchange, ccyLeft, marginCcy, "");
        this.marginCcy = marginCcy;
        this.margingScale = margingCcyScale;
    }

    @Override
    public Position buy(Position position, double qty, double price, double leverage) {
        Preconditions.checkArgument(leverage >= 1., "Leverage can't be <1: %s", leverage);
        Preconditions.checkArgument(qty > 0 && price > 0);
        position = markup(position, price);
        return markup(add(position, qty, qty / leverage / price), price);
    }

    @Override
    public Position sell(Position position, double qty, double price, double leverage) {
        Preconditions.checkArgument(leverage >= 1., "Leverage can't be <1: %s", leverage);
        Preconditions.checkArgument(qty > 0 && price > 0);
        position = markup(position, price);
        return markup(add(position, -qty, qty / leverage / price), price);
    }

    private Position markup(Position position, double price) {
        Amnt qtyAmnt = position.getQtyAmnt();
        Amnt marginAmnt = position.get(marginCcy);
        if (!qtyAmnt.isFlat()) {
            Double prevPrice = qtyAmnt.getProp(MARKUP_PRICE).doubleValue();
            Amnt refQtyAmnt = qtyAmnt.toRef(marginCcy, 1 / prevPrice);
            Preconditions.checkNotNull(prevPrice, "Previous markup price not set: %s", qtyAmnt);
            double delta = (price - prevPrice) / prevPrice;
            BigDecimal val = refQtyAmnt.total().multiply(BigDecimal.valueOf(delta)).setScale(marginAmnt.getScale(), RoundingMode.HALF_EVEN);
            marginAmnt = marginAmnt.rcv(val.doubleValue(), PNL_UNREALIZED);
            position = position.put(marginAmnt);
        }
        position = position.put(qtyAmnt.setProp(price, MARKUP_PRICE));
        return position;
    }

    @Override
    public Position fee(Position p, double fee) {
        Preconditions.checkArgument(fee >= 0, "Fee invalid: %s", fee);
        return p.put(p.get(marginCcy).pay(Math.max(fee, p.get(marginCcy).getMinVal().doubleValue()), FEE));
    }

    @Override
    public Position rebate(Position p, double rebate) {
        Preconditions.checkArgument(rebate >= 0, "Rebait invalid: %s", rebate);
        return p.put(p.get(marginCcy).rcv(Math.max(rebate, p.get(marginCcy).getMinVal().doubleValue()), REBATE));
    }

    @Override
    public Position funding(Position position, double funding) {
        return position.put(empty("BTC", position.prcSrc()).pay(funding, FUNDING));
    }

    @Override
    public Amnt getPosRefAmnt(Position position, double marketPrice) {
        return position.getAmnts().stream()
                .map(amnt -> {
                    if (pair.getCcyLeft().equals(amnt.getSymbol())) {
                        return amnt.toRef(marginCcy, d -> 1 / marketPrice);
                    } else if (marginCcy.equals(amnt.getSymbol())) {
                        return amnt.set(0, POS);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .reduce(Amnt::add).orElse(empty(marginCcy, position.prcSrc()));
    }


    private Position add(Position position, double qty, double sum) {
        return position.add(
                position.clone(position.get(pair.getCcyLeft()).flatCopy().rcv(qty), position.get(marginCcy).flatCopy().rcv(sum, MARGIN).pay(sum, DEPO)));
    }

    @Override
    public Position add(Position p1, Position p2) {
        Preconditions.checkArgument(p1.getSymbol().equals(p2.getSymbol()), "Tickers are not equal: %s and %s", p1, p2);
        CcyPair pair = CcyPair.parseSpot(p1.getSymbol());
        BigDecimal markupPrice = p2.getQtyAmnt().getProp(MARKUP_PRICE);
        if (markupPrice != null) {
            p1 = markup(p1, markupPrice.doubleValue());
        }

        Map<String, Amnt> amntMap = new LinkedHashMap<>();
        p1.getAmnts().forEach(v -> amntMap.put(v.getSymbol(), v));
        p2.getAmnts().forEach(amnt -> amntMap.compute(amnt.getSymbol(),
                (k, v) -> v == null ? amnt : v.add(amnt)));

        BigDecimal p1Qty = p1.get(pair.getCcyLeft()).pos();
        BigDecimal p2Qty = p2.get(pair.getCcyLeft()).pos();
        if (p1Qty.compareTo(ZERO) != 0 && p1Qty.signum() == -p2Qty.signum()) {
            Amnt mgnAmnt = p1.get(marginCcy);
            BigDecimal p1Sum = mgnAmnt.get(MARGIN);
            BigDecimal p2Sum = p2.get(marginCcy).get(MARGIN);
            BigDecimal p1UnrPlSum = mgnAmnt.get(PNL_UNREALIZED);
            BigDecimal resQty = p1Qty.add(p2Qty);
            BigDecimal resSum;
            BigDecimal resFixPnl;
            if (resQty.signum() == p2Qty.signum()) {
                resSum = p2Sum.multiply(resQty.divide(p2Qty, 8, RoundingMode.HALF_EVEN))
                        .setScale(mgnAmnt.getScale(), RoundingMode.HALF_EVEN);
                resFixPnl = p1UnrPlSum;
            } else if (resQty.signum() == p1Qty.signum()) {
                BigDecimal rate = resQty.divide(p1Qty, 8, RoundingMode.HALF_EVEN);
                resSum = p1Sum.multiply(rate)
                        .setScale(mgnAmnt.getScale(), RoundingMode.HALF_EVEN);
                resFixPnl = p1UnrPlSum.multiply(ONE.subtract(rate))
                        .setScale(mgnAmnt.getScale(), RoundingMode.HALF_EVEN);
            } else {
                resSum = ZERO;
                resFixPnl = p1UnrPlSum;
            }
            amntMap.compute(pair.getCcyLeft(), (s, amnt) -> amnt.set(resQty.doubleValue(), POS));
            amntMap.compute(marginCcy, (s, amnt) -> amnt.set(Math.abs(resSum.doubleValue()), MARGIN)
                    .set(-Math.abs(resSum.doubleValue()), DEPO)
                    .rcv(resFixPnl.doubleValue(), PNL)
                    .pay(resFixPnl.doubleValue(), PNL_UNREALIZED));
        }
        return p1.copyFlat().put(amntMap.values().toArray(new Amnt[0]));
    }

    @Override
    public BigDecimal pnl(Position p, double marketPrice) {
        return impPnl(markup(p, marketPrice), marketPrice, allPlFltr);
    }

    @Override
    public BigDecimal fixedPnl(Position p, double marketPrice) {
        return impPnl(p, marketPrice, fxdPlFltr);
    }

    private BigDecimal impPnl(Position p, double marketPrice, Function<String, Boolean> filter) {
        Amnt pnl = p.getAmnts().stream().map(amnt -> {
            if (!pair.getCcyLeft().equals(amnt.getSymbol())) {
                return amnt.clone(filter).toRef(Tickers.collateralCcy, sz -> marketPrice);
            }
            return null;
        }).filter(Objects::nonNull).reduce(Amnt::add).orElse(empty(Tickers.collateralCcy, p.prcSrc()));
        return pnl.total();
    }

    @Override
    public Amnt getNotional(Position position) {
        return position.get(marginCcy).clone(withMargin).toRef(Tickers.collateralCcy);
    }

    @Override
    public Amnt empty(String ccy, Function<String, Function<Double, Double>> prcSrc) {
        return new Amnt(ccy, Tickers.instance.scale(pair, ccy), null, null).prcSrc(prcSrc);
    }

    @Override
    public double getLeverage(Position position) {
        BigDecimal depo = position.getAmnts().stream()
                .map(v -> {
                    BigDecimal bigDecimal = v.get(DEPO);
                    if (bigDecimal == null) return null;
                    position.prcSrc().apply(v.getSymbol() + "USD").apply(bigDecimal.doubleValue());
                    return bigDecimal;
                })
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add).orElse(ZERO);
        Function<Double, Double> prcSrc = position.prcSrc().apply("BTCUSD");
        BigDecimal val = position.getPosRefAmnt().total();
        val = val.multiply(BigDecimal.valueOf(prcSrc.apply(val.doubleValue())));
        val = val.add(position.getAmnts().stream()
                .filter(v -> !"XBT".equals(v.getSymbol()))
                .map(v -> v.total(account -> !DEPO.equals(account)))
                .reduce(BigDecimal::add).get());
        return depo.divide(val, 8, RoundingMode.HALF_EVEN).doubleValue();
    }
}
