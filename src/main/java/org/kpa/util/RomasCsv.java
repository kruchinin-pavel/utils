package org.kpa.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import jdk.internal.joptsimple.internal.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public class RomasCsv {
    private static final CharMatcher delimMatch = CharMatcher.anyOf(",");
    private static final CharMatcher openMatch = CharMatcher.anyOf("{[");
    private static final CharMatcher closeMatch = CharMatcher.anyOf("]}");
    private final List<String> columnNames;

    public RomasCsv(String colNameStr) {
        columnNames = Strings.isNullOrEmpty(colNameStr) ? emptyList() :
                Splitter.on(",").trimResults().splitToList(colNameStr);
    }

    private int lookupOpen(String line, int fromIndex, int openedCount) {
        int openIndex = openMatch.indexIn(line, fromIndex);
        int closeIndex = closeMatch.indexIn(line, fromIndex);
        int delimIndex = delimMatch.indexIn(line, fromIndex);
        if (delimIndex == -1) {
            return line.length();
        }
        if (openedCount > 0 && closeIndex >= 0) {
            if (openIndex >= 0 && openIndex < closeIndex) {
                return lookupOpen(line, openIndex + 1, openedCount + 1);
            }
            return lookupOpen(line, closeIndex + 1, openedCount - 1);
        }
        if (openIndex == -1 || delimIndex < openIndex) {
            return delimIndex;
        }
        return lookupOpen(line, openIndex + 1, openedCount + 1);
    }

    public Map<String, String> parse(String _line) {
        String line = _line;
        List<String> values = new ArrayList<>();
        int fromIndex = 0;
        int nextDelim;
        while (line.length() > 0 && (nextDelim = lookupOpen(line, fromIndex, 0)) >= 0) {
            values.add(line.substring(0, nextDelim));
            if (line.length() == nextDelim) break;
            line = line.substring(nextDelim + 1).trim();
            fromIndex = 0;
            if (line.length() == 0) values.add("");
        }

        Preconditions.checkArgument(columnNames.size() == values.size(),
                "Row and columns are not equal: columns=%s, rawString=%s", columnNames, _line);
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            String value = values.get(i);
            if (value.contains("\"")) {
                value = value.replace("\"\"", "\"");
                value = value.substring(value.startsWith("\"") ? 1 : 0, value.length() - (value.endsWith("\"") ? 1 : 0));
            }
            row.put(columnNames.get(i), value);
        }
        return row;
    }
}
