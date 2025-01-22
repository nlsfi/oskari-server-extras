package fi.nls.oskari;

import fi.nls.oskari.domain.geo.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by SMAKINEN on 14.3.2016.
 */
public class NLSFIPointTransformerTest {

    NLSFIPointTransformer transformer = new NLSFIPointTransformer();

    @Test
    public void testReproject() throws Exception {

        Point input = new Point(3632299.672, 7163325.996);
        Point value = transformer.reproject(input, "NLSFI:ykj", "NLSFI:euref");

        assertEquals(632073.8229999975, value.getLon(), 0.0, "lon");
        assertEquals(7160328.224999596, value.getLat(), 0.0, "lat");
    }

    @Test
    public void testReprojectCoordinateOrder() throws Exception {

        Point input = new Point(1530012.833,6942456.165);
        Point value = transformer.reproject(input, "NLSFI:kkj", "LATLON:kkj");
        // System.out.println(value.getLon() + "\t" + value.getLat());
        assertEquals(21.58399205221266, value.getLon(), 0.0, "lon");
        assertEquals(62.58520521458718, value.getLat(), 0.0, "lat");
    }
}
