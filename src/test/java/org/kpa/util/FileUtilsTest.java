package org.kpa.util;

import com.google.common.base.Joiner;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FileUtilsTest {
    @Test
    public void doTestTarGz() throws IOException {
        List<String> files = FileUtils.list("src/test/resources/output_store.tar.gz");
        Assert.assertEquals(2, files.size());
        InputStream is = FileUtils.getInputStream(files.get(0));
        System.out.println(Joiner.on("\n").join(files));

    }

}