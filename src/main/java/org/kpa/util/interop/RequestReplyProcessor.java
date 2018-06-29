package org.kpa.util.interop;

import org.kpa.util.DaemonNamedFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestReplyProcessor<T> {
    private final BiFunction<T, T, Boolean> replyByRequestFunction;
    private final Map<T, T> repliesByRequest = new HashMap<>();
    private final ExecutorService service = DaemonNamedFactory.newCachedThreadPool("req-rep");
    private Consumer<T> onRequestCome;
    private Consumer<Object> unprocessedMessageCome;

    public RequestReplyProcessor<T> onRequestCome(Consumer<T> onRequestCome) {
        this.onRequestCome = onRequestCome;
        return this;
    }

    public RequestReplyProcessor<T> onLostMessage(Consumer<Object> unprocessedMessageCome) {
        this.unprocessedMessageCome = unprocessedMessageCome;
        return this;
    }

    public int openRequest() {
        synchronized (repliesByRequest) {
            return repliesByRequest.size();
        }
    }

    public RequestReplyProcessor(BiFunction<T, T, Boolean> replyByRequestFunction) {
        this.replyByRequestFunction = replyByRequestFunction;
    }

    public void reply(T incomeMesasge) {
        T v;
        synchronized (repliesByRequest) {
            v = repliesByRequest.keySet().stream()
                    .filter(r -> replyByRequestFunction.apply(r, incomeMesasge))
                    .findFirst().orElse(null);
            if (v != null) repliesByRequest.replace(v, incomeMesasge);

        }
        if (v == null) unprocessedMessageCome.accept(incomeMesasge);
    }

    public <R extends T> Future<R> request(T request, long timeout) {
        return request(request, timeout, null, null);
    }

    public <R extends T> Future<R> request(T request, long timeout, Consumer<R> onResult, BiConsumer<T, Exception> onError) {
        synchronized (repliesByRequest) {
            checkArgument(repliesByRequest.put(request, null) == null, "Already contains request: %s", request);
        }
        if (onRequestCome != null) onRequestCome.accept(request);
        return service.submit(() -> {
            long upTime = System.currentTimeMillis() + timeout;
            try {
                T reply;
                while ((reply = repliesByRequest.get(request)) == null) {
                    if (System.currentTimeMillis() > upTime) {
                        throw new TimeoutException();
                    }
                    Thread.sleep(100);
                }
                if (onResult != null) onResult.accept((R) reply);
                return (R) reply;
            } catch (Exception e) {
                if (onError != null) onError.accept(request, e);
                throw e;
            } finally {
                synchronized (repliesByRequest) {
                    repliesByRequest.remove(request);
                }
            }
        });
    }
}
