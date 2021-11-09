package com.axway;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final  class  Util {

    public static Map<String, Map<String, String>> parseCred(Map<String, String> envMap) {

        Map<String, Map<String, String>> values = new HashMap<>();
        if (envMap != null && !envMap.isEmpty()) {
            Iterator<String> keyIterator = envMap.keySet().iterator();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                String[] delimitedKeys = key.split("_");
                String filterName;
                if (delimitedKeys.length == 3) {
                    filterName = delimitedKeys[1];
                    String attribute = delimitedKeys[2];
                    String value = envMap.get(key);
                    Map<String, String> attributes = values.get(filterName);
                    if (attributes == null) {
                        attributes = new HashMap<>();
                        values.put(filterName, attributes);
                    }
                    attributes.put(attribute, value);
                }
            }
        }
        return values;
    }
}
