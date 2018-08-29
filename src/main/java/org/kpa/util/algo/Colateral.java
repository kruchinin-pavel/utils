package org.kpa.util.algo;

import java.util.ArrayList;
import java.util.List;

public class Colateral {
    private final String collateralCcy;
    private final Amnt startinAmount;
    private final List<Position> positions = new ArrayList<>();

    public Colateral(String collateralCcy, Amnt startinAmount) {
        this.collateralCcy = collateralCcy;
        this.startinAmount = startinAmount;
    }

}
