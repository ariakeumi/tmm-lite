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
package org.tinymediamanager.core.movie.http;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.ExportTemplate;
import org.tinymediamanager.core.MediaEntityExporter;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.http.AbstractCommandHandler;
import org.tinymediamanager.core.http.AbstractCommandHandler.CommandScope;
import org.tinymediamanager.core.movie.MovieList;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieRenamerProfile;
import org.tinymediamanager.core.movie.MovieSetArtworkHelper;
import org.tinymediamanager.core.movie.MovieSetExporter;
import org.tinymediamanager.core.movie.MovieSetMoviePostProcessExecutor;
import org.tinymediamanager.core.movie.MovieSetPostProcessExecutor;
import org.tinymediamanager.core.movie.MovieSetScraperMetadataConfig;
import org.tinymediamanager.core.movie.MovieSetSearchAndScrapeOptions;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.entities.MovieSet;
import org.tinymediamanager.core.movie.tasks.MovieRenameTask;
import org.tinymediamanager.core.movie.tasks.MovieSetMissingArtworkDownloadTask;
import org.tinymediamanager.core.movie.tasks.MovieSetScrapeTask;
import org.tinymediamanager.core.tasks.ExportTask;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.ScraperType;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.thirdparty.KodiRPC;
import org.tinymediamanager.thirdparty.trakttv.MovieSyncTraktTvTask;

/**
 * the class {@link MovieSetCommandTask} handles movie set related API calls
 *
 * @author Manuel Laggner
 */
class MovieSetCommandTask extends TmmThreadPool {
  private static final Logger                        LOGGER    = LoggerFactory.getLogger(MovieSetCommandTask.class);

  private final List<AbstractCommandHandler.Command> commands;
  private final MovieList                            movieList = MovieModuleManager.getInstance().getMovieList();
  private final MovieSettings                        settings  = MovieModuleManager.getInstance().getSettings();

  private TmmTask                                    activeTask;

  public MovieSetCommandTask(List<AbstractCommandHandler.Command> commands) {
    super("Movie set - HTTP commands");
    this.commands = commands;
  }

  @Override
  protected void doInBackground() {
    // 1. scrape commands
    scrape();

    // 2. download missing artwork
    downloadMissingArtwork();

    // 2.1 cleanup artwork (move into correct folders)
    cleanupArtwork();

    // 3. rename (the movies of the sets)
    rename();

    // 4. export (export templates + NFO)
    export();
    writeNfo();

    // 5. post process (external scripts)
    postProcess();

    // 6. notify/sync external services (Kodi, Trakt)
    updateKodi();
    syncTrakt();
  }

  private void scrape() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("scrape".equals(command.action)) {
        LOGGER.debug("HTTP API: Scraping movie sets - '{}'", command);
        List<MovieSet> movieSets = getMovieSetsForScope(command.scope);

        if (!movieSets.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movieset.scraping"));
          publishState(TmmResourceBundle.getString("movieset.scraping"), getProgressDone());

          MovieSetSearchAndScrapeOptions options = new MovieSetSearchAndScrapeOptions();
          options.loadDefaults();

          // override default scraper?
          if (StringUtils.isNotBlank(command.args.get("scraper"))) {
            String scraperId = command.args.get("scraper");
            MediaScraper scraper = MediaScraper.getMediaScraperById(scraperId, ScraperType.MOVIE_SET);
            if (scraper != null && scraper.isEnabled()) {
              options.setMetadataScraper(scraper);
            }
          }

          // take the configured metadata/artwork fields from the settings
          List<MovieSetScraperMetadataConfig> config = new ArrayList<>(settings.getMovieSetCheckMetadata());
          config.addAll(settings.getMovieSetCheckArtwork());

          activeTask = new MovieSetScrapeTask(movieSets, options, config);
          activeTask.run(); // blocking

          // wait for all image downloads!
          while (TmmTaskManager.getInstance().isImageDownloadsRunning()) {
            try {
              Thread.sleep(2000);
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }

          // done
          activeTask = null;
        }
      }
    }
  }

  private void downloadMissingArtwork() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("downloadMissingArtwork".equals(command.action)) {
        LOGGER.debug("HTTP API: Downloading missing artwork of movie sets - '{}'", command);
        List<MovieSet> movieSets = getMovieSetsForScope(command.scope);

        if (!movieSets.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movieset.downloadmissingartwork"));
          publishState(TmmResourceBundle.getString("movieset.downloadmissingartwork"), getProgressDone());

          MovieSetSearchAndScrapeOptions options = new MovieSetSearchAndScrapeOptions();
          options.loadDefaults();

          // override default scrapers?
          if (StringUtils.isNotBlank(command.args.get("scraper"))) {
            List<MediaScraper> selectedArtworkScrapers = new ArrayList<>();
            for (String id : ParserUtils.split(command.args.get("scraper"))) {
              MediaScraper scraper = MediaScraper.getMediaScraperById(id, ScraperType.MOVIE_SET);
              if (scraper != null && scraper.isEnabled()) {
                selectedArtworkScrapers.add(scraper);
              }
            }

            if (!selectedArtworkScrapers.isEmpty()) {
              options.setArtworkScraper(selectedArtworkScrapers);
            }
          }

          activeTask = new MovieSetMissingArtworkDownloadTask(movieSets, options);
          activeTask.run(); // blocking

          // wait for all image downloads!
          while (TmmTaskManager.getInstance().isImageDownloadsRunning()) {
            try {
              Thread.sleep(2000);
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }

          // done
          activeTask = null;
        }
      }
    }
  }

  private void cleanupArtwork() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("cleanupArtwork".equals(command.action)) {
        LOGGER.debug("HTTP API: Cleaning up artwork of movie sets - '{}'", command);
        List<MovieSet> movieSets = getMovieSetsForScope(command.scope);

        if (!movieSets.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movieset.cleanupartwork"));
          publishState(TmmResourceBundle.getString("movieset.cleanupartwork"), getProgressDone());

          int i = 0;
          for (MovieSet movieSet : movieSets) {
            MovieSetArtworkHelper.cleanupArtwork(movieSet);

            publishState(TmmResourceBundle.getString("movieset.cleanupartwork"), ++i);
            if (cancel) {
              return;
            }
          }
        }
      }
    }
  }

  private void rename() {
    // process all renaming tasks in the order the user wants to
    // we need that to let the user call a rename task with different profile per call
    for (AbstractCommandHandler.Command command : commands) {
      if ("rename".equals(command.action)) {
        // get the profile
        String profileName = MovieRenamerProfile.DEFAULT_RENAMER_PROFILE;

        String arg = command.args.get("profile");
        if (StringUtils.isNotBlank(arg)) {
          profileName = arg;
        }

        // the movies of the sets get renamed
        List<Movie> moviesToRename = getMoviesOfSets(getMovieSetsForScope(command.scope));
        if (!moviesToRename.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.rename"));
          publishState(TmmResourceBundle.getString("movie.rename"), getProgressDone());

          MovieRenamerProfile renamerProfile;
          if (settings.getRenamerProfiles().containsKey(profileName)) {
            renamerProfile = settings.getRenamerProfiles().get(profileName);
          }
          else {
            // we need to fall back here, since the user can send unavailable renamer profile names in the API!
            LOGGER.warn("given profile '{}' not found, using default profile", profileName);
            renamerProfile = settings.getDefaultRenamerProfile();
          }

          activeTask = new MovieRenameTask(moviesToRename, renamerProfile);
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private void export() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("export".equals(command.action)) {
        List<MovieSet> toExport = getMovieSetsForScope(command.scope);

        if (toExport.isEmpty()) {
          continue;
        }

        String templateName = command.args.get("template");
        if (StringUtils.isBlank(templateName)) {
          continue;
        }

        ExportTemplate template = MediaEntityExporter.findTemplates(MediaEntityExporter.TemplateType.MOVIE_SET)
            .stream()
            .filter(t -> t.getPath().endsWith(templateName))
            .findFirst()
            .orElse(null);

        if (template == null) {
          continue;
        }

        String exportPath = command.args.get("exportPath");
        if (StringUtils.isBlank(exportPath)) {
          continue;
        }

        try {
          LOGGER.debug("HTTP API: Exporting movie sets - '{}'", command);
          setTaskName(TmmResourceBundle.getString("movieset.export"));
          publishState(TmmResourceBundle.getString("movieset.export"), getProgressDone());

          activeTask = new ExportTask(TmmResourceBundle.getString("movieset.export"), new MovieSetExporter(Paths.get(template.getPath())), toExport,
              Paths.get(exportPath));
          activeTask.run(); // blocking

        }
        catch (Exception e) {
          LOGGER.debug("Could not export movie sets - '{}'", e.getMessage());
        }

        // done
        activeTask = null;
      }
    }
  }

  private void writeNfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("writeNfo".equals(command.action)) {
        if (settings.getMovieSetNfoFilenames().isEmpty()) {
          LOGGER.warn("No movie set NFO filename configured - skipping NFO writing");
          continue;
        }

        LOGGER.debug("HTTP API: Writing NFO files of movie sets - '{}'", command);
        List<MovieSet> movieSets = getMovieSetsForScope(command.scope);

        if (!movieSets.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movieset.rewritenfo"));
          publishState(TmmResourceBundle.getString("movieset.rewritenfo"), getProgressDone());

          int i = 0;
          for (MovieSet movieSet : movieSets) {
            movieSet.writeNFO();
            movieSet.saveToDb();

            publishState(TmmResourceBundle.getString("movieset.rewritenfo"), ++i);
            if (cancel) {
              return;
            }
          }
        }
      }
    }
  }

  private void postProcess() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("postProcess".equals(command.action)) {
        LOGGER.debug("HTTP API: Post processing movie sets - '{}'", command);
        List<MovieSet> movieSets = getMovieSetsForScope(command.scope);
        List<Movie> setMovies = getMoviesOfSets(movieSets);

        for (PostProcess process : new ArrayList<>(settings.getMovieSetPostProcess())) {
          if (cancel) {
            return;
          }

          if (!movieSets.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName());
            publishState(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName(), getProgressDone());

            activeTask = new MovieSetPostProcessExecutor(process, movieSets);
            activeTask.run(); // blocking

            // done
            activeTask = null;
          }
        }

        for (PostProcess process : new ArrayList<>(settings.getPostProcess())) {
          if (cancel) {
            return;
          }

          if (!setMovies.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName());
            publishState(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName(), getProgressDone());

            activeTask = new MovieSetMoviePostProcessExecutor(process, setMovies);
            activeTask.run(); // blocking

            // done
            activeTask = null;
          }
        }
      }
    }
  }

  private void updateKodi() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("updateKodi".equals(command.action)) {
        LOGGER.debug("HTTP API: Refreshing Kodi library - '{}'", command);
        List<Movie> movies = getMoviesOfSets(getMovieSetsForScope(command.scope));

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("kodi.rpc.refreshnfo"));
          publishState(TmmResourceBundle.getString("kodi.rpc.refreshnfo"), getProgressDone());

          KodiRPC kodiRPC = KodiRPC.getInstance();
          int i = 0;
          for (Movie movie : movies) {
            kodiRPC.refreshFromNfo(movie);

            publishState(TmmResourceBundle.getString("kodi.rpc.refreshnfo"), ++i);
            if (cancel) {
              return;
            }
          }

          // we have updated at least one movie, so we need to re-match the movies
          try {
            // need some time to propagate the new movieId
            Thread.sleep(1000);
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          kodiRPC.updateMovieMappings();
        }
      }
    }
  }

  private void syncTrakt() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("syncTrakt".equals(command.action)) {
        LOGGER.debug("HTTP API: Syncing movie sets to Trakt.tv - '{}'", command);
        List<Movie> movies = getMoviesOfSets(getMovieSetsForScope(command.scope));

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.synctrakt.selected"));
          publishState(TmmResourceBundle.getString("movie.synctrakt.selected"), getProgressDone());

          MovieSyncTraktTvTask task = new MovieSyncTraktTvTask(movies);
          task.setSyncCollection(getBoolArg(command, "collection", true));
          task.setSyncWatched(getBoolArg(command, "watched", true));
          task.setSyncRating(getBoolArg(command, "rating", true));

          activeTask = task;
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private List<Movie> getMoviesOfSets(List<MovieSet> movieSets) {
    Set<Movie> movies = new LinkedHashSet<>();
    for (MovieSet movieSet : movieSets) {
      for (Movie movie : movieSet.getMovies()) {
        if (!movie.isLocked()) {
          movies.add(movie);
        }
      }
    }

    return new ArrayList<>(movies);
  }

  private List<MovieSet> getMovieSetsForScope(CommandScope scope) {
    List<MovieSet> movieSetsToProcess = new ArrayList<>();
    if (StringUtils.isBlank(scope.name)) {
      scope.name = "all";
    }

    switch (scope.name) {
      case "name":
        if (scope.args != null && scope.args.length > 0) {
          List<String> names = Arrays.asList(scope.args);
          movieSetsToProcess.addAll(movieList.getMovieSetList().stream().filter(movieSet -> names.contains(movieSet.getTitle())).toList());
        }
        break;

      case "path":
        if (scope.args != null && scope.args.length > 0) {
          List<Path> paths = new ArrayList<>();
          for (String path : scope.args) {
            paths.add(Path.of(path).toAbsolutePath());
          }

          movieSetsToProcess.addAll(movieList.getMovieSetList()
              .stream()
              .filter(movieSet -> movieSet.getPathNIO() != null && paths.contains(movieSet.getPathNIO().toAbsolutePath()))
              .toList());
        }
        break;

      case "all":
      default:
        movieSetsToProcess.addAll(movieList.getMovieSetList());
        break;
    }

    // filter out locked ones
    return movieSetsToProcess.stream().filter(movieSet -> !movieSet.isLocked()).collect(Collectors.toList());
  }

  private boolean getBoolArg(AbstractCommandHandler.Command command, String key, boolean defaultValue) {
    String value = command.args.get(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value);
  }

  @Override
  public void cancel() {
    super.cancel();

    if (activeTask != null) {
      activeTask.cancel();
    }
  }

  @Override
  public void callback(Object obj) {
    publishState(progressDone);
  }
}
