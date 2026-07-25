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
package org.tinymediamanager.core.movie;

import static org.tinymediamanager.core.bus.EventBus.TOPIC_MOVIES;
import static org.tinymediamanager.core.bus.EventBus.TOPIC_MOVIES_UI;
import static org.tinymediamanager.core.bus.EventBus.TOPIC_MOVIE_SETS;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.h2.mvstore.MVMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.TmmOsUtils;
import org.tinymediamanager.core.AbstractModelObject;
import org.tinymediamanager.core.Constants;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.ObservableCopyOnWriteArrayList;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.bus.Event;
import org.tinymediamanager.core.bus.EventBus;
import org.tinymediamanager.core.bus.EventBusConnector;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileAudioStream;
import org.tinymediamanager.core.entities.MediaFileSubtitle;
import org.tinymediamanager.core.entities.MediaGenres;
import org.tinymediamanager.core.entities.MediaSource;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.entities.MovieSet;
import org.tinymediamanager.core.movie.tasks.MovieUpdateDatasourceTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.ScraperType;
import org.tinymediamanager.scraper.entities.MediaCertification;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.IMovieMetadataProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;

import com.fasterxml.jackson.databind.ObjectReader;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.ObservableElementList;

/**
 * The Class MovieList.
 * 
 * @author Manuel Laggner
 */
public final class MovieList extends AbstractModelObject {
  private static final Logger                            LOGGER             = LoggerFactory.getLogger(MovieList.class);
  private static volatile MovieList                      instance;

  private final List<Movie>                              movieList;
  private final List<MovieSet>                           movieSetList;

  private final CopyOnWriteArrayList<Integer>            yearsInMovies;
  private final CopyOnWriteArrayList<String>             tagsInMovies;
  private final CopyOnWriteArrayList<MediaGenres>        genresInMovies;
  private final CopyOnWriteArrayList<String>             videoCodecsInMovies;
  private final CopyOnWriteArrayList<String>             videoContainersInMovies;
  private final CopyOnWriteArrayList<String>             audioCodecsInMovies;
  private final CopyOnWriteArrayList<Integer>            audioChannelsInMovies;
  private final CopyOnWriteArrayList<MediaCertification> certificationsInMovies;
  private final CopyOnWriteArrayList<Double>             frameRatesInMovies;
  private final CopyOnWriteArrayList<Integer>            audioStreamsInMovies;
  private final CopyOnWriteArrayList<Integer>            subtitlesInMovies;
  private final CopyOnWriteArrayList<String>             audioLanguagesInMovies;
  private final CopyOnWriteArrayList<String>             subtitleLanguagesInMovies;
  private final CopyOnWriteArrayList<String>             decadesInMovies;
  private final CopyOnWriteArrayList<String>             hdrFormatInMovies;
  private final CopyOnWriteArrayList<String>             audioTitlesInMovies;
  private final CopyOnWriteArrayList<String>             subtitleFormatsInMovies;

  /**
   * Internal lookup index: dbid -> movie
   */
  private final Map<UUID, Movie>                         byDbId;
  /**
   * Internal lookup index: absolute normalized path -> movies in that folder
   */
  private final Map<Path, List<Movie>>                   byPath;

  private final ReadWriteLock                            readWriteLock      = new ReentrantReadWriteLock();

  private final PropertyChangeListener                   movieSetListener;
  private final Comparator<MovieSet>                     movieSetComparator = new MovieSetComparator();

  /**
   * Instantiates a new movie list.
   */
  private MovieList() {
    // create all lists
    movieList = new ObservableElementList<>(GlazedLists.threadSafeList(new BasicEventList<>()), new EventBusConnector<>(TOPIC_MOVIES));
    movieSetList = new ObservableCopyOnWriteArrayList<>();

    byDbId = new HashMap<>();
    byPath = new HashMap<>();

    yearsInMovies = new CopyOnWriteArrayList<>();
    tagsInMovies = new CopyOnWriteArrayList<>();
    genresInMovies = new CopyOnWriteArrayList<>();
    videoCodecsInMovies = new CopyOnWriteArrayList<>();
    videoContainersInMovies = new CopyOnWriteArrayList<>();
    audioCodecsInMovies = new CopyOnWriteArrayList<>();
    audioChannelsInMovies = new CopyOnWriteArrayList<>();
    certificationsInMovies = new CopyOnWriteArrayList<>();
    frameRatesInMovies = new CopyOnWriteArrayList<>();
    audioStreamsInMovies = new CopyOnWriteArrayList<>();
    subtitlesInMovies = new CopyOnWriteArrayList<>();
    audioLanguagesInMovies = new CopyOnWriteArrayList<>();
    subtitleLanguagesInMovies = new CopyOnWriteArrayList<>();
    decadesInMovies = new CopyOnWriteArrayList<>();
    hdrFormatInMovies = new CopyOnWriteArrayList<>();
    audioTitlesInMovies = new CopyOnWriteArrayList<>();
    subtitleFormatsInMovies = new CopyOnWriteArrayList<>();

    EventBus.registerListener(EventBus.TOPIC_MOVIES, event -> {
      if (event.sender() instanceof Movie movie) {
        if (event.eventType().equals(Event.TYPE_SAVE)) {
          TmmTaskManager.getInstance().addUnnamedTask(() -> {
            // reindex this movie on path or media file changes
            readWriteLock.writeLock().lock();
            try {
              deindexMovie(movie);
              if (movieList.contains(movie)) {
                // only re-index movies which are still in the movie list
                indexMovie(movie);
              }
            }
            finally {
              readWriteLock.writeLock().unlock();
            }
          });
        }
      }
    });

    movieSetListener = evt -> {
      switch (evt.getPropertyName()) {
        case Constants.ADDED_MOVIE:
        case Constants.REMOVED_MOVIE:
          firePropertyChange("movieInMovieSetCount", null, getMovieInMovieSetCount());
          break;

        default:
          break;
      }
    };

    MovieModuleManager.getInstance().getSettings().addPropertyChangeListener(evt -> {
      switch (evt.getPropertyName()) {
        case "movieSetDataFolder":
          movieSetList.forEach(MovieSetArtworkHelper::updateArtwork);
          break;
      }
    });

    // eventbus listener - just forward movie changes to the movie list listener to collect changes/avoid unnecessary multiple updates
    EventBus.registerListener(EventBus.TOPIC_MOVIES, event -> {
      if (event.sender() instanceof Movie) {
        if (event.eventType().equals(Event.TYPE_REMOVE) || event.eventType().equals(Event.TYPE_SAVE)) {
          EventBus.publishEvent("movieList", Event.createUpdateEvent(instance));
        }
      }
    });

    // collect movie changes locally
    EventBus.registerListener("movieList", event -> {
      if (event.sender() == instance) {
        // needed since new ArrayList<>(movieList) is not thread-safe enough here
        List<Movie> movies = new ArrayList<>();

        readWriteLock.readLock().lock();
        try {
          movies.addAll(movieList);
        }
        finally {
          readWriteLock.readLock().unlock();
        }

        updateYear(movies);
        updateDecades(movies);
        updateTags(movies);
        updateGenres(movies);
        updateCertifications(movies);
        updateMediaInformationLists(movies);
      }
    });
  }

  /**
   * Gets the single instance of MovieList.
   * 
   * @return single instance of MovieList
   */
  static MovieList getInstance() {
    if (instance == null) {
      synchronized (MovieList.class) {
        if (instance == null) {
          instance = new MovieList();
        }
      }
    }

    return instance;
  }

  /**
   * removes the active instance <br>
   * <b>Should only be used for unit testing et all!</b><br>
   */
  static void clearInstance() {
    instance = null;
  }

  /**
   * Adds the movie.
   * 
   * @param movie
   *          the movie
   */
  public void addMovie(Movie movie) {
    if (findByDbId(movie.getDbId()).isEmpty()) {
      int oldValue = movieList.size();

      // create entries
      readWriteLock.writeLock().lock();
      try {
        movieList.add(movie);
        indexMovie(movie);
      }
      finally {
        readWriteLock.writeLock().unlock();
      }

      firePropertyChange("movies", null, movieList);
      firePropertyChange("movieCount", oldValue, movieList.size());
    }
  }

  /**
   * Removes the datasource.
   * 
   * @param datasource
   *          the path
   */
  void removeDatasource(String datasource) {
    if (StringUtils.isEmpty(datasource)) {
      return;
    }

    List<Movie> moviesToRemove = new ArrayList<>();
    Path path = Paths.get(datasource);
    for (int i = movieList.size() - 1; i >= 0; i--) {
      Movie movie = movieList.get(i);
      if (path.equals(Paths.get(movie.getDataSource()))) {
        moviesToRemove.add(movie);
      }
    }

    removeMovies(moviesToRemove);
  }

  /**
   * exchanges the given datasource in the entities/database with a new one
   */
  void exchangeDatasource(String oldDatasource, String newDatasource) {
    Path oldPath = Paths.get(oldDatasource);
    List<Movie> moviesToChange = movieList.stream().filter(movie -> oldPath.equals(Paths.get(movie.getDataSource()))).toList();
    List<MediaFile> imagesToCache = new ArrayList<>();

    for (Movie movie : moviesToChange) {
      Path oldMoviePath = movie.getPathNIO();
      Path newMoviePath;

      try {
        // try to _cleanly_ calculate the relative path
        newMoviePath = Paths.get(newDatasource, Paths.get(movie.getDataSource()).relativize(oldMoviePath).toString());
      }
      catch (Exception e) {
        // if that fails (maybe migrate from windows to linux/macos), just try a simple string replacement
        newMoviePath = Paths.get(newDatasource, FilenameUtils.separatorsToSystem(movie.getPath().replace(movie.getDataSource(), "")));
      }

      movie.setDataSource(newDatasource);
      movie.setPath(newMoviePath.toAbsolutePath().toString());
      movie.updateMediaFilePath(oldMoviePath, newMoviePath);
      movie.saveToDb(); // since we moved already, save it

      // re-build the image cache afterwards in an own thread
      imagesToCache.addAll(movie.getImagesToCache());
    }

    if (!imagesToCache.isEmpty()) {
      imagesToCache.forEach(ImageCache::cacheImageAsync);
    }
  }

  /**
   * Gets the unscraped movies.
   * 
   * @return the unscraped movies
   */
  public List<Movie> getUnscrapedMovies() {
    return movieList.parallelStream().filter(movie -> !movie.isScraped()).sorted(new MovieComparator()).collect(Collectors.toList());
  }

  /**
   * Gets the new movies or movies with new files
   * 
   * @return the new movies
   */
  public List<Movie> getNewMovies() {
    return movieList.parallelStream().filter(MediaEntity::isNewlyAdded).sorted(new MovieComparator()).collect(Collectors.toList());
  }

  /**
   * Gets a list of used genres.
   * 
   * @return MediaGenres list
   */
  public Collection<MediaGenres> getUsedGenres() {
    return Collections.unmodifiableList(genresInMovies);
  }

  /**
   * remove given movies from the database
   * 
   * @param movies
   *          list of movies to remove
   */
  public void removeMovies(List<Movie> movies) {
    if (movies == null || movies.isEmpty()) {
      return;
    }
    int oldValue = movieList.size();

    // remove in inverse order => performance
    for (int i = movies.size() - 1; i >= 0; i--) {
      Movie movie = movies.get(i);

      if (movie.getMovieSet() != null) {
        MovieSet movieSet = movie.getMovieSet();

        movieSet.removeMovie(movie, false);
        movie.setMovieSet(null);
      }

      readWriteLock.writeLock().lock();
      // deindex before removing
      try {
        deindexMovie(movie);
        movieList.remove(movie);
      }
      finally {
        readWriteLock.writeLock().unlock();
      }

      try {
        MovieModuleManager.getInstance().removeMovieFromDb(movie);
        EventBus.publishEvent(TOPIC_MOVIES, Event.createRemoveEvent(movie));
      }
      catch (Exception e) {
        LOGGER.error("Error removing movie '{}' from DB - '{}'", movie.getTitle(), e.getMessage());
      }

      // and remove the image cache
      for (MediaFile mf : movie.getMediaFiles()) {
        if (mf.isGraphic()) {
          ImageCache.invalidateCachedImage(mf);
        }
      }
    }

    firePropertyChange("movies", null, movieList);
    firePropertyChange("movieCount", oldValue, movieList.size());
  }

  /**
   * delete the given movies from the database and physically
   * 
   * @param movies
   *          list of movies to delete
   */
  public void deleteMovies(List<Movie> movies) {
    if (movies == null || movies.isEmpty()) {
      return;
    }
    int oldValue = movieList.size();

    // remove in inverse order => performance
    for (int i = movies.size() - 1; i >= 0; i--) {
      Movie movie = movies.get(i);
      movie.deleteFilesSafely();

      readWriteLock.writeLock().lock();
      // deindex before removing
      try {
        deindexMovie(movie);
        movieList.remove(movie);
      }
      finally {
        readWriteLock.writeLock().unlock();
      }

      if (movie.getMovieSet() != null) {
        MovieSet movieSet = movie.getMovieSet();
        movieSet.removeMovie(movie, false);
        movie.setMovieSet(null);
      }
      try {
        MovieModuleManager.getInstance().removeMovieFromDb(movie);
        EventBus.publishEvent(TOPIC_MOVIES, Event.createRemoveEvent(movie));
      }
      catch (Exception e) {
        LOGGER.error("Error removing movie '{}' from DB - '{}'", movie.getTitle(), e.getMessage());
      }

      // and remove the image cache
      for (MediaFile mf : movie.getMediaFiles()) {
        if (mf.isGraphic()) {
          ImageCache.invalidateCachedImage(mf);
        }
      }
    }

    firePropertyChange("movies", null, movieList);
    firePropertyChange("movieCount", oldValue, movieList.size());
  }

  /**
   * Gets the movies.
   * 
   * @return the movies
   */
  public List<Movie> getMovies() {
    return movieList;
  }

  /**
   * Load movies from database.
   */
  void loadMoviesFromDatabase(MVMap<UUID, String> movieMap) {
    LOGGER.info("Loading movies from database...");

    // load movies
    ObjectReader movieObjectReader = MovieModuleManager.getInstance().getMovieObjectReader();

    List<UUID> toRemove = new ArrayList<>();
    Set<Movie> loadedMoviesWithoutDuplicates = new HashSet<>();

    long start = System.nanoTime();

    new ArrayList<>(movieMap.keyList()).forEach((uuid) -> {
      String json = "";
      try {
        json = movieMap.get(uuid);
        Movie movie = movieObjectReader.readValue(json);
        movie.setDbId(uuid);

        // some sanity checks
        if (isCorrupt(movie)) {
          LOGGER.debug("Removing corrupt movie: {}", json);
          toRemove.add(uuid);
          return;
        }

        // for performance reasons we add movies directly
        if (!loadedMoviesWithoutDuplicates.add(movie)) {
          // already in there?! remove dupe
          LOGGER.debug("removed duplicate '{}'", movie.getTitle());
          toRemove.add(uuid);
        }
      }
      catch (Exception e) {
        LOGGER.debug("problem decoding movie json string: {}", json);
        LOGGER.warn("Dropping corrupt movie from database because of '{}'", e.getMessage());
        toRemove.add(uuid);
      }
    });

    movieList.addAll(loadedMoviesWithoutDuplicates);

    long end = System.nanoTime();

    // remove defect movie sets
    for (UUID uuid : toRemove) {
      movieMap.remove(uuid);
    }

    LOGGER.debug("took {} ms", (end - start) / 1000000);

    LOGGER.info("==> Loaded {} movies", loadedMoviesWithoutDuplicates.size());
  }

  void loadMovieSetsFromDatabase(MVMap<UUID, String> movieSetMap) {
    LOGGER.info("Loading movie sets from database...");
    ReadWriteLock lock = new ReentrantReadWriteLock();

    // load movie sets
    ObjectReader movieSetObjectReader = MovieModuleManager.getInstance().getMovieSetObjectReader();

    List<UUID> toRemove = new ArrayList<>();
    Set<MovieSet> loadedMovieSetsWithoutDuplicates = new HashSet<>();

    long start = System.nanoTime();

    new ArrayList<>(movieSetMap.keyList()).parallelStream().forEach((uuid) -> {
      try {
        MovieSet movieSet = movieSetObjectReader.readValue(movieSetMap.get(uuid));
        movieSet.setDbId(uuid);

        // for performance reasons we add movies sets directly
        lock.writeLock().lock();
        loadedMovieSetsWithoutDuplicates.add(movieSet);
        lock.writeLock().unlock();
      }
      catch (Exception e) {
        LOGGER.debug("problem decoding movie set json string: {}", e.getMessage());
        LOGGER.info("Dropping corrupt movie set");
        lock.writeLock().lock();
        toRemove.add(uuid);
        lock.writeLock().unlock();
      }
    });

    long end = System.nanoTime();

    movieSetList.addAll(loadedMovieSetsWithoutDuplicates);

    // remove defect movie sets
    for (UUID uuid : toRemove) {
      movieSetMap.remove(uuid);
    }

    LOGGER.debug("took {} ms", (end - start) / 1000000);

    LOGGER.info("==> Loaded {} movie sets", loadedMovieSetsWithoutDuplicates.size());
  }

  void initDataAfterLoading() {
    // remove invalid movies which have no VIDEO files
    checkAndCleanupMediaFiles();

    // initialize movies/movie sets (e.g. link with each others)
    // updateLists is slow here calling for a bunch of movies, so we do the work directly
    for (Movie movie : movieList) {
      movie.initializeAfterLoading();
    }

    // build initial index
    readWriteLock.writeLock().lock();
    try {
      byDbId.clear();
      byPath.clear();
      for (Movie movie : movieList) {
        indexMovie(movie);
      }
    }
    finally {
      readWriteLock.writeLock().unlock();
    }

    // update the lists
    EventBus.publishEvent("movieList", Event.createUpdateEvent(instance));

    for (MovieSet movieSet : movieSetList) {
      movieSet.initializeAfterLoading();
    }
  }

  private boolean isCorrupt(Movie movie) {
    if (movie.getMediaFiles(MediaFileType.VIDEO).isEmpty()) {
      LOGGER.debug("Movie without MediaFiles - dropping");
      return true;
    }
    if (StringUtils.isBlank(movie.getPath())) {
      LOGGER.debug("Movie without path - dropping");
      return true;
    }
    if (StringUtils.isBlank(movie.getDataSource())) {
      LOGGER.debug("Movie without datasource - dropping");
      return true;
    }
    if (TmmOsUtils.hasInvalidCharactersForFilesystem(movie.getPath())) {
      LOGGER.debug("Movie with invalid characters for this OS - dropping");
      return true;
    }
    return false;
  }

  public void persistMovie(Movie movie) {
    // the given movie must be in the movie list (same dbId and not only same path!)
    if (findByDbId(movie.getDbId()).isEmpty()) {
      LOGGER.debug("not persisting movie - not in movielist");
      return;
    }

    if (isCorrupt(movie)) {
      // not valid -> remove
      LOGGER.warn("Cannot persist movie '{}' - dropping", movie.getTitle());
      removeMovies(Collections.singletonList(movie));
    }
    else {
      // save
      try {
        MovieModuleManager.getInstance().persistMovie(movie);
        EventBus.publishEvent(TOPIC_MOVIES, Event.createSaveEvent(movie));
      }
      catch (Exception e) {
        LOGGER.error("Failed to persist movie '{}' - '{}'", movie.getTitle(), e.getMessage());
      }
    }
  }

  public void persistMovieSet(MovieSet movieSet) {
    // remove this movie set from the database
    try {
      MovieModuleManager.getInstance().persistMovieSet(movieSet);
      EventBus.publishEvent(TOPIC_MOVIE_SETS, Event.createSaveEvent(movieSet));
    }
    catch (Exception e) {
      LOGGER.error("Failed to persist movie set '{}' - '{}'", movieSet.getTitle(), e.getMessage());
    }
  }

  public MovieSet lookupMovieSet(UUID uuid) {
    for (MovieSet movieSet : movieSetList) {
      if (movieSet.getDbId().equals(uuid)) {
        return movieSet;
      }
    }
    return null;
  }

  /**
   * Index a movie into the internal lookup maps.
   *
   * @param movie
   *          the movie to index
   */
  private void indexMovie(Movie movie) {
    // dbid
    UUID id = movie.getDbId();
    if (id != null) {
      byDbId.put(id, movie);
    }

    // movie path
    Path p = movie.getPathNIO();
    if (p != null) {
      Path np = normalizePath(p);
      List<Movie> list = byPath.computeIfAbsent(np, k -> new CopyOnWriteArrayList<>());
      if (!list.contains(movie)) {
        list.add(movie);
      }
    }
  }

  /**
   * Remove a movie from the internal lookup maps.
   *
   * @param movie
   *          the movie to deindex
   */
  private void deindexMovie(Movie movie) {
    // dbid
    UUID id = movie.getDbId();
    if (id != null) {
      byDbId.remove(id);
    }

    // movie path - scan all paths to find and remove this movie
    // This is necessary because the movie's path may have changed since indexing
    byPath.values().forEach(list -> list.remove(movie));
    byPath.values().removeIf(List::isEmpty);
  }

  /**
   * Normalize paths for consistent lookup across platforms.
   *
   * @param p
   *          input path
   * @return normalized absolute path
   */
  private Path normalizePath(Path p) {
    Path np = p.toAbsolutePath().normalize();
    // handle case-insensitive FS by lowercasing string representation on Windows/macOS if needed
    if (SystemUtils.IS_OS_WINDOWS) {
      return Paths.get(np.toString().toLowerCase(Locale.ROOT));
    }
    return np;
  }

  /**
   * Find a movie by its database id.
   *
   * @param id
   *          the UUID dbid
   * @return optional movie
   */
  public Optional<Movie> findByDbId(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    readWriteLock.readLock().lock();
    try {
      return Optional.ofNullable(byDbId.get(id));
    }
    finally {
      readWriteLock.readLock().unlock();
    }
  }

  /**
   * Find movies by their folder path. Multiple movies can share the same path (multi-movie directories).
   *
   * @param path
   *          the movie directory path
   * @return immutable list of movies under that path (may be empty)
   */
  public List<Movie> findByPath(Path path) {
    if (path == null) {
      return Collections.emptyList();
    }
    readWriteLock.readLock().lock();
    try {
      List<Movie> list = byPath.get(normalizePath(path));
      return list == null ? Collections.emptyList() : List.copyOf(list);
    }
    finally {
      readWriteLock.readLock().unlock();
    }
  }

  /**
   * Find the first movie by its folder path.
   * 
   * @param path
   *          the movie directory path
   * @return the movie or null
   */
  public Movie findFirstByPath(Path path) {
    List<Movie> movies = findByPath(path);
    if (movies.isEmpty()) {
      return null;
    }
    return movies.get(0);
  }

  /**
   * for each movie, re-evaluate all the paths against others, and set MMD correctly
   */
  public void reevaluateMMD() {
    if (ListUtils.isEmpty(movieList)) {
      return;
    }

    // build up lookup maps for a faster MMD comparison
    Map<String, List<Movie>> moviePathMap = new HashMap<>();
    for (Movie movie : movieList) {
      String path = movie.getPathNIO().toAbsolutePath().toString();
      List<Movie> moviesForPath = moviePathMap.computeIfAbsent(path, k -> new ArrayList<>());
      moviesForPath.add(movie);
    }

    Map<String, List<Movie>> subMoviePathMap = new HashMap<>();
    for (Movie movie : movieList) {
      Path datasource = Paths.get(movie.getDataSource());

      Path path = movie.getPathNIO().toAbsolutePath();
      do {
        List<Movie> moviesForPath = subMoviePathMap.computeIfAbsent(path.toString(), k -> new ArrayList<>());
        moviesForPath.add(movie);

        if (path.equals(datasource)) {
          break;
        }

        path = path.getParent();
      } while (path != null);
    }

    LOGGER.debug("re-evaluating MMD for {} movies...", movieList.size());
    for (Movie movie : movieList) {
      boolean old = movie.isMultiMovieDir();

      // imagine a structure like
      // movies/bla/bla.mkv
      // movies/bla/blubb/blubb.mkv
      // both would be single (not MMD) file, although the parent MUST be a MMD!
      // so get all sub movies within path (some levels deeper)
      if (!movie.getPathNIO().equals(Paths.get(movie.getDataSource()))) {
        List<Movie> subMovies = subMoviePathMap.get(movie.getPathNIO().toAbsolutePath().toString());
        if (subMovies.size() > 1) {
          // there are some other movies down the path - it MUST be treated as MMD
          movie.setMultiMovieDir(true);
        }
        else {
          // no sub movies, but some in exact same folder? (including myself)
          List<Movie> samePath = moviePathMap.get(movie.getPathNIO().toAbsolutePath().toString());
          if (samePath.size() > 1) {
            movie.setMultiMovieDir(true);
          }
          else {
            // so just me; check another variant - see UDS L840
            if (movie.getPathNIO().getFileName().toString().matches(MovieUpdateDatasourceTask.FOLDER_STRUCTURE)
                || MediaGenres.containsGenre(movie.getPathNIO().getFileName().toString())) {
              movie.setMultiMovieDir(true);
            }
            else {
              // - no submovie
              // - noone in same path
              // - no genres/decade/alphanum folder
              // -> we can now safely assume a single movie - phew :)
              movie.setMultiMovieDir(false);
            }
          }
        }
      }
      else {
        // DS root
        movie.setMultiMovieDir(true);
      }

      if (old != movie.isMultiMovieDir()) {
        LOGGER.debug("Movie '{}' changed MMD {} -> {}", movie.getTitle(), old, movie.isMultiMovieDir());
        movie.saveToDb();
      }
    }
  }

  /**
   * Search for a movie with the default settings.
   * 
   * @param searchTerm
   *          the search term
   * @param year
   *          the year of the movie (if available, otherwise <= 0)
   * @param ids
   *          a map of all available ids of the movie or null if no id based search is requested
   * @param metadataScraper
   *          the media scraper
   * @throws ScrapeException
   *           any {@link ScrapeException} occurred while searching
   * @return the list
   */
  public List<MediaSearchResult> searchMovie(String searchTerm, int year, Map<String, Object> ids, Map<String, String> checksums,
      MediaScraper metadataScraper) throws ScrapeException {
    return searchMovie(searchTerm, year, ids, checksums, metadataScraper, MovieModuleManager.getInstance().getSettings().getScraperLanguage());
  }

  /**
   * Search movie with the chosen language.
   * 
   * @param searchTerm
   *          the search term
   * @param year
   *          the year of the movie (if available, otherwise <= 0)
   * @param ids
   *          a map of all available ids of the movie or null if no id based search is requested
   * @param mediaScraper
   *          the media scraper
   * @param language
   *          the language to search with
   * @throws ScrapeException
   *           any {@link ScrapeException} occurred while searching
   * @return the list
   */
  public List<MediaSearchResult> searchMovie(String searchTerm, int year, Map<String, Object> ids, Map<String, String> checksums,
      MediaScraper mediaScraper, MediaLanguages language) throws ScrapeException {

    if (mediaScraper == null || !mediaScraper.isEnabled()) {
      return Collections.emptyList();
    }

    Set<MediaSearchResult> sr = new TreeSet<>();
    IMovieMetadataProvider provider = (IMovieMetadataProvider) mediaScraper.getMediaProvider();

    Pattern tmdbPattern = Pattern.compile("https://www.themoviedb.org/movie/(.*?)-.*");

    // set what we have, so the provider could chose from all :)
    MovieSearchAndScrapeOptions options = new MovieSearchAndScrapeOptions();
    options.setLanguage(language);
    options.setCertificationCountry(MovieModuleManager.getInstance().getSettings().getCertificationCountry());
    options.setReleaseDateCountry(MovieModuleManager.getInstance().getSettings().getReleaseDateCountry());
    options.setMetadataScraper(mediaScraper);

    if (ids != null) {
      options.setIds(ids);
    }
    options.setChecksums(checksums);

    if (!searchTerm.isEmpty()) {
      String query = searchTerm.toLowerCase(Locale.ROOT);
      if (MediaIdUtil.isValidImdbId(query)) {
        options.setImdbId(query);
      }
      else if (query.startsWith("imdb:")) {
        String imdbId = query.replace("imdb:", "");
        if (MediaIdUtil.isValidImdbId(imdbId)) {
          options.setImdbId(imdbId);
        }
      }
      else if (query.startsWith("https://www.imdb.com/title/")) {
        String imdbId = query.split("/")[4];
        if (MediaIdUtil.isValidImdbId(imdbId)) {
          options.setImdbId(imdbId);
        }
      }
      else if (query.startsWith("tmdb:")) {
        try {
          int tmdbId = Integer.parseInt(query.replace("tmdb:", ""));
          if (tmdbId > 0) {
            options.setTmdbId(tmdbId);
          }
        }
        catch (Exception e) {
          // ignored
        }
      }
      else if (tmdbPattern.matcher(query).matches()) {
        try {
          int tmdbId = Integer.parseInt(tmdbPattern.matcher(query).replaceAll("$1"));
          if (tmdbId > 0) {
            options.setTmdbId(tmdbId);
          }
        }
        catch (Exception e) {
          // ignored
        }
      }
      options.setSearchQuery(searchTerm);
    }

    if (year > 0) {
      options.setSearchYear(year);
    }

    // year in brackets (0000) - this cannot be a movie title!
    String yearInTitle = StrgUtils.substr(searchTerm, "(\\(\\d{4}\\))");
    if (!yearInTitle.isEmpty()) {
      String newSearchTerm = searchTerm.replace(yearInTitle, "").strip();
      if (!newSearchTerm.isBlank()) {
        options.setSearchQuery(newSearchTerm);
        yearInTitle = yearInTitle.substring(1, 5); // yeah, since we have a fixed regex
        year = MetadataUtil.parseInt(yearInTitle, 0);
        options.setSearchYear(year);
      }
    }

    LOGGER.info("Search '{}' for movie title '{}'", provider.getProviderInfo().getId(), searchTerm);

    LOGGER.debug("=====================================================");
    LOGGER.debug("Searching with scraper: {}", provider.getProviderInfo().getId());
    LOGGER.debug("options: {}", options);
    LOGGER.debug("=====================================================");
    sr.addAll(provider.search(options)
        .stream()
        .filter(Objects::nonNull)
        .filter(mediaSearchResult -> !mediaSearchResult.getIds().isEmpty() && StringUtils.isNotBlank(mediaSearchResult.getTitle()))
        .toList());

    // Before retrying ALL scrapers, use this as first fallback:
    // check if title starts with a year, and remove/retry...

    if (sr.isEmpty() && options.getSearchQuery().matches("^\\d{4}.*")) {
      LOGGER.info("Nothing found - trying to search without year in title");

      MovieSearchAndScrapeOptions o = new MovieSearchAndScrapeOptions(options); // copy
      o.setSearchQuery(options.getSearchQuery().substring(4));
      LOGGER.debug("=====================================================");
      LOGGER.debug("Searching again without year in title: {}", provider.getProviderInfo().getId());
      LOGGER.debug("options: {}", o);
      LOGGER.debug("=====================================================");
      sr.addAll(provider.search(o)
          .stream()
          .filter(Objects::nonNull)
          .filter(mediaSearchResult -> !mediaSearchResult.getIds().isEmpty() && StringUtils.isNotBlank(mediaSearchResult.getTitle()))
          .toList());
    }

    // if result is empty, try all scrapers
    if (sr.isEmpty() && MovieModuleManager.getInstance().getSettings().isScraperFallback()) {
      LOGGER.info("Nothing found - trying to search with other scrapers");

      for (MediaScraper ms : getAvailableMediaScrapers()) {
        if (provider.getProviderInfo().equals(ms.getMediaProvider().getProviderInfo())
            || ms.getMediaProvider().getProviderInfo().getName().startsWith("Kodi") || !ms.getMediaProvider().isActive()) {
          continue;
        }
        LOGGER.debug("no result yet - trying alternate scraper: {}", ms.getName());
        try {
          LOGGER.debug("=====================================================");
          LOGGER.debug("Searching with alternate scraper: '{}', '{}'", ms.getMediaProvider().getId(), provider.getProviderInfo().getVersion());
          LOGGER.debug("options: {}", options);
          LOGGER.debug("=====================================================");
          sr.addAll(((IMovieMetadataProvider) ms.getMediaProvider()).search(options)
              .stream()
              .filter(Objects::nonNull)
              .filter(mediaSearchResult -> !mediaSearchResult.getIds().isEmpty() && StringUtils.isNotBlank(mediaSearchResult.getTitle()))
              .toList());
        }
        catch (ScrapeException e) {
          LOGGER.error("Could not search for movie '{}' with '{}' - '{}'", searchTerm, ms.getId(), e.getMessage());
          // just swallow those errors here
        }

        if (!sr.isEmpty()) {
          break;
        }
      }
    }

    LOGGER.info("Found '{}' results for movie title '{}'", sr.size(), searchTerm);

    return new ArrayList<>(sr);
  }

  public List<MediaScraper> getAvailableMediaScrapers() {
    List<MediaScraper> availableScrapers = MediaScraper.getMediaScrapers(ScraperType.MOVIE);
    availableScrapers.sort(new MovieMediaScraperComparator());
    return availableScrapers;
  }

  public MediaScraper getDefaultMediaScraper() {
    MediaScraper scraper = MediaScraper.getMediaScraperById(MovieModuleManager.getInstance().getSettings().getMovieScraper(), ScraperType.MOVIE);
    if (scraper == null || !scraper.isEnabled()) {
      scraper = MediaScraper.getMediaScraperById(MediaMetadata.TMDB, ScraperType.MOVIE);
    }
    return scraper;
  }

  public MediaScraper getMediaScraperById(String providerId) {
    return MediaScraper.getMediaScraperById(providerId, ScraperType.MOVIE);
  }

  /**
   * get all available artwork scrapers.
   * 
   * @return the artwork scrapers
   */
  public List<MediaScraper> getAvailableArtworkScrapers() {
    List<MediaScraper> availableScrapers = MediaScraper.getMediaScrapers(ScraperType.MOVIE_ARTWORK);
    // we can use the MovieMediaScraperComparator here too, since TMDB should also be first
    availableScrapers.sort(new MovieMediaScraperComparator());
    return availableScrapers;
  }

  /**
   * get all specified artwork scrapers
   * 
   * @param providerIds
   *          a list of all specified scraper ids
   * @return the specified artwork scrapers
   */
  public List<MediaScraper> getArtworkScrapers(List<String> providerIds) {
    List<MediaScraper> artworkScrapers = new ArrayList<>();

    for (String providerId : providerIds) {
      if (StringUtils.isBlank(providerId)) {
        continue;
      }
      MediaScraper artworkScraper = MediaScraper.getMediaScraperById(providerId, ScraperType.MOVIE_ARTWORK);
      if (artworkScraper != null) {
        artworkScrapers.add(artworkScraper);
      }
    }

    return artworkScrapers;
  }

  /**
   * get all default (specified via settings) artwork scrapers
   * 
   * @return the specified artwork scrapers
   */
  public List<MediaScraper> getDefaultArtworkScrapers() {
    List<MediaScraper> defaultScrapers = getArtworkScrapers(MovieModuleManager.getInstance().getSettings().getArtworkScrapers());
    return defaultScrapers.stream().filter(MediaScraper::isActive).collect(Collectors.toList());
  }

  /**
   * all available trailer scrapers.
   * 
   * @return the trailer scrapers
   */
  public List<MediaScraper> getAvailableTrailerScrapers() {
    List<MediaScraper> availableScrapers = MediaScraper.getMediaScrapers(ScraperType.MOVIE_TRAILER);
    // we can use the MovieMediaScraperComparator here too, since TMDB should also be first
    availableScrapers.sort(new MovieMediaScraperComparator());
    return availableScrapers;
  }

  /**
   * get all default (specified via settings) trailer scrapers
   * 
   * @return the specified trailer scrapers
   */
  public List<MediaScraper> getDefaultTrailerScrapers() {
    List<MediaScraper> defaultScrapers = getTrailerScrapers(MovieModuleManager.getInstance().getSettings().getTrailerScrapers());
    return defaultScrapers.stream().filter(MediaScraper::isActive).collect(Collectors.toList());
  }

  /**
   * get all specified trailer scrapers.
   * 
   * @param providerIds
   *          the scrapers
   * @return the trailer providers
   */
  public List<MediaScraper> getTrailerScrapers(List<String> providerIds) {
    List<MediaScraper> trailerScrapers = new ArrayList<>();

    for (String providerId : providerIds) {
      if (StringUtils.isBlank(providerId)) {
        continue;
      }
      MediaScraper trailerScraper = MediaScraper.getMediaScraperById(providerId, ScraperType.MOVIE_TRAILER);
      if (trailerScraper != null) {
        trailerScrapers.add(trailerScraper);
      }
    }

    return trailerScrapers;
  }

  /**
   * all available subtitle scrapers.
   *
   * @return the subtitle scrapers
   */
  public List<MediaScraper> getAvailableSubtitleScrapers() {
    List<MediaScraper> availableScrapers = MediaScraper.getMediaScrapers(ScraperType.MOVIE_SUBTITLE);
    availableScrapers.sort(new MovieMediaScraperComparator());
    return availableScrapers;
  }

  /**
   * get all default (specified via settings) subtitle scrapers
   *
   * @return the specified subtitle scrapers
   */
  public List<MediaScraper> getDefaultSubtitleScrapers() {
    List<MediaScraper> defaultScrapers = getSubtitleScrapers(MovieModuleManager.getInstance().getSettings().getSubtitleScrapers());
    return defaultScrapers.stream().filter(MediaScraper::isActive).collect(Collectors.toList());
  }

  /**
   * get all specified subtitle scrapers.
   *
   * @param providerIds
   *          the scrapers
   * @return the subtitle scrapers
   */
  public List<MediaScraper> getSubtitleScrapers(List<String> providerIds) {
    List<MediaScraper> subtitleScrapers = new ArrayList<>();

    for (String providerId : providerIds) {
      if (StringUtils.isBlank(providerId)) {
        continue;
      }
      MediaScraper subtitleScraper = MediaScraper.getMediaScraperById(providerId, ScraperType.MOVIE_SUBTITLE);
      if (subtitleScraper != null) {
        subtitleScrapers.add(subtitleScraper);
      }
    }

    return subtitleScrapers;
  }

  /**
   * Gets the movie count.
   * 
   * @return the movie count
   */
  public int getMovieCount() {
    return movieList.size();
  }

  /**
   * Gets the movie set count.
   * 
   * @return the movie set count
   */
  public int getMovieSetCount() {
    return movieSetList.size();
  }

  /**
   * Gets the movie in movie set count.
   *
   * @return the movie in movie set count
   */
  public int getMovieInMovieSetCount() {
    int count = 0;
    for (MovieSet movieSet : movieSetList) {
      count += movieSet.getMovies().size();
    }
    return count;
  }

  /**
   * Update year in movies - used to sync all years
   *
   */
  private void updateYear(List<Movie> movies) {
    Set<Integer> years = new TreeSet<>();
    movies.forEach(movie -> years.add(movie.getYear()));

    if (!new HashSet<>(yearsInMovies).equals(years)) {
      yearsInMovies.clear();
      yearsInMovies.addAll(years);
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }
  }

  /**
   * Update decades in movies - used to sync all decades
   */
  private void updateDecades(List<Movie> movies) {
    Set<String> decades = new HashSet<>();
    movies.forEach(movie -> {
      String decadeShort = movie.getDecadeShort();
      if (StringUtils.isNotBlank(decadeShort)) {
        decades.add(decadeShort);
      }
    });

    if (!new HashSet<>(decadesInMovies).equals(decades)) {
      decadesInMovies.clear();
      decadesInMovies.addAll(decades);
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }
  }

  /**
   * Update genres used in movies - used to sync all genres
   */
  private void updateGenres(List<Movie> movies) {
    Set<MediaGenres> genres = new HashSet<>();
    movies.forEach(movie -> genres.addAll(movie.getGenres()));

    if (!new HashSet<>(genresInMovies).equals(genres)) {
      genresInMovies.clear();
      genresInMovies.addAll(genres);
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }
  }

  /**
   * Update tags used in movies - used to sync all tags
   */
  private void updateTags(List<Movie> movies) {
    Set<String> tags = new TreeSet<>();
    movies.forEach(movie -> tags.addAll(movie.getTags()));
    Utils.removeDuplicateStringFromCollectionIgnoreCase(tags);

    if (!new HashSet<>(tagsInMovies).equals(tags)) {
      tagsInMovies.clear();
      tagsInMovies.addAll(tags);
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }
  }

  /**
   * Update media information used in movies.
   *
   */
  private void updateMediaInformationLists(List<Movie> movies) {
    Set<String> videoCodecs = new HashSet<>();
    Set<Double> frameRates = new HashSet<>();
    Map<String, String> videoContainers = new HashMap<>();
    Set<String> audioCodecs = new HashSet<>();
    Set<Integer> audioChannels = new HashSet<>();
    Set<Integer> audioStreamCount = new HashSet<>();
    Set<Integer> subtitleStreamCount = new HashSet<>();
    Set<String> audioLanguages = new HashSet<>();
    Set<String> subtitleLanguages = new HashSet<>();
    Set<String> hdrFormat = new HashSet<>();
    Set<String> audioTitles = new HashSet<>();
    Set<String> subtitleFormats = new HashSet<>();

    for (Movie movie : movies) {

      // get subtitle language/format from video files and subtitle files
      boolean firstVideoFile = true;
      for (MediaFile mf : movie.getMediaFiles(MediaFileType.VIDEO, MediaFileType.SUBTITLE)) {
        if (mf.getType() == MediaFileType.VIDEO && !firstVideoFile) {
          // only get subtitle information from the first video file
          continue;
        }

        // subtitle language
        if (!mf.getSubtitleLanguages().isEmpty()) {
          subtitleLanguages.addAll(mf.getSubtitleLanguages());
        }
        // subtitle formats
        for (MediaFileSubtitle subtitle : mf.getSubtitles()) {
          if (!subtitle.getCodec().isEmpty()) {
            subtitleFormats.add(subtitle.getCodec());
          }
        }

        if (mf.getType() == MediaFileType.VIDEO) {
          firstVideoFile = false;
        }
      }

      subtitleStreamCount.add(movie.getMediaInfoSubtitleStreamCount());

      // get all audio information from video files and audio files
      firstVideoFile = true;
      for (MediaFile mf : movie.getMediaFiles(MediaFileType.VIDEO, MediaFileType.AUDIO)) {
        if (mf.getType() == MediaFileType.VIDEO && !firstVideoFile) {
          // only get audio information from the first video file
          continue;
        }

        for (MediaFileAudioStream audio : mf.getAudioStreams()) {
          if (StringUtils.isNotBlank(audio.getCodec())) {
            audioCodecs.add(audio.getCodec());
          }
          audioChannels.add(audio.getAudioChannels());
        }

        // audio language
        if (!mf.getAudioLanguagesList().isEmpty()) {
          audioLanguages.addAll(mf.getAudioLanguagesList());
        }

        // Audio Titles
        if (!mf.getAudioTitleList().isEmpty()) {
          audioTitles.addAll(mf.getAudioTitleList());
        }

        if (mf.getType() == MediaFileType.VIDEO) {
          firstVideoFile = false;
        }
      }

      audioStreamCount.add(movie.getMediaInfoAudioStreamCount());

      // get video related information only from main video file
      MediaFile mf = movie.getMainVideoFile();

      // video codec
      if (StringUtils.isNotBlank(mf.getVideoCodec())) {
        videoCodecs.add(mf.getVideoCodec());
      }

      // frame rate
      if (mf.getFrameRate() > 0) {
        frameRates.add(mf.getFrameRate());
      }

      // video container
      if (StringUtils.isNotBlank(mf.getContainerFormat())) {
        videoContainers.putIfAbsent(mf.getContainerFormat().toLowerCase(Locale.ROOT), mf.getContainerFormat());
      }

      // HDR Format (comma separated)
      if (!mf.getHdrFormat().isEmpty()) {
        hdrFormat.addAll(Arrays.asList(mf.getHdrFormat().split(", ")));
      }
    }

    // video codecs
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(videoCodecsInMovies, videoCodecs)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // frame rate
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(frameRatesInMovies, frameRates)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // video container
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(videoContainersInMovies, videoContainers.values())) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // audio codec
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(audioCodecsInMovies, audioCodecs)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // audio channels
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(audioChannelsInMovies, audioChannels)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // audio streams
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(audioStreamsInMovies, audioStreamCount)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // audio languages
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(audioLanguagesInMovies, audioLanguages)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // subtitles
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(subtitlesInMovies, subtitleStreamCount)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // subtitle languages
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(subtitleLanguagesInMovies, subtitleLanguages)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // subtitle formats
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(subtitleFormatsInMovies, subtitleFormats)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // HDR Format
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(hdrFormatInMovies, hdrFormat)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }

    // audio titles
    if (ListUtils.addToCopyOnWriteArrayListIfAbsent(audioTitlesInMovies, audioTitles)) {
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(instance));
    }
  }

  /**
   * Update certifications used in movies - used to sync all certifications
   */
  private void updateCertifications(List<Movie> movies) {
    Set<MediaCertification> certifications = new TreeSet<>();
    movies.forEach(movie -> certifications.add(movie.getCertification()));

    if (certifications.hashCode() != certificationsInMovies.hashCode()) {
      certificationsInMovies.clear();
      certificationsInMovies.addAll(certifications);
      EventBus.publishEvent(TOPIC_MOVIES_UI, Event.createUpdateEvent(movieList));
    }
  }

  /**
   * get a {@link List} of all years in movies
   * 
   * @return a {@link List} of all years
   */
  public List<Integer> getYearsInMovies() {
    return Collections.unmodifiableList(yearsInMovies);
  }

  /**
   * get a {@link List} of all decades in movies
   *
   * @return a {@link List} of all decades
   */
  public List<String> getDecadesInMovies() {
    return Collections.unmodifiableList(decadesInMovies);
  }

  /**
   * get a {@link List} of all tags in movies.
   *
   * @return a {@link List} of all tags
   */
  public List<String> getTagsInMovies() {
    return Collections.unmodifiableList(tagsInMovies);
  }

  public List<String> getVideoCodecsInMovies() {
    return Collections.unmodifiableList(videoCodecsInMovies);
  }

  public List<String> getVideoContainersInMovies() {
    return Collections.unmodifiableList(videoContainersInMovies);
  }

  public List<String> getAudioCodecsInMovies() {
    return Collections.unmodifiableList(audioCodecsInMovies);
  }

  public List<Integer> getAudioChannelsInMovies() {
    return Collections.unmodifiableList(audioChannelsInMovies);
  }

  public List<MediaCertification> getCertificationsInMovies() {
    return Collections.unmodifiableList(certificationsInMovies);
  }

  public List<Double> getFrameRatesInMovies() {
    return Collections.unmodifiableList(frameRatesInMovies);
  }

  public List<Integer> getAudioStreamsInMovies() {
    return Collections.unmodifiableList(audioStreamsInMovies);
  }

  public List<Integer> getSubtitlesInMovies() {
    return Collections.unmodifiableList(subtitlesInMovies);
  }

  public List<String> getAudioLanguagesInMovies() {
    return Collections.unmodifiableList(audioLanguagesInMovies);
  }

  public List<String> getSubtitleLanguagesInMovies() {
    return Collections.unmodifiableList(subtitleLanguagesInMovies);
  }

  public List<String> getSubtitleFormatsInMovies() {
    return Collections.unmodifiableList(subtitleFormatsInMovies);
  }

  public List<String> getHDRFormatInMovies() {
    return Collections.unmodifiableList(hdrFormatInMovies);
  }

  public List<String> getAudioTitlesInMovies() {
    return Collections.unmodifiableList(audioTitlesInMovies);
  }

  /**
   * Search duplicates.
   */
  public void searchDuplicates() {
    Map<String, Movie> duplicates = new HashMap<>();

    for (Movie movie : movieList) {
      movie.clearDuplicate();

      Map<String, Object> ids = movie.getIds();
      for (var entry : ids.entrySet()) {
        // only do the duplicate check on well known IDs (to prevent false positives from facebook, twitter, ... IDs)
        switch (entry.getKey().toLowerCase(Locale.ROOT)) {
          case MediaMetadata.TMDB:
          case MediaMetadata.IMDB:
          case MediaMetadata.TVDB:
          case MediaMetadata.TRAKT_TV:
          case MediaMetadata.WIKIDATA:
            break;

          default:
            continue;
        }

        if (entry.getValue() == null || StringUtils.isBlank(entry.getValue().toString()) || entry.getValue().toString().equals("0")) {
          continue;
        }

        String id = entry.getKey() + entry.getValue();
        if (duplicates.containsKey(id)) {
          // yes - set duplicate flag on both movies
          movie.setDuplicate();
          Movie movie2 = duplicates.get(id);
          movie2.setDuplicate();
          LOGGER.info("Duplicate check: movies have the same ID ({}): {} <=> {}", id, movie.getTitle(), movie2.getTitle());
        }
        else {
          // no, store movie
          duplicates.put(id, movie);
        }
      }

      // search per name/year
      // nope - too many dupes https://www.reddit.com/r/tinyMediaManager/comments/sxj4hu/incorrect_flagging_of_duplicate_movies_in_version/
      // String nameYear = movie.getTitle() + movie.getYear();
      // if (duplicates.containsKey(nameYear)) {
      // movie.setDuplicate();
      // Movie movie2 = duplicates.get(nameYear);
      // movie2.setDuplicate();
      // }
      // else {
      // duplicates.put(nameYear, movie);
      // }

      // check video CRC32 checksum
      String crc = movie.getCRC32();
      if (!crc.isEmpty()) {
        if (duplicates.containsKey(crc)) {
          movie.setDuplicate();
          Movie movie2 = duplicates.get(crc);
          movie2.setDuplicate();
          LOGGER.info("Duplicate check: files have the same hash ({}): {} <=> {}", crc, movie.getMainFile().getFileAsPath().toAbsolutePath(),
              movie2.getMainFile().getFileAsPath().toAbsolutePath());
        }
        else {
          duplicates.put(crc, movie);
        }
      }

      // check video PHASH checksum if same; TODO: hammingDistance
      String phash = movie.getPHash();
      if (!phash.isEmpty()) {
        if (duplicates.containsKey(phash)) {
          movie.setDuplicate();
          Movie movie2 = duplicates.get(phash);
          movie2.setDuplicate();
          LOGGER.info("Duplicate check: files have the same hash ({}): {} <=> {}", phash, movie.getMainFile().getFileAsPath().toAbsolutePath(),
              movie2.getMainFile().getFileAsPath().toAbsolutePath());
        }
        else {
          duplicates.put(phash, movie);
        }
      }
    }

    duplicates.clear();
  }

  /**
   * Gets the movie set list.
   * 
   * @return the movieSetList
   */
  public List<MovieSet> getMovieSetList() {
    return Collections.unmodifiableList(movieSetList);
  }

  /**
   * get the movie set list in a sorted order
   * 
   * @return the movie set list (sorted)
   */
  public List<MovieSet> getSortedMovieSetList() {
    List<MovieSet> sortedMovieSets = new ArrayList<>(getMovieSetList());
    sortedMovieSets.sort(movieSetComparator);
    return sortedMovieSets;
  }

  /**
   * Adds the movie set.
   * 
   * @param movieSet
   *          the movie set
   */
  public void addMovieSet(MovieSet movieSet) {
    int oldValue = movieSetList.size();
    readWriteLock.writeLock().lock();
    movieSetList.add(movieSet);
    readWriteLock.writeLock().unlock();
    movieSet.addPropertyChangeListener(movieSetListener);
    firePropertyChange(Constants.ADDED_MOVIE_SET, null, movieSet);
    firePropertyChange("movieSetCount", oldValue, movieSetList.size());
    firePropertyChange("movieInMovieSetCount", oldValue, getMovieInMovieSetCount());
  }

  /**
   * Removes the movie set.
   * 
   * @param movieSet
   *          the movie set
   */
  public void removeMovieSet(MovieSet movieSet) {
    int oldValue = movieSetList.size();
    movieSet.removePropertyChangeListener(movieSetListener);

    try {
      // remove NFO
      for (MediaFile mf : movieSet.getMediaFiles(MediaFileType.NFO)) {
        Utils.deleteFileSafely(mf.getFileAsPath());
      }

      // remove artwork
      MovieSetArtworkHelper.removeMovieSetArtwork(movieSet);

      // remove any empty movie set data folder
      if (StringUtils.isNotBlank(MovieModuleManager.getInstance().getSettings().getMovieSetDataFolder())) {
        String movieSetName = MovieSetArtworkHelper.getMovieSetTitleForStorage(movieSet);
        Utils.deleteEmptyDirectoryRecursive(Paths.get(MovieModuleManager.getInstance().getSettings().getMovieSetDataFolder(), movieSetName));
      }

      movieSet.removeAllMovies();

      readWriteLock.writeLock().lock();
      movieSetList.remove(movieSet);
      readWriteLock.writeLock().unlock();
      MovieModuleManager.getInstance().removeMovieSetFromDb(movieSet);
      EventBus.publishEvent(TOPIC_MOVIE_SETS, Event.createRemoveEvent(movieSet));
    }
    catch (Exception e) {
      LOGGER.error("Error removing movie set '{}' from DB - '{}'", movieSet.getTitle(), e.getMessage());
    }

    firePropertyChange(Constants.REMOVED_MOVIE_SET, null, movieSet);
    firePropertyChange("movieSetCount", oldValue, movieSetList.size());
    firePropertyChange("movieInMovieSetCount", oldValue, getMovieInMovieSetCount());
  }

  public MovieSet findMovieSet(String title, int tmdbId) {
    // first search by tmdbId
    if (tmdbId > 0) {
      for (MovieSet movieSet : movieSetList) {
        if (movieSet.getTmdbId() == tmdbId) {
          return movieSet;
        }
      }
    }

    // search for the movieset by name
    if (StringUtils.isNotBlank(title)) {
      for (MovieSet movieSet : movieSetList) {
        if (movieSet.getTitle().equals(title)) {
          return movieSet;
        }
      }
    }

    return null;
  }

  public synchronized MovieSet getMovieSet(String title, int tmdbId) {
    MovieSet movieSet = findMovieSet(title, tmdbId);

    if (movieSet == null && StringUtils.isNotBlank(title)) {
      movieSet = new MovieSet(title);
      if (tmdbId > 0) {
        movieSet.setTmdbId(tmdbId);
      }
      movieSet.saveToDb();
      addMovieSet(movieSet);
    }

    return movieSet;
  }

  /**
   * check if there are movies without (at least) one VIDEO mf
   */
  private void checkAndCleanupMediaFiles() {
    List<Movie> moviesToRemove = new ArrayList<>();
    for (Movie movie : movieList) {
      List<MediaFile> mfs = movie.getMediaFiles(MediaFileType.VIDEO);
      if (mfs.isEmpty()) {
        // mark movie for removal
        moviesToRemove.add(movie);
      }
    }

    if (!moviesToRemove.isEmpty()) {
      LOGGER.debug("movies without VIDEOs detected");
      for (Movie movie : moviesToRemove) {
        LOGGER.debug("  -> '{}' / '{}'", movie.getTitle(), movie.getPathNIO());
      }
      removeMovies(moviesToRemove);

      // and push a message
      // also delay it so that the UI has time to start up
      Thread thread = new Thread(() -> {
        try {
          Thread.sleep(15000);
        }
        catch (Exception ignored) {
          // ignored
        }
        Message message = new Message(MessageLevel.SEVERE, "tmm.movies", "message.database.corrupteddata");
        MessageManager.getInstance().pushMessage(message);
      });
      thread.start();
    }
  }

  /**
   * invalidate the title sortable upon changes to the sortable prefixes
   */
  public void invalidateTitleSortable() {
    movieList.parallelStream().forEach(Movie::clearTitleSortable);
  }

  /**
   * create a new offline movie with the given title in the specified data source
   * 
   * @param title
   *          the given title
   * @param datasource
   *          the data source to create the offline movie in
   * @param mediaSource
   *          the media source to be set for the offline movie
   */
  public void addOfflineMovie(String title, String datasource, MediaSource mediaSource) {
    // first crosscheck if the data source is in our settings
    if (!MovieModuleManager.getInstance().getSettings().getMovieDataSource().contains(datasource)) {
      return;
    }

    // create a movie and set it as MF
    Movie movie = new Movie();
    movie.setTitle(title);

    String cleanedTitle = MovieRenamer.createDestinationForFoldername(movie);

    // check if there is already an identical stub folder
    int i = 1;
    Path stubFolder = Paths.get(datasource, cleanedTitle);
    while (Files.exists(stubFolder)) {
      stubFolder = Paths.get(datasource, cleanedTitle + "(" + i++ + ")");
    }

    cleanedTitle = MovieRenamer.createDestinationForFilename(movie);

    Path stubFile = stubFolder.resolve(cleanedTitle + ".disc");

    // create the stub file
    try {
      Files.createDirectory(stubFolder);
      Files.createFile(stubFile);
    }
    catch (IOException e) {
      LOGGER.error("Could not create stub file '{}' - '{}'", stubFile, e.getMessage());
      return;
    }

    movie.setPath(stubFolder.toAbsolutePath().toString());

    movie.setDataSource(datasource);
    movie.setMediaSource(mediaSource);
    movie.setDateAdded(new Date());

    MediaFile mf = new MediaFile(stubFile);
    mf.gatherMediaInformation();
    movie.addToMediaFiles(mf);
    movie.setOffline(true);
    movie.setNewlyAdded(true);

    try {
      addMovie(movie);
      movie.saveToDb();
    }
    catch (Exception e) {
      try {
        Utils.deleteDirectoryRecursive(stubFolder);
      }
      catch (Exception e1) {
        LOGGER.debug("could not delete stub folder - {}", e1.getMessage());
      }
      throw e;
    }
  }

  /**
   * get all titles from TV shows (mainly for the showlink feature)
   *
   * @return a {@link List} of all TV show titles
   */
  public List<String> getTvShowTitles() {
    List<String> tvShowTitles = new ArrayList<>();
    TvShowModuleManager.getInstance().getTvShowList().getTvShows().forEach(tvShow -> tvShowTitles.add(tvShow.getTitle()));
    tvShowTitles.sort(Comparator.naturalOrder());
    return tvShowTitles;
  }

  public List<MovieScraperMetadataConfig> detectMissingMetadata(Movie movie) {
    return detectMissingFields(movie, MovieModuleManager.getInstance().getSettings().getMovieCheckMetadata());
  }

  public List<MovieScraperMetadataConfig> detectMissingArtwork(Movie movie) {
    return detectMissingFields(movie, MovieModuleManager.getInstance().getSettings().getMovieCheckArtwork());
  }

  public List<MovieScraperMetadataConfig> detectMissingFields(Movie movie, List<MovieScraperMetadataConfig> toCheck) {
    List<MovieScraperMetadataConfig> missingMetadata = new ArrayList<>();

    for (MovieScraperMetadataConfig metadataConfig : toCheck) {
      Object value = movie.getValueForMetadata(metadataConfig);
      if (value == null) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof String && StringUtils.isBlank((String) value)) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof Number && ((Number) value).intValue() <= 0) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
        missingMetadata.add(metadataConfig);
      }
      else if (value == MediaCertification.UNKNOWN) {
        missingMetadata.add(metadataConfig);
      }
    }

    return missingMetadata;
  }

  public List<MovieSetScraperMetadataConfig> detectMissingMetadata(MovieSet movieSet) {
    return detectMissingFields(movieSet, MovieModuleManager.getInstance().getSettings().getMovieSetCheckMetadata());
  }

  public List<MovieSetScraperMetadataConfig> detectMissingArtwork(MovieSet movieSet) {
    return detectMissingFields(movieSet, MovieModuleManager.getInstance().getSettings().getMovieSetCheckArtwork());
  }

  public List<MovieSetScraperMetadataConfig> detectMissingFields(MovieSet movieSet, List<MovieSetScraperMetadataConfig> toCheck) {
    List<MovieSetScraperMetadataConfig> missingMetadata = new ArrayList<>();

    for (MovieSetScraperMetadataConfig metadataConfig : toCheck) {
      Object value = movieSet.getValueForMetadata(metadataConfig);
      if (value == null) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof String && StringUtils.isBlank((String) value)) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof Number && ((Number) value).intValue() <= 0) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
        missingMetadata.add(metadataConfig);
      }
      else if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
        missingMetadata.add(metadataConfig);
      }
      else if (value == MediaCertification.UNKNOWN) {
        missingMetadata.add(metadataConfig);
      }
    }

    return missingMetadata;
  }

  private static class MovieComparator implements Comparator<Movie> {
    @Override
    public int compare(Movie o1, Movie o2) {
      if (o1 == null || o2 == null || o1.getTitleSortable() == null || o2.getTitleSortable() == null) {
        return 0;
      }
      return o1.getTitleSortable().compareToIgnoreCase(o2.getTitleSortable());
    }
  }

  private static class MovieSetComparator implements Comparator<MovieSet> {
    @Override
    public int compare(MovieSet o1, MovieSet o2) {
      if (o1 == null || o2 == null || o1.getTitleSortable() == null || o2.getTitleSortable() == null) {
        return 0;
      }
      return o1.getTitleSortable().compareToIgnoreCase(o2.getTitleSortable());
    }
  }

  private static class MovieMediaScraperComparator implements Comparator<MediaScraper> {
    @Override
    public int compare(MediaScraper o1, MediaScraper o2) {
      if (o1.getPriority() == o2.getPriority()) {
        return o1.getId().compareTo(o2.getId()); // samne prio? alphabetical
      }
      else {
        return Integer.compare(o2.getPriority(), o1.getPriority()); // highest first
      }
    }
  }
}
