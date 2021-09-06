package fi.nls.oskari.search;

import fi.nls.oskari.service.ServiceRuntimeException;
import fi.nls.oskari.util.PropertyUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class GeolocatorNLSFISearchChannelTest {

    private static final GeolocatorNLSFISearchChannel channel = new GeolocatorNLSFISearchChannel();

    @BeforeClass
    public static void setUp() throws Exception {
        //PropertyUtil.addProperty("search.channel." + GeolocatorNLSFISearchChannel.ID + ".service.url", "https://mydomain.com/testing?");
        try {
            channel.init();
        } catch (ServiceRuntimeException e) {
            assertTrue(e.getMessage().startsWith("API-key missing for channel"));
        }
        String user = "[this is an api key for testing]"; //apikey
        PropertyUtil.addProperty("search.channel." + GeolocatorNLSFISearchChannel.ID + ".APIkey", user);
        channel.init();
    }

    @AfterClass
    public static void tearDown() {
        PropertyUtil.clearProperties();
    }

    //
    @Test
    public void getUrl() {
        String expected = "https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding/v1/pelias/search?" +
                "text=test" +
                "&lang=fi&size=6" +
                "&sources=geographic-names%2Caddresses%2Ccadastral-units" +
                "&crs=http%3A%2F%2Fwww.opengis.net%2Fdef%2Fcrs%2FEPSG%2F0%2F3067" +
                "&request-crs=http%3A%2F%2Fwww.opengis.net%2Fdef%2Fcrs%2FEPSG%2F0%2F3067";
        assertEquals(expected, channel.getUrl(channel.getQueryParams("test", "fi", "ESPG:3067", 5)));
    }

    @Test
    public void testAutoComplete() {
        //List<String> results = channel.doSearchAutocomplete("test");
        List<String> results = channel.parseAutocomplete(this.getClass().getResourceAsStream("geolocator-nlsfi-similar-response.json"));
        String commaSeparated = results.stream().collect(Collectors.joining(","));
        assertEquals("Parsed autocomplete response to words", "Tesmo,Tessu,Tesala,Tesoma,Tessjö", commaSeparated);
    }

    @Test
    public void testResponseParsing() throws IOException {
        //List<String> results = channel.doSearchAutocomplete("test");

        Map<String, Object> geojson = channel.readMap(this.getClass().getResourceAsStream("geolocator-nlsfi-search-response-addresses.json"));
        List<GeolocatorNLSFISearchChannel.Feature> results = channel.parseResponse(geojson);
        results.stream().forEach(feat -> System.out.println(feat.id));
        //assertEquals("Parsed autocomplete response to words", "Tesmo,Tessu,Tesala,Tesoma,Tessjö", commaSeparated);
    }
}