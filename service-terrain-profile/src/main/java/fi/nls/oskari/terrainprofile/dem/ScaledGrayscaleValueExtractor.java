package fi.nls.oskari.terrainprofile.dem;

import java.util.HashMap;
import java.util.Map;

import org.oskari.wcs.geotiff.IFD;
import org.oskari.wcs.geotiff.TIFFReader;

public class ScaledGrayscaleValueExtractor implements TileValueExtractor {

    public static final String ID = "INT";

    private final double scaleInv;
    private final double negatedOffset;
    private final int noData;

    private final Map<Integer, short[]> tileCache = new HashMap<>();

    private boolean unsigned;

    public ScaledGrayscaleValueExtractor(double scale, double offset, int noData) {
        this.scaleInv = 1.0 / scale;
        this.negatedOffset = -offset;
        this.noData = noData;
    }

    @Override
    public void validate(IFD ifd) throws IllegalArgumentException {
        if (ifd.getSamplesPerPixel() != 1) {
            throw new IllegalArgumentException("Unexpected samples per pixel, expected 1 (grayscale)");
        }
        if (ifd.getSampleFormat()[0] != 1 && ifd.getSampleFormat()[1] != 2) {
            throw new IllegalArgumentException("Unexpected sample format, expected unsigned or signed integer");
        }
        unsigned = ifd.getSampleFormat()[0] == 1;
        if (ifd.getBitsPerSample()[0] != 16) {
            throw new IllegalArgumentException("Unexpected bits per sample, expected 16 bits per sample (grayscale)");
        }
    }

    @Override
    public double getTileValue(TIFFReader r, IFD ifd, int ifdIdx, int tileIndex, int tileOffset) {
        int n = ifd.getTileWidth() * ifd.getTileHeight();
        short[] tile = tileCache.computeIfAbsent(tileIndex, __ -> r.readTile(ifdIdx, tileIndex, new short[n]));
        int value = unsigned ? tile[tileOffset] & 0xFFFF : tile[tileOffset];
        return value == noData ? Double.NaN : (value + negatedOffset) * scaleInv;
    }

}
