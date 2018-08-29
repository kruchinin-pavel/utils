package org.kpa.util.algo;

import com.google.common.base.Preconditions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.kpa.util.algo.Amnt.*;

public class SpotInstrument implements Tickers.Instrument {
    private final String symbol;
    private final CcyPair pair;
    private final BigDecimal priceStep;
    private static final Function<String, Boolean> POS_FILTER = with(POS);

    public SpotInstrument(String symbol, BigDecimal priceStep) {
        this.symbol = symbol;
        pair = CcyPair.parseSpot(symbol);
        this.priceStep = priceStep;
    }

    @Override
    public BigDecimal priceStep() {
        return priceStep;
    }

    @Override
    public CcyPair getPair() {
        return pair;
    }

    @Override
    public Position buy(Position position, double qty, double price, double leverage) {
        Preconditions.checkArgument(leverage == 1., "Leverage for spot must be 1.: %s. Pos: %s", leverage, position);
        Preconditions.checkArgument(qty > 0 && price > 0);
        return add(position, qty, -qty * price);
    }

    @Override
    public Position sell(Position position, double qty, double price, double leverage) {
        Preconditions.checkArgument(leverage == 1., "Leverage for spot must be 1.: %s. Pos: %s", leverage, position);
        Preconditions.checkArgument(qty > 0 && price > 0);
        return add(position, -qty, qty * price);
    }

    @Override
    public Position fee(Position position, double fee) {
        Preconditions.checkArgument(fee >= 0, "Fee invalid: %s", fee);
        Amnt qtyAmnt = position.get(pair.getCcyLeft());
        Position ret = position.clone(qtyAmnt.pay(Math.max(fee, qtyAmnt.getMinVal().doubleValue()), FEE), position.get(pair.getCcyRight()));
        ret.prcSrc(position.prcSrc());
        return ret;
    }

    @Override
    public Position rebate(Position position, double rebate) {
        Preconditions.checkArgument(rebate >= 0, "Rebate invalid: %s", rebate);
        Amnt qtyAmnt = position.get(pair.getCcyLeft());
        Position ret = position.clone(qtyAmnt.rcv(Math.max(rebate, qtyAmnt.getMinVal().doubleValue()), REBATE), position.get(pair.getCcyRight()));
        ret.prcSrc(position.prcSrc());
        return ret;
    }

    @Override
    public Position funding(Position position, double funding) {
        Position ret = position.clone(position.get(pair.getCcyLeft()).pay(funding, FUNDING), position.get(pair.getCcyRight()));
        ret.prcSrc(position.prcSrc());
        return ret;
    }

    @Override
    public Amnt getPosRefAmnt(Position position, double marketPrice) {
        return position.get(pair.getCcyLeft());
    }

    @Override
    public Amnt empty(String ccy, Function<String, Function<Double, Double>> prcSrc) {
        return new Amnt(ccy, Tickers.instance.scale(pair, ccy), null, null).prcSrc(prcSrc);
    }

    private Position add(Position position, double qty, double sum) {
        Preconditions.checkArgument(!(Math.signum(qty) == Math.signum(sum)), "qty.signum()!= total.signum(): qty=%s, total=%s", qty, sum);
        Position newPos = position.clone(
                position.get(pair.getCcyLeft()).flatCopy().rcv(qty),
                position.get(pair.getCcyRight()).flatCopy().rcv(sum));
        BigDecimal pnl = BigDecimal.ZERO;
        if (!position.getQtyAmnt().isFlatPos() && position.getQtyAmnt().pos().signum() != newPos.getQtyAmnt().pos().signum()) {
            BigDecimal curBalPrice = balancePrice(position);
            BigDecimal newBalPrice = balancePrice(newPos);
            if (position.getQtyAmnt().pos().abs().doubleValue() < newPos.getQtyAmnt().pos().abs().doubleValue()) {
                pnl = newBalPrice.subtract(curBalPrice).multiply(position.getQtyAmnt().pos());
            } else {
                pnl = newBalPrice.subtract(curBalPrice).multiply(newPos.getQtyAmnt().pos().negate());
            }
            pnl = pnl.setScale(position.getSumAmnt().getScale(), RoundingMode.HALF_EVEN);
        }
        Position add = add(position, newPos, false);
        return position.clone(add.getQtyAmnt(),
                add.getSumAmnt()
                        .rcv(pnl.doubleValue(), PNL)
                        .pay(pnl.doubleValue(), POS)
        );
    }

    @Override
    public Position add(Position p1, Position p2) {
        return add(p1, p2, true);
    }

    private Position add(Position p1, Position p2, boolean extractFixedPnl) {
        Amnt qtyPos = p2.getQtyAmnt();
        Amnt sumPos = p2.getSumAmnt();
        Position sum;
        if (extractFixedPnl && qtyPos.get(POS).compareTo(BigDecimal.ZERO) != 0) {
            sum = add(p1, qtyPos.get(POS).doubleValue(), sumPos.get(POS).doubleValue());
        } else {
            sum = null;
        }
        Preconditions.checkArgument(symbol.equals(p2.getSymbol()), "Tickers are not equal: %s and %s", p1, p2);
        List<Amnt> amnts = p2.getAmnts();
        amnts.addAll(p1.getAmnts());
        Amnt[] res = amnts.stream().collect(Collectors.groupingBy(Amnt::getSymbol,
                Collectors.reducing(Amnt::add))).values().stream()
                .map(Optional::get)
                .map(amnt -> {
                    if (sum != null) {
                        if (qtyPos.getSymbol().equals(amnt.getSymbol())) {
                            return amnt.set(sum.getQtyAmnt().get(POS).doubleValue(), POS);
                        } else if (sumPos.getSymbol().equals(amnt.getSymbol())) {
                            return amnt.set(sum.getSumAmnt().get(POS).doubleValue(), POS)
                                    .set(sum.getSumAmnt().get(PNL).doubleValue(), PNL);
                        }
                    }
                    return amnt;
                })
                .toArray(Amnt[]::new);
        return p1.copyFlat().put(res);
    }

    private BigDecimal balancePrice(Position pos) {
        if (pos.getQtyAmnt().isFlatPos()) {
            return BigDecimal.ZERO;
        }
        return pos.getSumAmnt().pos().divide(pos.getQtyAmnt().pos(), 8, RoundingMode.HALF_EVEN).negate();
    }

    @Override
    public BigDecimal pnl(Position p, double marketPrice) {
        Amnt pnl = p.getAmnts().stream().map(amnt -> {
            Preconditions.checkArgument(pair.getCcyLeft().equals(amnt.getSymbol()) || pair.getCcyRight().equals(amnt.getSymbol()),
                    "Unsupported ccy: %s in for pnl %", amnt.getSymbol(), p);
            return amnt.toRef(Tickers.collateralCcy, sz -> marketPrice);
        }).filter(Objects::nonNull).reduce(Amnt::add).orElse(empty(Tickers.collateralCcy, p.prcSrc()));
        return pnl.total();
    }

    @Override
    public BigDecimal fixedPnl(Position position, double marketPrice) {
        return position.getSumAmnt().rest().add(
                position.getQtyAmnt().rest().multiply(BigDecimal.valueOf(marketPrice))).stripTrailingZeros();
    }


    @Override
    public Amnt getNotional(Position position) {
        return position.get(position.getPair().getCcyRight()).toRef(Tickers.collateralCcy).clone(POS_FILTER).toRef(Tickers.collateralCcy);
    }

    @Override
    public double getLeverage(Position position) {
        return 1.;
    }
}
