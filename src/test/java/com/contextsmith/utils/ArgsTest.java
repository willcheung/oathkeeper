package com.contextsmith.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.contextsmith.utils.Args.options;
import static org.junit.Assert.*;

/**
 * Created by beders on 6/6/17.
 */
public class ArgsTest {
    String val = null;

    @Test
    public void match() throws Exception {
        String[] args = { "--dir", "test" };
        final boolean[] gotOption = new boolean[1];
        Args.match().on("--dir", () -> gotOption[0] = true).parse(args);
        assertTrue(gotOption[0]);

    }

    @Test
    public void consume() throws Exception {
        String[] args = { "--dir", "test" };
        Args.match().on("--dir", value -> val = value).parse(args);
        assertEquals(args[1], val);
    }

    @Test
    public void consumeNow() throws Exception {
        String[] args = { "--dir", "test" };
        Args.match(args).on("--dir", value -> val = value);
        assertEquals(args[1], val);
    }

    @Test
    public void rest() throws Exception {
        String[] args = { "--dir", "test", "--src", "bubu", "r1", "r2", "r3"};
        String[] rest = Arrays.copyOfRange(args, 2, args.length);
        Args.match(args).on("--dir", value -> val = value).rest(restArgs -> assertEquals(Arrays.asList(rest), restArgs));
        Args.match().on("--dir", value -> val = value).rest(restArgs -> assertEquals(Arrays.asList(rest), restArgs)).parse(args);

    }

    @Test
    public void alternatives() throws Exception {
        String[] args = {"--dir", "test", "-d", "bubu", "r1", "r2", "r3"};
        List<String> values = new ArrayList<>();
        List<String> expected = Arrays.asList(new String[] { "test", "bubu" });
        Args.match().on(options("--dir", "-d"), values::add).parse(args);
        assertEquals(expected, values);

    }
}