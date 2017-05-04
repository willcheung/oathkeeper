package com.contextsmith.utils;

import org.apache.commons.lang.StringUtils;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Helper to support different deployment modes
 * Created by beders on 4/11/17.
 */
public class Environment {
    static {
        Optional<String> s = Stream.of(System.getenv("MODE"), System.getProperty("mode")).filter(StringUtils::isNotEmpty).findFirst();
        switch (s.orElse("production")) {
            case "production":
                production = true;
                dev = false;
                System.out.println("Running in PRODUCTION mode");
                break;
            default:
                production = false;
                dev = true;
                System.out.println("Running in DEVELOPMENT mode");
                break;
        }

    }
    public static final boolean dev;
    public static final boolean production;

    private Environment() {
    }


}
