package net.wushilin.props;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * EnvAware properties allows you to chain multiple properties, with resolution order,
 * as well as placing environment varible, as well as reference other config properties
 * so your properties are not the same ever again!
 * <p>
 * Updates to this properties always goes to the flattened (resolved) properties!
 * After constructor, this is just a plain properties, except that it offers a getPropertyResolve that
 * does a little bit of magic!
 */
public class EnvAwareProperties extends Properties {
    public static class Builder {
        private List<Object> target;
        private boolean enableCwdJProperties;
        private boolean enableHomeJProperties;
        private boolean enableRootJProperties;
        private boolean enableEnvironment;
        private boolean enableSysProperties;
        private Properties overrides;

        public Builder() {
            target = new ArrayList<Object>();
            enableCwdJProperties = true;
            enableHomeJProperties = true;
            enableRootJProperties = true;
            enableEnvironment = true;
            enableSysProperties = true;
            overrides = new Properties();
            target.add(overrides);
        }

        public Builder disableAllJProperties() {
            return this.disableCwdJProperties().disableHomeJProperties().disableRootJProperties();
        }

        public Builder disableHomeJProperties() {
            this.enableHomeJProperties = false;
            return this;
        }

        public Builder disableRootJProperties() {
            this.enableRootJProperties = false;
            return this;
        }

        public Builder disableCwdJProperties() {
            this.enableCwdJProperties = false;
            return this;
        }

        public Builder disableEnvironment() {
            this.enableEnvironment = false;
            return this;
        }

        public Builder disableSysProperties() {
            this.enableSysProperties = false;
            return this;
        }

        public Builder enableAllJProperties() {
            return this.enableCwdJProperties().enableHomeJProperties().enableRootJProperties();
        }

        public Builder enableHomeJProperties() {
            this.enableHomeJProperties = true;
            return this;
        }

        public Builder enableRootJProperties() {
            this.enableRootJProperties = true;
            return this;
        }

        public Builder enableCwdJProperties() {
            this.enableCwdJProperties = true;
            return this;
        }

        public Builder override(String key, String value) {
            this.overrides.setProperty(key, value);
            return this;
        }

        public Builder deleteOverride(String key) {
            this.overrides.remove(key);
            return this;
        }

        public Builder clearOverrides() {
            this.overrides.clear();
            return this;
        }

        public Builder enableEnvironment() {
            this.enableEnvironment = true;
            return this;
        }

        public Builder enableSysProperties() {
            this.enableSysProperties = true;
            return this;
        }

        public Builder thenAddInputStream(InputStream... istreams) {
            for (InputStream istream : istreams) {
                this.target.add(istream);
            }
            return this;
        }

        public Builder thenAddReader(Reader... readers) {
            for (Reader reader : readers)
                this.target.add(reader);
            return this;
        }

        public Builder thenAddPropertiesFile(File... files) {
            Builder result = this;

            for (File next : files) {
                result.target.add(next);
            }
            return result;
        }

        public Builder thenAddProperties(Properties... p) {
            for (Properties next : p) {
                this.target.add(next);
            }
            return this;
        }

        public Builder thenAddMap(Map<String, String>... p) {
            for (Map<String, String> next : p) {
                this.target.add(p);
            }
            return this;
        }

        public Builder thenAddPropertiesFilePath(String... files) {
            Builder result = this;
            for (String next : files) {
                result = result.thenAddPropertiesFile(new File(next));
            }
            return result;
        }

        public Builder thenAddPropertiesFromClasspath(String... classpaths) {
            for (String classpath : classpaths) {
                this.target.add(classpath);
            }
            return this;
        }

        public Builder removeAll() {
            this.target.clear();
            return this;
        }

        public EnvAwareProperties build() {
            return new EnvAwareProperties(this.enableCwdJProperties, this.enableHomeJProperties, this.enableRootJProperties, this.enableEnvironment, this.enableSysProperties, target);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * This controls max resolution depth where a property is referencing another, the other
     * properties might reference more other properties.
     */
    private static final int MAX_DEPTH = 500;

    private boolean enableCwdJProperties = true;
    private boolean enableHomeJProperties = true;
    private boolean enableRootJProperties = true;
    private boolean enableEnvironment = true;
    private boolean enableSysProperties = true;

    private EnvAwareProperties(boolean enableCwdJProperties, boolean enableHomeJProperties,
                              boolean enableRootJProperties, boolean enableEnv, boolean enableSysProperties,
                              List<Object> targets) {
        this.enableCwdJProperties = enableCwdJProperties;
        this.enableHomeJProperties = enableHomeJProperties;
        this.enableRootJProperties = enableRootJProperties;
        this.enableEnvironment = enableEnv;
        this.enableSysProperties = enableSysProperties;
        loadAll(targets.toArray());
    }

    private void loadAll(Object... target) {
        List<Properties> candidates = new ArrayList<Properties>(target.length);
        for (Object next : target) {
            if (next == null) {
                throw new NullPointerException("Null object received");
            }
            try {
                candidates.add(toProperties(next));
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        initialize(candidates.toArray(new Properties[0]));
    }

    private void initialize(Properties... toLoad) {
        Properties resolved = new Properties();
        Set<String> keys = new HashSet<String>();
        List<Properties> list = new ArrayList<>();
        list.addAll(Arrays.asList(toLoad));
        List<Object> extraKeys = new ArrayList<>();
        List<Properties> extraList = new ArrayList<>();

        if (this.enableCwdJProperties) {
            extraList.add(fromCurrentDirectoryEnv());
        }
        if (this.enableHomeJProperties) {
            extraList.add(fromHomeEnv());
        }
        if (this.enableRootJProperties) {
            extraList.add(fromRootEnv());
        }
        if (this.enableSysProperties) {
            extraList.add(sysProps());
        }
        if (this.enableEnvironment) {
            extraList.add(sysEnv());
        }
        for (Properties next : list) {
            for (Object nextKey : next.keySet()) {
                resolved.putIfAbsent(nextKey, next.get(nextKey));
                keys.add((String) nextKey);
            }
        }

        /**
         * Extra env keys will be removed at last
         */
        for (Properties next : extraList) {
            for (Object nextKey : next.keySet()) {
                if (!resolved.containsKey(nextKey)) {
                    extraKeys.add(nextKey);
                    resolved.put(nextKey, next.get(nextKey));
                }
                keys.add((String) nextKey);
            }
        }

        for (String nextKey : keys) {
            String originalKey = nextKey;
            String originalValue = resolved.getProperty(nextKey);
            String resolvedKey = resolveEnv(nextKey, resolved);
            //String valueRaw = resolved.getProperty(resolvedKey);
            String resolvedValue = resolveEnv(originalValue, resolved);
            setProperty(originalKey, resolvedValue);
        }


        for (Object extraK : extraKeys) {
            remove(extraK);
        }
    }

    private Properties toProperties(Object next) throws IOException {
        if (next instanceof InputStream) {
            Properties p = new Properties();
            p.load((InputStream) next);
            return p;
        } else if (next instanceof Reader) {
            Properties p = new Properties();
            p.load((Reader) next);
            return p;
        } else if (next instanceof File) {
            try (FileInputStream fis = new FileInputStream((File) next)) {
                return toProperties(fis);
            }
        } else if (next instanceof Properties) {
            return (Properties) next;
        } else if (next instanceof Map) {
            Properties p = new Properties();
            Set<Map.Entry> entries = ((Map) next).entrySet();
            for (Map.Entry nextEntry : entries) {
                Object ko = nextEntry.getKey();
                Object vo = nextEntry.getValue();
                if (!(ko instanceof String) || !(vo instanceof String)) {
                    throw new IllegalArgumentException("Map must be string -> string. Found " + ko.getClass() + " -> " + vo.getClass());
                }
                p.put((String) ko, (String) vo);
            }
            return p;
        } else if (next instanceof String) {
            try (InputStream istream = EnvAwareProperties.class.getResourceAsStream((String) next)) {
                return toProperties(istream);
            }
        } else {
            throw new IllegalArgumentException("Not sure how to deal with target of type " + next.getClass());
        }
    }

    /**
     * Create a EnvAwareProperties using base properties.
     * For value lookup, the passed in env will be looked up first
     * if not found, system.getProperties will be looked up
     * if not found, system.getEnv will be looked up
     *
     * @param toLoad The base properties to lookup. It may contain special place holder like ${key}
     *               and it will be resolved as much as we can!
     */
    public EnvAwareProperties(Properties... toLoad) {
        initialize(toLoad);
    }

    private Properties fromRootEnv() {
        return loadFile(new File("/.jproperties"));
    }

    private Properties fromHomeEnv() {
        return loadFile(new File(System.getenv("HOME") + "/.jproperties"));
    }

    private Properties loadFile(File file) {
        Properties prop = new Properties();
        if (file != null && file.isFile() && file.exists() && file.canRead() && file.length() > 0) {
            try (FileInputStream fis = new FileInputStream(file)) {
                prop.load(fis);
            } catch (IOException e) {
            }
        }
        return prop;
    }

    private Properties fromCurrentDirectoryEnv() {
        return loadFile(new File(".jproperties"));
    }

    /**
     * Same as fromFile, but gettting rom path
     *
     * @param path Path of files, in order of resolution importance
     * @return The EnvAwareProperties.
     * @throws IOException if IO Exception happened
     */
    public static EnvAwareProperties fromPath(String... path) throws IOException {
        java.io.File[] list = new java.io.File[path.length];
        for (int i = 0; i < path.length; i++) {
            list[i] = new java.io.File(path[i]);
        }
        return fromFile(list);
    }


    /**
     * Load properties from multiple Files, in order of resolution
     *
     * @param input the files to load from
     * @return The EnvAwareProperties
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromFile(java.io.File... input) throws IOException {
        InputStream[] isrs = Arrays.stream(input).map(
                i -> {
                    try {
                        return new FileInputStream(i);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
        ).filter(i -> i != null).collect(Collectors.toList()).toArray(new InputStream[0]);
        try {
            return fromInputStream(isrs);
        } finally {
            Arrays.stream(isrs).forEach(
                    i -> {
                        try {
                            i.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            );
        }
    }

    /**
     * Load properties from inputStreams
     *
     * @param is the streams to load from
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromInputStream(InputStream... is) throws IOException {
        if (is == null || is.length == 0) {
            throw new IllegalArgumentException("None of the inputs are valid!");
        }
        Properties[] base = Arrays.stream(is).filter(i -> i != null).map(
                i -> {
                    try {
                        Properties p = new Properties();
                        p.load(i);
                        return p;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return null;
                    }
                }
        ).filter(i -> i != null).collect(Collectors.toList()).toArray(new Properties[0]);
        if (base.length == 0) {
            throw new IllegalArgumentException("None of the inputs are valid!");
        }
        return new EnvAwareProperties(base);
    }

    /**
     * Load properties from classpaths
     *
     * @param path Path of classpath
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromClassPath(String... path) throws IOException {
        InputStream[] isrs = Arrays.stream(path).map(
                i -> EnvAwareProperties.class.getResourceAsStream(i)
        ).filter(i -> i != null).collect(Collectors.toList()).toArray(new InputStream[0]);
        try {
            return fromInputStream(isrs);
        } finally {
            Arrays.stream(isrs).forEach(
                    i -> {
                        try {
                            i.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            );
        }
    }


    /**
     * Convenient method to get system Properties as a new Properties
     *
     * @return System properties as Properties (not new, a copy of System.getProperties())
     */
    public static Properties sysProps() {
        return System.getProperties();
    }

    /**
     * Convenient method to get system environment as a new Properties
     *
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
        } catch (CircularReferenceException ex) {
            return raw;
        }
    }

    private static String resolveEnv(String raw, Properties env, int depth) throws CircularReferenceException {
        if (depth >= MAX_DEPTH) {
            throw new CircularReferenceException("Possible resolution loop!");
        }
        if (raw == null) {
            return null;
        }
        Pattern p = Pattern.compile("\\$\\{([a-zA-Z0-9-_.]+)\\}");
        String result = raw;
        Matcher m = p.matcher(raw);
        while (m.find()) {
            String key = m.group(1);
            String value = env.getProperty(key);
            if (value != null) {

                result = result.replaceAll(Pattern.quote("${" + key + "}"), Matcher.quoteReplacement(value));
            }
        }
        if (result.equals(raw)) {
            // no more solution possible
            return result;
        } else {
            return resolveEnv(result, env, depth + 1);
        }
    }

    /**
     * Get property with resolution. By default, the getProperty doesn't work well if your key has
     * ${var} placeholders. If you want to resolve that key as well, use this method instead.
     *
     * @param key The key to resolve. Key may contain place holders like ${key}
     * @return The resolved property
     */
    public String getPropertyResolve(String key) {
        return getPropertyResolve(key, null);
    }

    /**
     * Get property with resolution. By default, the getProperty doesn't work well if your key has
     * ${var} placeholders. If you want to resolve that key as well, use this method instead.
     *
     * @param key          The key to resolve. Key may contain place holders like ${key}
     * @param defaultValue When key is not found, the defaultValue is returned
     * @return The resolved property
     */
    public String getPropertyResolve(String key, String defaultValue) {
        String keyResolved = resolveEnv(key, this);
        String valueRaw = this.getProperty(keyResolved);
        String result = resolveEnv(valueRaw, this);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Sample use cases
     *
     * @param args command line argument
     */
    public static void main(String[] args) {
        Properties dict1 = new Properties();
        dict1.put("key.1", "${key.2}");
        dict1.put("key.2", "${key.1}");
        dict1.put("java.class.path", "${PATH}/ABC");
        System.out.println("Hello, world");

        Properties dict2 = new Properties();
        dict2.put("key.1", "value.3"); // key.1 is shadowed by dict
        dict2.put("key.3", "value.3");
        dict1.put("SPECIAL", "special3");
        dict2.put("key1", "${key2}");
        dict2.put("key2", "${key3}");
        dict1.put("special", "${SPECIAL}");
        dict2.put("key3", "The real key1 value is here");

        Properties dict3 = new Properties();
        dict3.put("key.1", "value.32"); // key.1 is shadowed by dict1, it is useless here
        dict3.put("key.33", "java.class.path"); // key.33 is plain text value java.class.path
        dict3.put("key.44", "${${key.33}}"); // key.44 is value of variable referenced by key.33,
        // so it is ${java.class.path}, and it
        // is resolved to java.class.path environment variable
        dict3.put("appDir", "${user.home}/app01/config"); // appDir might be resolved to /home/ubuntu/app01/config, for example
        dict3.put("new_${A}", "B");
        dict3.put("some_value", "C");
        dict3.put("A", "some_value");
        EnvAwareProperties ep = new EnvAwareProperties(dict1, dict2, dict3);
        System.out.println(ep.getProperty("key.1")); //shows ${key.2} since it has a circular reference!
        System.out.println(ep.getProperty("key1")); //show "The real key1 value is here" since it is resolved!
        System.out.println(ep.getProperty("user.home")); // shows user home directory
        System.out.println(ep.getProperty("key.44")); // shows value of env java.class.path, it resolves the chain!
        System.out.println(ep.getProperty("appDir")); // shows your_home_dir/app01/config
        for (Map.Entry next : ep.entrySet()) {
            System.out.println(next.getKey() + " => " + next.getValue());
            //Lists everything including the dict, dic1, dic2, sysEnv, sysProps!
            // Order of importance dict > dic1 > dic2 > sysProps > sysEnv!
        }
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

