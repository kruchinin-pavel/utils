package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.math.BigDecimal.valueOf;
import static org.kpa.util.algo.Tickers.toExchangeQty;

public class Order {
    public static final String SLIPPAGE = "slippage";
    public static final String EXECUTED_AT = "executedAt";
    public static final String PRICE_EXECUTED = "priceExecuted";
    public static final String QTY_EXECUTED = "qtyExecuted";
    public static final String SUM_EXECUTED = "sumExecuted";

    private ZonedDateTime placedAt;
    private ZonedDateTime cancelled;
    private ZonedDateTime executedAt;
    private BidAsk executedBidAsk;

    private final double fee;
    private final Function<String, Function<Double, Double>> prcSrc;
    private BigDecimal executedQty = BigDecimal.ZERO;
    private boolean rejected;
    public final BidAsk bidAsk;
    public final boolean isBuy;
    private double executedPrice = -1;
    public final BigDecimal qty;
    public final double price;
    private Position positionSeen;
    public final double exchQty;
    public final String symbol;
    public final boolean make;
    public final boolean dependent;
    private BiConsumer<Order, BigDecimal> onExecuted;
    private Consumer<Order> onPull;

    @JsonCreator
    public Order(BidAsk bidAsk, boolean isBuy, double qty, Function<String, Function<Double, Double>> prcSrc, boolean make, boolean dependent) {
        Preconditions.checkNotNull(prcSrc, "prcSrc is null");
        Preconditions.checkArgument(!make || !dependent, "Can't be make dependent order.");
        this.dependent = dependent;
        this.make = make;
        this.symbol = bidAsk.getSymbol();
        this.bidAsk = bidAsk;
        this.isBuy = isBuy;
        this.qty = new BigDecimal("" + qty);
        this.fee = Tickers.instance.fee(symbol, qty, make);
        this.prcSrc = prcSrc;
        if (!make) {
            price = isBuy ? bidAsk.getAsk() : bidAsk.getBid();
        } else {
            BigDecimal step = Tickers.instance.instrumentMap.get(symbol).priceStep();
            Preconditions.checkNotNull(step, "step is null for %s", symbol);
            price = isBuy ? valueOf(bidAsk.getAsk()).subtract(step).doubleValue() :
                    valueOf(bidAsk.getBid()).add(step).doubleValue();
        }
        this.exchQty = toExchangeQty(symbol, qty, price);
    }

    public void place(ZonedDateTime placedAt) {
        this.placedAt = placedAt;
    }

    public boolean isPlaced() {
        return placedAt != null;
    }

    public BidAsk getExecutedBidAsk() {
        return executedBidAsk;
    }

    public ZonedDateTime getExecutedAt() {
        return executedAt;
    }

    public Order onExecuted(BiConsumer<Order, BigDecimal> onExecuted) {
        this.onExecuted = onExecuted;
        return this;
    }

    public Order onPull(Consumer<Order> onPull) {
        this.onPull = onPull;
        return this;
    }

    @JsonIgnore
    public Function<String, Function<Double, Double>> getPrcSrc() {
        return prcSrc;
    }

    public double getFee() {
        return fee;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public void setExecutedPrice(double executedPrice) {
        this.executedPrice = executedPrice;
    }

    public ZonedDateTime getPlacedAt() {
        return placedAt;
    }

    public void reject() {
        rejected = true;
    }

    public boolean isRejected() {
        return rejected;
    }

    public boolean executeAt(ZonedDateTime at, double qty, double price, BidAsk bidAsk) {
        executedQty = executedQty.add(valueOf(qty));
        executedPrice = price;
        executedAt = at;
        Preconditions.checkNotNull(bidAsk, "execution bidAsk not set: %s", this);
        this.executedBidAsk = bidAsk;
        if (onExecuted != null) onExecuted.accept(this, valueOf(qty));
        return isExecuted();
    }


    public double getExecutedPrice() {
        Preconditions.checkArgument(executedPrice > 0);
        return executedPrice;
    }

    public boolean isExecuted() {
        return executedQty.equals(qty);
    }

    private Position position;

    public Position getPosition() {
        if (position == null && isExecuted()) {
            position = getPosition(getExecutedPrice());
        }
        return position;
    }

    public Position getPosition(BidAsk bidAsk) {
        double executionPrice = price;
        if (!make) {
            executionPrice = isBuy ? bidAsk.getAsk() : bidAsk.getBid();
        }
        return getPosition(executionPrice);
    }

    public Position getPosition(double executionPrice) {
        Position position;
        Position flat = Position.flat(symbol, prcSrc);
        if (isBuy) {
            position = flat.buy(exchQty, executionPrice).fee(fee);
        } else {
            position = flat.sell(exchQty, executionPrice).fee(fee);
        }
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Position getPositionSeen() {
        if (positionSeen == null) {
            positionSeen = getPosition(price);
        }
        return positionSeen;
    }

    public double slippagePnl() {
        return slippage().pnl(0.).doubleValue();
    }

    private Position slippage;

    public Position slippage() {
        if (slippage == null && isExecuted()) {
            slippage = getPositionSeen().substract(getPosition());
            Preconditions.checkArgument(slippage.isFlat());
            Preconditions.checkArgument(slippage.pnl(0).doubleValue() <= 0, "Slippage pnl >0: %s. Order=%s",
                    new Object() {
                        @Override
                        public String toString() {
                            return slippage.pnlStr();
                        }
                    }, this);
        }
        return slippage;
    }

    @Override
    public String toString() {
        return "Order{" +
                (isBuy ? "buy" : "sell") +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                '}';
    }


    public Position getSlippage() {
        return slippage;
    }

    public void setSlippage(Position slippage) {
        this.slippage = slippage;
    }

    public Map<String, Object> toCsv() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("placedAt", getPlacedAt());
        ret.put("ticker", symbol);
        ret.put("side", isBuy() ? "buy" : "sell");
        ret.put("price", price);
        ret.put("fee", -getFee());
        Position position = getPositionSeen();
        ret.put("qty", position.getQtyAmnt().total().toPlainString());
        ret.put("exch.qty", exchQty);
        ret.put("sum", position.getSumAmnt().total().toPlainString());
        ret.put(PRICE_EXECUTED, 0);
        ret.put(EXECUTED_AT, 0);
        ret.put(SLIPPAGE, 0);
        ret.put(QTY_EXECUTED, 0);
        ret.put(SUM_EXECUTED, 0);
//        ret.put("source", bidAsk.getSrcFile());
//        ret.put("exec.source", "");
        if (isExecuted()) {
            position = getPosition();
            ret.put(PRICE_EXECUTED, getExecutedPrice());
            ret.put(EXECUTED_AT, getExecutedBidAsk().getLocalTime());
            ret.put(SLIPPAGE, slippage().pnl(0));
            ret.put(QTY_EXECUTED, position.getQtyAmnt().total().doubleValue());
            ret.put(SUM_EXECUTED, position.getSumAmnt().total().doubleValue());
//            ret.put("exec.source", lastBidAsk.getSrcFile());
        }
        return ret;
    }

    public void pull(ZonedDateTime cancelledAt) {
        Preconditions.checkArgument(cancelled == null, "Trade is already cancelled: %s", this);
        cancelled = cancelledAt;
        Preconditions.checkNotNull(onPull);
        onPull.accept(this);
    }

    public boolean isCancelled() {
        return cancelled != null;
    }

    public boolean isInactive() {
        return isRejected() || isExecuted() || isCancelled();
    }
}
