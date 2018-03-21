package org.kpa.util.telegram;

public class TelegramBotTest {

    public static void main(String[] args) throws InterruptedException {
        TelegramBot bot = TelegramBot.get("MyExtraSuperBot", "", "chats.json");
        bot.broadcast("Hi from test!", true);
        Thread.sleep(10000);
    }

}