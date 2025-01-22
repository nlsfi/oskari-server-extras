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

    private static String endPoint = "https://beta-karttakuva.maanmittauslaitos.fi/wcs/service/ows";
    private static String coverageId = "korkeusmalli__korkeusmalli";
    private static TerrainProfileService tps;

    @BeforeAll
    public static void setup() throws ServiceException {
        tps = new TerrainProfileService(endPoint, coverageId);
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
