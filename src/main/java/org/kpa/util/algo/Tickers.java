package org.kpa.util.algo;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.util.Precision;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Tickers {
    public static double bnncRate = .100;
    public static final String collateralCcy = "USD";
    public static final String BMEX_XBTUSD = "BMEX:XBTUSD";
    public static final String BFNX_BTCUSD = "BFNX:BTCUSD";
    public static final String KRKN_BTCUSD = "KRKN:BTCUSD";
    public static final String GDAX_BTCUSD = "GDAX:BTCUSD";
    public static final String QUNE_BTCUSD = "QUNE:BTCUSD";
    public static final String BNNC_BTCUSD = "BNNC:BTCUSD";
    public static final String BMEX_USDBON8H = "BMEX:USDBON8H";
    public static final String BMEX_XBTBON8H = "BMEX:XBTBON8H";
    public static final String BMEX_XBTUSDPI8H = "BMEX:XBTUSDPI8H";
    public static final SpotInstrument BNNC_BTCUSD_INSTR = new SpotInstrument(BNNC_BTCUSD, BigDecimal.valueOf(0.01));
    public static final BmexInstrument BMEX_XBTUSD_INSTR = new BmexInstrument(BMEX_XBTUSD, "BMEX", "XBT", "BTC", 8, BigDecimal.valueOf(0.5));
    public static final Tickers instance = new Tickers();

    public final ImmutableMap<String, Instrument> instrumentMap = new ImmutableMap.Builder<String, Instrument>()
            .put(BMEX_XBTUSD, BMEX_XBTUSD_INSTR)
            .put(BNNC_BTCUSD, BNNC_BTCUSD_INSTR)
            .put(BFNX_BTCUSD, new SpotInstrument(BFNX_BTCUSD, BigDecimal.valueOf(0.1)))
            .put(KRKN_BTCUSD, new SpotInstrument(KRKN_BTCUSD, BigDecimal.valueOf(0.1)))
            .put(GDAX_BTCUSD, new SpotInstrument(GDAX_BTCUSD, BigDecimal.valueOf(0.01)))
            .build();

    private final Map<String, Function<Double, Double>> feeMap = new LinkedHashMap<>();
    private final Map<String, Function<Double, Double>> feeMakeMap = new LinkedHashMap<>();
    private final Map<CcyPair, Map<String, Integer>> scalesByTicker = new LinkedHashMap<>();
    private final ImmutableMap<String, Function<Double, Double>> minSizes = new ImmutableMap.Builder<String, Function<Double, Double>>()
            .put(BMEX_XBTUSD, price -> Precision.round(1 / price, 8))
            .put(BNNC_BTCUSD, price -> Precision.round(10 / price, 8))
            .put(BFNX_BTCUSD, price -> Precision.round(10 / price, 8)) // https://support.bitfinex.com/hc/en-us/articles/115003283709-What-is-the-minimum-order-size-
            .put(KRKN_BTCUSD, price -> Precision.round(.002, 8)) // https://support.kraken.com/hc/en-us/articles/205893708-What-is-the-minimum-order-size-
            .put(GDAX_BTCUSD, price -> Precision.round(.001, 8)) // https://support.gdax.com/customer/portal/articles/2725970-trading-rules
            .build();

    public static BiFunction<Double, Double, Double> getSizeConverter(String ticker) {
        if (BFNX_BTCUSD.equals(ticker)
                || KRKN_BTCUSD.equals(ticker)
                || GDAX_BTCUSD.equals(ticker)
                || QUNE_BTCUSD.equals(ticker)
                || BNNC_BTCUSD.equals(ticker)) {
            return (size, price) -> size;
        } else if (BMEX_XBTUSD.equals(ticker)) {
            return (size, price) -> size / price;
        }
        throw new IllegalArgumentException("Unknown ticker " + ticker);
    }

    public static BiFunction<BidAsk, Boolean, Double> getLCcySizeConverter(String ticker) {
        if (BFNX_BTCUSD.equals(ticker)
                || KRKN_BTCUSD.equals(ticker)
                || GDAX_BTCUSD.equals(ticker)
                || QUNE_BTCUSD.equals(ticker)
                || BNNC_BTCUSD.equals(ticker)) {
            return (bidAsk, isBid) -> isBid ? bidAsk.getBidSize() : bidAsk.getAskSize();
        } else if (BMEX_XBTUSD.equals(ticker)) {
            return (bidAsk, isBid) -> isBid ? bidAsk.getBidSize() / bidAsk.getBid() : bidAsk.getAskSize() / bidAsk.getAsk();
        }
        throw new IllegalArgumentException("Unknown ticker " + ticker);
    }

    /**
     * @param ticker
     * @return (price, size)->resultQty converter
     */
    public static BiFunction<Double, Double, Double> toExchangeSizeConverter(String ticker) {
        if (BFNX_BTCUSD.equals(ticker)
                || KRKN_BTCUSD.equals(ticker)
                || GDAX_BTCUSD.equals(ticker)
                || QUNE_BTCUSD.equals(ticker)
                || BNNC_BTCUSD.equals(ticker)) {
            return (price, size) -> size;
        } else if (BMEX_XBTUSD.equals(ticker)) {
            return (price, size) -> BigDecimal.valueOf(size * price).setScale(0, RoundingMode.FLOOR).doubleValue();
        }
        throw new IllegalArgumentException("Unknown ticker " + ticker);
    }

    public static Function<Order, Double> getOrderToExchangeSizeConverter(String ticker) {
        return order -> {
            validate(order, order.qty.doubleValue());
            return toExchangeSizeConverter(ticker).apply(order.price, order.qty.doubleValue());
        };
    }

    private static double validate(Order order, Double orderQty) {
        Preconditions.checkArgument(orderQty > 0, "Order qty is invalid: %s. Order=%s", orderQty, order);
        return orderQty;
    }

    public Tickers() {
        this.feeMap.put(BMEX_XBTUSD, input -> input * .075 / 100);
        this.feeMap.put(BFNX_BTCUSD, input -> input * .200 / 100);
        this.feeMap.put(GDAX_BTCUSD, input -> input * .250 / 100);
        this.feeMap.put(KRKN_BTCUSD, input -> input * .260 / 100);
        this.feeMap.put(QUNE_BTCUSD, input -> input * .100 / 100);
        this.feeMap.put(BNNC_BTCUSD, input -> input * bnncRate / 100);
        this.feeMakeMap.put(BMEX_XBTUSD, input -> input * -.025 / 100);
        scalesByTicker.put(BMEX_XBTUSD_INSTR.getPair(), new ImmutableMap.Builder<String, Integer>().put("XBT", 8).put("USD", 8).put("BTC", 8).build());
        scalesByTicker.put(CcyPair.parseSpot(BFNX_BTCUSD), new ImmutableMap.Builder<String, Integer>().put("BTC", 8).put("USD", 8).build());
        scalesByTicker.put(CcyPair.parseSpot(GDAX_BTCUSD), new ImmutableMap.Builder<String, Integer>().put("BTC", 8).put("USD", 8).build());
        scalesByTicker.put(CcyPair.parseSpot(KRKN_BTCUSD), new ImmutableMap.Builder<String, Integer>().put("BTC", 8).put("USD", 8).build());
        scalesByTicker.put(CcyPair.parseSpot(QUNE_BTCUSD), new ImmutableMap.Builder<String, Integer>().put("BTC", 8).put("USD", 8).build());
        scalesByTicker.put(CcyPair.parseSpot(BNNC_BTCUSD), new ImmutableMap.Builder<String, Integer>().put("BTC", 8).put("USD", 8).build());
    }

    public double fee(String ticker, double qty, boolean make) {
        Function<Double, Double> function = (make ? feeMakeMap : feeMap).get(ticker);
        Preconditions.checkNotNull(function, "Not found feeMap for ticker: %s, make: %s", ticker, make);
        return function.apply(qty);
    }


    public int scale(CcyPair pair, String ccy) {
        try {
            return scalesByTicker.get(pair).get(ccy);
        } catch (Exception e) {
            throw new RuntimeException("Error getting scale for ccy " + ccy + " of ticker: " + pair, e);
        }
    }

    public static double minSize(String symbol, double price) {
        Function<Double, Double> sizeFunc = instance.minSizes.get(symbol);
        Preconditions.checkNotNull(sizeFunc, "Min.size function not found for %s", symbol);
        return sizeFunc.apply(price);
    }

    public static double toExchangeQty(String ticker, double qty, double price) {
        return Tickers.toExchangeSizeConverter(ticker).apply(price, qty);
    }

    public static double fromExchangeQty(String ticker, double price, double qty) {
        return getSizeConverter(ticker).apply(qty, price);
    }

    public interface Instrument {
        BigDecimal priceStep();

        CcyPair getPair();

        Amnt empty(String ccy, Function<String, Function<Double, Double>> prcSrc);

        Position add(Position position, Position newPos);

        Position buy(Position position, double qty, double price, double leverage);

        Position sell(Position position, double qty, double price, double leverage);

        Position fee(Position position, double fee);

        Position rebate(Position position, double fee);

        Position funding(Position position, double funding);

        Amnt getPosRefAmnt(Position position, double marketPrice);

        BigDecimal pnl(Position position, double marketPrice);

        BigDecimal fixedPnl(Position position, double marketPrice);

        Amnt getNotional(Position position);

        double getLeverage(Position position);
    }

}
