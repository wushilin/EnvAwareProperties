# EnvAwareProperties
An environment aware properties


# What problems does this code solve?
1. Environment aware properties.
Ever wanted to use my.property=${HOME}/config?
This is for you!

2. Ever wanted to have placeholder like variable in properties?
BASE_DIR=/tmp/base
APP_BINARY=${BASE_DIR}/bin
APP_LOG=${BASE_DIR}/logs

This is for you!

3. Ever wanted to have chain of variables?

K1=K2
K2=K3
key=${${K1}}
=>
key=K3

This is for you!

# Note: This resolves statically. That means all keys are expanded to their value at initialization.

# Note: If you want to access key with resolution, use .getPropertyResolve(String key) instead. Here key can contain sth like "some${suffix}" where suffix is defined in properties.

# Note: When constructing, the priority of resolution is by the order of properties in constructor.

Usage:

```java
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

```
