package org.kpa.util;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.supercsv.prefs.CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE;

public class CsvWriteTest {

    public static final String FILE_NAME = "build/CsvWriteTest/test.csv.gz";

    @Test
    public void testGzip() throws IOException {
        Files.createDirectories(Paths.get("build/CsvWriteTest/"));
        Map<String, Object> expected = new ImmutableMap.Builder<String, Object>()
                .put("col1", "val1")
                .put("col2", "val2").build();
        try (CloseableFlushableConsumer<Map<String, Object>> csv = Csv.writeTo(FILE_NAME,
                EXCEL_NORTH_EUROPE_PREFERENCE)) {
            csv.accept(expected);
        }

        Map<String, String> restored = Csv.readFrom(FILE_NAME, EXCEL_NORTH_EUROPE_PREFERENCE).next();

        assertEquals(expected, restored);
    }

}