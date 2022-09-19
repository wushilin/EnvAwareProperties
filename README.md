# EnvAwareProperties
# What problems does this code solve?
1. Environment aware properties.

Ever wanted to use `config.base.directory=${HOME}/config`? This is for you!


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

4. Ever wanted to automatically resolve environment variable in config?
```
# Assume you launch tomcat with java -Dtomcat.runtime.version=9.0 xxxxx
APP_HOME=${HOME}/${USER}/config-${tomcat.runtime.version}
TOMCAT_VERSION=${tomcat.runtime.version}
```

This is for you!

All keys are resolved at initialization. At runtime, this has zero overhead!

If you want to access key with resolution, you can call .getPropertyResolve(String key).
Here key can contain sth like "some${suffix}" where suffix is defined in properties.

It supports loading properties from multiple source, with preference of order of resolution.

It expands the Properties values that contains ${tag} automatically.

It supports autoload of System environment, system properties (-Djava.xxx.xxx=xxx),
./.jproperties, /.jproperties, /home/<user>/.jproperties as resolution candidates.

The resolution order is:
1. It looks for the EnvAwareProperties.newBuilder() override keys if specified
2. It looks for ./.properties file (current directory)
3. It looks for $HOME/.jproperties
4. It looks for /.jproperties
5. It looks for System properties (specified by -Dsome.key=some.value)
6. It looks for System environment variable

If you do not want to load the above files, please use a builder, and call the disableXXX before building.

For example
```java
EnvAwareProperties props = EnvAwareProperties.newBuilder()
        .disableHomeJProperties()
        .disableCwdJProperties()
        .disableRootJProperties()
        .disableEnvironment()
        .disableSysProperties()
        .override("mykey", "myvalue")
        .thenAddPropertiesFilePath("testdata/test3.properties", "testdata/test4.properties")
        .build();
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


       // Or simply, load multiple files
       Properties p = EnvAwareProperties.fromPath("/path/to/p1", "/path/to/p2");
	
			 // Or simply, load from classpath
       Properties p = EnvAwareProperties.fromFile(new File("/path/to/p1"), new File("/path/to/p2"));
 
       // Or load from input stream
       Properties p = EnvAwareProperties.fromInputStream(is1, is2, is3);

			 // Or from classPath
       Properties p = EnvAwareProperties.fromClassPath("/resources/p1.properties", "/resources/p2.properties");
			
			 // In the above configs, the properties are resolved in the order. Most important properties loads first

```

# Change log
## v1.06  (maven repo https://mvnrepository.com/artifact/net.wushilin/envawareproperties)
1. Added EnvAwareProperties.merge(EnvAwareProperties other) to merge. other has lower priority

## v1.0.5 (maven repo https://mvnrepository.com/artifact/net.wushilin/envawareproperties)
1. Added EnvAwareProperties.defaultProperties() that will load `./config/application.properties` or `./application.properties`
2. Added javadoc for the behavior

## v1.0.3 (maven repo https://mvnrepository.com/artifact/net.wushilin/envawareproperties)
1. Added a partition function for envaware properties
```java
EnvAwareProperties p = ...;
// root.ns1.key1=value1
// root.ns1.key2=value2
// root.ns2.key1=value1_diff
// root.ns2.key2=value2_diff
// other=other_value
EnvAwareProperties root = p.partition("root");
// ns1.key1, ns1.key2, ns2.key1, ns2.key2 <= valid keys in root
EnvAwareProperties ns1 = root.partition("ns1."); // '.' suffix is optional
// key1, key2 <= valid keys for ns1

EnvAwareProperties ns1new = p.partition("root.ns1."); // same!
```
## v1.0.2 (maven repo https://mvnrepository.com/artifact/net.wushilin/envawareproperties)
1. Added builder function
```java
        Properties defaultP5 = EnvAwareProperties.newBuilder()
                .thenAddPropertiesFilePath("testdata/test3.properties")
                .thenAddPropertiesFile(new File("testdata/test2.properties"))
                .thenAddInputStream(new FileInputStream("testdata/test1.properties"))
                .override("key", "value")
                .build();
        // Note you should close the inputstream yourself.
```

2. Revised resolution order

3. Added test case
## v1.0.1 (maven repo https://mvnrepository.com/artifact/net.wushilin/envawareproperties)
1. Added auto load of $current_directory/.jproperties, $HOME/.jproperties, /.jproperties in order, if they present, and is readable, and is not empty
2. The above properties has priority over sysProperties, and sysEnv. sysProperties has priority over sysEnv.
3. You can't nest more than 500 levels. 
```
K1 = ${K2}
K2 = ${K1}
```
This will not resolve, but will not cause error too. K1 = literal of ("${K2}")
