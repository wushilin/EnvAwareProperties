package net.wushilin.props;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EnvAwareProperties extends Properties {
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
            if(nextKey.indexOf("$") != -1) {
                System.out.println("" + nextKey);
            }
            setProperty(resolvedKey, resolvedValue);
            setProperty(nextKey, resolvedValue);
        }
        for(Object extraK:extraKeys) {
            remove(extraK);
        }
    }

    /**
     * Same as fromFile, but getting path as string
     * @param path Path of file
     */
    public static EnvAwareProperties fromPath(String ...path) throws IOException {
        java.io.File[] list = new java.io.File[path.length];
        for(int i = 0; i < path.length; i++) {
            list[i] = new java.io.File(path[i]);
        }
        return fromFile(list);
    }


    /**
     * Load properties from File
     * @param input the file to load from
     * @return EnvAwareProperties (will mix with system env, sytem properties)
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
     * Load properties from inputStream
     * @param is the stream to load from
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
     * Load properties from classpath
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

    public String getPropertyResolve(String key) {
        return getPropertyResolve(key, null);
    }

    public String getPropertyResolve(String key, String defaultValue) {
        String keyResolved = resolveEnv(key, this);
        String valueRaw = this.getProperty(keyResolved);
        String result = resolveEnv(valueRaw, this);
        if(result == null) {
            return defaultValue;
        }
        return result;
    }

    public static void main(String[] args) {
        Properties dict = new Properties();
        dict.put("key.1", "${key.2}");
        dict.put("key.2", "${key.1}");

        Properties dic1 = new Properties();
        dic1.put("key.1", "value.3");
        dic1.put("key.3", "value.3");
        dic1.put("key1", "${key2}");
        dic1.put("key2", "${key3}");
        dic1.put("key3", "The real key1 value is here");

        Properties dic2 = new Properties();
        dic2.put("key.1", "value.32");
        dic2.put("key.33", "java.class.path");
        dic2.put("key.44", "${${key.33}}");
        dic2.put("appDir", "${user.home}/app01/config");

        EnvAwareProperties ep = new EnvAwareProperties(dict, dic1, dic2);
        System.out.println(ep.getProperty("key.1")); //shows ${key.2} since it has a circular reference!
        System.out.println(ep.getProperty("key1")); //show "The real key1 value is here" since it is resolved!
        System.out.println(ep.getProperty("user.home")); // shows user home directory
        System.out.println(ep.getProperty("key.44")); // shows value of env java.class.path, it resolves the chain!
        System.out.println(ep.getProperty("appDir")); // shows your_home_dir/app01/config
        for(var next:ep.entrySet()) {
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

