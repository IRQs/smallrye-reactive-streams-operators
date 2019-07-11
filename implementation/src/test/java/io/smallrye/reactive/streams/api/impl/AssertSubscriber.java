package io.smallrye.reactive.streams.api.impl;

import io.smallrye.reactive.streams.api.UniSubscriber;
import io.smallrye.reactive.streams.api.UniSubscription;

import java.util.concurrent.CompletableFuture;

public class AssertSubscriber<T> implements UniSubscriber<T> {
    private UniSubscription subscription;
    private T result;
    private Throwable failure;
    private CompletableFuture<T> future;
    private String onResultThreadName;
    private String onErrorThreadName;

    public static <T> AssertSubscriber<T> create() {
        return new AssertSubscriber<>();
    }


    @Override
    public synchronized void onSubscribe(UniSubscription subscription) {
        this.subscription = subscription;
        this.future = new CompletableFuture<>();
    }

    @Override
    public synchronized void onResult(T result) {
        if (this.future == null) {
            throw new IllegalStateException("No subscription");
        }
        this.result = result;
        this.onResultThreadName = Thread.currentThread().getName();
        this.future.complete(result);
    }

    @Override
    public synchronized void onFailure(Throwable t) {
        if (this.future == null) {
            throw new IllegalStateException("No subscription");
        }
        this.failure = t;
        this.onErrorThreadName = Thread.currentThread().getName();
        this.future.completeExceptionally(t);
    }

    public AssertSubscriber<T> await() {
        CompletableFuture<T> fut;
        synchronized (this) {
            if (this.future == null) {
                throw new IllegalStateException("No subscription");
            }
            fut = this.future;
        }
        try {
            fut.join();
        } catch (Exception e) {
            // Error already caught.
        }
        return this;
    }

    public synchronized AssertSubscriber<T> assertCompletedSuccessfully() {
        if (this.future == null) {
            throw new IllegalStateException("No subscription");
        }
        if (!this.future.isDone()) {
            throw new IllegalStateException("Not done yet");
        }

        if (future.isCompletedExceptionally()) {
            throw new AssertionError("The uni didn't completed successfully: " + failure);
        }
        if (future.isCancelled()) {
            throw new AssertionError("The uni didn't completed successfully, it was cancelled");
        }
        return this;
    }

    public synchronized T getResult() {
        if (this.future == null) {
            throw new IllegalStateException("No subscription");
        }
        if (!this.future.isDone()) {
            throw new IllegalStateException("Not done yet");
        }
        return result;
    }

    public synchronized Throwable getFailure() {
        if (this.future == null) {
            throw new IllegalStateException("No subscription");
        }
        if (!this.future.isDone()) {
            throw new IllegalStateException("Not done yet");
        }
        return failure;
    }

    public AssertSubscriber<T> assertResult(T expected) {
        T result = getResult();
        if (result == null && expected != null) {
            throw new AssertionError("Expected: " + expected + " but was `null`");
        }
        if (result != null && !result.equals(expected)) {
            throw new AssertionError("Expected: " + expected + " but was " + result);
        }
        return this;
    }

    public AssertSubscriber<T> assertFailed(Class<? extends Throwable> exceptionClass, String message) {
        Throwable failure = getFailure();

        if (failure == null) {
            throw new AssertionError("Expected a failure, but the Uni completed with a result");
        }
        if (!exceptionClass.isInstance(failure)) {
            throw new AssertionError("Expected a failure of type " + exceptionClass + ", but it was a " + failure.getClass());
        }
        if (!failure.getMessage().contains(message)) {
            throw new AssertionError("Expected a failure with a message containing '" + message + "', but it was '" + failure.getMessage() + "'");
        }
        return this;
    }

    public String getOnResultThreadName() {
        return onResultThreadName;
    }

    public String getOnErrorThreadName() {
        return onErrorThreadName;
    }

    public void cancel() {
        this.subscription.cancel();
    }

    public AssertSubscriber<T> assertHasNotBeenCompleted() {
        if (this.future == null) {
            throw new IllegalStateException("No subscription");
        }
        if (this.future.isDone()  || future.isCompletedExceptionally()) {
            throw new AssertionError("The uni completed");
        }
        return this;
    }
}
