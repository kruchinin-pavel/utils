package org.kpa.util.telegram;

import com.fasterxml.jackson.annotation.*;
import org.kpa.util.Json;
import org.kpa.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updateshandlers.SentCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelegramBot extends TelegramLongPollingBot implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);
    private final Set<ChatInfo> chatSet = new HashSet<>();
    private final String storePath;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final String token;
    private final String botUserName;

    private TelegramBot(String botUserName, String token, String storePath) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "shtdnhk-thr"));
        this.storePath = storePath;
        this.token = token;
        this.botUserName = botUserName;
        try {
            new TelegramBotsApi().registerBot(this);
            loadState();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        logger.info("Telegram bot {} started.", botUserName);
    }

    private synchronized void loadState() {
        if (Files.isRegularFile(Paths.get(storePath))) {
            chatSet.addAll(Utils.asList(Json.iterableFile(storePath, ChatInfo.class)));
            logger.info("Restored {} chats.", chatSet.size());
            broadcast("I'm up!", false);
        } else {
            logger.info("No chats yet");
        }
    }

    private synchronized void storeState() {
        if (chatSet.size() > 0) {
            Json.toFile(storePath, chatSet);
        } else {
            try {
                Files.deleteIfExists(Paths.get(storePath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized void broadcast(String message, boolean async) {
        chatSet.forEach(v -> send(v, message, async));
    }

    public synchronized void broadcastSecret(String message, boolean async) {
        chatSet.stream().filter(v -> v.enabled).forEach(v -> send(v, message, async));
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    @Override
    public void onUpdateReceived(Update e) {
        // Тут будет то, что выполняется при получении сообщения
        logger.info("Update come: {}", e);
        ChatInfo info = new ChatInfo(e.getMessage());
        if (!chatSet.contains(info)) {
            chatSet.add(info);
            send(info, "Welcome.", true);
            storeState();
        } else {
            if (e.getMessage().getText().equalsIgnoreCase("/fgt")) {
                chatSet.remove(info);
                send(info, "Bye!", true);
            } else {
                send(info, "You are already in. Commands:\n/fgt - forget", true);
            }
            storeState();
        }
    }


    private synchronized void send(ChatInfo msg, String text, boolean async) {
        SendMessage s = new SendMessage();
        logger.info("Sending message to chatId: {}", msg);
        s.setChatId(msg.chatId);
        s.setText(text);
        SendMessage method = new SendMessage(msg.chatId, text);
        if (async) {
            sendApiMethodAsync(method, new SentCallback<Message>() {
                @Override
                public void onResult(BotApiMethod<Message> method, Message response) {
                    logger.info("Method successful: {}. Resonce: {}", method, response);
                }

                @Override
                public void onError(BotApiMethod<Message> method, TelegramApiRequestException apiException) {
                    onException(method, apiException);
                }

                @Override
                public void onException(BotApiMethod<Message> method, Exception exception) {
                    logger.error("Error on method {}. Forgetting chat {}", method, msg, exception);
                    synchronized (TelegramBot.this) {
                        chatSet.remove(msg);
                        storeState();
                    }
                }
            });
        } else {
            try {
                sendApiMethod(method);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getBotToken() {
        return token;
    }


    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            broadcast("I'm off!", false);
        }
    }


    private static Map<String, TelegramBot> botByName = new HashMap<>();

    public static synchronized TelegramBot get(String botUserName, String token, String storePath) {
        return botByName.computeIfAbsent(botUserName, bUN -> new TelegramBot(bUN, token, storePath));
    }

    static {
        ApiContextInitializer.init();
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_type")
    @JsonSubTypes({@JsonSubTypes.Type(value = ChatInfo.class, name = "ChatInfo")})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatInfo {
        public final long chatId;
        public final int userId;
        public final String userName;
        public final String userFirstName;
        public final String userLastName;
        public final boolean subscribed;
        public final boolean enabled;

        public ChatInfo(Message message) {
            this(message.getChatId(),
                    message.getFrom().getId(),
                    message.getFrom().getUserName(),
                    message.getFrom().getFirstName(),
                    message.getFrom().getLastName(),
                    false,
                    false
            );
        }

        @JsonCreator
        public ChatInfo(@JsonProperty("chatId") long chatId,
                        @JsonProperty("userId") int userId,
                        @JsonProperty("userName") String userName,
                        @JsonProperty("userFirstName") String userFirstName,
                        @JsonProperty("lastName") String lastName,
                        @JsonProperty("subscribed") boolean subscribed,
                        @JsonProperty("enabled") boolean enabled) {
            this.chatId = chatId;
            this.userId = userId;
            this.userName = userName;
            this.userFirstName = userFirstName;
            this.userLastName = lastName;
            this.subscribed = subscribed;
            this.enabled = enabled;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChatInfo chatInfo = (ChatInfo) o;
            return chatId == chatInfo.chatId &&
                    Objects.equals(userId, chatInfo.userId);
        }

        @Override
        public int hashCode() {

            return Objects.hash(chatId, userId);
        }

        @Override
        public String toString() {
            return "ChatInfo{" +
                    "chatId=" + chatId +
                    ", userId='" + userId + '\'' +
                    ", subscribed=" + subscribed +
                    '}';
        }
    }
}