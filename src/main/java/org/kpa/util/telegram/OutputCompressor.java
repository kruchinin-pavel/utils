package org.kpa.util.telegram;

import com.google.common.base.Joiner;

import java.util.LinkedHashSet;
import java.util.Set;

public class OutputCompressor {
    private final int masOutputMessageLength;
    private volatile String lastMessage = "";
    private final Set<String> messagesBuf = new LinkedHashSet<>();

    public OutputCompressor(int masOutputMessageLength) {
        this.masOutputMessageLength = masOutputMessageLength;
    }

    public void addStr(String messageToSend) {
        messagesBuf.add(messageToSend);
        lastMessage = messageToSend;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void clear() {
        messagesBuf.clear();
    }

    public String getStr() {
        String join = Joiner.on("\n").join(messagesBuf);
        if (join.length() > masOutputMessageLength) {
            join = "..." + join.substring(join.length() - masOutputMessageLength);
        }
        return join;
    }
}
