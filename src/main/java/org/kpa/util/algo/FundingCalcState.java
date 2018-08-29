package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

public class FundingCalcState {
    final ZonedDateTime lastTickTime;
    final TickDto lastPremiumIndex;
    final TickDto lastLccyRate;
    final TickDto lastRccyRate;

    @JsonCreator
    public FundingCalcState(@JsonProperty("lastTickTime") ZonedDateTime lastTickTime,
                            @JsonProperty("lastPremiumIndex") TickDto lastPremiumIndex,
                            @JsonProperty("lastLccyRate") TickDto lastLccyRate,
                            @JsonProperty("lastRccyRate") TickDto lastRccyRate) {
        this.lastTickTime = lastTickTime;
        this.lastPremiumIndex = lastPremiumIndex;
        this.lastLccyRate = lastLccyRate;
        this.lastRccyRate = lastRccyRate;
    }
}
