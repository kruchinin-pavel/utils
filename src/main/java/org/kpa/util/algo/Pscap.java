package org.kpa.util.algo;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.commons.io.FilenameUtils;
import org.kpa.util.Csv;
import org.kpa.util.FileRef;
import org.kpa.util.FileUtils;
import org.kpa.util.MultiIterable;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.kpa.util.FileUtils.list;
import static org.kpa.util.algo.Tickers.*;

public class Pscap {
    private static final DateTimeFormatter ldtBackupFileF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static Iterable<BidAsk> suvorBidAsks(String... filesOrDirectories) {
        return bidAsks(Pscap::tickerName, filesOrDirectories);
    }

    public static Iterable<BidAsk> bidAsks(Function<String, CcyPair> fileNameToTicker, String... filesOrDirectories) {
        return MultiIterable.create(list(filesOrDirectories).stream()
                .map(v -> {
                    CcyPair ticker = fileNameToTicker.apply(v);
                    if (ticker == null) return null;
                    return Csv.<BidAsk>fromCsv(v, Pscap.bidAskBldr(ticker.toString()));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    public static Iterable<TickDto> ticks(Function<String, CcyPair> tickMapper, String... fileOrDirectory) {
        return MultiIterable.create(list(fileOrDirectory).stream()
                .map(v -> {
                    CcyPair tickerName = tickMapper.apply(v);
                    if (tickerName == null) return null;
                    return Csv.<TickDto>fromCsv(v, Pscap.tickBldr(tickerName.toString()));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }


    public static CcyPair tickerName(String path) {
        if (!path.contains("_")) return null;
        String baseName = FilenameUtils.getBaseName(Paths.get(path).getFileName().toString());
        Iterator<String> iter = Splitter.on(CharMatcher.anyOf("_.")).omitEmptyStrings().trimResults().split(baseName).iterator();
        return CcyPair.parseSpot(iter.next() + ":" + iter.next());
    }

    private static BiFunction<Map<String, String>, FileRef, BidAsk> bidAskBldr(String ticker) {
        return (map, ref) -> new BidAsk(ticker,
                getFromUtc(map.get("Time")),
                null,//getFromUtc(map.get("MsgTime")),
                Double.parseDouble(map.get("Bid")),
                Double.parseDouble(map.get("BidSize")),
                Double.parseDouble(map.get("Ask")),
                Double.parseDouble(map.get("AskSize")),
                ref
        );
    }

    private static BiFunction<Map<String, String>, FileRef, TickDto> tickBldr(String ticker) {
        return (map, ref) -> new TickDto(
                ticker,
                Double.parseDouble(map.get("Price")),
                Double.parseDouble(map.get("Size")),
                getFromUtc(MoreObjects.firstNonNull(map.get("Time"), map.get("SrvTime"))),
                getFromUtc(MoreObjects.firstNonNull(map.get("TradeTime"), map.get("MsgTime"))),
                ref
        );
    }


    public static ZonedDateTime getFromUtc(String val) {
        try {
            if (val == null) return null;
            return LocalDateTime.parse(val, ldtBackupFileF)
                    .atZone(ZoneId.of("UTC"));
        } catch (Exception e) {
            throw new RuntimeException("Error parsing date from [" + val + "]: " + e.getMessage(), e);
        }
    }


    public static Iterable<BidAsk> romasBidAsks(String... strings) {
        return Pscap.bidAsks(Pscap::romasBidAskTickers, FileUtils.list(strings).toArray(new String[0]));
    }

    public static Iterable<TickDto> romasTicks(String... strings) {
        return Pscap.ticks(Pscap::romasTickTickers, FileUtils.list(strings).toArray(new String[0]));
    }

    public static CcyPair romasBidAskTickers(String path) {
        if (!path.contains("L1")) return null;
        String baseName = FilenameUtils.getBaseName(path);
        if (path.toLowerCase().contains("bitfinex")) {
            if (baseName.startsWith("tBTCUSD")) {
                return CcyPair.parseSpot(Tickers.BFNX_BTCUSD);
            }
        } else if (path.toLowerCase().contains("bitmex")) {
            if (baseName.toUpperCase().startsWith("XBTUSD")) {
                return CcyPair.parseSpot(BMEX_XBTUSD);
            }
        } else if (path.toLowerCase().contains("binance")) {
            if (baseName.toLowerCase().startsWith("btcusd")) {
                return CcyPair.parseSpot(Tickers.BNNC_BTCUSD);
            }
        }
        return null;
    }

    public static CcyPair romasTickTickers(String path) {
        if (!path.contains("trades")) return null;
        String baseName = FilenameUtils.getBaseName(path);
        if (path.toLowerCase().contains("bitmex")) {
            if (!baseName.startsWith(".")) {
                if (Splitter.on(".").split(baseName).iterator().next().equalsIgnoreCase("XBTUSD")) {
                    return CcyPair.parseSpot(BMEX_XBTUSD);
                }
                return null;
            }
            String name = Splitter.on(".").trimResults().omitEmptyStrings().split(baseName).iterator().next();
            if (name.equalsIgnoreCase("USDBON8H")) {
                return CcyPair.parseSpot(BMEX_USDBON8H);
            } else if (name.equalsIgnoreCase("XBTBON8H")) {
                return CcyPair.parseSpot(BMEX_XBTBON8H);
            } else if (name.equalsIgnoreCase("XBTUSDPI8H")) {
                return CcyPair.parseSpot(BMEX_XBTUSDPI8H);
            }
        } else if (path.toLowerCase().contains("binance")) {
            if (Splitter.on(".").split(baseName).iterator().next().equalsIgnoreCase("BTCUSDT")) {
                return CcyPair.parseSpot(BNNC_BTCUSD);
            }
        }
        return null;
    }

    public static Iterable<Funding> romasFunding(CcyPair ticker, String... files) {
        return () -> {
            final Iterator<TickDto> ticks = romasTicks(files).iterator();
            AtomicReference<Funding> nextFunding = new AtomicReference<>();
            final FundingCalculator calculator = new FundingCalculator(ticker.toString()).onNewFunding(nextFunding::set);

            return new Iterator<Funding>() {
                @Override
                public boolean hasNext() {
                    while (nextFunding.get() == null && ticks.hasNext()) {
                        calculator.accept(ticks.next());
                    }
                    return nextFunding.get() != null;
                }

                @Override
                public Funding next() {
                    hasNext();
                    Funding funding = nextFunding.get();
                    Preconditions.checkArgument(nextFunding.compareAndSet(funding, null));
                    return funding;
                }
            };
        };

    }
}
