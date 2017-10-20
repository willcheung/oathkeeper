package com.contextsmith.utils;

import org.junit.Test;

import javax.management.Attribute;
import java.util.List;

public class LambdaTest {
    @Test
    public void listFromTuples() throws Exception {
        List<Attribute> attributes = Lambda.listFromTuples(Attribute::new, "Foo", "Bar", "Bubu", "Lala");
        System.out.println(attributes);
        attributes = Lambda.listFromTuples(Attribute::new, "Foo", "Bar", "Bubu", "Lala", "Hurga");
        System.out.println(attributes);
    }

}