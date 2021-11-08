package fi.nls.oskari.search.geocoding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.PropertyUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.stream.Collectors;

public class GeocodeHelper {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final TypeReference<HashMap<String, Object>> TYPE_REF = new TypeReference<HashMap<String, Object>>() {};
    private static final Map<String, String> localization = new HashMap<>();

    public static Map<String, Object> readJSON(HttpURLConnection conn) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            return readJSON(in);
        }
    }

    public static Map<String, Object> readJSON(InputStream in) throws IOException {
        return OM.readValue(in, TYPE_REF);
    }

    public static List<Feature> parseResponse(Map<String, Object> geojson) {
        List<Object> features = (List) geojson.getOrDefault("features", Collections.emptyList());
        return features.stream().map(item -> {
            Map<String, Object> map = (Map) item;
            Feature feat = new Feature();
            feat.id = (String) map.get("id");
            feat.properties = (Map) map.get("properties");
            Map<String, Object> geom = (Map) map.get("geometry");
            List<Double> coords = (List) geom.get("coordinates");
            feat.x = coords.get(0);
            feat.y = coords.get(1);
            return feat;
        }).collect(Collectors.toList());
    }

    public static List<String> parseAutocomplete(InputStream in) {
        List responseTerms;
        try {
            Map<String, Object> response = readJSON(in);
            responseTerms = (List) response.getOrDefault("terms", Collections.emptyList());
        }
        catch (Exception ex) {
            throw new ServiceRuntimeException("Couldn't open or read from connection!", ex);
        }
        List<String> result = new ArrayList<>();
        responseTerms.forEach(item -> {
            Map<String, Object> term = (Map<String, Object>) (item);
            if (term != null && term.get("text") != null) {
                result.add((String) term.get("text"));
            }
        });
        return result;
    }

    private static void initLocales() {
        for (String lang : PropertyUtil.getSupportedLanguages() ) {
            ResourceBundle loc = ResourceBundle.getBundle("SearchLocalization", new Locale(lang));
            localization.put(lang + ".cadastral-units", loc.getString("realEstateIdentifiers"));
            localization.put(lang + ".addresses", loc.getString("address"));
            localization.put(lang + ".interpolated-road-addresses", loc.getString("address"));
        }
    }

    public static String getLocalizedType(String source, String lang) {
        if (localization.isEmpty()) {
            initLocales();
        }
        return localization.getOrDefault(lang + "." + source, "");
    }
}
