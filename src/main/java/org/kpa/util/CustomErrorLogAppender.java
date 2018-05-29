package org.kpa.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.EvictingQueue;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 6/2/13
 * Time: 8:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class CustomErrorLogAppender<E> extends AppenderBase<E> {
    private final Consumer<String> bot;
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final EvictingQueue<String> queue = EvictingQueue.create(20);

    public CustomErrorLogAppender(Consumer<String> bot) {
        this.bot = bot;
    }

    @Override
    protected void append(E event) {
        try {
            String message = addMessage(event);
            if (!Strings.isNullOrEmpty(message)) {
                bot.accept(message);

            }
        } catch (Throwable e) {
            bot.accept(String.format("Didn't manage to show log '%s'. Error message: %s", event, e.getMessage()));
        }
    }

    private String addMessage(Object obj) {
        ch.qos.logback.classic.spi.LoggingEvent event = (ch.qos.logback.classic.spi.LoggingEvent) obj;
        if (event.getLevel().levelInt >= Level.ERROR_INT) {
            String msgId = event.getThreadName() + event.getMessage();
            if (queue.contains(msgId)) {
                return null;
            }
            queue.add(msgId);
            String text = event.getLevel() + " thread " + event.getThreadName() + " at " +
                    dateFormatter.format(ZonedDateTime.now()) + ": " + event.getFormattedMessage() + ":\n";
            text += Joiner.on("\n\t").join(event.getCallerData());
            return text;
        }
        return null;
    }

    public static void registerErrorHandler(Consumer<String> bot) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern("%date %level [%thread] %logger{10} [%file:%line] %msg%n");
        ple.setContext(lc);
        ple.start();
        CustomErrorLogAppender<ILoggingEvent> fileAppender = new CustomErrorLogAppender<>(bot);
        fileAppender.setContext(lc);
        fileAppender.start();

        lc.getLoggerList().forEach(logger -> logger.addAppender(fileAppender));
    }


}
