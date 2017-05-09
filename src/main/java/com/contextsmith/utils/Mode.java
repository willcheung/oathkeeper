package com.contextsmith.utils;

import java.util.Optional;
import java.util.function.Supplier;

/** Enum of all available run modes */
public enum Mode {
    production, dev, test;

    public Mode is(Mode mode, Runnable code) {
        if (this == mode) {
            code.run();
        }
        return this;
    }
}
