package com.contextsmith.utils;

import org.apache.commons.lang.StringUtils;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Helper to support different deployment modes
 * Created by beders on 4/11/17.
 */
public class Environment {
    public static final Mode mode;

    static {
        Optional<String> s = Stream.of(System.getenv("MODE"), System.getProperty("mode")).filter(StringUtils::isNotEmpty).findFirst();
        mode = Mode.valueOf(s.orElse("production"));
        is(Mode.production, () -> System.out.println("Running in PRODUCTION mode"))
                .is(Mode.dev, () -> System.out.println("Running in DEVELOPMENT mode"))
                .is(Mode.test, () -> System.out.println("Running in TEST mode"));

    }

    public static Mode is(Mode aMode, Runnable code) {
        return mode.is(aMode, code);
    }

    private Environment() {
    }

}
