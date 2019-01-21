package org.kpa.util.telegram;

import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.status.ErrorStatus;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.kpa.util.TurnoverCounter;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Paolo Denti
 * <p>
 * TelegramAppender appends log to a Telegram chat by using a Telegram BOT, via Telegram BOT Api (https://core.telegram.org/bots/api)
 * The append log execution is slow; use it only for critical errors
 */
public class TelegramAppender<E> extends UnsynchronizedAppenderBase<E> {
    private final OutputCompressor compressor = new OutputCompressor(200);
    private TelegramBot bot;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1,
            5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1_024 * 1_024));


    /**
     * It is the layout used for message formatting
     */
    protected Layout<E> layout;

    /**
     * The telegram bot token.
     */
    private String botToken = null;

    /**
     * The minimum interval allowed between each telegram message
     */
    private int minInterval = 5000;

    /**
     * send each telegram in separate thread
     */
    private boolean nonBlocking = true;

    /**
     * Json file to store sessions (chatIds) information
     *
     * @param chatSessionsFile
     */
    private String chatSessionsFile;
    /**
     * Bot name to start
     */
    private String botUserName;

    /**
     * Bot name to start
     */
    private String botInstanceName;

    /**
     * @param proxyString user:pwd@host:port
     */
    public void setProxy(String proxyString) {
        List<String> items = Splitter.on("@").trimResults().splitToList(proxyString);
        List<String> userPwd = Splitter.on(":").trimResults().splitToList(items.get(0));
        List<String> hostPort = Splitter.on(":").trimResults().splitToList(items.get(1));
        Proxy.enable(hostPort.get(0), Integer.parseInt(hostPort.get(1)), userPwd.get(0), userPwd.get(1));
    }

    public void setLayout(Layout<E> layout) {
        this.layout = layout;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public void setBotInstanceName(String botInstanceName) {
        this.botInstanceName = botInstanceName;
    }

    public void setMinInterval(String minInterval) {
        try {
            this.minInterval = Integer.parseInt(minInterval);
        } catch (NumberFormatException e) {
            internalAddStatus("Bad minInterval");
        }
    }

    public void setNonBlocking(String nonBlocking) {
        this.nonBlocking = Boolean.parseBoolean(nonBlocking);
    }

    public void setBotUserName(String botUserName) {
        this.botUserName = botUserName;
    }


    public void setChatSessionsFile(String chatSessionsFile) {
        this.chatSessionsFile = chatSessionsFile;
    }


    private TurnoverCounter sentCounter;
    @Override
    public void start() {
        int errors = 0;

        if (this.layout == null) {
            internalAddStatus("No layout set");
            errors++;
        }

        if (this.botToken == null) {
            internalAddStatus("No botToken set");
            errors++;
        }

        if (this.minInterval < 0) {
            internalAddStatus("Bad minInterval");
            errors++;
        }

        try {
            bot = TelegramBot.get(botUserName, botToken, chatSessionsFile, botInstanceName);
            bot.cmd("l", (chatInfo, message) -> {
                String str = compressor.getLastMessage();
                if (!Strings.isNullOrEmpty(str)) bot.send(chatInfo, compressor.getLastMessage(), false);
            });
        } catch (Exception e) {
            internalAddStatus(e.getMessage());
            errors++;
        }

        sentCounter = new TurnoverCounter(minInterval, 1);
        if (errors == 0) {
            super.start();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    @Override
    public void stop() {
        if (bot != null) bot.close();
        super.stop();
    }

    @Override
    protected void append(E eventObject) {
        if (!isStarted()) {
            return;
        }
        executor.submit(() -> implSendTelegramMessage(eventObject));
    }

    private void implSendTelegramMessage(E eventObject) {
        if (eventObject != null) {
            String messageToSend = layout.doLayout(eventObject);
            compressor.addStr(messageToSend);
        }
        String str = compressor.getStr();
        if (!Strings.isNullOrEmpty(str)) {
            sentCounter.runIfCan(() -> {
                compressor.clear();
                bot.broadcast(str, false);
            }, () -> new Thread(() -> {
                try {
                    Thread.sleep(minInterval + 100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executor.submit(() -> implSendTelegramMessage(null));
            }).start());
        }
    }

    private static final String MSG_FORMAT = "%s for the appender named '%s'.";

    private void internalAddStatus(String msgPrefix) {
        addStatus(new ErrorStatus(String.format(MSG_FORMAT, msgPrefix, name), this));
    }

}
