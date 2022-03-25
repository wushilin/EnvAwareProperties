# EnvAwareProperties
An environment aware properties


# Change log
## v1.0.1 (maven repo https://mvnrepository.com/artifact/net.wushilin/envawareproperties)
1. Added auto load of $current_directory/.jproperties, $HOME/.jproperties, /.jproperties in order, if they present, and is readable, and is not empty
2. The above properties has priority over sysProperties, and sysEnv. sysProperties has priority over sysEnv.
3. You can't nest more than 500 levels. 
```
K1 = ${K2}
K2 = ${K1}
```
This will not resolve, but will not cause error too. K1 = literal of ("${K2}")

# What problems does this code solve?
1. Environment aware properties.

Ever wanted to use my.property=${HOME}/config? This is for you!


2. Ever wanted to have placeholder like variable in properties?
```
BASE_DIR=/tmp/base
APP_BINARY=${BASE_DIR}/bin
APP_LOG=${BASE_DIR}/logs
```
This is for you!

3. Ever wanted to have chain of variables?
```
K1=K2
K2=K3
key=${${K1}}
=>
key=K3
```
This is for you!

```
Note: This resolves statically. That means all keys are expanded to their value at initialization.

Note: If you want to access key with resolution, use .getPropertyResolve(String key) instead. Here key can contain sth like "some${suffix}" where suffix is defined in properties.

Note: When constructing, the priority of resolution is by the order of properties in constructor.
```

Usage:

```java
        Properties dict1 = new Properties();
        dict1.put("key.1", "${key.2}");
        dict1.put("key.2", "${key.1}");

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
        for(var next:ep.entrySet()) {
            System.out.println(next.getKey() + " => " + next.getValue());
            //Lists everything including the dict, dic1, dic2, sysEnv, sysProps!
            // Order of importance dict > dic1 > dic2 > sysProps > sysEnv!
        }

```
