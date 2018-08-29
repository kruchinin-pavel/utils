package org.kpa.util.algo;


import org.kpa.util.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Fundings {
    private static final DateTimeFormatter ldtF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static Stream<Funding> streamCsv(String fileName) {
        return StreamSupport.stream(csv(fileName).spliterator(), false);
    }

    // https://www.bitmex.com/app/fundingHistory
    public static Iterable<Funding> csv(List<String> fileNames) {
        return MultiIterable.create(FileUtils.list(fileNames).stream()
                .map(fileName -> Utils.sorted(Csv.fromCsv(fileName, Fundings::build))).collect(Collectors.toList()));
    }

    public static Iterable<Funding> csv(String... fileNames) {
        return csv(Arrays.asList(fileNames));
    }

    public static Funding build(Map<String, String> vals, FileRef ref) {
        String timestamp = vals.get("timestamp");
        String symbol = vals.get("symbol");
        String fundingRate = vals.get("fundingRate");
        if (timestamp == null || symbol == null || fundingRate == null) return null;
        return new Funding(
                LocalDateTime.parse(timestamp, ldtF).atZone(ZoneId.of("UTC")),
                "BMEX:" + symbol,
                Double.parseDouble(fundingRate));
    }

    public static Iterable<ChronoBased> toChrono(List<String> fileNames) {
        return Utils.toChrono(csv(fileNames));
    }
}