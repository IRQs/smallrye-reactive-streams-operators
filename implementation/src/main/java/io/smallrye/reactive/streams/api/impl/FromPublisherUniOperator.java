package io.smallrye.reactive.streams.api.impl;

import io.smallrye.reactive.streams.api.UniSubscriber;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class FromPublisherUniOperator<O> extends UniOperator<Void, O> {
    private final PublisherBuilder<O> publisher;

    public FromPublisherUniOperator(PublisherBuilder<O> publisher) {
        super(null);
        this.publisher = Objects.requireNonNull(publisher, "`stage` must not be `null`");
    }

    @Override
    public void subscribe(UniSubscriber<? super O> subscriber) {
        // cancellation should be better handled here.
        CompletableFuture<Optional<O>> stage = publisher.findFirst().run().toCompletableFuture();
        subscriber.onSubscribe(() -> stage.cancel(false));
        stage.whenComplete((res, fail) -> {
            // It's a bit racey here as the cancellation may happen after the check and the subscriber will still
            // be notified
            if (!stage.isCancelled()) {
                if (fail != null) {
                    subscriber.onFailure(fail);
                } else {
                    // If we get the end of stream signal, inject `null`.
                    subscriber.onResult(res.orElse(null));
                }
            }
        });
    }
}
