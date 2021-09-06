package fi.nls.oskari.search.geocoding;

import java.util.Map;

public class Feature {
    public String id;
    public Map<String, Object> properties;
    public double x;
    public double y;

    public String getString(String key) {
        return (String) properties.get(key);
    }
}
