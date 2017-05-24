package com.contextsmith.api.service;

import com.contextsmith.utils.StringUtil;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.mail.internet.InternetAddress;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by beders on 5/13/17.
 */
public class SourceConfigurationTest {
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void readEmptySourceConfiguration() {
        String s = "{ \"sources\": []}";
        SourceConfiguration sc = StringUtil.getGsonInstance().fromJson(s, SourceConfiguration.class);
        assertNotNull(sc.sources);
        assertTrue(sc.sources.length == 0);
    }

    @Test
    public void readSourceConfiguration() {
        String s2 = "{ \"sources\": [ { \"kind\": \"gmail\", \"url\": \"http://bild.de\" } ] }";
        SourceConfiguration sc = StringUtil.getGsonInstance().fromJson(s2, SourceConfiguration.class);
        assertNotNull(sc.sources);
        assertTrue(sc.sources.length == 1);
        assertEquals(NewsFeederRequest.Provider.gmail, sc.sources[0].kind);

        s2 = "{ \"sources\": [ { \"kind\": \"exchange\", \"token\": \"12345\", \"url\": \"http://bild.de\" } ] }";
        sc = StringUtil.getGsonInstance().fromJson(s2, SourceConfiguration.class);
        assertNotNull(sc.sources);
        assertTrue(sc.sources.length == 1);
        assertEquals(NewsFeederRequest.Provider.exchange, sc.sources[0].kind);
        assertNotNull(sc.sources[0].token);
        assertNotNull(sc.sources[0].url);

    }

    @Test
    public void readExternalClusters() {
        String[] testAddresses = { "user1@stark.com", "user2@stark.com" };
        JsonArray cluster = new JsonArray();
        for (String a: testAddresses) cluster.add(a);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);
        JsonObject object = new JsonObject();
        object.add("sources", new JsonArray());
        object.add("external_clusters", clusters);
        String s = StringUtil.getGsonInstance().toJson(object);
        System.out.println(s);
        SourceConfiguration sc = StringUtil.getGsonInstance().fromJson(s, SourceConfiguration.class);
        assertNotNull(sc.rawExternalClusters);
        assertTrue(sc.rawExternalClusters.length == 1);
        assertEquals(testAddresses[0], sc.rawExternalClusters[0][0]);
        assertEquals(testAddresses[1], sc.rawExternalClusters[0][1]);

        List<Set<InternetAddress>> ext = sc.getExternalClusters();
        assertEquals(ext.size(), 1);
        assertEquals(ext.get(0).size(), testAddresses.length);
        Arrays.equals(testAddresses, ext.get(0).stream().map(InternetAddress::getAddress).toArray());

        /* s2 = "{ \"sources\": [ { \"kind\": \"exchange\", \"token\": \"12345\", \"url\": \"http://bild.de\" } ] }";
        sc = StringUtil.getGsonInstance().fromJson(s2, SourceConfiguration.class);
        assertNotNull(sc.sources);
        assertTrue(sc.sources.length == 1);
        assertEquals(NewsFeederRequest.Provider.exchange, sc.sources[0].kind);
        assertNotNull(sc.sources[0].token);
        assertNotNull(sc.sources[0].url);
*/
    }

}