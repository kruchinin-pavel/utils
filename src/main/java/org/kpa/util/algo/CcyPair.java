package org.kpa.util.algo;

import com.google.common.base.Splitter;

import java.util.Iterator;
import java.util.Objects;

public class CcyPair {
    private final String exchange;
    private final String ccyLeft;
    private final String ccyRight;
    private final String modifier;
    private final String symbol;

    public CcyPair(String symbol, String exchange, String ccyLeft, String ccyRight, String modifier) {
        this.symbol = symbol;
        this.exchange = exchange;
        this.ccyLeft = ccyLeft;
        this.ccyRight = ccyRight;
        this.modifier = modifier;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CcyPair pair = (CcyPair) o;
        return Objects.equals(symbol, pair.symbol);
    }

    @Override
    public int hashCode() {

        return Objects.hash(symbol);
    }

    public String getExchange() {
        return exchange;
    }

    public String getCcyLeft() {
        return ccyLeft;
    }

    public String getCcyRight() {
        return ccyRight;
    }

    @Override
    public String toString() {
        return symbol;
    }

    public static CcyPair parseSpot(String val) {
        Iterator<String> iter = Splitter.on(":").split(val).iterator();
        String exchange = iter.next();
        String ccyPair = iter.next();
        String modifier = "";
        if (ccyPair.length() > 6) {
            modifier = ccyPair.substring(6);
        }
        return new CcyPair(val, exchange, ccyPair.substring(0, 3), ccyPair.substring(3, 6), modifier);
    }
}
