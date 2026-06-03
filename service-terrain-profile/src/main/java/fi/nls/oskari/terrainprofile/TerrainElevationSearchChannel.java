package fi.nls.oskari.terrainprofile;

import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.SearchCriteria;
import fi.mml.portti.service.search.SearchResultItem;
import fi.nls.oskari.annotation.Oskari;
import fi.nls.oskari.domain.geo.Point;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.map.geometry.ProjectionHelper;
import fi.nls.oskari.search.channel.SearchChannel;
import fi.nls.oskari.service.ServiceException;

import java.util.List;

/**
 * Search channel for resolving the terrain elevation (altitude) of a single point via "reverse geocoding".
 */
@Oskari(TerrainElevationSearchChannel.ID)
public class TerrainElevationSearchChannel extends SearchChannel {

    public static final String ID = "TerrainElevationSearchChannel";

    private static final Logger LOG = LogFactory.getLogger(TerrainElevationSearchChannel.class);

    private TerrainProfileService tps;

    public TerrainElevationSearchChannel() {
        this(null);
    }

    public TerrainElevationSearchChannel(TerrainProfileService tps) {
        this.tps = tps;
    }

    @Override
    public void init() {
        super.init();
        try {
            getService();
        } catch (ServiceException e) {
            // not fatal, try again later when a request comes in
            LOG.error("Failed to init TerrainProfileService: " + e.getMessage(), e);
        }
    }

    protected synchronized TerrainProfileService getService() throws ServiceException {
        if (tps == null) {
            tps = TerrainProfileService.fromProperties();
        }
        return tps;
    }

    // Only reverse geocoding is supported
    @Override
    public Capabilities getCapabilities() {
        return Capabilities.COORD;
    }

    @Override
    public boolean isValidSearchTerm(SearchCriteria criteria) {
        return criteria.isReverseGeocode();
    }

    @Override
    public ChannelSearchResult reverseGeocode(SearchCriteria criteria) {
        ChannelSearchResult result = new ChannelSearchResult();
        result.setChannelId(ID);

        final double lon = criteria.getLon();
        final double lat = criteria.getLat();
        final String srs = criteria.getSRS();

        try {
            TerrainProfileService service = getService();
            double e = lon;
            double n = lat;
            String serviceSrs = service.getServiceSrs();
            // only reproject to the DEM's coordinate system if the queried point isn't already in it
            if (srs != null && !srs.equalsIgnoreCase(serviceSrs)) {
                Point p = ProjectionHelper.transformPoint(lon, lat, srs, serviceSrs);
                e = p.getLon();
                n = p.getLat();
            }
            List<DataPoint> points = service.getTerrainProfile(new double[] { e, n }, 1, 0);
            if (points.isEmpty()) {
                return result;
            }
            double altitude = points.get(0).getAltitude();
            if (Double.isNaN(altitude)) {
                // no elevation data at this point
                return result;
            }

            SearchResultItem item = new SearchResultItem();
            // echo the queried coordinates back in the requested SRS
            item.setLon(lon);
            item.setLat(lat);
            item.setType(ID);
            item.setTitle(Double.toString(altitude));
            item.addValue("altitude", altitude);
            result.addItem(item);
        } catch (Exception ex) {
            LOG.error("Couldn't get terrain elevation from service", ex.getMessage());
            result.setQueryFailed(true);
        }

        return result;
    }
}
