package fi.nls.oskari.search.channel;

import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.SearchCriteria;
import fi.mml.portti.service.search.SearchResultItem;
import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.search.channel.SearchChannel;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.PropertyUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Oskari(OgcApiSearchChannel.ID)
public class OgcApiSearchChannel extends SearchChannel {


    public static final String ID = "OGC_API_SEARCH_CHANNEL";

    //Local configs
    private static String apiKey = null;
    private static String searchAddress = "https://avoin-paikkatieto.maanmittauslaitos.fi/geographic-names/features/v1/collections/placenames/items?spelling_case_insensitive=";
    private static String additionalPlaceTypeInfoBaseUrl = "https://beta-paikkatieto.maanmittauslaitos.fi/catalogue/v1/codelistgroups/geographic-names/codelists/";
    private static String[] localAdditionalInfoNames = {"placeType", "municipality"};
    private static final boolean USE_LOCAL_ADDITIONAL_INFO = true;
    private static String[] languageArray = {"fin", "eng", "swe"};

    private static HashMap<Integer, HashMap<String, String>> placetypeCodes;
    private static HashMap<Integer, HashMap<String, String>> municipalityCodes;

    private Logger log = LogFactory.getLogger(this.getClass());

    private static String CRS = null;
    private static final String defaultCRS = "http://www.opengis.net/def/crs/EPSG/0/3067";


    public ChannelSearchResult doSearch(SearchCriteria criteria) {
        ChannelSearchResult result = new ChannelSearchResult();

        if (placetypeCodes == null) {
            placetypeCodes = buildAdditionalInfo(localAdditionalInfoNames[0]);
        }
        if (municipalityCodes == null) {
            municipalityCodes = buildAdditionalInfo(localAdditionalInfoNames[1]);
        }

        //ApiKey Things
        String propertyKey = PropertyUtil.get("search.channel." + ID + ".apikey");
        if (propertyKey != null) {
            String encodedApiKey = IOHelper.encode64(propertyKey + ":");
            apiKey = "Basic " + encodedApiKey;

        }
        if (apiKey == null) {
            log.error("OGC Api key was not found. OGC api will not return results.");
        }
        Map<String, String> headers = new HashMap<>(5);
        headers.put("Authorization", apiKey);

        String encodedSearchTerm = "";
        try {
            encodedSearchTerm = URLEncoder.encode(criteria.getSearchString(), StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String searchQuerry = searchAddress + encodedSearchTerm + "&limit=" + criteria.getMaxResults();

        CRS = PropertyUtil.get("OgcApiCrs", defaultCRS);
        if (CRS != null) {
            searchQuerry += "&crs=" + CRS;
        }

        try {
            final String responseData = IOHelper.getURL(searchQuerry, headers);

            JSONObject responseDataObject = new JSONObject(responseData);
            JSONArray features = responseDataObject.getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject properties = feature.getJSONObject("properties");
                JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");


                //Make SearchItem
                SearchResultItem item = new SearchResultItem();
                item.setTitle(properties.getString("spelling"));
                item.setType(placetypeCodes.get(properties.getInt(localAdditionalInfoNames[0])).get(properties.getString("language")));
                item.setLon(Double.parseDouble(coordinates.get(0).toString()));
                item.setLat(Double.parseDouble(coordinates.get(1).toString()));
                item.setZoomScale(properties.getDouble("scaleRelevance"));
                item.setRegion(municipalityCodes.get(properties.getInt(localAdditionalInfoNames[1])).get(properties.getString("language")));
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
            results = buildAdditionalInfoFromURL(additionalPlaceTypeInfoBaseUrl + thing + "/items");
        }
        return results;
    }

    /**
     * Takes url suffix in and constructs string represantation of that file.
     *
     * @param infoURL
     * @return HashMap that holds id to hashmap of languages.
     */
    private HashMap<Integer, HashMap<String, String>> buildAdditionalInfoFromURL(String infoURL) {
        HashMap<Integer, HashMap<String, String>> results = new HashMap<>();
        for (int i = 0; i < languageArray.length; i++) {
            try {
                final String responseData = IOHelper.getURL(infoURL + "?lang=" + languageArray[i]);
                buildLanguageInfoMap(results, responseData);

            } catch (IOException ex) {
                throw new RuntimeException("Error getting additional information", ex);
            }
        }
        return results;
    }

    /**
     * Takes file suffix in and constructs string represantation of that file.
     *
     * @param fileSuffix
     * @return HashMap that holds id to hashmap of languages.
     */
    private HashMap<Integer, HashMap<String, String>> buildAdditionalInfoFromLocals(String fileSuffix) {
        HashMap<Integer, HashMap<String, String>> results = new HashMap<>();
        for (int i = 0; i < languageArray.length; i++) {
            InputStream inputStreamFromFile = OgcApiSearchChannel.class.getResourceAsStream(ID + "_" + fileSuffix.toLowerCase() + "_" + languageArray[i] + ".json");
            Scanner s = new Scanner(inputStreamFromFile, "UTF-8").useDelimiter("\\A");
            String fileAsString = s.hasNext() ? s.next() : "";
            buildLanguageInfoMap(results, fileAsString);

        }
        return results;
    }

    /**
     * Goes through Json and adds all the label data to Hashmap that holds language id pairs.
     *
     * @param results: HashMap to fill with results.
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
