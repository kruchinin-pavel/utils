package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kpa.util.ChronoBased;
import org.kpa.util.FileRef;

import java.time.ZonedDateTime;

public class TickDto implements ChronoBased<TickDto>, CryptoDto {

    private ZonedDateTime localDateTime;
    private ZonedDateTime timestamp;
    private String symbol;
    private double price;
    private double qty;
    @JsonIgnore
    private FileRef srcFile;

    @JsonCreator
    public TickDto(@JsonProperty("symbol") String symbol,
                   @JsonProperty("price") double price,
                   @JsonProperty("qty") double qty,
                   @JsonProperty("localDateTime") ZonedDateTime localDateTime,
                   @JsonProperty("timestamp") ZonedDateTime timestamp,
                   @JsonProperty("srcFile") FileRef srcFile) {
        this.localDateTime = localDateTime;
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.price = price;
        this.qty = qty;
        this.srcFile = srcFile;
    }

    @Override
    public ZonedDateTime getLocalTime() {
        return localDateTime;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public double getQty() {
        return qty;
    }

    public void setQty(double qty) {
        this.qty = qty;
    }

    public FileRef getSrcFile() {
        return srcFile;
    }

    @Override
    public String toString() {
        return "TickDto{" +
                "localDateTime='" + localDateTime + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", qty=" + qty +
                '}';
    }

}
