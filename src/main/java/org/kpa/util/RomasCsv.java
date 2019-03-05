package org.kpa.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RomasCsv {

    private final List<String> columnNames;

    public RomasCsv(String colNameStr) {
        columnNames = Splitter.on(",").trimResults().splitToList(colNameStr);
    }

    public Map<String, String> parse(String _line) {
        String line = _line;
        List<String> values = new ArrayList<>();
        int fromIndex = 0;
        int nextDelim;
        while (line.length() > 0 && (nextDelim = line.indexOf(",", fromIndex)) >= 0) {
            if (line.indexOf("{") >= 0 && line.indexOf("{") < nextDelim && fromIndex == 0) {
                fromIndex = line.indexOf("}");
            } else {
                values.add(line.substring(0, nextDelim));
                line = line.substring(nextDelim + 1).trim();
                fromIndex = 0;
                if (line.length() == 0) values.add("");
            }
        }
        if (line.length() > 0) values.add(line.trim());

        Preconditions.checkArgument(columnNames.size() == values.size(),
                "Row and columns are not equal: columns=%s, rawString=%s", columnNames, _line);
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            row.put(columnNames.get(i), values.get(i));
        }
        return row;
    }
}
