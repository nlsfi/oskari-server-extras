package fi.nls.oskari.search;

import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.SearchCriteria;
import fi.mml.portti.service.search.SearchResultItem;
import fi.nls.oskari.SearchWorker;
import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.map.geometry.ProjectionHelper;
import fi.nls.oskari.search.channel.SearchAutocomplete;
import fi.nls.oskari.search.channel.SearchChannel;
import fi.nls.oskari.search.geocoding.Feature;
import fi.nls.oskari.search.geocoding.GeocodeHelper;
import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.PropertyUtil;
import fi.nls.oskari.domain.geo.Point;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.*;

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
@Oskari(NLSFIGeocodingSearchChannel.ID)
public class NLSFIGeocodingSearchChannel extends SearchChannel implements SearchAutocomplete {
    public static final String ID = "NLSFI_GEOCODING";

    private static final Logger LOG = LogFactory.getLogger(NLSFIGeocodingSearchChannel.class);
    // password is always empty string with apikey
    private static final String PASSWORD = "";

    private static final int MAX_REDIRECTS = 5;

    private String baseURL;
    private String apiKey;
    private final Map<String, String> endPoints = new HashMap();
    private static final int SERVICE_SRS_CODE = 3067;
    private CoordinateReferenceSystem serviceCRS;


    @Override
    public void init() {
        super.init();
        baseURL = getProperty("service.url", "https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding");
        apiKey = getProperty("APIkey", getProperty("service.user", null));
        if (apiKey == null) {
            throw new ServiceRuntimeException("API-key missing for channel. Configure with "
                    + getPropertyName("APIkey")
                    + ". You can get an apikey by registering in https://omatili.maanmittauslaitos.fi/.");
        }
        // defaults
        //endPoints.put("default", "/v1/pelias/search"); // the text search
        endPoints.put("default", "/v2/advanced/search"); // the text search
        endPoints.put("autocomplete", "/v2/searchterm/similar"); // autocompletion
        endPoints.put("reverse", "/v1/pelias/reverse"); // reverse geocoding
        // override/add more from props:
        // search.channel.GEOLOCATOR_NLS_FI.endpoint.horses=/v8/wroom
        // would add a new endpoint called "horses" with path "/v8/wroom"
        // search.channel.GEOLOCATOR_NLS_FI.endpoint.default=/v2/advanced/search
        // search.channel.GEOLOCATOR_NLS_FI.endpoint.autocomplete=/v2/searchterm/similar
        String prefix = getPropertyName("endpoint.");
        PropertyUtil.getMatchingPropertyNames(prefix)
            .forEach(propName -> {
                String id = propName.substring(prefix.length());
                String value = PropertyUtil.get(propName);
                endPoints.put(id, value);
                LOG.info("Additional configuration endpoint '", id, "' with path:", value);
            });
        try {
            serviceCRS = CRS.decode("EPSG:" + SERVICE_SRS_CODE, true);
        } catch (FactoryException e) {
            throw new ServiceRuntimeException("Can't provide transformation for service");
        }
        LOG.info("ServiceURL set to " + baseURL);
    }

    private String getPropertyName(String key) {
        return "search.channel." + ID + "." + key;
    }

    public ChannelSearchResult doSearch(SearchCriteria searchCriteria) {
        try {
            Map<String, Object> geojson = getResult(searchCriteria);
            return parseResponse(searchCriteria, geojson, searchCriteria.getSRS());
        } catch (Exception e) {
            LOG.error("Failed to search locations from:", ID, "Message was:", e.getMessage());
            ChannelSearchResult result = new ChannelSearchResult();
            result.setQueryFailed(true);
            return result;
        }
    }

    public List<String> doSearchAutocomplete(String searchString) {
        String url = getUrl("autocomplete", getAutocompleteParams(searchString, PropertyUtil.getDefaultLanguage(), 10));
        try {
            HttpURLConnection conn = connectToService(url);
            try (InputStream in = conn.getInputStream()) {
                return GeocodeHelper.parseAutocomplete(in);
            }
        }
        catch (IOException ex) {
            throw new ServiceRuntimeException("Couldn't open or read from connection!", ex);
        }
    }


    private HttpURLConnection connectToService(String url) throws IOException {
        HttpURLConnection conn = IOHelper.getConnection(url, apiKey, PASSWORD);
        IOHelper.addIdentifierHeaders(conn);
        return IOHelper.followRedirect(conn, apiKey, PASSWORD, MAX_REDIRECTS);
    }

    private Map<String, Object> getResult(SearchCriteria criteria) throws IOException {
        String url = getUrl(getSearchParams(criteria.getSearchString(),
                criteria.getLocale(),
                criteria.getMaxResults()));

        LOG.debug("Calling search with", url);
        HttpURLConnection conn = connectToService(url);
        validateResponse(conn);
        return GeocodeHelper.readJSON(conn);
    }

    private ChannelSearchResult parseResponse(SearchCriteria searchCriteria, Map<String, Object> geojson, String srs) {
        List<Feature> features = GeocodeHelper.parseResponse(geojson);
        ChannelSearchResult result = new ChannelSearchResult();
        result.setChannelId(ID);
        boolean reqProj = requiresReprojection(srs);
        features.forEach(feat -> {
            if (reqProj) {
                Point poi = ProjectionHelper.transformPoint(feat.x, feat.y, serviceCRS, srs);
                feat.x = poi.getLon();
                feat.y = poi.getLat();
            }
            result.addItem(getItem(feat, searchCriteria.getLocale()));
        });
        return result;
    }

    private SearchResultItem getItem(Feature feat, String lang) {

        SearchResultItem item = new SearchResultItem();
        item.setLat(feat.y);
        item.setLon(feat.x);

        // all features seems to have label, geonames have "name" that is an object
        //item.setLocationName((String) feat.properties.get("label"));
        item.setTitle(feat.getString("label"));
        // "label:municipality" is atleast in both "source" : "geographic-names" && "addresses"
        item.setRegion(feat.getString("label:municipality"));
        String src = feat.getString("source");
        if (src == null) {
            // all features seems to have label, geonames have "name" that is an object
            return item;
        }
        item.addValue("source", src);
        // TODO: maybe do some mapping from source to localized string
        item.setType(src);

        if ("geographic-names".equals(src)) {
            // all features seems to have label, geonames have "name" that is an object
            item.setResourceId("" + feat.properties.get("placeId"));
            Integer scaleRelevance = (Integer)feat.properties.get("scaleRelevance");
            if (scaleRelevance != null && scaleRelevance > 0) {
                // divide by to get a closer zoom. Oskari zooms out to get the scale
                item.setZoomScale(scaleRelevance.doubleValue() / 2);
            }
            item.setType(feat.getString("label:placeType"));
            // TODO: check the name AND value for "paikkatyyppi"
            item.addValue("paikkatyyppi", feat.getString("label:placeTypeCategory"));
        } else {
            item.setType(GeocodeHelper.getLocalizedType(src, lang));
            if ("cadastral-units".equals(src)) {
                item.setResourceId(item.getTitle());
                item.setZoomScale(2000);
            } else if ("addresses".equals(src) || "interpolated-road-addresses".equals(src)) {
                item.setZoomScale(5000);
                // label includes the same postfixed with " ([municipality] )" that we want to get rid of
                String address = feat.getString("katunimi");
                String addressNumber = feat.getString("katunumero");
                if (addressNumber != null && !addressNumber.isEmpty()) {
                    address += " " + addressNumber;
                }
                item.setTitle(address);
            }
        }
        return item;
    }

    private boolean requiresReprojection(String srs) {
        return !("EPSG:" + SERVICE_SRS_CODE).equals(srs);
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

    protected Map<String, String> getSearchParams(String query, String language, int count) {
        Map<String, String> params = getAutocompleteParams(query, language, count);
        params.put("crs", "http://www.opengis.net/def/crs/EPSG/0/" + SERVICE_SRS_CODE);
        params.put("request-crs", "http://www.opengis.net/def/crs/EPSG/0/" + SERVICE_SRS_CODE);
        return params;
    }

    protected Map<String, String> getAutocompleteParams(String query, String language, int count) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("text", query);
        params.put("lang", language);
        params.put("size", Integer.toString(SearchWorker.getMaxResults(count) + 1));

        // we can put all of these here by default. The service will detect if query is matching cadastral-unit id and optimize internally
        params.put("sources", "geographic-names,cadastral-units,interpolated-road-addresses"); // ,addresses
        return params;
    }

    private static void validateResponse(HttpURLConnection conn)
            throws ServiceRuntimeException, IOException {
        if (conn.getResponseCode() != 200) {
            throw new ServiceRuntimeException("Unexpected status code " + conn.getResponseCode());
        }

        String contentType = conn.getContentType();
        if (contentType != null && !contentType.startsWith("application/json")) {
            throw new ServiceRuntimeException("Unexpected content type " + contentType);
        }
    }
}
