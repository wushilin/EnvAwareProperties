package net.wushilin.props;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * EnvAware properties allows you to chain multiple properties, with resolution order,
 * as well as placing environment varible, as well as reference other config properties
 * so your properties are not the same ever again!
 *
 * Updates to this properties always goes to the flattened (resolved) properties!
 * After constructor, this is just a plain properties, except that it offers a getPropertyResolve that
 * does a little bit of magic!
 */
public class EnvAwareProperties extends Properties {
    /**
     * This controls max resolution depth where a property is referencing another, the other
     * properties might reference more other properties.
     */
    private static final int MAX_DEPTH = 500;
    /**
     * Create a EnvAwareProperties using base properties.
     * For value lookup, the passed in env will be looked up first
     * if not found, system.getProperties will be looked up
     * if not found, system.getEnv will be looked up
     * @param defaults The base properties to lookup. It may contain special place holder like ${key}
     *            and it will be resolved as much as we can!
     */
    public EnvAwareProperties(Properties ...defaults) {
        Properties resolved = new Properties();
        Set<String> keys = new HashSet<String>();
        List<Properties> list = new ArrayList<>();
        list.addAll(Arrays.asList(defaults));
        List<Object> extraKeys = new ArrayList<>();
        List<Properties> extraList = new ArrayList<>();

        extraList.add(sysProps());
        extraList.add(sysEnv());
        for(Properties next:list) {
            for(Object nextKey:next.keySet()) {
                resolved.putIfAbsent(nextKey, next.get(nextKey));
                keys.add((String)nextKey);
            }
        }

        /**
         * Extra env keys will be removed at last
         */
        for(Properties next:extraList) {
            for(Object nextKey:next.keySet()) {
                if(!resolved.containsKey(nextKey)) {
                    extraKeys.add(nextKey);
                    resolved.put(nextKey, next.get(nextKey));
                }
                keys.add((String)nextKey);
            }
        }

        for(String nextKey:keys) {
            String resolvedKey = resolveEnv(nextKey, resolved);
            String valueRaw = resolved.getProperty(resolvedKey);
            String resolvedValue = resolveEnv(valueRaw, resolved);
            setProperty(resolvedKey, resolvedValue);
            setProperty(nextKey, resolvedValue);
        }
        for(Object extraK:extraKeys) {
            remove(extraK);
        }
    }

    /**
     * Same as fromFile, but gettting rom path
     * @param path Path of files, in order of resolution importance
     * @return The EnvAwareProperties.
     * @throws IOException if IO Exception happened
     */
    public static EnvAwareProperties fromPath(String ...path) throws IOException {
        java.io.File[] list = new java.io.File[path.length];
        for(int i = 0; i < path.length; i++) {
            list[i] = new java.io.File(path[i]);
        }
        return fromFile(list);
    }


    /**
     * Load properties from multiple Files, in order of resolution
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
     * @param is the streams to load from
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromInputStream(InputStream...is) throws IOException {
        if(is == null || is.length == 0) {
            throw new IllegalArgumentException("None of the inputs are valid!");
        }
        Properties[] base = Arrays.stream(is).filter(i -> i != null).map(
                i -> {
                    try {
                        Properties p = new Properties();
                        p.load(i);
                        return p;
                    } catch(Exception ex) {
                        ex.printStackTrace();
                        return null;
                    }
                }
        ).filter(i -> i!= null).collect(Collectors.toList()).toArray(new Properties[0]);
        if(base.length == 0) {
            throw new IllegalArgumentException("None of the inputs are valid!");
        }
        return new EnvAwareProperties(base);
    }

    /**
     * Load properties from classpaths
     * @param path Path of classpath
     * @return EnvAwareProperties (will mix with system env, sytem properties)
     * @throws IOException If IO Exception happened
     */
    public static EnvAwareProperties fromClassPath(String ... path) throws IOException {
        InputStream[] isrs = Arrays.stream(path).map(
                i ->  EnvAwareProperties.class.getResourceAsStream(i)
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

    /**
     * Get property with resolution. By default, the getProperty doesn't work well if your key has
     * ${var} placeholders. If you want to resolve that key as well, use this method instead.
     * @param key The key to resolve. Key may contain place holders like ${key}
     * @return The resolved property
     */
    public String getPropertyResolve(String key) {
        return getPropertyResolve(key, null);
    }

    /**
     * Get property with resolution. By default, the getProperty doesn't work well if your key has
     * ${var} placeholders. If you want to resolve that key as well, use this method instead.
     * @param key The key to resolve. Key may contain place holders like ${key}
     * @param defaultValue When key is not found, the defaultValue is returned
     * @return The resolved property
     */
    public String getPropertyResolve(String key, String defaultValue) {
        String keyResolved = resolveEnv(key, this);
        String valueRaw = this.getProperty(keyResolved);
        String result = resolveEnv(valueRaw, this);
        if(result == null) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Sample use cases
     * @param args command line argument
     */
    public static void main(String[] args) {
        Properties dict1 = new Properties();
        dict1.put("key.1", "${key.2}");
        dict1.put("key.2", "${key.1}");

        System.out.println("Hello, world");
        
        Properties dict2 = new Properties();
        dict2.put("key.1", "value.3"); // key.1 is shadowed by dict
        dict2.put("key.3", "value.3");
        dict2.put("key1", "${key2}");
        dict2.put("key2", "${key3}");
        dict2.put("key3", "The real key1 value is here");

        Properties dict3 = new Properties();
        dict3.put("key.1", "value.32"); // key.1 is shadowed by dict1, it is useless here
        dict3.put("key.33", "java.class.path"); // key.33 is plain text value java.class.path
        dict3.put("key.44", "${${key.33}}"); // key.44 is value of variable referenced by key.33,
        // so it is ${java.class.path}, and it
        // is resolved to java.class.path environment variable
        dict3.put("appDir", "${user.home}/app01/config"); // appDir might be resolved to /home/ubuntu/app01/config, for example

        EnvAwareProperties ep = new EnvAwareProperties(dict1, dict2, dict3);
        System.out.println(ep.getProperty("key.1")); //shows ${key.2} since it has a circular reference!
        System.out.println(ep.getProperty("key1")); //show "The real key1 value is here" since it is resolved!
        System.out.println(ep.getProperty("user.home")); // shows user home directory
        System.out.println(ep.getProperty("key.44")); // shows value of env java.class.path, it resolves the chain!
        System.out.println(ep.getProperty("appDir")); // shows your_home_dir/app01/config
        for(Map.Entry next:ep.entrySet()) {
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

