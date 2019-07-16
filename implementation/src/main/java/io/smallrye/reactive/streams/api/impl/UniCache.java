package io.smallrye.reactive.streams.api.impl;

import io.smallrye.reactive.streams.api.Uni;
import io.smallrye.reactive.streams.api.UniSubscriber;
import io.smallrye.reactive.streams.api.UniSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class UniCache<I> extends UniOperator<I, I> implements UniSubscriber<I> {

    private static final int NOT_INITIALIZED = 0;
    private static final int SUBSCRIBING = 1;
    private static final int SUBSCRIBED = 2;
    private static final int COMPLETED = 3;

    private int state = 0;


    private AtomicReference<UniSubscription> subscription = new AtomicReference<>();
    private List<UniSubscriber<? super I>> subscribers = new ArrayList<>();
    private I result;
    private Throwable failure;

    UniCache(Uni<? extends I> source) {
        super(Objects.requireNonNull(source, "`source` must not be `null`"));
    }

    @Override
    public void subscribing(WrapperUniSubscriber<? super I> subscriber) {
        Runnable action = null;
        synchronized (this) {
            switch (state) {
                case NOT_INITIALIZED:
                    // First subscriber,
                    state = SUBSCRIBING;
                    action = () -> source().subscribe(this);
                    subscribers.add(subscriber);
                    break;
                case SUBSCRIBING:
                    // Subscription pending
                    subscribers.add(subscriber);
                    break;
                case SUBSCRIBED:
                    // No result yet, but we can provide a Subscription
                    subscribers.add(subscriber);
                    action = () ->
                            subscriber.onSubscribe(() -> onCancellation(subscriber));
                    break;
                case COMPLETED:
                    // Result already computed
                    subscribers.add(subscriber);
                    action = () -> {
                        // We must first pass a subscription
                        subscriber.onSubscribe(() -> onCancellation(subscriber));
                        replay(subscriber);
                    };
                    break;
                default:
                    throw new IllegalStateException("Unknown state: " + state);
            }
        }

        // Execute outside of the synchronized await
        if (action != null) {
            action.run();
        }
    }

    private synchronized void onCancellation(UniSubscriber<? super I> subscriber) {
        subscribers.remove(subscriber);
    }

    private void replay(UniSubscriber<? super I> subscriber) {
        synchronized (this) {
            if (state != COMPLETED) {
                throw new IllegalStateException("Invalid state - expected being in the DONE state, but is in state: " + state);
            }
        }
        if (failure != null) {
            subscriber.onFailure(failure);
        } else {
            subscriber.onResult(result);
        }
    }

    @Override
    public void onSubscribe(UniSubscription subscription) {
        List<UniSubscriber<? super I>> list;
        synchronized (this) {
            if (!this.subscription.compareAndSet(null, subscription)) {
                throw new IllegalStateException("Invalid state - received a second subscription from source");
            }
            state = SUBSCRIBED;
            list = new ArrayList<>(subscribers);
        }
        list.forEach(s -> s.onSubscribe(() -> onCancellation(s)));
    }

    @Override
    public void onResult(I result) {
        List<UniSubscriber<? super I>> list;
        synchronized (this) {
            if (state != SUBSCRIBED) {
                throw new IllegalStateException("Invalid state - received result while we where not in the SUBSCRIBED state, current state is: " + state);
            }
            state = COMPLETED;
            this.result = result;
            list = new ArrayList<>(subscribers);
            // Clear the list
            this.subscribers.clear();
        }
        // Here we may notify a subscriber that would have cancelled its subscription just after the synchronized await
        // we consider it as pending cancellation.
        list.forEach(s -> s.onResult(result));
    }

    @Override
    public void onFailure(Throwable failure) {
        List<UniSubscriber<? super I>> list;
        synchronized (this) {
            if (state != SUBSCRIBED) {
                throw new IllegalStateException("Invalid state - received result while we where not in the SUBSCRIBED state, current state is: " + state);
            }
            state = COMPLETED;
            this.failure = failure;
            list = new ArrayList<>(subscribers);
            // Clear the list
            this.subscribers.clear();
        }
        // Here we may notify a subscriber that would have cancelled its subscription just after the synchronized await
        // we consider it as pending cancellation.
        list.forEach(s -> s.onFailure(failure));
    }
}
