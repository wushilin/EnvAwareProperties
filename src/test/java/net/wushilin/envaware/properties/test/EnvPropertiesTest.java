package net.wushilin.envaware.properties.test;

import net.wushilin.props.EnvProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class EnvPropertiesTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("SYS_ENV_DATABASE__URL");
        System.clearProperty("SYS_ENV_DATABASE__USER");
        System.clearProperty("SYS_ENV_6D792E6B6579");
        System.clearProperty("SYS_ENV_64617461626173652E75736572");
    }

    @Test
    void testDefaultTranslatorSimple() {
        EnvProperties.DefaultTranslator trans = new EnvProperties.DefaultTranslator();
        String result = trans.translate("ENV_DATABASE__URL", "ENV_");
        String result1 = trans.translate("ENV_DATABASE_URL", "ENV_");
        assertEquals("database_url", result);
        assertEquals("database.url", result1);
    }

    @Test
    void testDefaultTranslatorUnderscorePreserved() {
        EnvProperties.DefaultTranslator trans = new EnvProperties.DefaultTranslator();
        String result = trans.translate("ENV_MY__NAME", "ENV_");
        assertEquals("my_name", result);
    }

    @Test
    void testHexTranslatorDecoding() {
        EnvProperties.HexTranslator trans = new EnvProperties.HexTranslator();
        String result = trans.translate("ENV_6D792E6B6579", "ENV_"); // "my.key"
        assertEquals("my.key", result);
    }

    @Test
    void testFromSysPropertiesDefault() {
        System.setProperty("SYS_ENV_DATABASE__URL", "jdbc:mysql://...");
        System.setProperty("SYS_ENV_DATABASE_USER", "root");

        Properties props = EnvProperties.fromSysProperties("SYS_ENV_");
        assertEquals("jdbc:mysql://...", props.getProperty("database_url"));
        assertEquals("root", props.getProperty("database.user"));
    }

    @Test
    void testFromSysPropertiesHexTranslator() {
        System.setProperty("SYS_ENV_64617461626173652E75736572", "root"); // "database.user"

        Properties props = EnvProperties.fromSysProperties("SYS_ENV_", "hex");
        assertEquals("root", props.getProperty("database.user"));
    }

    @Test
    void testUnknownFlavorFallsBackToDefault() {
        System.setProperty("SYS_ENV_DATABASE__USER", "root");

        Properties props = EnvProperties.fromSysProperties("SYS_ENV_", "nonsense");
        assertEquals("root", props.getProperty("database_user"));
    }
}
