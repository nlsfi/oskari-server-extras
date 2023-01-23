package fi.nls.oskari.control.view.modifier.param;

import fi.mml.portti.service.search.*;
import fi.nls.oskari.annotation.OskariViewModifier;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.search.NLSFIGeocodingSearchChannel;
import fi.nls.oskari.util.PropertyUtil;
import fi.nls.oskari.view.modifier.ModifierException;
import fi.nls.oskari.view.modifier.ModifierParams;
import fi.nls.oskari.view.modifier.ParamHandler;
import fi.nls.oskari.view.modifier.ViewModifier;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@OskariViewModifier("nationalCadastralReference")
public class NationalCadastralRefParamHandler extends ParamHandler {

    private static SearchService searchService = new SearchServiceImpl();
    private static final Logger log = LogFactory.getLogger(NationalCadastralRefParamHandler.class);

    private List<String> channelIDs = new ArrayList<>();
    private final String[] defaultChannels = {NLSFIGeocodingSearchChannel.ID};

    public void init() {
        String[] channels = PropertyUtil.getCommaSeparatedList("paramhandler.nationalCadastralReference.channels");
        if (channels.length == 0) {
            channels = defaultChannels;
        }
        channelIDs.clear();
        Arrays.stream(channels).forEach(channelId -> channelIDs.add(channelId));
    }

    public boolean handleParam(final ModifierParams params) throws ModifierException {
        if (params.getParamValue() == null) {
            return false;
        }

        final String cadastralRef = params.getParamValue().replace('_', ' ');
        // we can use fi as language since we only get coordinates, could
        // get it from published map if needed
        final ArrayList<SearchResultItem> latlon_list = getCoordinatesFromNatCadRef(
                params.getLocale(), cadastralRef, params.getView().getSrsName());
        log.debug("National cadastral reference coordinates", latlon_list);
        if (latlon_list.size() > 0) {
            final JSONObject state = getBundleState(params.getConfig(), ViewModifier.BUNDLE_MAPFULL);
            try {
                state.put(KEY_EAST, latlon_list.get(0).getLon());
                state.put(KEY_NORTH, latlon_list.get(0).getLat());
                return true;
            } catch (JSONException je) {
                throw new ModifierException("Could not set coordinates from cadastral ref.");
            }
        }
        return false;
    }

    private ArrayList<SearchResultItem> getCoordinatesFromNatCadRef(Locale locale,
                                                                    String searchString, String srs) {

        SearchCriteria sc = new SearchCriteria();
        channelIDs.forEach(id -> sc.addChannel(id));
        sc.setSearchString(searchString);
        sc.setLocale(locale.getLanguage());
        sc.setSRS(srs);

        Query query = searchService.doSearch(sc);

        ArrayList<SearchResultItem> lat_lon = new ArrayList<>();
        channelIDs.forEach(channelId -> {
            query.findResult(channelId).getSearchResultItems()
                    .forEach(item -> lat_lon.add(item));
        });
        return lat_lon;
    }
}
