package io.smallrye.reactive.streams.api.groups;

import io.smallrye.reactive.streams.api.Uni;
import io.smallrye.reactive.streams.api.tuples.Functions;
import io.smallrye.reactive.streams.api.tuples.Tuple4;
import io.smallrye.reactive.streams.api.tuples.Tuples;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class AndGroup4<T1, T2, T3, T4> extends AndGroupIterable<T1> {

    public AndGroup4(Uni<T1> source, Uni<? extends T2> o1, Uni<? extends T3> o2, Uni<? extends T4> o3) {
        super(source, Arrays.asList(o1, o2, o3));
    }

    public AndGroup4<T1, T2, T3, T4> awaitCompletion() {
        super.awaitCompletion();
        return this;
    }

    public Uni<Tuple4<T1, T2, T3, T4>> asTuple() {
        return combinedWith(Tuple4::of);
    }

    @SuppressWarnings("unchecked")
    public <O> Uni<O> combinedWith(Functions.Function4<T1, T2, T3, T4, O> combinator) {
        Function<List<?>, O> function = list -> {
            Tuples.ensureArity(list, 4);
            T1 item1 = (T1) list.get(0);
            T2 item2 = (T2) list.get(1);
            T3 item3 = (T3) list.get(2);
            T4 item4 = (T4) list.get(3);
            return combinator.apply(item1, item2, item3, item4);
        };
        return super.combinedWith(function);
    }


}
