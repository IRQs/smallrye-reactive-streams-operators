package io.smallrye.reactive.streams.api.impl;

import io.smallrye.reactive.streams.api.AssertSubscriber;
import io.smallrye.reactive.streams.api.Uni;
import org.junit.Test;

public class UniMapToTest {

    private Uni<Integer> one = Uni.of(1);

    @Test(expected = NullPointerException.class)
    public void testThatClassCannotBeNull() {
        one = Uni.of(1);
        one.map().to(null);
    }

    @Test
    public void testOkCast() {
        one.map().to(Number.class).subscribe().<AssertSubscriber<Number>>withSubscriber(AssertSubscriber.create())
                .assertCompletedSuccessfully().assertResult(1);
    }

    @Test
    public void testFailingCast() {
        one.map().to(String.class).subscribe().<AssertSubscriber<String>>withSubscriber(AssertSubscriber.create())
                .assertCompletedWithFailure()
                .assertFailure(ClassCastException.class, "String");
    }

}
