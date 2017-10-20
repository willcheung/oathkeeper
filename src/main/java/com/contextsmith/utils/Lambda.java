package com.contextsmith.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Lambda {

    public static Tester test(Object o) {
        return new Tester(o);
    }

    public static class Tester {
        Object obj;
        Object result;
        boolean match = false;

        public Tester(Object o) {
            obj = o;
        }

        public <T,R> Tester match(Class<T> clazz, Function<T,R> handler) {
            if (clazz.isInstance(obj)) {
                match = true;
                result = handler.apply((T)obj);
            }
            return this;
        }

        public <R>  R or(R defaultValue) {
            return match ? (R)result : defaultValue;
        }
    }

    @SafeVarargs
    public static <S,R> List<R> listFromTuples(BiFunction<S,S,R> provider, S... tuples) {
        return IntStream.range(0, tuples.length/2).mapToObj(i -> provider.apply(tuples[i*2], tuples[i*2+1])).collect(Collectors.toList());
    }

    public static <S> List<List<S>> listFromTuples(S... tuples) {
        return listFromTuples((a,b) -> { List<S> pair = new ArrayList<>(); pair.add(a); pair.add(b); return pair; }, tuples);
    }

    public static void main(String... args) {
        String result = test("bubu").match(String.class, s -> "lala").or("default");

    }
}

