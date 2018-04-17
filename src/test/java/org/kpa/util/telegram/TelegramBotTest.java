package org.kpa.util.telegram;

import org.junit.Test;
import org.kpa.util.Json;
import org.kpa.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class TelegramBotTest {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotTest.class);


    public static void main(String[] args) throws InterruptedException {
        TelegramBot.Proxy.enable("ljrgf.tgproxy.me", 1080);
        TelegramBot bot = TelegramBot.get("MyExtraSuperBot", "480685955:AAHQSzlwpBIfITxoY_4_a1inAqcrE2GpqUQ", "chats.json");
        Thread.sleep(100000000);
    }

    @Test
    public void testLoadState() {
        HashSet<TelegramBot.ChatInfo> set = new HashSet<>(Utils.asList(Json.iterableFile("src/test/resources/org/kpa/util/telegram/chats_PROD.json", TelegramBot.ChatInfo.class)));
        logger.info("Set is {}", set);
        assertEquals(3, set.size());

    }


}