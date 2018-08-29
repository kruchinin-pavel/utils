package org.kpa.util.algo;

import org.kpa.util.ChronoBased;
import org.kpa.util.Json;
import org.kpa.util.MultiIterable;
import org.kpa.util.Utils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.kpa.util.FileUtils.list;

public class BidAsks {

    public static Stream<BidAsk> stream(List<String> fileOrDirectory) {
        return StreamSupport.stream(iterable(fileOrDirectory).spliterator(), false);
    }

    public static Iterable<BidAsk> iterable(Iterable<String> fileOrDirectory) {
        return MultiIterable.create(list(fileOrDirectory).stream()
                .map(v -> Json.iterableFile(v, BidAsk.class))
                .collect(Collectors.toList()));
    }

    public static Iterable<ChronoBased> toChrono(Iterable<String> fileOrDirectory) {
        return Utils.toChrono(iterable(fileOrDirectory));
    }


}

