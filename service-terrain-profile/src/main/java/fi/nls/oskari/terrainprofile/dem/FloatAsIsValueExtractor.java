package fi.nls.oskari.terrainprofile.dem;

import java.util.HashMap;
import java.util.Map;

import org.oskari.wcs.geotiff.IFD;
import org.oskari.wcs.geotiff.TIFFReader;

public class FloatAsIsValueExtractor implements TileValueExtractor {

    public static final String ID = "FLOAT";

    private final float noData;
    private final Map<Integer, float[]> tileCache = new HashMap<>();

    public FloatAsIsValueExtractor(float noData) {
        this.noData = noData;
    }

    @Override
    public void validate(IFD ifd) throws IllegalArgumentException {
        if (ifd.getSampleFormat()[0] != 3) {
            throw new IllegalArgumentException("Unexpected sample format, expected float32");
        }
    }

    @Override
    public double getTileValue(TIFFReader r, IFD ifd, int ifdIdx, int tileIndex, int tileOffset) {
        int n = ifd.getTileWidth() * ifd.getTileHeight();
        float[] tile = tileCache.computeIfAbsent(tileIndex, __ -> r.readTile(ifdIdx, tileIndex, new float[n]));
        float value = tile[tileOffset];
        return (value == noData) ? Double.NaN : value;
    }

}
