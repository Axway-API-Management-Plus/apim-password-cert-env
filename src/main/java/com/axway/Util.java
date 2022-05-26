package com.axway;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static Map<String, String> groupEnvVariables(Map<String, String> envValues, String namePrefix) {
        return envValues.entrySet()
            .stream()
            .filter(map -> map.getKey().startsWith(namePrefix))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static String getAliasName(X509Certificate certificate){


        String alias = certificate.getSubjectDN().getName();
        if (alias.equals("")) {
            alias = certificate.getSerialNumber().toString();
        }
        return alias;
    }
}
