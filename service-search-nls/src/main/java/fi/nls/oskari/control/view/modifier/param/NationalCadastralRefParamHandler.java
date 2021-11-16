package fi.nls.oskari.control.view.modifier.param;

import fi.mml.portti.service.search.*;
import fi.nls.oskari.SearchWorker;
import fi.nls.oskari.annotation.OskariViewModifier;
import fi.nls.oskari.view.modifier.ParamHandler;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.search.channel.KTJkiiSearchChannel;
import fi.nls.oskari.search.channel.MaastoAddressChannelSearchService;
import fi.nls.oskari.util.PropertyUtil;
import fi.nls.oskari.view.modifier.ModifierException;
import fi.nls.oskari.view.modifier.ModifierParams;
import fi.nls.oskari.view.modifier.ViewModifier;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@OskariViewModifier("nationalCadastralReference")
public class NationalCadastralRefParamHandler extends ParamHandler {

    private static SearchService searchService = new SearchServiceImpl();
    private static final Logger log = LogFactory.getLogger(NationalCadastralRefParamHandler.class);

    private List<String> channelIDs = new ArrayList<>();
    private final String[] defaultChannels = {MaastoAddressChannelSearchService.ID, KTJkiiSearchChannel.ID};

    public void init() {
        String[] channels = PropertyUtil.getCommaSeparatedList("paramhandler.nationalCadastralReference.channels");
        if (channels.length == 0) {
            channels = defaultChannels;
        }
        channelIDs.clear();
        Arrays.stream(channels).forEach(channelId -> channelIDs.add(channelId));
    }
    public boolean handleParam(final ModifierParams params) throws ModifierException {
        if(params.getParamValue() == null) {
            return false;
        }

        final String cadastralRef = params.getParamValue().replace('_', ' ');
        // we can use fi as language since we only get coordinates, could
        // get it from published map if needed
        final ArrayList<String[]> latlon_list = getCoordinatesFromNatCadRef(
                params.getLocale(), cadastralRef, PropertyUtil.getDefaultLanguage());
        // TODO: KTJ search channel now returns "palstat" instead of
        // "kiinteistöt" -> one ref returns multiple results
        // we need to think this through
        // if (latlon_list.size() == 1) {
        log.debug("National cadastral reference coordinates", latlon_list);
        if (latlon_list.size() > 0) {
            final JSONObject state = getBundleState(params.getConfig(), ViewModifier.BUNDLE_MAPFULL);
            try {
                state.put(KEY_EAST, latlon_list.get(0)[0]);
                state.put(KEY_NORTH, latlon_list.get(0)[1]);
                return true;
            } catch (JSONException je) {
                throw new ModifierException("Could not set coordinates from cadastral ref.");
            }
        }

        // TODO: need error handling if address not found or multiple
        // address found [http://haisuli.nls.fi/jira/browse/PORTTISK-1078]
        return false;
    }

    private ArrayList<String[]> getCoordinatesFromNatCadRef(Locale locale,
            String searchString, String publishedMapLanguage) {

        ArrayList<String[]> lat_lon = new ArrayList<>();

        if (!"".equalsIgnoreCase(publishedMapLanguage)
                && publishedMapLanguage != null) {
            locale = new Locale(publishedMapLanguage);
        }

        String isLegal = SearchWorker.checkLegalSearch(searchString);
        if (isLegal.equals(SearchWorker.STR_TRUE)) {
            try {
                searchString = URLDecoder.decode(searchString, "UTF-8");
                SearchCriteria sc = new SearchCriteria();
                channelIDs.forEach(id -> sc.addChannel(id));
                //sc.addChannel(channelID);
                //sc.addChannel(KTJkiiSearchChannel.ID);
                sc.setSearchString(searchString);
                sc.setLocale(locale.getLanguage());

                Query query = searchService.doSearch(sc);

                channelIDs.forEach(channelId -> {
                    query.findResult(channelId).getSearchResultItems()
                            .forEach(item -> lat_lon.add(item.getContentURL().split("_")));
                });
            } catch (UnsupportedEncodingException e) {
                System.err.println("Problem encoding searchString. ");
            }

        }

        return lat_lon;
    }
}
