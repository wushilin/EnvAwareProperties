package net.wushilin.envaware.properties.test;

import net.wushilin.props.EnvAwareProperties;
import org.junit.jupiter.api.*;
import java.io.*;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBuilder {
    @BeforeAll
    public static void beforeAll() {
    }

    @AfterAll
    public static void afterAll() {
    }

    @BeforeEach
    public void beforeEach() {
    }

    @AfterEach
    public void afterEach() {
    }
    @Test
    public void doTestBuilder() throws FileNotFoundException {
        Properties defaultP = EnvAwareProperties.newBuilder().override("k1", "${ENV1}").build();
        assertEquals(defaultP.getProperty("k1"), "A long story with ENV1");
        assertEquals(defaultP.keySet().size(), 1);
        System.out.println(defaultP);

        Properties defaultP2 = EnvAwareProperties.newBuilder().disableAllJProperties().disableEnvironment().disableSysProperties().override("k1", "${ENV1}").build();
        assertEquals(defaultP2.getProperty("k1"), "${ENV1}");
        assertEquals(defaultP2.keySet().size(), 1);
        System.out.println(defaultP2);

        Properties defaultP3 = EnvAwareProperties.newBuilder()
                .thenAddPropertiesFilePath("testdata/test1.properties")
                .thenAddPropertiesFilePath("testdata/test2.properties")
                .thenAddPropertiesFilePath("testdata/test3.properties")
                .build();
        assertEquals(defaultP3.size(), 6);
        assertEquals(defaultP3.getProperty("test1.k1"), "test1.v1");
        assertEquals(defaultP3.getProperty("test1.k2"), "test1.v2");
        assertEquals(defaultP3.getProperty("test2.k1"), "test2.v1");
        assertEquals(defaultP3.getProperty("test2.k2"), "test2.v2");
        assertEquals(defaultP3.getProperty("test3.k1"), "test3.v1");
        assertEquals(defaultP3.getProperty("test3.k2"), "test3.v2");

        System.out.println(defaultP3);
        Properties defaultP4 = EnvAwareProperties.newBuilder()
                .thenAddPropertiesFilePath("testdata/test3.properties", "testdata/test1.properties", "testdata/test1.properties")
                .build();
        assertEquals(defaultP4.size(), 6);
        assertEquals(defaultP4.getProperty("test1.k1"), "test1.v1.override.test3");
        assertEquals(defaultP4.getProperty("test1.k2"), "test1.v2.override.test3");
        assertEquals(defaultP4.getProperty("test2.k1"), "test2.v1.override.test3");
        assertEquals(defaultP4.getProperty("test2.k2"), "test2.v2.override.test3");
        assertEquals(defaultP4.getProperty("test3.k1"), "test3.v1");
        assertEquals(defaultP4.getProperty("test3.k2"), "test3.v2");
        System.out.println(defaultP4);


        Properties defaultP5 = EnvAwareProperties.newBuilder()
                .thenAddPropertiesFilePath("testdata/test3.properties")
                .thenAddPropertiesFile(new File("testdata/test2.properties"))
                .thenAddInputStream(new FileInputStream("testdata/test1.properties"))
                .build();

        assertEquals(defaultP5.size(), 6);
        assertEquals(defaultP5.getProperty("test1.k1"), "test1.v1.override.test3");
        assertEquals(defaultP5.getProperty("test1.k2"), "test1.v2.override.test3");
        assertEquals(defaultP5.getProperty("test2.k1"), "test2.v1.override.test3");
        assertEquals(defaultP5.getProperty("test2.k2"), "test2.v2.override.test3");
        assertEquals(defaultP5.getProperty("test3.k1"), "test3.v1");
        assertEquals(defaultP5.getProperty("test3.k2"), "test3.v2");
        System.out.println(defaultP5);

        Properties defaultP6 = EnvAwareProperties.newBuilder()
                .thenAddPropertiesFromClasspath("/testdata/test3.properties")
                .thenAddPropertiesFromClasspath("/testdata/test2.properties")
                .thenAddPropertiesFromClasspath("/testdata/test1.properties")
                .build();

        assertEquals(defaultP6.size(), 6);
        assertEquals(defaultP6.getProperty("test1.k1"), "test1.v1.override.test3");
        assertEquals(defaultP6.getProperty("test1.k2"), "test1.v2.override.test3");
        assertEquals(defaultP6.getProperty("test2.k1"), "test2.v1.override.test3");
        assertEquals(defaultP6.getProperty("test2.k2"), "test2.v2.override.test3");
        assertEquals(defaultP6.getProperty("test3.k1"), "test3.v1");
        assertEquals(defaultP6.getProperty("test3.k2"), "test3.v2");

        EnvAwareProperties defaultP7 = EnvAwareProperties.newBuilder().thenAddPropertiesFilePath("testdata/resolve.properties")
                .override("k9", "k9v")
                .override("test123", "test123")
                .override("keytest123", "${k1}").build();
        System.out.println(defaultP7);
        System.out.println(defaultP7.getPropertyResolve("key${${k2value1}}"));

        EnvAwareProperties defaultP8 = EnvAwareProperties.newBuilder().thenAddPropertiesFilePath("testdata/defaultp8.properties").build();
        System.out.println(defaultP8.partition("root"));
        System.out.println(defaultP8.partition("root."));
        System.out.println(defaultP8.partition("root").partition("ns1"));
        System.out.println(defaultP8.partition("root.").partition("ns2."));
    }
}