package fi.nls.oskari.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.SearchCriteria;
import fi.mml.portti.service.search.SearchResultItem;
import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.search.channel.SearchAutocomplete;
import fi.nls.oskari.search.channel.SearchChannel;
import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.PropertyUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Search channel impl for https://www.maanmittauslaitos.fi/geokoodauspalvelu/tekninen-kuvaus
 * Replacing:
 * - "nimistö"/RegisterOfNomenclatureChannelSearchService
 * - "kiinteistö"/KTJkiiSearchChannel
 * - "maasto"/MaastoAddressChannelSearchService
 * and probably with reverse geocoding: NLSNearestFeatureSearchChannel
 */
//
// addresses
// interpolated-road-addresses
// cadastral-units
// mapsheets-tm35 (tätä tuskin tarvitaan)
@Oskari(GeolocatorNLSFISearchChannel.ID)
public class GeolocatorNLSFISearchChannel extends SearchChannel implements SearchAutocomplete {
    public static final String ID = "GEOLOCATOR_NLS_FI";

    private static final Logger LOG = LogFactory.getLogger(GeolocatorNLSFISearchChannel.class);
    // password is always empty string with apikey
    private static final String PASSWORD = "";

    private static final int MAX_REDIRECTS = 5;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final TypeReference<HashMap<String, Object>> TYPE_REF = new TypeReference<HashMap<String, Object>>() {};

    private static final ObjectMapper OM = new ObjectMapper();
    private String baseURL;
    private String apiKey;
    private Map<String, String> endPoints = new HashMap();


    @Override
    public void init() {
        super.init();
        baseURL = getProperty("url", "https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding");
        apiKey = getProperty("user", getProperty("apiKey", null));
        if (apiKey == null) {
            throw new ServiceRuntimeException("API-key missing for channel. Configure with " + getPropertyName("user"));
        }
        // TODO: from props?
        endPoints.put("default", "/v1/pelias/search"); // the text search
        endPoints.put("reverse", "/v1/pelias/reverse"); // reverse geocoding
        endPoints.put("autocomplete", "/v1/searchterm/similar"); // autocompletion
        // TODO: we might have more endPoints for app requested search result prioritizing
        LOG.info("ServiceURL set to " + baseURL);
    }

    private String getPropertyName(String key) {
        return "search.channel." + ID + "." + key;
    }

    public ChannelSearchResult doSearch(SearchCriteria searchCriteria) {
        try {
            Map<String, Object> geojson = getResult(searchCriteria);
            return parseResponse(searchCriteria, geojson);
        } catch (Exception e) {
            LOG.error(e, "Failed to search locations from " + ID);
            return new ChannelSearchResult();
        }
    }

    public List<String> doSearchAutocomplete(String searchString) {
        String url = getUrl("autocomplete", getQueryParams(searchString, PropertyUtil.getDefaultLanguage(), 10));
        try {
            HttpURLConnection conn = connectToService(url);
            try (InputStream in = conn.getInputStream()) {
                return parseAutocomplete(in);
            }
        }
        catch (IOException ex) {
            throw new ServiceRuntimeException("Couldn't open or read from connection!", ex);
        }
    }

    protected List<String> parseAutocomplete(InputStream in) {
        List responseTerms;
        try {
            Map<String, Object> response = readMap(in);
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

    private HttpURLConnection connectToService(String url) throws IOException {
        HttpURLConnection conn = IOHelper.getConnection(url, apiKey, PASSWORD);
        IOHelper.addIdentifierHeaders(conn);
        return IOHelper.followRedirect(conn, apiKey, PASSWORD, MAX_REDIRECTS);
    }

    private Map<String, Object> getResult(SearchCriteria criteria) throws IOException {
        String url = getUrl(getQueryParams(criteria.getSearchString(),
                criteria.getLocale(),
                criteria.getSRS(),
                criteria.getMaxResults()));

        HttpURLConnection conn = connectToService(url);
        validateResponse(conn, CONTENT_TYPE_JSON);
        Map<String, Object> geojson = readMap(conn);
        return geojson;
    }

    private ChannelSearchResult parseResponse(SearchCriteria searchCriteria, Map<String, Object> geojson) {
        List<Feature> features = parseResponse(geojson);
        ChannelSearchResult result = new ChannelSearchResult();
        result.setChannelId(ID);
        features.forEach(feat -> result.addItem(getItem(feat, searchCriteria.getLocale())));
        return result;
    }

    private SearchResultItem getItem(Feature feat, String lang) {

        SearchResultItem item = new SearchResultItem();
        item.setLat(feat.y);
        item.setLon(feat.x);
        // "label:municipality" is atleast in both "source" : "geographic-names" && "addresses"
        item.setRegion((String) feat.properties.get("label:municipality"));
        String src = (String) feat.properties.get("source");
        if (src == null) {
            // all features seems to have label, geonames have "name" that is an object
            item.setLocationName((String) feat.properties.get("label"));
            return item;
        }
        if ("geographic-names".equals(src)) {
            // all features seems to have label, geonames have "name" that is an object
            item.setLocationName((String) feat.properties.get("label"));
            // TODO: parse name, use lang
        }
        return item;
    }

    protected List<Feature> parseResponse(Map<String, Object> geojson) {
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

    protected String getEndpoint() {
        return getEndpoint("default");
    }

    protected String getEndpoint(String id) {
        if (id == null) {
            id = "default";
        }
        String servicePath = endPoints.getOrDefault(id, endPoints.get("default"));
        return baseURL + servicePath;
    }

    protected String getUrl(Map<String, String> params) {
        return getUrl("default", params);
    }

    protected String getUrl(String endpointId, Map<String, String> params) {
        return IOHelper.constructUrl(getEndpoint(endpointId), params);
    }
    protected Map<String, String> getQueryParams(String query, String language, int count) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("text", query);
        params.put("lang", language);
        params.put("size", Integer.toString(count));

        // we can put all of these here by default. The service will detect if query is matching cadastral-unit id and optimize internally
        params.put("sources", "geographic-names,addresses,cadastral-units");
        return params;
    }
    protected Map<String, String> getQueryParams(String query, String language, String srs, int count) {
        Map<String, String> params = getQueryParams(query, language, count);
        String epsgCode = srs.split("\\:")[1];
        params.put("crs", "http://www.opengis.net/def/crs/EPSG/0/" + epsgCode);
        params.put("request-crs", "http://www.opengis.net/def/crs/EPSG/0/" + epsgCode);
        return params;
    }

    private static Map<String, Object> readMap(HttpURLConnection conn) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            return readMap(in);
        }
    }

    protected static Map<String, Object> readMap(InputStream in) throws IOException {
        return OM.readValue(in, TYPE_REF);
    }

    public static void validateResponse(HttpURLConnection conn, String expectedContentType)
            throws ServiceRuntimeException, IOException {
        if (conn.getResponseCode() != 200) {
            throw new ServiceRuntimeException("Unexpected status code " + conn.getResponseCode());
        }

        if (expectedContentType != null) {
            String contentType = conn.getContentType();
            if (contentType != null && contentType.indexOf(expectedContentType) != -1) {
                throw new ServiceRuntimeException("Unexpected content type " + contentType);
            }
        }
    }

    class Feature {
        String id;
        Map<String, Object> properties;
        double x;
        double y;
    }
}
