package org.kpa.util.telegram;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class OutputCompressorTest {
    @Test
    public void testOutputCompressor(){
        OutputCompressor compressor = new OutputCompressor(20);
        compressor.addStr(String.join("", Collections.nCopies(30, "1")));
        compressor.addStr(String.join("", Collections.nCopies(30, "2")));
        String lastStr = String.join("", Collections.nCopies(30, "3"));
        compressor.addStr(lastStr);
        System.out.println(compressor.getStr());
        Assert.assertEquals(lastStr,compressor.getLastMessage());
    }

}