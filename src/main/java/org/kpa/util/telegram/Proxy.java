package org.kpa.util.telegram;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.kpa.util.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Proxy extends ProxySelector {
    private final ProxySelector defsel;
    private final Map<String, java.net.Proxy> proxies;
    private static AtomicBoolean enabled = new AtomicBoolean();
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);

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

    public static void setProxyUser(String userName, String userPass) {
        final Map<String, String> props = new ImmutableMap.Builder<String, String>()
                .put("telegram_proxy.user", userName)
                .put("telegram_proxy.pswd", userPass).build();
        Props.setCustomProps(props::get);
    }

    public static void enable(String proxyHost, int proxyPort) {
        enable(proxyHost, proxyPort,
                () -> Props.getProperty("telegram_proxy.user", "telegram_proxy.user", "", true),
                () -> Props.getProperty("telegram_proxy.pswd", "telegram_proxy.pswd", "", true)
        );
    }

    public static void enable(String proxyHost, int proxyPort, String userName, String userPwd) {
        enable(proxyHost, proxyPort, () -> userName, () -> userPwd);
    }

    private static void enable(String proxyHost, int proxyPort, Supplier<String> userName, Supplier<String> userPwd) {
        Preconditions.checkArgument(enabled.compareAndSet(false, true), "Already enabled telegram proxy");
        logger.info("Enabling telegram proxy: {}:{}", proxyHost, proxyPort);
        Proxy ps = new Proxy(ProxySelector.getDefault(), new ImmutableMap.Builder<String, java.net.Proxy>()
                .put("socket://api.telegram.org:443",
                        new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)))
                .build());
        ProxySelector.setDefault(ps);
        Authenticator.setDefault(new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                if (proxyHost.equalsIgnoreCase(this.getRequestingHost())) {
                    return new PasswordAuthentication(userName.get(), userPwd.get().toCharArray());
                }
                return super.getPasswordAuthentication();
            }
        });
    }

}
