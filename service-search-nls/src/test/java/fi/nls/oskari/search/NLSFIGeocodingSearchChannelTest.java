package fi.nls.oskari.search;

import fi.nls.oskari.search.geocoding.Feature;
import fi.nls.oskari.search.geocoding.GeocodeHelper;
import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class NLSFIGeocodingSearchChannelTest {

    private static final NLSFIGeocodingSearchChannel channel = new NLSFIGeocodingSearchChannel();

    @BeforeAll
    public static void setUp() throws Exception {
        //PropertyUtil.addProperty("search.channel." + NLSFIGeocodingSearchChannel.ID + ".service.url", "https://mydomain.com/testing?");
        try {
            channel.init();
        } catch (ServiceRuntimeException e) {
            assertTrue(e.getMessage().startsWith("API-key missing for channel"));
        }
        String user = "[this is an api key for testing]"; //apikey
        PropertyUtil.addProperty("search.channel." + NLSFIGeocodingSearchChannel.ID + ".APIkey", user);
        PropertyUtil.addProperty("search.channel." + NLSFIGeocodingSearchChannel.ID + ".endpoint.test", "/xyz/testing");
        channel.init();
    }

    @AfterAll
    public static void tearDown() {
        PropertyUtil.clearProperties();
    }

    @Test
    public void getUrl() {
        String expected = "https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding/v2/advanced/search?" +
                "text=test" +
                "&lang=fi&size=6" +
                "&sources=geographic-names%2Ccadastral-units%2Cinterpolated-road-addresses" +
                "&crs=http%3A%2F%2Fwww.opengis.net%2Fdef%2Fcrs%2FEPSG%2F0%2F3067" +
                "&request-crs=http%3A%2F%2Fwww.opengis.net%2Fdef%2Fcrs%2FEPSG%2F0%2F3067";
        assertEquals(expected, channel.getUrl(channel.getSearchParams("test", "fi", 5, null)));

        String requestedSourcesExpected = "https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding/xyz/testing?" +
                "text=test" +
                "&lang=fi&size=6" +
                "&sources=testing%2Cmy%2Cdummy%2Csources" +
                "&crs=http%3A%2F%2Fwww.opengis.net%2Fdef%2Fcrs%2FEPSG%2F0%2F3067" +
                "&request-crs=http%3A%2F%2Fwww.opengis.net%2Fdef%2Fcrs%2FEPSG%2F0%2F3067";
        assertEquals(requestedSourcesExpected, channel.getUrl("test", channel.getSearchParams("test", "fi", 5, "testing,my,dummy,sources")));
    }

    @Test
    public void testAutoComplete() {
        //List<String> results = channel.doSearchAutocomplete("test");
        List<String> results = GeocodeHelper.parseAutocomplete(this.getClass().getResourceAsStream("nlsfi-geocoding-similar-response.json"));
        String commaSeparated = results.stream().collect(Collectors.joining(","));
        assertEquals("Tesmo,Tessu,Tesala,Tesoma,Tessjö", commaSeparated, "Parsed autocomplete response to words");
    }

    @Test
    public void testResponseParsing() throws IOException {
        Map<String, Object> geojson = GeocodeHelper.readJSON(this.getClass().getResourceAsStream("nlsfi-geocoding-search-response-addresses.json"));
        List<Feature> results = GeocodeHelper.parseResponse(geojson);
        assertEquals(5, results.size(), "Should get 5 results");
        assertEquals(channel.searchForAddressValue(results.get(0), "katunimi"), "Hämeenkatu");
        //results.stream().forEach(feat -> System.out.println(feat.id));
    }

    @Test
    public void testResponseParsingForReverse() throws IOException {
        Map<String, Object> geojson = GeocodeHelper.readJSON(this.getClass().getResourceAsStream("nlsfi-geocoding-search-response-reverse-addresses.json"));
        List<Feature> results = GeocodeHelper.parseResponse(geojson);
        assertEquals(4, results.size(), "Should get 4 results");
        assertEquals(channel.searchForAddressValue(results.get(0), "katunimi"), "Repovuorentie");
        //results.stream().forEach(feat -> System.out.println(feat.id));
    }
}