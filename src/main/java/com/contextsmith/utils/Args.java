package com.contextsmith.utils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simple command line parser.
 * Created by beders on 6/6/17.
 */
public class Args {
    List<Matcher> matchers;
    ArgsIterator args;

    public Args() {
        matchers = new ArrayList<>();
    }

    public Args(ArgsIterator args) {
        this.args = args;
    }

    public Args on(String option, Runnable handler) {
        return on(options(option), handler);
    }

    public Args on(String option, Consumer<String> handler) {
        return on(options(option), handler);
    }

    public Args on(Predicate<String> test, Runnable handler) {
        Matcher m = args -> {
            boolean p = test.test(args.arg);
            if (p) {
                handler.run();
            }
            return p;
        };
        runNowOrLater(m);
        return this;
    }

    public Args on(Predicate<String> test, Consumer<String> handler) {
       Matcher m = args -> {
            boolean p = test.test(args.arg);
            if (p) {
                handler.accept(args.value());
            }
            return p;
        };
        runNowOrLater(m);
        return this;
    }

    public Args rest(Consumer<List<String>> restHandler) {
        Matcher m = args -> {
            restHandler.accept(args.rest());
            return true;
        };
        runNowOrLater(m);
        return this;
    }

    protected void runNowOrLater(Matcher m) {
        if (args != null) {
            while (args.hasNext()) {
                args.matchNext(m);
            }
            args.reset();
        } else {
            matchers.add(m);
        }
    }

    public void parse(String... someArgs) {
        ArgsIterator args = new ArgsIterator(someArgs);
        while (args.hasNext()) {
            args.matchNext(matchers);
        }
    }

    boolean optionMatches(String option, ArgsIterator args) {
        return args.arg.equalsIgnoreCase(option);
    }

    public static Predicate<String> options(String... alternatives) {
        if (alternatives.length == 1) {
            return s -> alternatives[0].equalsIgnoreCase(s);
        }
        return s -> Arrays.stream(alternatives).anyMatch(alt -> alt.equalsIgnoreCase(s));
    }

    interface RestConsumer {
        void accept(List<String> rest);
    }

    interface Matcher {
        /** @return true if option was consumed **/
        boolean match(ArgsIterator args);
    }

    public static Args match() {
        return new Args();
    }

    public static Args match(String... args) {
        Args cmd = new Args(new ArgsIterator(args));
        return cmd;
    }

    static class ArgsIterator {
        String[] allArgs;
        String arg;
        BitSet consumed;
        int current;

        ArgsIterator(String[] args) {
            this.allArgs = analyzeArgs(args);
            if (args.length > 0) {
                arg = args[current];
            }
        }

        protected String[] analyzeArgs(String[] args) {
            consumed = new BitSet(args.length);
            return args;
        }

        String value() {
            String value = (current + 1 >= allArgs.length) ? null : allArgs[current + 1];
            if (value != null) {
                consumed.set(current + 1);
            }
            return value;
        }

        List<String> rest() {
            // all unconsumed options
            return IntStream.range(0, allArgs.length).filter(i -> !consumed.get(i)).mapToObj(i -> { consumed.set(i); return allArgs[i];}).collect(Collectors.toList());
        }

        void matchNext(List<Matcher> matchers) {
            matchers.forEach(m -> {
                int currentOption = current;
                if (m.match(this)) {
                    consumed.set(currentOption);
                }
            });
            advance();
        }

        void matchNext(Matcher matcher) {
            int currentOption = current;
            if (matcher.match(this)) {
                consumed.set(currentOption);
            }
            advance();
        }

        void advance() {
            current++;
            // find next unconsumed
            while (consumed.get(current) && hasNext()) current++;
            if (hasNext()) {
                arg = allArgs[current];
            }
        }

        boolean hasNext() {
            return current < allArgs.length;
        }

        void reset() {
            current = 0;
            arg = allArgs[current];
        }
    }
}
