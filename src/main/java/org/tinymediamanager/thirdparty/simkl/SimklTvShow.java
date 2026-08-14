/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.thirdparty.simkl;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.thirdparty.simkl.model.SimklActivities;
import org.tinymediamanager.thirdparty.simkl.model.SimklEpisode;
import org.tinymediamanager.thirdparty.simkl.model.SimklIds;
import org.tinymediamanager.thirdparty.simkl.model.SimklSeason;
import org.tinymediamanager.thirdparty.simkl.model.SimklShowItem;
import org.tinymediamanager.thirdparty.simkl.model.SimklShowsResponse;

/**
 * The TV show implementation of the Simkl.com interface.
 * 
 * @author Manuel Laggner
 */
class SimklTvShow {
  private static final Logger LOGGER              = LoggerFactory.getLogger(SimklTvShow.class);
  private static final String TVDB_EPISODE_PREFIX = "tvdb:";

  private final Simkl         simkl;

  SimklTvShow(Simkl simkl) {
    this.simkl = simkl;
  }

  /**
   * Fetch all shows from Simkl.com with full episode data, to determine the watched state.
   */
  private List<SimklShowItem> fetchWatchedShows() throws IOException {
    SimklShowsResponse response = simkl.executeCall(simkl.getApi().getShows(null, "full", "yes", "yes", "yes"));
    if (response == null || response.shows == null) {
      return new ArrayList<>();
    }
    response.shows.forEach(SimklShowItem::normalize);
    return response.shows;
  }

  /**
   * 2-way watched state sync for TV shows.
   */
  void syncWatched(List<TvShow> tvShowsInTmm) {
    LOGGER.debug("Starting Simkl.com TV show watched sync for {} shows", tvShowsInTmm.size());

    SimklSyncState state = simkl.getSyncState();

    List<SimklShowItem> simklShows;
    try {
      // Phase 1 - initial sync (no watermark/cache stored yet): full pull, no activities handling
      if (StringUtils.isBlank(state.showsWatermark) || state.shows == null) {
        LOGGER.debug("Performing initial Simkl.com TV show sync (full pull)");
        simklShows = new ArrayList<>(fetchWatchedShows());
        state.shows = new ArrayList<>(simklShows);

        // seed the bootstrap watermark from /sync/activities, so the next sync can run incrementally
        try {
          SimklActivities activities = simkl.executeCall(simkl.getApi().getActivities());
          if (activities != null && activities.tv_shows != null && StringUtils.isNotBlank(activities.tv_shows.all)) {
            state.showsWatermark = activities.tv_shows.all;
          }
        }
        catch (Exception e) {
          LOGGER.warn("Could not seed the Simkl.com TV show watermark - '{}'", e.getMessage());
        }

        simkl.saveSyncState(state);
      }
      else {
        // Phase 2 - incremental: gate on /sync/activities and only fetch a delta if something changed
        SimklActivities activities = simkl.executeCall(simkl.getApi().getActivities());
        simklShows = new ArrayList<>(state.shows);
        String currentWatermark = activities.tv_shows.all;
        if (StringUtils.isBlank(currentWatermark) || currentWatermark.equals(state.showsWatermark)) {
          LOGGER.debug("No Simkl.com TV show changes detected - reusing cached data");
        }
        else {
          LOGGER.debug("Simkl.com TV show data changed - fetching delta since '{}'", state.showsWatermark);
          List<SimklShowItem> delta = fetchShowsSince(state.showsWatermark);
          mergeShows(simklShows, delta);
          state.shows = new ArrayList<>(simklShows);
        }

        // reconcile removals
        String removed = activities.tv_shows.removed_from_list;
        if (StringUtils.isNotBlank(removed) && !removed.equals(state.showsRemovedFromList)) {
          LOGGER.debug("Simkl.com TV show removals detected - reconciling cache");
          pruneRemovedShows(simklShows);
          state.shows = new ArrayList<>(simklShows);
          state.showsRemovedFromList = removed;
        }

        // persist the new watermark + cache
        if (StringUtils.isNotBlank(activities.tv_shows.all)) {
          state.showsWatermark = activities.tv_shows.all;
        }
        simkl.saveSyncState(state);
      }
    }
    catch (Exception e) {
      LOGGER.error("Failed to fetch Simkl.com shows for watched sync - '{}'", e.getMessage());
      return;
    }

    LOGGER.info("You have {} TV shows marked as watched on Simkl.com", simklShows.size());

    // Step 1: Apply Simkl watched state to TMM
    for (SimklShowItem simklShow : simklShows) {
      List<TvShow> matching = getTmmTvShowsForSimklShowItem(new ArrayList<>(tvShowsInTmm), simklShow);
      for (TvShow tmmShow : matching) {
        if (tmmShow.isLocked()) {
          continue;
        }

        boolean dirty = updateIDs(tmmShow, simklShow);

        // Apply episode-level watched state
        if (simklShow.seasons != null) {
          for (SimklSeason simklSeason : simklShow.seasons) {
            if (simklSeason.number == null || simklSeason.episodes == null) {
              continue;
            }
            for (SimklEpisode simklEp : simklSeason.episodes) {
              if (simklEp.number == null) {
                continue;
              }
              List<TvShowEpisode> matchingEps = getTmmEpisodesForSimklEpisode(tmmShow, simklSeason.number, simklEp);
              for (TvShowEpisode tmmEp : matchingEps) {
                if (!tmmEp.isWatched()) {
                  LOGGER.trace("Marking episode S{}E{} as watched from Simkl.com", simklSeason.number, simklEp.number);
                  tmmEp.setWatched(true);
                  tmmEp.writeNFO();
                  tmmEp.saveToDb();
                }
              }
            }
          }
        }

        if (dirty) {
          tmmShow.writeNFO();
          tmmShow.saveToDb();
        }
      }
    }

    // Step 2: Send only the delta (watched in TMM, but not yet watched on Simkl) to Simkl in a single batch
    List<SimklShowItem> showsToSync = new ArrayList<>();
    for (TvShow tmmShow : tvShowsInTmm) {
      Set<String> alreadyWatchedEpisodes = getWatchedEpisodeKeys(tmmShow, simklShows);
      SimklShowItem show = buildShowDelta(tmmShow, alreadyWatchedEpisodes);
      if (show != null) {
        showsToSync.add(show);
      }
    }

    if (showsToSync.isEmpty()) {
      LOGGER.debug("No new watched episodes for Simkl.com sync found");
      return;
    }

    SimklShowsResponse body = new SimklShowsResponse();
    body.shows = showsToSync;
    try {
      simkl.executeCall(simkl.getApi().addToHistory(body));
      LOGGER.info("Marked episodes for {} TV shows as watched on Simkl.com", showsToSync.size());
    }
    catch (Exception e) {
      LOGGER.error("Failed to sync watched state for TV shows to Simkl.com - '{}'", e.getMessage());
    }
  }

  /**
   * Fetch the TV show delta (all statuses) modified since the given watermark.
   */
  private List<SimklShowItem> fetchShowsSince(String dateFrom) throws IOException {
    SimklShowsResponse response = simkl.executeCall(simkl.getApi().getShows(dateFrom, "full", "yes", "yes", "yes"));
    if (response == null || response.shows == null) {
      return new ArrayList<>();
    }
    response.shows.forEach(SimklShowItem::normalize);
    return response.shows;
  }

  /**
   * Merge the fetched delta into the cached watched shows (newer copy wins on conflict).
   */
  private void mergeShows(List<SimklShowItem> cache, List<SimklShowItem> delta) {
    for (SimklShowItem item : delta) {
      if (item.ids == null) {
        continue;
      }

      SimklShowItem existing = findInCache(cache, item);
      if (existing != null) {
        int idx = cache.indexOf(existing);
        cache.set(idx, item);
      }
      else {
        cache.add(item);
      }
    }
  }

  /**
   * Prune shows that have been removed from the user's Simkl.com library from the cache.
   */
  private void pruneRemovedShows(List<SimklShowItem> cache) throws IOException {
    SimklShowsResponse response = simkl.executeCall(simkl.getApi().getShows(null, "simkl_ids_only", null, null, null));
    if (response == null || response.shows == null) {
      return;
    }
    response.shows.forEach(SimklShowItem::normalize);

    Set<Integer> currentIds = new HashSet<>();
    for (SimklShowItem item : response.shows) {
      if (item.ids != null && item.ids.simkl != null) {
        currentIds.add(item.ids.simkl);
      }
    }

    cache.removeIf(item -> item.ids != null && item.ids.simkl != null && !currentIds.contains(item.ids.simkl));
  }

  /**
   * Find an item in the cache matching the given item by any known id.
   */
  private SimklShowItem findInCache(List<SimklShowItem> cache, SimklShowItem item) {
    for (SimklShowItem cached : cache) {
      if (sameItem(cached, item)) {
        return cached;
      }
    }
    return null;
  }

  /**
   * Check whether two Simkl show items refer to the same show (via simkl/imdb/tmdb/tvdb id).
   */
  private boolean sameItem(SimklShowItem a, SimklShowItem b) {
    if (a == null || a.ids == null || b == null || b.ids == null) {
      return false;
    }
    if (a.ids.simkl != null && b.ids.simkl != null && a.ids.simkl.equals(b.ids.simkl)) {
      return true;
    }
    if (StringUtils.isNotBlank(a.ids.imdb) && a.ids.imdb.equals(b.ids.imdb)) {
      return true;
    }
    if (a.ids.tmdb != null && a.ids.tmdb.equals(b.ids.tmdb)) {
      return true;
    }
    if (a.ids.tvdb != null && a.ids.tvdb.equals(b.ids.tvdb)) {
      return true;
    }
    return false;
  }

  /**
   * Collect the episode keys already marked as watched on Simkl.com for the given TMM show. Both the tvdb episode id ("tvdb:<id>") and the
   * season/episode number ("<season>_<episode>") are recorded, so ID matches take priority.
   */
  private Set<String> getWatchedEpisodeKeys(TvShow tmmShow, List<SimklShowItem> simklShows) {
    Set<String> keys = new HashSet<>();
    for (SimklShowItem simklShow : simklShows) {
      if (!matches(tmmShow, simklShow) || simklShow.seasons == null) {
        continue;
      }
      for (SimklSeason simklSeason : simklShow.seasons) {
        if (simklSeason.number == null || simklSeason.episodes == null) {
          continue;
        }
        for (SimklEpisode simklEp : simklSeason.episodes) {
          if (simklEp.number == null) {
            continue;
          }
          keys.add(simklSeason.number + "_" + simklEp.number);
          if (simklEp.ids != null && StringUtils.isNotBlank(simklEp.ids.tvdb_id)) {
            keys.add(TVDB_EPISODE_PREFIX + simklEp.ids.tvdb_id);
          }
        }
      }
    }
    return keys;
  }

  /**
   * Find the matching TMM episodes for a Simkl.com episode. The tvdb episode id takes priority over the season/episode number match, since the
   * season/episode numbering can differ between the local and the Simkl catalog.
   */
  private List<TvShowEpisode> getTmmEpisodesForSimklEpisode(TvShow tmmShow, int season, SimklEpisode simklEp) {
    // 1) prefer a match on the tvdb episode id
    if (simklEp.ids != null && StringUtils.isNotBlank(simklEp.ids.tvdb_id)) {
      List<TvShowEpisode> matching = new ArrayList<>();
      for (TvShowEpisode tmmEp : tmmShow.getEpisodes()) {
        if (simklEp.ids.tvdb_id.equals(tmmEp.getTvdbId())) {
          matching.add(tmmEp);
        }
      }
      if (!matching.isEmpty()) {
        return matching;
      }
    }

    // 2) fall back to the season/episode number
    return tmmShow.getEpisode(season, simklEp.number);
  }

  /**
   * Build the delta of episodes of a single show which are watched in TMM but not yet watched on Simkl.com.
   *
   * @param tmmShow
   *          the TMM TV show
   * @param alreadyWatchedEpisodes
   *          the episode keys already marked as watched on Simkl.com
   * @return the show delta, or {@code null} if there is nothing new to sync
   */
  private SimklShowItem buildShowDelta(TvShow tmmShow, Set<String> alreadyWatchedEpisodes) {
    SimklIds ids = buildShowIds(tmmShow);
    if (ids == null) {
      return null;
    }

    // Build seasons/episodes
    List<SimklSeason> seasons = new ArrayList<>();

    for (TvShowSeason tmmSeason : tmmShow.getSeasons()) {
      if (tmmSeason.getSeason() < 0) {
        continue;
      }

      List<SimklEpisode> episodes = new ArrayList<>();

      // Use a map to deduplicate by season/episode
      Map<String, TvShowEpisode> episodeMap = new HashMap<>();
      for (TvShowEpisode tmmEp : tmmSeason.getEpisodes()) {
        if (tmmEp.getEpisode() < 0 || tmmEp.getSeason() < 0) {
          continue;
        }
        String key = tmmEp.getSeason() + "_" + tmmEp.getEpisode();
        if (!episodeMap.containsKey(key)) {
          episodeMap.put(key, tmmEp);
        }
      }

      for (TvShowEpisode tmmEp : episodeMap.values()) {
        if (!tmmEp.isWatched()) {
          continue;
        }

        if (alreadyWatchedEpisodes.contains(tmmEp.getSeason() + "_" + tmmEp.getEpisode())
            || (StringUtils.isNotBlank(tmmEp.getTvdbId()) && alreadyWatchedEpisodes.contains(TVDB_EPISODE_PREFIX + tmmEp.getTvdbId()))) {
          continue;
        }

        SimklEpisode ep = new SimklEpisode();
        ep.number = tmmEp.getEpisode();

        // add the tvdb episode id, if available
        String tvdbId = tmmEp.getTvdbId();
        if (StringUtils.isNotBlank(tvdbId)) {
          SimklEpisode.SimklEpisodeIds epIds = new SimklEpisode.SimklEpisodeIds();
          epIds.tvdb = tvdbId;
          ep.ids = epIds;
        }

        if (tmmEp.getLastWatched() != null) {
          OffsetDateTime watchedAt = OffsetDateTime.ofInstant(tmmEp.getLastWatched().toInstant(), ZoneOffset.UTC);
          ep.watched_at = watchedAt.toString();
        }

        episodes.add(ep);
      }

      if (!episodes.isEmpty()) {
        SimklSeason season = new SimklSeason();
        season.number = tmmSeason.getSeason();
        season.episodes = episodes;
        seasons.add(season);
      }
    }

    if (seasons.isEmpty()) {
      return null;
    }

    SimklShowItem show = new SimklShowItem();
    show.ids = ids;
    show.seasons = seasons;
    return show;
  }

  /**
   * Remove TV shows from Simkl.com watched history.
   */
  void removeFromWatched(List<TvShow> tmmShows) throws IOException {
    List<SimklShowItem> removed = new ArrayList<>();
    for (TvShow tmmShow : tmmShows) {
      SimklIds ids = buildShowIds(tmmShow);
      if (ids == null) {
        continue;
      }

      SimklShowItem show = new SimklShowItem();
      show.ids = ids;
      removed.add(show);
    }

    if (removed.isEmpty()) {
      return;
    }

    SimklShowsResponse body = new SimklShowsResponse();
    body.shows = removed;

    simkl.executeCall(simkl.getApi().removeFromHistory(body));
    LOGGER.debug("Removed {} TV shows from Simkl.com watched history", removed.size());

    // drop the removed shows from the local cache so they can be re-pushed if watched in TMM
    SimklSyncState state = simkl.getSyncState();
    if (state.shows != null) {
      state.shows.removeIf(cached -> removed.stream().anyMatch(show -> sameItem(cached, show)));
      simkl.saveSyncState(state);
    }
  }

  /**
   * Clear all Simkl TV show data.
   */
  void clearTvShows() throws IOException {
    List<SimklShowItem> simklShows = fetchWatchedShows();

    List<SimklShowItem> historyShows = new ArrayList<>();
    for (SimklShowItem item : simklShows) {
      if (item.ids == null) {
        continue;
      }

      SimklIds ids = new SimklIds();
      if (item.ids.simkl != null && item.ids.simkl > 0) {
        ids.simkl = item.ids.simkl;
      }
      else if (StringUtils.isNotBlank(item.ids.imdb)) {
        ids.imdb = item.ids.imdb;
      }
      else if (item.ids.tmdb != null && item.ids.tmdb > 0) {
        ids.tmdb = item.ids.tmdb;
      }
      else if (item.ids.tvdb != null && item.ids.tvdb > 0) {
        ids.tvdb = item.ids.tvdb;
      }
      else {
        continue;
      }

      SimklShowItem show = new SimklShowItem();
      show.ids = ids;
      historyShows.add(show);
    }

    if (!historyShows.isEmpty()) {
      SimklShowsResponse body = new SimklShowsResponse();
      body.shows = historyShows;
      simkl.executeCall(simkl.getApi().removeFromHistory(body));
    }

    LOGGER.info("Cleared {} TV shows from Simkl.com", simklShows.size());

    // reset the incremental sync state so the next sync does a full re-bootstrap
    SimklSyncState state = simkl.getSyncState();
    state.shows = null;
    state.showsWatermark = null;
    state.showsRemovedFromList = null;
    simkl.saveSyncState(state);
  }

  /**
   * Build show IDs for Simkl.com API calls.
   */
  private SimklIds buildShowIds(TvShow tmmShow) {
    SimklIds ids = new SimklIds();
    boolean hasId = false;

    if (MediaIdUtil.isValidImdbId(tmmShow.getImdbId())) {
      ids.imdb = tmmShow.getImdbId();
      hasId = true;
    }
    if (tmmShow.getTmdbId() > 0) {
      ids.tmdb = tmmShow.getTmdbId();
      hasId = true;
    }
    int tvdbId = tmmShow.getIdAsInt(MediaMetadata.TVDB);
    if (tvdbId > 0) {
      ids.tvdb = tvdbId;
      hasId = true;
    }
    int simklId = tmmShow.getIdAsInt(MediaMetadata.SIMKL);
    if (simklId > 0) {
      ids.simkl = simklId;
      hasId = true;
    }

    if (!hasId) {
      return null;
    }

    return ids;
  }

  /**
   * Find matching TMM TV shows for a Simkl.com show.
   */
  private List<TvShow> getTmmTvShowsForSimklShowItem(List<TvShow> tmmShows, SimklShowItem simklShow) {
    return tmmShows.stream().filter(show -> matches(show, simklShow)).toList();
  }

  /**
   * Match a TMM TV show against a Simkl.com show by ID.
   */
  private boolean matches(TvShow tmmShow, SimklShowItem simklShow) {
    if (simklShow.ids == null) {
      return false;
    }

    SimklIds ids = simklShow.ids;
    if (ids.simkl != null && ids.simkl > 0 && ids.simkl == tmmShow.getIdAsInt(MediaMetadata.SIMKL)) {
      return true;
    }
    if (StringUtils.isNotBlank(ids.imdb) && ids.imdb.equals(tmmShow.getImdbId())) {
      return true;
    }
    if (ids.tmdb != null && ids.tmdb > 0 && ids.tmdb == tmmShow.getTmdbId()) {
      return true;
    }
    if (ids.tvdb != null && ids.tvdb > 0 && ids.tvdb == tmmShow.getIdAsInt(MediaMetadata.TVDB)) {
      return true;
    }
    return false;
  }

  /**
   * Update TMM TV show IDs from Simkl.com data.
   */
  private boolean updateIDs(TvShow tmmShow, SimklShowItem simklShow) {
    if (simklShow.ids == null) {
      return false;
    }

    SimklIds ids = simklShow.ids;
    boolean dirty = false;

    if (StringUtils.isBlank(tmmShow.getIdAsString(MediaMetadata.IMDB)) && StringUtils.isNotBlank(ids.imdb)) {
      tmmShow.setId(MediaMetadata.IMDB, ids.imdb);
      dirty = true;
    }
    if (tmmShow.getIdAsInt(MediaMetadata.TMDB) == 0 && ids.tmdb != null && ids.tmdb > 0) {
      tmmShow.setId(MediaMetadata.TMDB, ids.tmdb);
      dirty = true;
    }
    if (tmmShow.getIdAsInt(MediaMetadata.TVDB) == 0 && ids.tvdb != null && ids.tvdb > 0) {
      tmmShow.setId(MediaMetadata.TVDB, ids.tvdb);
      dirty = true;
    }
    if (tmmShow.getIdAsInt(MediaMetadata.SIMKL) == 0 && ids.simkl != null && ids.simkl > 0) {
      tmmShow.setId(MediaMetadata.SIMKL, ids.simkl);
      dirty = true;
    }

    return dirty;
  }
}
