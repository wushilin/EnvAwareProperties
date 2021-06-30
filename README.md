# EnvAwareProperties
An environment aware properties


Usage:

```java
        String raw = "hello ${${key.1}}";
        Properties dict = new Properties();
        dict.put("key.1", "${key.2}");
        dict.put("key.2", "${key.1}");

        Properties dic1 = new Properties();
        dic1.put("key.1", "value.3");
        dic1.put("key.3", "value.3");

        Properties dic2 = new Properties();
        dic2.put("key.1", "value.32");
        dic2.put("key.33", "java.class.path");
        dic2.put("key.44", "${${key.33}}");
        EnvAwareProperties ep = new EnvAwareProperties(dict, dic1, dic2);
        System.out.println(ep.getProperty("key.1"));
        System.out.println(ep.getProperty("LANG"));
        System.out.println(ep.getProperty("key.44"));
        for(var next:ep.entrySet()) {
            System.out.println(next.getKey() + " => " + next.getValue());
        }

```
