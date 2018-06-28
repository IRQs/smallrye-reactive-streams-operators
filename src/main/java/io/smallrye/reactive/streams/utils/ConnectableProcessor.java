package io.smallrye.reactive.streams.utils;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A processor forwarding to a subscriber. This is used to connect a "next to be" producer.
 */
public class ConnectableProcessor<T> implements Processor<T, T> {


  private enum State {
    IDLE, // Start state
    HAS_SUBSCRIBER, // When we get a subscriber
    HAS_SUBSCRIPTION, // When we get a subscription
    PROCESSING, // Processing started
    FAILED, // Caught an error, final state
    COMPLETE // Completed, final state
  }

  /**
   * Reference of the subscriber if any.
   * If set the state is HAS_SUBSCRIBER+
   */
  private final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();


  /**
   * Reference on the subscription if any.
   * If set the state is HAS_SUBSCRIPTION+
   */

  private final AtomicReference<Subscription> subscription = new AtomicReference<>();

  /**
   * Reported failure if any.
   * If set the state if FAILED
   */
  private final AtomicReference<Throwable> failure = new AtomicReference<>();

  /**
   * Current state.
   */
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    Objects.requireNonNull(subscriber);

    // Set the subscriber, if we already have one report an error as we do not support multicasting.
    if (!this.subscriber.compareAndSet(null, subscriber)) {
      subscriber.onSubscribe(new EmptySubscription());
      subscriber.onError(new IllegalStateException("Multicasting not supported"));
      return;
    }

    // Set the state, if failed, report the error
    if (!state.compareAndSet(State.IDLE, State.HAS_SUBSCRIBER)) {
      // We were not in the idle state, the behavior depends on our current state
      // For failure and completed, we just creates an empty subscription and immediately report the error or completion
      if (state.get() == State.FAILED) {
        subscriber.onSubscribe(new EmptySubscription());
        subscriber.onError(failure.get());
        return;
      }

      if (state.get() == State.COMPLETE) {
        subscriber.onSubscribe(new EmptySubscription());
        subscriber.onComplete();
      }

      // We already have a subscription, use it.
      // However, we could complete of failed in the meantime.
      if (state.get() == State.HAS_SUBSCRIPTION) {
        subscriber.onSubscribe(
          new WrappedSubscription(subscription.get(),
            () -> this.subscriber.set(new CancellationSubscriber<>()))
        );
        if (!state.compareAndSet(State.HAS_SUBSCRIPTION, State.PROCESSING)) {
          if (state.get() == State.FAILED) {
            subscriber.onError(failure.get());
            return;
          }
          if (state.get() == State.COMPLETE) {
            subscriber.onComplete();
          }
        }
      }
    }
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    Objects.requireNonNull(subscription);
    // We already have a subscription, cancel the received one.
    if (!this.subscription.compareAndSet(null, subscription)) {
      subscription.cancel();
      return;
    }

    // Handle the transition: IDLE -> HAS_SUBSCRIPTION.
    if (!state.compareAndSet(State.IDLE, State.HAS_SUBSCRIPTION)) {
      state.set(State.PROCESSING);
      subscriber.get().onSubscribe(new WrappedSubscription(subscription,
        () -> subscriber.set(new CancellationSubscriber<>())));
    }
  }

  @Override
  public void onNext(T item) {
    Objects.requireNonNull(item);
    Subscriber<? super T> actualSubscriber = this.subscriber.get();
    if (actualSubscriber == null) {
      throw new IllegalStateException("No subscriber - cannot handle onNext");
    } else {
      actualSubscriber.onNext(item);
    }
  }

  @Override
  public void onComplete() {
    if (state.get() == State.PROCESSING) {
      subscriber.get().onComplete();
    } else if (state.get() == State.FAILED || state.get() == State.COMPLETE || state.get() == State.IDLE) {
      throw new IllegalStateException("Invalid transition, cannot handle onComplete in " + state.get().name());
    }
    state.set(State.COMPLETE);
  }

  @Override
  public void onError(Throwable throwable) {
    Objects.requireNonNull(throwable);
    this.failure.set(throwable);
    if (state.get() == State.PROCESSING) {
      subscriber.get().onError(throwable);
    } else if (state.get() == State.FAILED || state.get() == State.COMPLETE || state.get() == State.IDLE) {
      throw new IllegalStateException("Invalid transition, cannot handle onError in " + state.get().name());
    }
    state.set(State.FAILED);
  }


}
