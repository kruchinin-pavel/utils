package org.kpa.util.telegram;

public class TelegramBotTest {

    public static void main(String[] args) throws InterruptedException {
        TelegramBot bot = TelegramBot.get("MyExtraSuperBot", "480685955:AAHQSzlwpBIfITxoY_4_a1inAqcrE2GpqUQ", "chats.json");
        Thread.sleep(100000000);
    }

}