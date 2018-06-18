package org.kpa.util.interop.msgs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Bye.class, name = "Bye"),
        @JsonSubTypes.Type(value = Close.class, name = "Close"),
        @JsonSubTypes.Type(value = TestRequest.class, name = "TestRequest"),
        @JsonSubTypes.Type(value = Heartbeat.class, name = "Heartbeat")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Message {
}

