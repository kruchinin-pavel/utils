package org.kpa.util;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Props {
    private static List<Function<String, String>> customProps = new ArrayList<>();

    public static void setCustomProps(Function<String, String> customProps) {
        Props.customProps.add(customProps);
    }

    public static String getSilent(String propertyName, String defaultVal) {
        return getProperty(propertyName, propertyName, defaultVal, false);
    }

    public static String[] getList(String propertyName) {
        return Splitter.on(";").trimResults().omitEmptyStrings().splitToList(getSilent(propertyName)).toArray(new String[0]);
    }

    public static String getSilent(String propertyName) {
        return getProperty(propertyName, "", null, false);
    }


    public static String getProperty(String propertyName, String propDescription, String defaultValue) {
        return getProperty(propertyName, propDescription, defaultValue, defaultValue == null);
    }

    public static String getProperty(String propertyName, String propDescription, String defaultValue, boolean throwOnAbsent) {
        String value = System.getProperty(propertyName);
        if (Strings.isNullOrEmpty(value)) {
            value = System.getenv(propertyName);
            if (Strings.isNullOrEmpty(value) && customProps != null) {
                value = customProps.stream().map(prop -> prop.apply(propertyName))
                        .filter(Objects::nonNull).findFirst().orElse(null);
            }
        }

        if (Strings.isNullOrEmpty(value)) {

            if (throwOnAbsent) {
                throw new IllegalArgumentException("Property " + propertyName + " is not set. Need to set it for: " + propDescription);
            } else {
                return defaultValue;
            }
        }
        return value;
    }

}
