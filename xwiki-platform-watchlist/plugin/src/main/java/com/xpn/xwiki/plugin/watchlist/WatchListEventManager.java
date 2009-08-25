/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.plugin.watchlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.activitystream.api.ActivityEventType;
import com.xpn.xwiki.plugin.activitystream.api.ActivityStreamException;
import com.xpn.xwiki.plugin.activitystream.plugin.ActivityEvent;
import com.xpn.xwiki.plugin.activitystream.plugin.ActivityStreamPluginApi;

/**
 * Manager for WatchList events. This class allow to store all the events fired during a given interval. It also allows
 * to perform a match between events and elements watched by a user.
 * 
 * @version $Id$
 */
@SuppressWarnings("serial")
public class WatchListEventManager
{
    /**
     * Logger.
     */
    private static final Log LOG = LogFactory.getLog(WatchListEventManager.class);

    /**
     * Events to match.
     */
    private static final List<String> MATCHING_EVENT_TYPES = new ArrayList<String>()
    {
        {
            add(ActivityEventType.CREATE);
            add(ActivityEventType.UPDATE);
            add(ActivityEventType.DELETE);
        }
    };

    /**
     * ActivityStream plugin API.
     */
    private final ActivityStreamPluginApi asApi;

    /**
     * List of events which have occurred between the start date and the current time.
     */
    private final List<WatchListEvent> events = new ArrayList<WatchListEvent>();

    /**
     * Constructor. Gets all the events fired during the interval between the given date and the current date.
     * 
     * @param context the XWiki context
     * @param start start date to use for document matching
     */
    public WatchListEventManager(Date start, XWikiContext context)
    {
        asApi = (ActivityStreamPluginApi) context.getWiki().getPluginApi("activitystream", context);
        List<Object> parameters = new ArrayList<Object>();
        List<ActivityEvent> rawEvents;

        parameters.add(start);

        try {
            rawEvents =
                asApi.searchEvents("act.date > ? and act.type in ('" + StringUtils.join(MATCHING_EVENT_TYPES, "','")
                    + "')", false, 0, 0, parameters);

            // If the page has been modified several times we wan't to display only one diff, if the page has been
            // delete after update events we want to discard the update events since we won't be able to display diff
            // from a deleted document. See WatchListEvent#addEvent(WatchListEvent) and
            // WatchListEvent#equals(WatchListEvent).
            for (ActivityEvent rawEvent : rawEvents) {
                WatchListEvent event = new WatchListEvent(rawEvent);
                if (!events.contains(event)) {
                    events.add(new WatchListEvent(rawEvent));
                } else {
                    WatchListEvent existingCompositeEvent = events.get(events.indexOf(event));
                    existingCompositeEvent.addEvent(event);
                }
            }
        } catch (ActivityStreamException e) {
            LOG.error("Failed to retrieve updated documents from activity stream");
            e.printStackTrace();
        }
    }

    /**
     * @return the number of events the matcher will work with.
     */
    public int getEventNumber()
    {
        return events.size();
    }

    /**
     * Get the events matching criteria.
     * 
     * @param wikis a list of wikis from which events should match
     * @param spaces a list of spaces from which events should match
     * @param documents a list of documents from which events should match
     * @param userName notification recipient
     * @param context the XWiki context
     * @return the list of events matching the given scopes
     */
    public List<WatchListEvent> getMatchingEvents(List<String> wikis, List<String> spaces, List<String> documents,
        String userName, XWikiContext context)
    {
        List<WatchListEvent> matchingEvents = new ArrayList<WatchListEvent>();

        for (WatchListEvent event : events) {
            if (wikis.contains(event.getWiki()) || spaces.contains(event.getPrefixedSpace())
                || documents.contains(event.getPrefixedFullName())) {
                try {
                    if (context.getWiki().getRightService().hasAccessLevel("view", userName,
                        event.getPrefixedFullName(), context)) {
                        matchingEvents.add(event);
                    }
                } catch (XWikiException e) {
                    // We're in a job, we don't throw exceptions
                    e.printStackTrace();
                }
            }
        }

        Collections.sort(matchingEvents);

        return matchingEvents;
    }
}
