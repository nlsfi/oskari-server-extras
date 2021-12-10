package fi.nls.oskari.search.channel;

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
import java.io.InputStream;
import java.util.*;

import static org.springframework.beans.PropertyAccessorUtils.getPropertyName;


@Oskari(NLSFIGeographicNamesSearchChannel.ID)
public class NLSFIGeographicNamesSearchChannel extends SearchChannel {


    public static final String ID = "NLSFIGeographicNamesSearchChannel";

    //Local configs
    /**
     * One can use property search.channel.OGC_API_SEARCH_CHANNEL.apikey but if really needed apikey can go here.
     */
    private static String apiKey = null;
    private static String defaultSearchAddress = "https://avoin-paikkatieto.maanmittauslaitos.fi/geographic-names/features/v1/collections/placenames/items";
    private static String defaultadditionalPlaceTypeInfoBaseUrl = "https://beta-paikkatieto.maanmittauslaitos.fi/catalogue/v1/codelistgroups/geographic-names/codelists/";
    private static String searchAddress;
    private static String additionalPlaceTypeInfoBaseUrl;

    private static final String PLACE_TYPE = "placeType";
    private static final String MUNICIPALITY = "municipality";
    /**
     * Set true if one wants to use local jsons instead of url based one
     */
    private static final boolean USE_LOCAL_ADDITIONAL_INFO = true;
    /**
     * Add languages you have translaited here as language code
     */
    private static String[] languageArray = {"fin", "eng", "swe"};

    private static HashMap<Integer, HashMap<String, String>> placetypeCodes;
    private static HashMap<Integer, HashMap<String, String>> municipalityCodes;

    private Logger log = LogFactory.getLogger(this.getClass());


    @Override
    public void init() {
        super.init();

        searchAddress = getProperty("searchAddress", defaultSearchAddress);
        if (searchAddress.equals(defaultSearchAddress)) {
            log.info("Using default searchAddress, use " + getPropertyName("searchAddress") + " to configure different one");
        }
        additionalPlaceTypeInfoBaseUrl = getProperty("additionalPlaceTypeInfoBaseUrl", defaultadditionalPlaceTypeInfoBaseUrl);
        if (additionalPlaceTypeInfoBaseUrl.equals(defaultadditionalPlaceTypeInfoBaseUrl)) {
            log.info("Using default additionalPlaceTypeInfoBaseUrl, use " + getPropertyName("additionalPlaceTypeInfoBaseUrl") + " to configure different one");
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

        String searchQuery = IOHelper.constructUrl(searchAddress, params);
        try {
            final String responseData = IOHelper.getURL(searchQuery, apiKey, "", Collections.EMPTY_MAP);
            JSONObject responseDataObject = new JSONObject(responseData);
            JSONArray features = responseDataObject.getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject properties = feature.getJSONObject("properties");
                JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");

                //Make SearchItem
                SearchResultItem item = new SearchResultItem();
                item.setTitle(properties.getString("spelling"));
                item.setLon(Double.parseDouble(coordinates.get(0).toString()));
                item.setLat(Double.parseDouble(coordinates.get(1).toString()));
                item.setZoomScale(properties.getDouble("scaleRelevance"));

                String placeType = placetypeCodes.get(properties.getInt(PLACE_TYPE)).get(properties.getString("language"));
                if (placeType.isEmpty() || placeType == null) {
                    log.warn("Problems getting placeType info for " + item.getTitle() + ".");
                }
                item.setType(placeType);
                String municipality = municipalityCodes.get(properties.getInt(MUNICIPALITY)).get(properties.getString("language"));
                if (municipality.isEmpty() || municipality == null) {
                    log.warn("Problems getting municipality info for " + item.getTitle() + ".");
                }
                item.setRegion(municipality);
                result.addItem(item);

            }

        } catch (IOException ex) {
            throw new RuntimeException("Error searching", ex);
        } catch (JSONException e) {
            log.warn("Json error during building and/or making of Search results", e);
        }
        return result;
    }

    /**
     * Chooses when ever to use local or url based resource to make code hashmaps.
     *
     * @param thing
     * @return
     */
    private HashMap<Integer, HashMap<String, String>> buildAdditionalInfo(String thing) {
        HashMap<Integer, HashMap<String, String>> results;
        if (USE_LOCAL_ADDITIONAL_INFO) {
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
                final String responseData = IOHelper.getURL(additionalPlaceTypeInfoBaseUrl + infoURL + "/items" + "?lang=" + languageArray[i]);
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
            String fileAsString = null;
            try {
                fileAsString = IOHelper.readString(getClass().getResourceAsStream(ID + "_" + fileSuffix.toLowerCase() + "_" + languageArray[i] + ".json"));
            } catch (IOException e) {
                log.error("Could not construct " + fileSuffix + " from local nor from url sources.");
                e.printStackTrace();
            }
            buildLanguageInfoMap(results, fileAsString);
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
            JSONObject values = (new JSONObject(responseData)).getJSONObject("values");
            for (int j = 0; j < values.names().length(); j++) {
                JSONObject singularValue = values.getJSONObject(values.names().get(j).toString());
                int value = Integer.parseInt(singularValue.getString("value"));
                HashMap<String, String> languageMap = results.getOrDefault(value, new HashMap<>(3));
                languageMap.put(singularValue.getString("lang"), singularValue.getString("label"));
                results.remove(value);
                results.put(value, languageMap);
            }

        } catch (JSONException ex) {
            throw new RuntimeException("Error constructing additional info json object", ex);
        }
    }
}
