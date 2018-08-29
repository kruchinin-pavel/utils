package org.kpa.util.algo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kpa.util.FileRef;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TickDto.class, name = "Tick"),
        @JsonSubTypes.Type(value = BidAsk.class, name = "BidAsk"),
        @JsonSubTypes.Type(value = Order.class, name = "Order"),
        @JsonSubTypes.Type(value = Position.class, name = "Position"),
        @JsonSubTypes.Type(value = FileRef.class, name = "FileRef"),
        @JsonSubTypes.Type(value = Amnt.class, name = "Amnt"),
        @JsonSubTypes.Type(value = Portfolio.class, name = "Portfolio")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface CryptoDto {
}
