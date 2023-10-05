package fi.nls.oskari.search;

import fi.mml.portti.service.search.ChannelSearchResult;
import fi.mml.portti.service.search.SearchCriteria;
import fi.nls.oskari.annotation.Oskari;

import java.util.Collections;
import java.util.List;

/**
 * Search channel for NLS nearest feature requests
 * sample request
 * https://ws.nls.fi/maasto/nearestfeature?TYPENAME=oso:Osoitepiste&COORDS=385445,6675125,EPSG:3067&SRSNAME=EPSG:3067&MAXFEATURES=1&BUFFER=1000
 */
@Oskari(NLSNearestFeatureSearchChannel.ID)
public class NLSNearestFeatureSearchChannel extends NLSFIGeocodingSearchChannel {

    public static final String ID = "NLS_NEAREST_FEATURE_CHANNEL";

    // We only do reverse geocoding for the nearest feature
    @Override
    public Capabilities getCapabilities() {
        return Capabilities.COORD;
    }

    // Only reverse so just return empty list always
    @Override
    public List<String> doSearchAutocomplete(String searchString) {
        return Collections.emptyList();
    }

    public ChannelSearchResult reverseGeocode(SearchCriteria criteria) {
        criteria.addParam(NLSFIGeocodingSearchChannel.ID + "_sources", "interpolated-road-addresses");
        criteria.addParam(PARAM_BUFFER, getBuffer(criteria.getParam(PARAM_BUFFER)));
        return super.reverseGeocode(criteria);
    }

    public int getMaxResults(int max) {
        if(max <= 0) {
            return 1;
        }
        return super.getMaxResults(max);
    }

    public String getBuffer(Object param) {
        if (param != null) {
            if (param instanceof String) {
                String str = (String) param;
                if (!str.isEmpty()) {
                    return str;
                }
            } else if (param instanceof Integer) {
                // fixes issue where GetReverseGeocoding sends default buffer as int
                return param.toString();
            }
        }
        return "1000";
    }

    public boolean isValidSearchTerm(SearchCriteria criteria) {
        return criteria.isReverseGeocode();
    }
}
