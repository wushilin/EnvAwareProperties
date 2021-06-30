# EnvAwareProperties
An environment aware properties


Usage:

```java
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
        ChainedProperties cp = EnvAwareProperties.chainedPropertiesOf(dict, dic1, dic2);

        // You can create envaware properties from ordinary properties as well
        // You can chain properties here, in order of resolution
        EnvAwareProperties ep = new EnvAwareProperties(cp);

        // Get a regular property
        System.out.println(ep.getProperty("key.44")); // prints key.33
        // The following demos a circular reference
        // key.1 first resolves to ${key.2} => ${key.1} = ${key.2} ... in this case, raw value of key.1 which is ${key.2} is returned.
        System.out.println(ep.getProperty("key.1")); 
        // Get a system environment variable
        System.out.println(ep.getProperty("LANG")); // prints system environment variable of LANG (same as echo $LANG in bash)
        // Get deep resolved variable ${user.home} first resolves to /Users/shwu, for example
        // Then value of /Users/shwu => Blah
        System.out.println(ep.getProperty("${user.home}")); // => prints Blah
        System.out.println(ep.getEnv());

```
