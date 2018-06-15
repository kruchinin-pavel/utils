package org.kpa.util.interop;

import com.google.common.base.Strings;
import org.kpa.util.AutoCloseableConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

public class Zmq implements AutoCloseableConsumer<String> {
    private final int sendToPort;
    private final String listenFromAdress;
    private final ZMQ.Socket receiver;
    private final ZMQ.Socket sender;
    private final ZMQ.Context context;
    private static final Logger log = LoggerFactory.getLogger(Zmq.class);

    private Zmq(int sendToPort, String listenFromAdress) {
        this.sendToPort = sendToPort;
        context = ZMQ.context(1);
        this.listenFromAdress = listenFromAdress;

        if (sendToPort > 0) {
            sender = context.socket(ZMQ.PUB);
            sender.bind(String.format("tcp://*:%s", sendToPort));
            log.info("Publishing to port: {}", sendToPort);
        } else {
            sender = null;
        }

        if (!Strings.isNullOrEmpty(listenFromAdress)) {
            receiver = context.socket(ZMQ.SUB);
            receiver.connect(listenFromAdress);
            receiver.setRcvHWM(0);
            receiver.subscribe("".getBytes());
            log.info("Receiving from: {}", listenFromAdress);
        } else {
            receiver = null;
        }
    }

    @Override
    public void accept(String s) {
        send(s);
    }

    public String tryReceive() {
        return tryReceive(0);
    }

    public String tryReceive(long timeOutMillis) {
        long upTime = System.currentTimeMillis() + timeOutMillis;
        String str;
        while ((str = receiver.recvStr(ZMQ.NOBLOCK)) == null) {
            if (System.currentTimeMillis() >= upTime) {
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return str;
    }

    public boolean send(String str) {
        return sender.send(str);
    }

    @Override
    public void close() {
        try {
            sender.close();
            receiver.close();
        } finally {
            context.term();
        }
    }

    public static Zmq publisher(int sendToPort) {
        return new Zmq(sendToPort, null);
    }

    public static Zmq subscriber(String listenFromAdress) {
        return new Zmq(-1, listenFromAdress);
    }

    public static Zmq pubSub(int sendToPort, String listenFromAdress) {
        return new Zmq(sendToPort, listenFromAdress);
    }

    @Override
    public String toString() {
        return "Zmq{" +
                "sendToPort=" + sendToPort +
                ", listenFromAdress='" + listenFromAdress + '\'' +
                '}';
    }
}
