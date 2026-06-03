package fi.nls.oskari.terrainprofile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import fi.nls.oskari.control.ActionException;
import fi.nls.oskari.service.ServiceException;

@Disabled("Depends on an outside API")
public class TerrainProfileServiceTest {

    // Configurable so the live endpoint/coverage/apikey aren't baked into the repo.
    // Run with e.g. -Dterrain.test.apikey=... -Dterrain.test.endpoint=... when enabling.
    private static String endPoint = System.getProperty("terrain.test.endpoint",
            "https://avoin-karttakuva.maanmittauslaitos.fi/ortokuvat-ja-korkeusmallit/wcs/v2");
    private static String coverageId = System.getProperty("terrain.test.coverage", "korkeusmalli_2m");
    private static String apiKey = System.getProperty("terrain.test.apikey");
    private static float noData = Float.parseFloat(System.getProperty("terrain.test.noData", "-9999"));
    private static TerrainProfileService tps;

    @BeforeAll
    public static void setup() throws ServiceException {
        tps = new TerrainProfileService(endPoint, coverageId, apiKey,
                () -> new fi.nls.oskari.terrainprofile.dem.FloatAsIsValueExtractor(noData)); //
    }

    @Test
    @Disabled("Depends on an outside API")
    public void offsetIsCorrect() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        double[] coordinates = new double[] {
                500002, 6822001,
                501003, 6821004,
                502006, 6823007,
                501003, 6822509,
                500502, 6823206
        };

        List<DataPoint> points = tps.getTerrainProfile(coordinates, 0, -1);
        for (DataPoint p : points) {
            double e = p.getE();
            double n = p.getN();
            DataPoint single = tps.getTerrainProfile(new double[] { e, n }, 0, -1).get(0);
            assertEquals(e, single.getE(), 0.0);
            assertEquals(n, single.getN(), 0.0);
            assertEquals(p.getAltitude(), single.getAltitude(), 0.0);
        }
    }

    @Test
    @Disabled("Depends on an outside API")
    public void singlePointReturnsSensibleValue() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        // a point on land in southern Finland (EPSG:3067) should have a real, positive elevation
        double e = 385445;
        double n = 6675125;
        double alt = tps.getTerrainProfile(new double[] { e, n }, 1, 0).get(0).getAltitude();
        assertFalse(Double.isNaN(alt), "expected a value, got NaN");
        assertTrue(alt > 0 && alt < 1500, "elevation out of expected range: " + alt);
    }

    @Test
    @Disabled("Depends on an outside API")
    public void singlePointOutsideCoverageIsNaN() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        // a point in open sea / outside the land DEM should have no elevation data
        double e = 150000;
        double n = 6900000;
        double alt = tps.getTerrainProfile(new double[] { e, n }, 1, 0).get(0).getAltitude();
        assertTrue(Double.isNaN(alt), "expected NaN for out-of-coverage point, got " + alt);
    }

    @Test
    @Disabled("Too long")
    public void testLongLine() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        double[] coordinates = {
                279816, 6640056,
                532571, 7762366
        };

        List<DataPoint> points = tps.getTerrainProfile(coordinates, 100, -1);
        for (DataPoint p : points) {
            assertNotEquals(0, p.getAltitude(), 0);
        }
    }

    @Test
    @Disabled("Depends on an outside API")
    public void testHorizontalLine() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        double[] coordinates = {
                400000, 6700000,
                404094, 6700000
        };

        List<DataPoint> points = tps.getTerrainProfile(coordinates, 100, -1);
        for (DataPoint p : points) {
            assertNotEquals(0, p.getAltitude(), 0);
        }
    }

    @Test
    @Disabled("Depends on an outside API")
    public void tesVerticalLine() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        double[] coordinates = {
                400000, 6700000,
                400000, 6704094
        };

        List<DataPoint> points = tps.getTerrainProfile(coordinates, 100, -1);
        for (DataPoint p : points) {
            assertNotEquals(0, p.getAltitude(), 0);
        }
    }

    @Test
    @Disabled("Depends on an outside API")
    public void testSmallLine() throws IOException, ActionException, ParserConfigurationException, SAXException, ServiceException {
        double[] coordinates = {
                400000, 6700000,
                400256, 6700000,
                400256, 6700256,
                400000, 6700256
        };

        List<DataPoint> points = tps.getTerrainProfile(coordinates, 100, -1);
        for (DataPoint p : points) {
            assertNotEquals(0, p.getAltitude(), 0);
        }
    }

}
