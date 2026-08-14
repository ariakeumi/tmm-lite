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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.thirdparty.simkl.model.SimklActivities;
import org.tinymediamanager.thirdparty.simkl.model.SimklIds;
import org.tinymediamanager.thirdparty.simkl.model.SimklMovieItem;
import org.tinymediamanager.thirdparty.simkl.model.SimklMoviesResponse;

/**
 * The movie implementation of the Simkl interface.
 * 
 * @author Manuel Laggner
 */
class SimklMovie {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimklMovie.class);

  private final Simkl         simkl;

  SimklMovie(Simkl simkl) {
    this.simkl = simkl;
  }

  /**
   * Read all watched (completed) movies from the Simkl user's library.
   */
  private List<SimklMovieItem> fetchCompletedMovies() throws IOException {
    SimklMoviesResponse response = simkl.executeCall(simkl.getApi().getCompletedMovies(null));
    if (response == null || response.movies == null) {
      return new ArrayList<>();
    }
    response.movies.forEach(SimklMovieItem::normalize);
    return response.movies;
  }

  /**
   * 2-way watched state sync.
   */
  void syncWatched(List<Movie> moviesInTmm) {
    LOGGER.debug("Starting Simkl.com movie watched sync for {} movies", moviesInTmm.size());

    SimklSyncState state = simkl.getSyncState();

    List<SimklMovieItem> simklMovies;
    try {
      // Phase 1 - initial sync (no watermark/cache stored yet): full pull, no activities handling
      if (StringUtils.isBlank(state.moviesWatermark) || state.movies == null) {
        LOGGER.debug("Performing initial Simkl.com movie sync (full pull)");
        simklMovies = new ArrayList<>(fetchCompletedMovies());
        state.movies = new ArrayList<>(simklMovies);

        // seed the bootstrap watermark from /sync/activities, so the next sync can run incrementally
        try {
          SimklActivities activities = simkl.executeCall(simkl.getApi().getActivities());
          if (activities != null && activities.movies != null && StringUtils.isNotBlank(activities.movies.all)) {
            state.moviesWatermark = activities.movies.all;
          }
        }
        catch (Exception e) {
          LOGGER.warn("Could not seed the Simkl.com movie watermark - '{}'", e.getMessage());
        }

        simkl.saveSyncState(state);
      }
      else {
        // Phase 2 - incremental: gate on /sync/activities and only fetch a delta if something changed
        SimklActivities activities = simkl.executeCall(simkl.getApi().getActivities());
        simklMovies = new ArrayList<>(state.movies);
        String currentWatermark = activities.movies.all;
        if (StringUtils.isBlank(currentWatermark) || currentWatermark.equals(state.moviesWatermark)) {
          LOGGER.debug("No Simkl.com movie changes detected - reusing cached data");
        }
        else {
          LOGGER.debug("Simkl.com movie data changed - fetching delta since '{}'", state.moviesWatermark);
          List<SimklMovieItem> delta = fetchMoviesSince(state.moviesWatermark);
          mergeMovies(simklMovies, delta);
          state.movies = new ArrayList<>(simklMovies);
        }

        // reconcile removals
        String removed = activities.movies.removed_from_list;
        if (StringUtils.isNotBlank(removed) && !removed.equals(state.moviesRemovedFromList)) {
          LOGGER.debug("Simkl.com movie removals detected - reconciling cache");
          pruneRemovedMovies(simklMovies);
          state.movies = new ArrayList<>(simklMovies);
          state.moviesRemovedFromList = removed;
        }

        // persist the new watermark + cache
        if (StringUtils.isNotBlank(activities.movies.all)) {
          state.moviesWatermark = activities.movies.all;
        }
        simkl.saveSyncState(state);
      }
    }
    catch (Exception e) {
      LOGGER.error("Failed to fetch Simkl.com movies for watched sync - '{}'", e.getMessage());
      return;
    }

    LOGGER.debug("You have {} movies marked as watched on Simkl.com", simklMovies.size());

    // Step 1: Apply Simkl.com watched state to TMM movies
    int synced = 0;
    for (SimklMovieItem simklMovie : simklMovies) {
      List<Movie> matching = getTmmMoviesForSimklMovieItem(moviesInTmm, simklMovie);
      for (Movie tmmMovie : matching) {
        if (tmmMovie.isLocked()) {
          LOGGER.trace("Skipping locked movie '{}' from Simkl.com watched sync", tmmMovie.getTitle());
          continue;
        }

        boolean dirty = updateIDs(tmmMovie, simklMovie);

        if (!tmmMovie.isWatched()) {
          LOGGER.trace("Marking movie '{}' as watched from Simkl.com", tmmMovie.getTitle());
          tmmMovie.setWatched(true);
          dirty = true;
          synced++;
        }

        if (dirty) {
          tmmMovie.writeNFO();
          tmmMovie.saveToDb();
        }
      }
    }

    if (synced > 0) {
      LOGGER.info("Synced {} movie watched states from Simkl.com", synced);
    }

    // Step 2: Send TMM watched movies that are not watched on Simkl.com
    List<Movie> tmmWatchedMovies = moviesInTmm.stream().filter(Movie::isWatched).toList();
    LOGGER.debug("You have {} movies marked as watched in your tMM database", tmmWatchedMovies.size());

    // Remove those already watched on Simkl.com
    List<Movie> toSync = new ArrayList<>(tmmWatchedMovies);
    for (SimklMovieItem simklMovie : simklMovies) {
      toSync.removeAll(getTmmMoviesForSimklMovieItem(moviesInTmm, simklMovie));
    }

    if (toSync.isEmpty()) {
      LOGGER.debug("No new watched movies for Simkl.com sync found");
      return;
    }

    List<SimklMovieItem> moviesToSync = new ArrayList<>();
    int nosync = 0;
    for (Movie movie : toSync) {
      SimklMovieItem node = buildMovieIds(movie);
      if (node != null) {
        // Add watched_at timestamp
        if (movie.getLastWatched() != null) {
          OffsetDateTime watchedAt = OffsetDateTime.ofInstant(movie.getLastWatched().toInstant(), ZoneOffset.UTC);
          node.watched_at = watchedAt.toString();
        }
        moviesToSync.add(node);
      }
      else {
        nosync++;
      }
    }

    if (nosync > 0) {
      LOGGER.debug("Skipping {} movies because they have not been scraped yet", nosync);
    }

    if (moviesToSync.isEmpty()) {
      LOGGER.debug("No new watched movies for Simkl.com sync found");
      return;
    }

    LOGGER.info("Sending {} movie watched states to Simkl.com", moviesToSync.size());
    try {
      markMoviesWatched(moviesToSync);
    }
    catch (Exception e) {
      LOGGER.error("Failed to mark movies as watched on Simkl - '{}'", e.getMessage());
    }
  }

  /**
   * Fetch the movie delta (all statuses) modified since the given watermark.
   */
  private List<SimklMovieItem> fetchMoviesSince(String dateFrom) throws IOException {
    SimklMoviesResponse response = simkl.executeCall(simkl.getApi().getMovies(dateFrom, null));
    if (response == null || response.movies == null) {
      return new ArrayList<>();
    }
    response.movies.forEach(SimklMovieItem::normalize);
    return response.movies;
  }

  /**
   * Merge the fetched delta into the cached watched movies. Items that are no longer completed are removed from the cache.
   */
  private void mergeMovies(List<SimklMovieItem> cache, List<SimklMovieItem> delta) {
    for (SimklMovieItem item : delta) {
      if (item.ids == null) {
        continue;
      }

      // drop items that are no longer in the completed bucket
      if (StringUtils.isNotBlank(item.status) && !"completed".equals(item.status)) {
        cache.removeIf(cached -> sameItem(cached, item));
        continue;
      }

      SimklMovieItem existing = findInCache(cache, item);
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
   * Prune movies that have been removed from the user's Simkl.com library from the cache.
   */
  private void pruneRemovedMovies(List<SimklMovieItem> cache) throws IOException {
    SimklMoviesResponse response = simkl.executeCall(simkl.getApi().getMovies(null, "simkl_ids_only"));
    if (response == null || response.movies == null) {
      return;
    }
    response.movies.forEach(SimklMovieItem::normalize);

    Set<Integer> currentIds = new HashSet<>();
    for (SimklMovieItem item : response.movies) {
      if (item.ids != null && item.ids.simkl != null) {
        currentIds.add(item.ids.simkl);
      }
    }

    cache.removeIf(item -> item.ids != null && item.ids.simkl != null && !currentIds.contains(item.ids.simkl));
  }

  /**
   * Find an item in the cache matching the given item by any known id.
   */
  private SimklMovieItem findInCache(List<SimklMovieItem> cache, SimklMovieItem item) {
    for (SimklMovieItem cached : cache) {
      if (sameItem(cached, item)) {
        return cached;
      }
    }
    return null;
  }

  /**
   * Check whether two Simkl movie items refer to the same movie (via simkl/imdb/tmdb id).
   */
  private boolean sameItem(SimklMovieItem a, SimklMovieItem b) {
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
    return false;
  }

  /**
   * Mark movies as watched on Simkl.com.
   */
  private void markMoviesWatched(List<SimklMovieItem> movies) throws IOException {
    LOGGER.debug("Marking {} movies as watched on Simkl.com", movies.size());

    SimklMoviesResponse body = new SimklMoviesResponse();
    body.movies = movies;

    simkl.executeCall(simkl.getApi().addToHistory(body));
  }

  /**
   * Remove movies from Simkl.com watched history.
   */
  void removeFromWatched(List<Movie> tmmMovies) throws IOException {
    List<SimklMovieItem> movies = new ArrayList<>();
    for (Movie tmmMovie : tmmMovies) {
      SimklMovieItem movie = buildMovieIds(tmmMovie);
      if (movie != null) {
        movies.add(movie);
      }
    }

    if (movies.isEmpty()) {
      return;
    }

    LOGGER.debug("Removing {} movies from Simkl.com watched history", movies.size());

    SimklMoviesResponse body = new SimklMoviesResponse();
    body.movies = movies;

    simkl.executeCall(simkl.getApi().removeFromHistory(body));

    // drop the removed movies from the local cache so they can be re-pushed if watched in TMM
    SimklSyncState state = simkl.getSyncState();
    if (state.movies != null) {
      state.movies.removeIf(cached -> movies.stream().anyMatch(movie -> sameItem(cached, movie)));
      simkl.saveSyncState(state);
    }
  }

  /**
   * Clear all Simkl.com movie data.
   */
  void clearMovies() throws IOException {
    List<SimklMovieItem> simklMovies = fetchCompletedMovies();

    if (simklMovies.isEmpty()) {
      return;
    }

    // Remove from history
    List<SimklMovieItem> historyMovies = new ArrayList<>();
    for (SimklMovieItem item : simklMovies) {
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
      else {
        continue;
      }

      SimklMovieItem movie = new SimklMovieItem();
      movie.ids = ids;
      historyMovies.add(movie);
    }

    if (!historyMovies.isEmpty()) {
      SimklMoviesResponse body = new SimklMoviesResponse();
      body.movies = historyMovies;
      simkl.executeCall(simkl.getApi().removeFromHistory(body));
    }

    LOGGER.info("Cleared {} movies from Simkl.com", simklMovies.size());

    // reset the incremental sync state so the next sync does a full re-bootstrap
    SimklSyncState state = simkl.getSyncState();
    state.movies = null;
    state.moviesWatermark = null;
    state.moviesRemovedFromList = null;
    simkl.saveSyncState(state);
  }

  /**
   * Build a movie with IDs for Simkl API calls.
   */
  private SimklMovieItem buildMovieIds(Movie tmmMovie) {
    SimklIds ids = new SimklIds();
    boolean hasId = false;

    if (MediaIdUtil.isValidImdbId(tmmMovie.getImdbId())) {
      ids.imdb = tmmMovie.getImdbId();
      hasId = true;
    }
    if (tmmMovie.getTmdbId() > 0) {
      ids.tmdb = tmmMovie.getTmdbId();
      hasId = true;
    }
    int simklId = tmmMovie.getIdAsInt(MediaMetadata.SIMKL);
    if (simklId > 0) {
      ids.simkl = simklId;
      hasId = true;
    }

    if (!hasId) {
      return null;
    }

    SimklMovieItem movie = new SimklMovieItem();
    movie.ids = ids;
    return movie;
  }

  /**
   * Find matching TMM movies for a Simkl.com movie item.
   */
  private List<Movie> getTmmMoviesForSimklMovieItem(List<Movie> tmmMovies, SimklMovieItem simklMovie) {
    return tmmMovies.stream().filter(movie -> matches(movie, simklMovie)).toList();
  }

  /**
   * Match a TMM movie against a Simkl.com movie by ID.
   */
  private boolean matches(Movie tmmMovie, SimklMovieItem simklMovie) {
    if (simklMovie.ids == null) {
      return false;
    }

    SimklIds ids = simklMovie.ids;
    if (ids.simkl != null && ids.simkl > 0 && ids.simkl == tmmMovie.getIdAsInt(MediaMetadata.SIMKL)) {
      return true;
    }
    if (StringUtils.isNotBlank(ids.imdb) && ids.imdb.equals(tmmMovie.getImdbId())) {
      return true;
    }
    if (ids.tmdb != null && ids.tmdb > 0 && ids.tmdb == tmmMovie.getTmdbId()) {
      return true;
    }
    return false;
  }

  /**
   * Update TMM movie IDs from Simkl.com data.
   */
  private boolean updateIDs(Movie tmmMovie, SimklMovieItem simklMovie) {
    if (simklMovie.ids == null) {
      return false;
    }

    SimklIds ids = simklMovie.ids;
    boolean dirty = false;

    if (tmmMovie.getIdAsString(MediaMetadata.IMDB).isEmpty() && StringUtils.isNotBlank(ids.imdb)) {
      tmmMovie.setId(MediaMetadata.IMDB, ids.imdb);
      dirty = true;
    }
    if (tmmMovie.getIdAsInt(MediaMetadata.TMDB) == 0 && ids.tmdb != null && ids.tmdb > 0) {
      tmmMovie.setId(MediaMetadata.TMDB, ids.tmdb);
      dirty = true;
    }
    if (tmmMovie.getIdAsInt(MediaMetadata.SIMKL) == 0 && ids.simkl != null && ids.simkl > 0) {
      tmmMovie.setId(MediaMetadata.SIMKL, ids.simkl);
      dirty = true;
    }

    return dirty;
  }
}
