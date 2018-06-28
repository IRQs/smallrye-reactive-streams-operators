package io.smallrye.reactive.streams.utils;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class WrappedSubscriber<T> implements Subscriber<T> {

  private final CompletableFuture<Void> future = new CompletableFuture<>();
  private final Subscriber<T> source;
  private final AtomicBoolean subscribed = new AtomicBoolean(false);

  public WrappedSubscriber(Subscriber<T> delegate) {
    this.source = delegate;
  }

  public CompletionStage<Void> future() {
    return future;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    Objects.requireNonNull(subscription);
    if (subscribed.compareAndSet(false, true)) {
      source.onSubscribe(new WrappedSubscription(subscription, () ->
        future.completeExceptionally(new CancellationException())));
    } else {
      subscription.cancel();
    }
  }

  @Override
  public void onNext(T item) {
    source.onNext(Objects.requireNonNull(item));
  }

  @Override
  public void onError(Throwable throwable) {
    future.completeExceptionally(Objects.requireNonNull(throwable));
    source.onError(throwable);
  }

  @Override
  public void onComplete() {
    future.complete(null);
    source.onComplete();
  }

}
