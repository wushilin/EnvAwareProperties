package net.wushilin.props;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

public class EnvProperties {

    public interface Translator {
        String translate(String key, String prefix);
    }
    // --- Translators ---

    public static class DefaultTranslator implements Translator {
        @Override
        public String translate(String key, String prefix) {
            if (!key.startsWith(prefix)) {
                return null;
            }
            String trimmed = key.substring(prefix.length());

            // Handle "__" as "_"
            trimmed = trimmed.replaceAll("__", "\u0000"); // placeholder
            // Replace single "_" with "."
            trimmed = trimmed.replace('_', '.');
            // Restore "__" to "_"
            trimmed = trimmed.replace('\u0000', '_');

            return trimmed.toLowerCase();
        }
    }

    public static class HexTranslator implements Translator {
        @Override
        public String translate(String key, String prefix) {
            if (!key.startsWith(prefix)) {
                return null;
            }
            String hexPart = key.substring(prefix.length());
            return hexToString(hexPart);
        }

        private String hexToString(String hex) {
            if (hex.length() % 2 != 0) {
                throw new IllegalArgumentException("Invalid hex string: " + hex);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 2) {
                String byteHex = hex.substring(i, i + 2);
                int ch = Integer.parseInt(byteHex, 16);
                sb.append((char) ch);
            }
            return sb.toString();
        }
    }


    // --- Helpers to choose translator by flavor ---
    private static Translator getTranslator(String flavor) {
        if (flavor == null) {
            return new DefaultTranslator();
        }
        switch (flavor.toLowerCase()) {
            case "hex":
                return new HexTranslator();
            case "default":
            default:
                return new DefaultTranslator();
        }
    }

    // --- fromEnvironment ---

    public static Properties fromEnvironment(String prefix) {
        return fromEnvironment(prefix, (String)null);
    }

    public static Properties fromEnvironment(String prefix, String flavor) {
        return fromEnvironment(prefix, getTranslator(flavor));
    }

    public static Properties fromEnvironment(String prefix, Translator trans) {
        Properties props = new Properties();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String translated = trans.translate(entry.getKey(), prefix);
            if (translated != null) {
                props.setProperty(translated, entry.getValue());
            }
        }
        return props;
    }

    // --- fromSysProperties ---

    public static Properties fromSysProperties(String prefix) {
        return fromSysProperties(prefix, new DefaultTranslator());
    }

    public static Properties fromSysProperties(String prefix, String flavor) {
        return fromSysProperties(prefix, getTranslator(flavor));
    }

    public static Properties fromSysProperties(String prefix, Translator trans) {
        Properties props = new Properties();
        for (String key : System.getProperties().stringPropertyNames()) {
            String translated = trans.translate(key, prefix);
            if (translated != null) {
                props.setProperty(translated, System.getProperty(key));
            }
        }
        return props;
    }

}