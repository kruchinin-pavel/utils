package org.kpa.util.telegram;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.kpa.util.Json;
import org.kpa.util.Props;
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
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class TelegramBot extends TelegramLongPollingBot implements AutoCloseable {
    private final String token;
    private final String storePath;
    private final String botUserName;
    private Map<String, BiConsumer<ChatInfo, Message>> secrectCallback = new LinkedHashMap<>();
    private Map<String, BiConsumer<ChatInfo, Message>> callback = new LinkedHashMap<>();
    private final List<ChatInfo> chatSet = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);

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
        cmd("bye", (chat, msg) -> {
            chatSet.remove(chat);
            send(chat, "Bye!", true);
            storeState();
        });
        cmd("me", (chat, msg) -> {
            send(chat, "Your info:\n" + chat.toString(), true);
        });
        cmd("sync", (chat, msg) -> {
            loadState();
            send(chat, "States reloaded", true);
        });
        cmd("?", (chat, msg) -> {
            String text = "Commands are:\n" + Joiner.on(",\n").join(
                    callback.keySet().stream().map(v -> "'" + v + "'").collect(Collectors.toList()));
            if (chat.enabled) {
                text += "\nSecret commands are:\n" + Joiner.on(",\n").join(
                        secrectCallback.keySet().stream().map(v -> "'" + v + "'").collect(Collectors.toList()));
            }
            send(chat, text, true);
        });
        secretCmd("all", (chat, msg) -> {
            loadState();
            send(chat, "Chats are:\n" + Joiner.on("\n").join(chatSet), true);
        });

        logger.info("Telegram bot {} started.", botUserName);
        broadcast("I'm up!", false);
    }

    public TelegramBot cmd(String command, BiConsumer<ChatInfo, Message> consumer) {
        Preconditions.checkArgument(this.callback.put(command.toLowerCase().trim(), consumer) == null,
                "Already contains command: %s", command);
        return this;
    }

    public TelegramBot secretCmd(String command, BiConsumer<ChatInfo, Message> consumer) {
        Preconditions.checkArgument(this.secrectCallback.put(command.toLowerCase().trim(), consumer) == null,
                "Already contains command: %s", command);
        return this;
    }

    private synchronized void loadState() {
        if (Files.isRegularFile(Paths.get(storePath))) {
            chatSet.clear();
            chatSet.addAll(new HashSet<>(Utils.asList(Json.iterableFile(storePath, ChatInfo.class))));
            logger.info("Restored states: {}", chatSet);
        } else {
            logger.info("No chats yet");
        }
    }

    private synchronized void storeState() {
        logger.info("Stored states: {}", chatSet);
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
        if (e.getMessage() == null) {
            logger.info("Empty message come: {}", e);
            return;
        }
        ChatInfo info = new ChatInfo(e.getMessage());
        if (!chatSet.contains(info)) {
            chatSet.add(info);
            send(info, "You are new to me. Type ? to list commands.", true);
            storeState();
        } else {
            info = chatSet.get(chatSet.indexOf(info));
            String command = e.getMessage().getText().toLowerCase();
            BiConsumer<ChatInfo, Message> cons = callback.get(command);
            if (cons == null) {
                cons = secrectCallback.get(command);
                if (cons != null && !info.enabled) {
                    send(info, "Not permitted", true);
                    return;
                }
            }
            if (cons != null) {
                cons.accept(info, e.getMessage());
            } else {
                send(info, "What??\bCommands are: " + Joiner.on(", ").join(callback.keySet()), true);
            }
        }
    }


    public synchronized void send(ChatInfo msg, String text, boolean async) {
        SendMessage s = new SendMessage();
        logger.info("Sending message to chatId: {}", msg);
        s.setChatId(msg.chatId);
        s.setText(text);
        SendMessage method = new SendMessage(msg.chatId, text);
        if (async) {
            sendApiMethodAsync(method, new SentCallback<Message>() {
                @Override
                public void onResult(BotApiMethod<Message> method, Message response) {
                    logger.debug("Method successful: {}. Resonce: {}", method, response);
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
    public void onClosing() {
        broadcast("I'm off!", false);
        super.onClosing();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            onClosing();
        }
    }


    private static Map<String, TelegramBot> botByName = new HashMap<>();

    public static synchronized TelegramBot get(String botUserName, String token, String storePath) {
        String proxy = Props.getSilent("telegram_proxy");
        if (!Strings.isNullOrEmpty(proxy) && !TelegramBot.Proxy.isEnabled()) {
            int port = 1080;
            Iterator<String> iter = Splitter.on(":").trimResults().omitEmptyStrings().split(proxy).iterator();
            String host = iter.next();
            if (iter.hasNext()) port = Integer.parseInt(iter.next());
            TelegramBot.Proxy.enable(host, port);
        }
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
            Map<String, Object> vals = new LinkedHashMap<>();
            if (!Strings.isNullOrEmpty(userName)) vals.put("userName", userName);
            if (!Strings.isNullOrEmpty(userFirstName)) vals.put("userFirstName", userFirstName);
            if (!Strings.isNullOrEmpty(userLastName)) vals.put("userLastName", userLastName);
            vals.put("subscribed", subscribed);
            vals.put("chatId", chatId);
            vals.put("userId", userId);
            vals.put("enabled", enabled);
            return "ChatInfo{" + Joiner.on(", ").withKeyValueSeparator("=").join(vals) + '}';
        }
    }


    public static class Proxy extends ProxySelector {
        private final ProxySelector defsel;
        private final Map<String, java.net.Proxy> proxies;
        private static AtomicBoolean enabled = new AtomicBoolean();

        private Proxy(ProxySelector def, Map<String, java.net.Proxy> proxies) {
            defsel = def;
            this.proxies = proxies;
        }

        public java.util.List<java.net.Proxy> select(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI can't be null.");
            }
            java.net.Proxy proxy = proxies.get(uri.toString());
            if (proxy != null) {
                return Collections.singletonList(proxy);
            }
            return defsel.select(uri);
        }

        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            logger.info("Connection failed to URI:{} with proxy(if any): {}", uri, proxies.get(uri.toString()));
        }

        public static boolean isEnabled() {
            return enabled.get();
        }

        public static void enable(String proxyHost, int proxyPort) {
            Preconditions.checkArgument(enabled.compareAndSet(false, true), "Already enabled telegram proxy");
            logger.info("Enabling telegram proxy: {}:{}", proxyHost, proxyPort);
            Proxy ps = new Proxy(ProxySelector.getDefault(), new ImmutableMap.Builder<String, java.net.Proxy>()
                    .put("socket://api.telegram.org:443",
                            new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)))
                    .build());
            ProxySelector.setDefault(ps);

        }

    }

}