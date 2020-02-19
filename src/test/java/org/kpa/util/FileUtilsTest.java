package org.kpa.util;

import com.google.common.base.Joiner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.kpa.util.Utils.localHostAndUserName;
import static org.kpa.util.Utils.stream;

public class FileUtilsTest {
    @Test
    public void doTestTarGz() throws IOException {
        List<String> files = FileUtils.list("src/test/resources/output_store.tar.gz");
        Assert.assertEquals(2, files.size());
        InputStream is = FileUtils.getInputStream(files.get(0));
        System.out.println(Joiner.on("\n").join(files));
    }

    @Test
    public void doTestgLocalHost() {
        System.out.println(localHostAndUserName());
    }

    @Test
    public void testReadGz() {
        List<Map<String, String>> lst = stream(() -> Utils.readCsv("src/test/resources/5_trade_signals.csv.gz")).collect(Collectors.toList());
        assertTrue(lst.size() > 0);
    }

    @Test
    public void testSlice(){
        List<List<Integer>> slices = Utils.slice(Stream.of(1, 2, 3, 4, 5, 6, 7), 3);
        assertEquals(3,slices.size());
        assertEquals(3,slices.get(0).size());
        assertEquals(3,slices.get(1).size());
        assertEquals(1,slices.get(2).size());
    }
}