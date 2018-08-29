package org.kpa.util.algo;

import com.google.common.base.Preconditions;
import org.kpa.util.ChronoBased;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class FundingCalculator implements Consumer<Object> {
    private final String symbol;
    private TickDto lastLccyRate;
    private TickDto lastRccyRate;
    private TickDto lastPremiumIndex;
    private ZonedDateTime lastDateTime = ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault());
    private ZonedDateTime nextDateTime;
    private Consumer<Funding> fundingConsumer;
    private Consumer<FundingCalcState> stateConsumer;
    private final Map<String, Consumer<TickDto>> consumerMap = new TreeMap<>();

    public FundingCalculator(String symbol) {
        this.symbol = symbol;
        CcyPair pair = CcyPair.parseSpot(symbol);
        String leftCcyIRSymbol = "BMEX:" + pair.getCcyLeft() + "BON8H";
        String rightCcyIRSymbol = "BMEX:" + pair.getCcyRight() + "BON8H";
        String premiumSymbol = "BMEX:" + pair.getCcyLeft() + pair.getCcyRight() + "PI8H";
        consumerMap.put(leftCcyIRSymbol, tick -> lastLccyRate = tick);
        consumerMap.put(rightCcyIRSymbol, tick -> lastRccyRate = tick);
        consumerMap.put(premiumSymbol, tick -> lastPremiumIndex = tick);
    }

    public FundingCalculator onNewFunding(Consumer<Funding> fundingConsumer) {
        this.fundingConsumer = fundingConsumer;
        return this;
    }

    public FundingCalculator onNewState(Consumer<FundingCalcState> stateConsumer) {
        this.stateConsumer = stateConsumer;
        return this;
    }

    public static ZonedDateTime nearestFundingTime(ZonedDateTime currTime) {
        LocalDate ld = currTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        ZonedDateTime tm = ZonedDateTime.of(ld, LocalTime.MIN, ZoneId.of("UTC")).plus(4, ChronoUnit.HOURS);
        while (tm.isBefore(currTime)) {
            tm = tm.plus(8, ChronoUnit.HOURS);
        }
        return tm;
    }

    @Override
    public void accept(Object object) {
        if (object instanceof ChronoBased) {
            ChronoBased chrono = (ChronoBased) object;
            ZonedDateTime newTickTime = chrono.getLocalTime();
            if (nextDateTime == null) {
                nextDateTime = nearestFundingTime(newTickTime);
            }
            lastDateTime = newTickTime;
        }
        if (object instanceof TickDto) {
            TickDto tickDto = (TickDto) object;
            if (consumerMap.containsKey(tickDto.getSymbol())) {
                consumerMap.get(tickDto.getSymbol()).accept(tickDto);
                if (stateConsumer != null) {
                    stateConsumer.accept(new FundingCalcState(lastDateTime, lastPremiumIndex, lastLccyRate, lastRccyRate));
                }
            }
        }
        if (nextDateTime != null && lastDateTime.isAfter(nextDateTime)) {
            if (fundingConsumer != null) {
                fundingConsumer.accept(new Funding(nextDateTime, symbol, getFundingRate()));
            }
            nextDateTime = null;
        }
    }

    public void setState(FundingCalcState state) {
        lastPremiumIndex = state.lastPremiumIndex;
        lastRccyRate = state.lastLccyRate;
        lastLccyRate = state.lastLccyRate;
    }

    public double getFundingRate() {
        Preconditions.checkArgument(isComplete(), "Calculator is not complete: %s", this);
        double interestRate = (lastRccyRate.getPrice() - lastLccyRate.getPrice()) / 3;
        double premiumRate = lastPremiumIndex.getPrice();
        double v = premiumRate + Math.min(Math.max(interestRate - premiumRate, -0.0005), 0.0005);
        return new BigDecimal(v).setScale(6, RoundingMode.HALF_EVEN).doubleValue();
    }

    public boolean isComplete() {
        return lastPremiumIndex != null && lastRccyRate != null && lastLccyRate != null;
    }

    @Override
    public String toString() {
        return "FundingCalculator{" +
                "lastPremiumIndex=" + lastPremiumIndex +
                ", lastLccyRate=" + lastLccyRate +
                ", lastRccyRate=" + lastRccyRate +
                '}';
    }
}
