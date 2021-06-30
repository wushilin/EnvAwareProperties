package net.wushilin.props;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvAwareProperties extends Properties {
    private Properties env = null;
    // Threshold when CircularReferenceException will be thrown
    private static final int MAX_DEPTH = 500;
    /**
     * Create a EnvAwareProperties using base properties.
     * For value lookup, the passed in env will be looked up first
     * if not found, system.getProperties will be looked up
     * if not found, system.getEnv will be looked up
     * @param defaults The base properties to lookup. It may contain special place holder like ${key}
     *            and it will be resolved as much as we can!
     */
    public EnvAwareProperties(Properties defaults) {
        super(defaults);
        this.env = chainedPropertiesOf(defaults, sysProps(), sysEnv());
    }

    /**
     * Same as fromFile, but getting path as string
     * @param path Path of file
     */
    public static EnvAwareProperties fromPath(String path) throws IOException {
        return fromFile(new java.io.File(path));
    }

    /**
     * Load properties from File
     * @param input the file to load from
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromFile(java.io.File input) throws IOException {
        try(FileInputStream fis = new FileInputStream(input)) {
            return fromInputStream(fis);
        }
    }

    /**
     * Load properties from inputStream
     * @param is the stream to load from
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromInputStream(InputStream is) throws IOException {
        Properties base = new Properties();
        base.load(is);
        return new EnvAwareProperties(base);
    }

    /**
     * Load properties from classpath
     * @param path Path of classpath
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromClassPath(String path) throws IOException {
        try(InputStream is = EnvAwareProperties.class.getResourceAsStream(path)) {
            return fromInputStream(is);
        }
    }

    /**
     * Get the environment of this properties
     * @return The environment
     */
    public Properties getEnv() {
        return env;
    }

    /**
     * Create a chained properties where resolve order of the chain.
     * @param p The properties list in the resolution order
     * @return The chained property, earlier properties will be resolved first
     */
    public static ChainedProperties chainedPropertiesOf(Properties p0, Properties...p) {
        return new ChainedProperties(p0, p);
    }

    /**
     * Get property from this properties, with ${variable} resolved to the value of variable in dictionary.
     * The resolution is recursive. you can have ${${key}} => value2 if in dict key = blah, blah = value2.
     * @param key The key to lookup. The key is also resolved! ${key} = 12 if key=blah and blah=12
     * @return The resolved value
     */
    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * Same as getProperty, but with a default value
     * @param key The key to lookup. The key is also resolved!
     * @param defaultValue The default value if resolve result is null
     * @return The resolved value
     */
    @Override
    public String getProperty(String key, String defaultValue) {
        String keyResolved = resolveEnv(key, env);
        String valueRaw = env.getProperty(keyResolved);
        String result = resolveEnv(valueRaw, env);
        if(result == null) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Convenient method to get system Properties as a new Properties
     * @return System properties as Properties (not new, a copy of System.getProperties())
     */
    public static Properties sysProps() {
        return System.getProperties();
    }

    /**
     * Convenient method to get system environment as a new Properties
     * @return System environment variables as Properties
     */
    public static Properties sysEnv() {
        Map<String, String> envMap = System.getenv();
        Properties p = new Properties();
        p.putAll(envMap);
        return p;
    }

    private static String resolveEnv(String raw, Properties env) {
        try {
            return resolveEnv(raw, env, 0);
        } catch(CircularReferenceException ex) {
            return raw;
        }
    }
    private static String resolveEnv(String raw, Properties env, int depth) throws CircularReferenceException {
        if(depth >= MAX_DEPTH) {
            throw new CircularReferenceException("Possible resolution loop!");
        }
        if(raw == null) {
            return null;
        }
        Pattern p = Pattern.compile("\\$\\{([a-zA-Z0-9-_.]+)\\}");
        String result = raw;
        Matcher m = p.matcher(raw);
        while(m.find()) {
            String key = m.group(1);
            String value = env.getProperty(key);
            if(value != null) {

                result = result.replaceAll(Pattern.quote("${"+key+"}"), Matcher.quoteReplacement(value));
            }
        }
        if(result.equals(raw)) {
            // no more solution possible
            return result;
        } else {
            return resolveEnv(result, env, depth + 1);
        }
    }

    public static void main(String[] args) {
        String raw = "hello ${${key.1}}";
        Properties dict = new Properties();
        dict.put("key.1", "${key.2}");
        dict.put("key.2", "${key.1}");

        Properties dic1 = new Properties();
        dic1.put("key.1", "value.3");
        dic1.put("key.3", "value.3");

        Properties dic2 = new Properties();
        dic2.put("key.1", "value.32");
        dic2.put("key.33", "${java.class.path}");
        dic2.put("key.44", "key.33");
        dic2.put("/Users/shwu", "Blah");
        ChainedProperties cp = chainedPropertiesOf(dict, dic1, dic2);

        EnvAwareProperties ep = new EnvAwareProperties(cp);
        System.out.println(ep.getProperty("key.1"));
        System.out.println(ep.getProperty("LANG"));
        System.out.println(ep.getProperty("${user.home}"));
        System.out.println(ep.getEnv());
    }
}

/**
 * When circular reference detected, this exception helps to detect it.
 * However it is not exposed.
 */
class CircularReferenceException extends Exception {
    public CircularReferenceException(String msg) {
        super(msg);
    }
}
/**
 * A chained properties.
 * All methods will be delegated to first Properties in chain, except for getProperty(key), getProperty(key, default)
 *
 * Updates will update the first properties
 */
class ChainedProperties extends Properties {
    private List<Properties> chain;
    public ChainedProperties(Properties basic, Properties ...rest) {
        super(basic);
        chain = new ArrayList<>();
        chain.add(basic);
        chain.addAll(Arrays.asList(rest));
    }

    /**
     * Resolve the property in chained order
     * see #Properties.getProperty(String key)
     */
    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    public Optional<String> getPropertyOptional(String key) {
        Optional<String> result = Optional.empty();
        for(Properties next:chain) {
            String value = next.getProperty(key);
            if(value != null) {
                result = result.or(()->Optional.of(value));
            }
        }
        return result;
    }

    /**
     * Add resolution chain
     * @param next The next property to chain up
     * @return this object
     */
    public ChainedProperties append(Properties next) {
        chain.add(next);
        return this;
    }

    /**
     * Prepend the resolution chain
     * @param next the new base properties (highest priority). It will also take all writes and modifications
     * @return this object
     */
    public ChainedProperties prepend(Properties next) {
        chain.add(0, next);
        return this;
    }
    /**
     * Resolve the property in chained order, with default value
     * @param  key The key to lookup
     * @param  defaultValue The default value
     * @return the resolved property
     */
    public String getProperty(String key, String defaultValue) {
        return getPropertyOptional(key).orElse(defaultValue);
    }

    public String toString() {
        return this.chain.toString();
    }
}

