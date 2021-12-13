package fi.nls.oskari.search;

import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.SearchCriteria;
import fi.mml.portti.service.search.SearchResultItem;
import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.search.channel.SearchChannel;
import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.IOHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static org.springframework.beans.PropertyAccessorUtils.getPropertyName;


@Oskari(NLSFIGeographicNamesSearchChannel.ID)
public class NLSFIGeographicNamesSearchChannel extends SearchChannel {


    public static final String ID = "NLSFIGeographicNamesSearchChannel";

    private static final String DEFAULT_SEARCH_URL = "https://avoin-paikkatieto.maanmittauslaitos.fi/geographic-names/features/v1/collections/placenames/items";
    private static final String DEFAULT_CODELIST_URL = "https://beta-paikkatieto.maanmittauslaitos.fi/catalogue/v1/codelistgroups/geographic-names/codelists/";
    private static final String PLACE_TYPE = "placeType";
    private static final String MUNICIPALITY = "municipality";

    private static String baseUrl;
    private static String codelistUrl;
    private static String apiKey = null;
    // Use local resource files instead of getting codelist over network
    private boolean useLocalCodelist = true;
    private static String[] languageArray = {"fin", "eng", "swe"};

    private HashMap<Integer, HashMap<String, String>> placetypeCodes;
    private HashMap<Integer, HashMap<String, String>> municipalityCodes;

    private Logger log = LogFactory.getLogger(this.getClass());


    @Override
    public void init() {
        super.init();

        baseUrl = getProperty("service.url", DEFAULT_SEARCH_URL);
        if (baseUrl.equals(DEFAULT_SEARCH_URL)) {
            log.info("Using default searchAddress, use " + getPropertyName("service.url") + " to configure different one");
        }
        codelistUrl = getProperty("codelist.url", null);
        useLocalCodelist = (codelistUrl == null);
        if (useLocalCodelist) {
            log.info("Using local code list. To configure network resource use " +
                    getPropertyName("codelist.url") + "=" + DEFAULT_CODELIST_URL +
                    " in oskari-ext.properties to configure one");
        }


        if (placetypeCodes == null) {
            placetypeCodes = buildAdditionalInfo(PLACE_TYPE);
        }
        if (municipalityCodes == null) {
            municipalityCodes = buildAdditionalInfo(MUNICIPALITY);
        }

        apiKey = getProperty("APIkey", getProperty("service.user", null));
        if (apiKey == null) {
            throw new ServiceRuntimeException("API-key missing for channel. Configure with "
                    + getPropertyName("APIkey")
                    + ". You can get an apikey by registering in https://omatili.maanmittauslaitos.fi/.");
        }
    }


    public ChannelSearchResult doSearch(SearchCriteria criteria) {
        ChannelSearchResult result = new ChannelSearchResult();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("spelling_case_insensitive", criteria.getSearchString());
        params.put("limit", Integer.toString(criteria.getMaxResults() + 1));
        String[] splittedCriteriaSRS = criteria.getSRS().split(":");
        params.put("crs", "http://www.opengis.net/def/crs/" + splittedCriteriaSRS[0] + "/0/" + splittedCriteriaSRS[1]);

        JSONArray features = getResults(IOHelper.constructUrl(baseUrl, params));
        try {
            for (int i = 0; i < features.length(); i++) {
                result.addItem(parseResultItem(features.getJSONObject(i)));
            }
        } catch (JSONException e) {
            throw new ServiceRuntimeException("Error parsing result items", e);
        }
        return result;
    }

    private JSONArray getResults(String url) {
        try {
            final String responseData = IOHelper.getURL(url, apiKey, "", Collections.EMPTY_MAP);
            JSONObject responseDataObject = new JSONObject(responseData);
            return responseDataObject.getJSONArray("features");
        } catch (IOException ex) {
            throw new ServiceRuntimeException("Error calling service", ex);
        } catch (JSONException e) {
            throw new ServiceRuntimeException("Error parsing result from service");
        }
    }

    private SearchResultItem parseResultItem(JSONObject feature) throws JSONException {
        JSONObject properties = feature.getJSONObject("properties");
        JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");

        SearchResultItem item = new SearchResultItem();
        item.setTitle(properties.getString("spelling"));
        item.setLon(Double.parseDouble(coordinates.get(0).toString()));
        item.setLat(Double.parseDouble(coordinates.get(1).toString()));
        item.setZoomScale(properties.getDouble("scaleRelevance"));

        String language = properties.getString("language");
        String placeType = getPlaceType(properties.getInt(PLACE_TYPE), language);
        if (placeType.isEmpty() || placeType == null) {
            log.warn("Problems getting placeType info for " + item.getTitle() + ".");
        }
        item.setType(placeType);
        String municipality = getMunicipality(properties.getInt(MUNICIPALITY), language);
        if (municipality.isEmpty() || municipality == null) {
            log.warn("Problems getting municipality info for " + item.getTitle() + ".");
        }
        item.setRegion(municipality);
        return item;
    }

    private String getPlaceType(int code, String lang) {
        HashMap<String, String> locale = placetypeCodes.get(code);
        if (locale == null || lang == null) {
            return null;
        }
        return locale.get(lang);
    }

    private String getMunicipality(int code, String lang) {
        HashMap<String, String> locale = municipalityCodes.get(code);
        if (locale == null || lang == null) {
            return null;
        }
        return locale.get(lang);
    }

    /**
     * Chooses when ever to use local or url based resource to make code hashmaps.
     *
     * @param thing
     * @return
     */
    private HashMap<Integer, HashMap<String, String>> buildAdditionalInfo(String thing) {
        HashMap<Integer, HashMap<String, String>> results;
        if (useLocalCodelist) {
            results = buildAdditionalInfoFromLocals(thing);
        } else {
            results = buildAdditionalInfoFromURL(thing);
        }
        return results;
    }

    /**
     * Takes url suffix in and constructs string representation of that file.
     *
     * @param infoURL
     * @return HashMap that holds id to hashmap of languages.
     */
    private HashMap<Integer, HashMap<String, String>> buildAdditionalInfoFromURL(String infoURL) {
        HashMap<Integer, HashMap<String, String>> results = new HashMap<>();
        try {
            for (int i = 0; i < languageArray.length; i++) {
                final String responseData = IOHelper.getURL(codelistUrl + infoURL + "/items" + "?lang=" + languageArray[i]);
                buildLanguageInfoMap(results, responseData);
            }
        } catch (IOException ex) {
            log.info("Failed to retrieve data for " + infoURL + " via url. Trying local data.");
            results = buildAdditionalInfoFromLocals(infoURL);
        }
        return results;
    }

    /**
     * Takes file suffix in and constructs string representation of that file.
     *
     * @param fileSuffix
     * @return HashMap that holds id to hashmap of languages.
     */
    private HashMap<Integer, HashMap<String, String>> buildAdditionalInfoFromLocals(String fileSuffix) {
        HashMap<Integer, HashMap<String, String>> results = new HashMap<>();
        for (int i = 0; i < languageArray.length; i++) {
            try {
                String fileAsString = IOHelper.readString(getClass().getResourceAsStream(ID + "_" + fileSuffix.toLowerCase() + "_" + languageArray[i] + ".json"));
                buildLanguageInfoMap(results, fileAsString);
            } catch (IOException e) {
                log.error(e, "Could not construct " + fileSuffix + " from local nor from url sources.");
            }
        }

        return results;
    }

    /**
     * Goes through Json and adds all the label data to Hashmap that holds language id pairs.
     *
     * @param results:      HashMap to fill with results.
     * @param responseData: The full json where to parse everything.
     */
    private void buildLanguageInfoMap(HashMap<Integer, HashMap<String, String>> results, String responseData) {
        try {
            JSONObject values = new JSONObject(responseData).getJSONObject("values");
            for (int j = 0; j < values.names().length(); j++) {
                JSONObject singularValue = values.getJSONObject(values.names().get(j).toString());
                int value = Integer.parseInt(singularValue.getString("value"));
                HashMap<String, String> languageMap = results.getOrDefault(value, new HashMap<>(3));
                languageMap.put(singularValue.getString("lang"), singularValue.getString("label"));
                results.put(value, languageMap);
            }
        } catch (JSONException ex) {
            throw new ServiceRuntimeException("Error parsing codelist", ex);
        }
    }
}
