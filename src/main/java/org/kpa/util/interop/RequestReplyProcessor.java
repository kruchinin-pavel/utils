package org.kpa.util.interop;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestReplyProcessor<T> {
    private final BiFunction<T, T, Boolean> replyByRequestFunction;
    private final Map<T, T> repliesByRequest = new HashMap<>();

    public int openRequest() {
        synchronized (repliesByRequest) {
            return repliesByRequest.size();
        }
    }

    public RequestReplyProcessor(BiFunction<T, T, Boolean> replyByRequestFunction) {
        this.replyByRequestFunction = replyByRequestFunction;
    }

    public void reply(T reply) {
        synchronized (repliesByRequest) {
            repliesByRequest.keySet().stream()
                    .filter(r -> replyByRequestFunction.apply(r, reply))
                    .findFirst().ifPresent(v -> {
                repliesByRequest.replace(v, reply);
            });
        }
    }

    public void request(T request, long timeout, Consumer<T> onResult, BiConsumer<T, Exception> onError) {
        synchronized (repliesByRequest) {
            checkArgument(repliesByRequest.put(request, null) == null, "Already contains request: %s", request);
        }
        Thread thread = new Thread(() -> {
            long upTime = System.currentTimeMillis() + timeout;
            try {
                T reply;
                while ((reply = repliesByRequest.get(request)) == null) {
                    if (System.currentTimeMillis() > upTime) {
                        throw new TimeoutException();
                    }
                    Thread.sleep(100);
                }
                onResult.accept(reply);
            } catch (Exception e) {
                onError.accept(request, e);
            } finally {
                synchronized (repliesByRequest) {
                    repliesByRequest.remove(request);
                }
            }
        });
        thread.start();
    }
}
