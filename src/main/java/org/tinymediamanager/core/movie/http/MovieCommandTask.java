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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.ExportTemplate;
import org.tinymediamanager.core.MediaEntityExporter;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileSubtitle;
import org.tinymediamanager.core.http.AbstractCommandHandler;
import org.tinymediamanager.core.http.AbstractCommandHandler.CommandScope;
import org.tinymediamanager.core.movie.MovieExporter;
import org.tinymediamanager.core.movie.MovieList;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MoviePostProcessExecutor;
import org.tinymediamanager.core.movie.MovieRenamerProfile;
import org.tinymediamanager.core.movie.MovieScraperMetadataConfig;
import org.tinymediamanager.core.movie.MovieSearchAndScrapeOptions;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.core.movie.connector.MovieNfoParser;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieARDetectorTask;
import org.tinymediamanager.core.movie.tasks.MovieAssignMovieSetTask;
import org.tinymediamanager.core.movie.tasks.MovieFetchRatingsTask;
import org.tinymediamanager.core.movie.tasks.MovieMissingArtworkDownloadTask;
import org.tinymediamanager.core.movie.tasks.MovieReloadMediaInformationTask;
import org.tinymediamanager.core.movie.tasks.MovieRenameTask;
import org.tinymediamanager.core.movie.tasks.MovieScrapeTask;
import org.tinymediamanager.core.movie.tasks.MovieSubtitleSearchAndDownloadTask;
import org.tinymediamanager.core.movie.tasks.MovieTrailerDownloadTask;
import org.tinymediamanager.core.movie.tasks.MovieUpdateDatasourceTask;
import org.tinymediamanager.core.tasks.CleanUpUnwantedFilesTask;
import org.tinymediamanager.core.tasks.ExportTask;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.ScraperType;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.scraper.util.VideoPHash;
import org.tinymediamanager.thirdparty.FFmpeg;
import org.tinymediamanager.thirdparty.FFprobe;
import org.tinymediamanager.thirdparty.KodiRPC;
import org.tinymediamanager.thirdparty.simkl.MovieSyncSimklTask;
import org.tinymediamanager.thirdparty.trakttv.MovieSyncTraktTvTask;

/**
 * the class {@link MovieCommandTask} handles movie related API calls
 * 
 * @author Manuel Laggner
 */
class MovieCommandTask extends TmmThreadPool {
  private static final Logger                        LOGGER    = LoggerFactory.getLogger(MovieCommandTask.class);

  private final List<AbstractCommandHandler.Command> commands;
  private final MovieList                            movieList = MovieModuleManager.getInstance().getMovieList();
  private final MovieSettings                        settings  = MovieModuleManager.getInstance().getSettings();
  private final List<Movie>                          newMovies = new ArrayList<>();

  private TmmTask                                    activeTask;

  public MovieCommandTask(List<AbstractCommandHandler.Command> commands) {
    super("Movie - HTTP commands");
    this.commands = commands;
  }

  @Override
  protected void doInBackground() {
    // 1. update library commands (datasources, NFO, mediainfo)
    updateDataSources();
    readNfo();
    reloadMediaInfo();
    calculateChecksum();
    aspectRatioDetection();

    // 2. scrape commands (incl. ratings)
    scrape();
    assignMovieSet();

    // 3. download trailer
    downloadTrailer();

    // 4. download subtitles
    downloadSubtitles();

    // 5. download missing artwork
    downloadMissingArtwork();

    // 6. rename
    rename();

    // 7. export (export templates + NFO)
    export();
    writeNfo();

    // 8. cleanup unwanted leftover files
    cleanup();

    // 9. post process (external scripts)
    postProcess();

    // 10. notify/sync external services (Kodi, Trakt, Simkl)
    updateKodi();
    syncTrakt();
    syncSimkl();
  }

  private void updateDataSources() {
    Set<Path> dataSources = new TreeSet<>();
    List<Movie> existingMovies = new ArrayList<>(movieList.getMovies());

    for (AbstractCommandHandler.Command command : commands) {
      if ("update".equals(command.action)) {
        dataSources.addAll(getDataSourcesForScope(command.scope));
      }
    }

    if (!dataSources.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("update.datasource"));
      publishState(TmmResourceBundle.getString("update.datasource"), getProgressDone());

      activeTask = new MovieUpdateDatasourceTask(dataSources);
      activeTask.run(); // blocking

      // done
      activeTask = null;
    }

    // store all new movies from this run
    for (Movie movie : movieList.getMovies()) {
      if (!existingMovies.contains(movie)) {
        newMovies.add(movie);
      }
    }
  }

  private Set<Path> getDataSourcesForScope(CommandScope scope) {
    Set<Path> dataSources = new TreeSet<>();

    if (StringUtils.isBlank(scope.name)) {
      scope.name = "all";
    }

    switch (scope.name) {
      case "all":
        for (String datasource : settings.getMovieDataSource()) {
          if (StringUtils.isNotBlank(datasource)) {
            dataSources.add(Paths.get(datasource).toAbsolutePath());
          }
        }
        break;

      case "single":
        for (String index : ListUtils.nullSafe(Arrays.asList(scope.args))) {
          try {
            int i = Integer.parseInt(index);
            if (settings.getMovieDataSource().size() >= i - 1) {
              dataSources.add(Paths.get(settings.getMovieDataSource().get(i - 1)).toAbsolutePath());
            }

          }
          catch (Exception e) {
            LOGGER.debug("Could not parse data source index from command - '{}'", e.getMessage());
          }
        }
        break;

      case "path":
        for (String path : ListUtils.nullSafe(Arrays.asList(scope.args))) {
          dataSources.add(Paths.get(path.strip()).toAbsolutePath());
        }
        break;

    }

    return dataSources;
  }

  private void calculateChecksum() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("calculateChecksum".equals(command.action)) {
        LOGGER.debug("HTTP API: Calculating checksums - '{}'", command);

        String type = StringUtils.defaultIfBlank(command.args.get("type"), "crc32").toLowerCase();
        boolean crc32 = type.contains("all") || type.contains("crc32");
        boolean phash = type.contains("all") || type.contains("phash");

        if (phash && !FFprobe.isAvailable() && !FFmpeg.isAvailable()) {
          LOGGER.warn("Cannot generate perceptual hash - FFprobe/FFmpeg could not be found");
          phash = false;
        }

        List<Movie> movies = getMoviesForScope(command.scope);
        if (movies.isEmpty()) {
          continue;
        }

        if (crc32) {
          calculateCrc32(movies);
        }
        if (phash) {
          calculatePHash(movies);
        }
      }
    }
  }

  private void calculateCrc32(List<Movie> movies) {
    String taskName = TmmResourceBundle.getString("checksum.crc32.calculate");
    setTaskName(taskName);
    publishState(taskName, getProgressDone());

    int i = 0;
    for (Movie movie : movies) {
      MediaFile main = movie.getMainVideoFile();
      if (main != null && main.getCRC32().isEmpty()) {
        String crc = Utils.getCRC32(main.getFileAsPath());
        if (!crc.isEmpty()) {
          main.setCRC32(crc);
          movie.saveToDb();
        }
      }

      publishState(taskName, ++i);
      if (cancel) {
        return;
      }
    }
  }

  private void calculatePHash(List<Movie> movies) {
    String taskName = TmmResourceBundle.getString("checksum.phash.calculate");
    setTaskName(taskName);
    publishState(taskName, getProgressDone());

    int i = 0;
    for (Movie movie : movies) {
      MediaFile main = movie.getMainVideoFile();
      if (main != null && main.getPHash().isEmpty()) {
        try {
          String phash = VideoPHash.generate(main.getFileAsPath());
          if (!phash.isEmpty()) {
            main.setPHash(phash);
            movie.saveToDb();
          }
        }
        catch (IOException | InterruptedException e) {
          LOGGER.debug("Error generating PHASH for movie '{}': {}", movie.getTitle(), e.getMessage());
        }
      }

      publishState(taskName, ++i);
      if (cancel) {
        return;
      }
    }
  }

  private void readNfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("readNfo".equals(command.action)) {
        LOGGER.debug("HTTP API: Reading NFO files - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.readnfo"));
          publishState(TmmResourceBundle.getString("movie.readnfo"), getProgressDone());

          int i = 0;
          for (Movie movie : movies) {
            Movie tempMovie = null;

            // process all registered NFOs
            for (MediaFile mf : movie.getMediaFiles(MediaFileType.NFO)) {
              // at the first NFO we get a movie object
              if (tempMovie == null) {
                try {
                  tempMovie = MovieNfoParser.parseNfo(mf.getFileAsPath()).toMovie();
                }
                catch (Exception ignored) {
                  // just ignore
                }
                continue;
              }

              // every other NFO gets merged into that temp. movie object
              try {
                tempMovie.merge(MovieNfoParser.parseNfo(mf.getFileAsPath()).toMovie());
              }
              catch (Exception ignored) {
              }
            }

            // no MF (yet)? try to find NFO...
            // it might have been added w/o UDS, and since we FORCE a read...
            if (tempMovie == null) {
              MediaFile vid = movie.getMainVideoFile();
              if (vid != null) {
                String name = vid.getFilenameWithoutStacking();
                name = FilenameUtils.getBaseName(name) + ".nfo";
                Path nfo = vid.getFileAsPath().getParent().resolve(name);
                if (Files.exists(nfo)) {
                  movie.addToMediaFiles(new MediaFile(nfo));
                  try {
                    tempMovie = MovieNfoParser.parseNfo(nfo).toMovie();
                  }
                  catch (Exception ignored) {
                    // just ignore
                  }
                }
              }
            }

            // did we get movie data from our NFOs
            if (tempMovie != null) {
              // force merge it to the actual movie object
              movie.forceMerge(tempMovie);
              movie.saveToDb();
            }

            publishState(TmmResourceBundle.getString("movie.readnfo"), ++i);
            if (cancel) {
              return;
            }
          }
        }
      }
    }
  }

  private void reloadMediaInfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("reloadMediaInfo".equals(command.action)) {
        LOGGER.debug("HTTP API: Reload media info - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.reloadMediaInfo"));
          publishState(TmmResourceBundle.getString("movie.reloadMediaInfo"), getProgressDone());
          activeTask = new MovieReloadMediaInformationTask(movies);
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private void aspectRatioDetection() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("detectAspectRatio".equals(command.action)) {
        LOGGER.debug("HTTP API: Detecting aspect ratio - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.ard"));
          publishState(TmmResourceBundle.getString("movie.ard"), getProgressDone());

          activeTask = new MovieARDetectorTask(movies);
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private void scrape() {
    for (AbstractCommandHandler.Command command : commands) {

      if ("scrape".equals(command.action)) {
        // scrape
        List<Movie> moviesToScrape = getMoviesForScope(command.scope);

        if (!moviesToScrape.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.scraping"));
          publishState(TmmResourceBundle.getString("movie.scraping"), getProgressDone());

          MovieSearchAndScrapeOptions options = new MovieSearchAndScrapeOptions();
          List<MovieScraperMetadataConfig> config = settings.getScraperMetadataConfig();

          // override default scraper?
          if (StringUtils.isNotBlank(command.args.get("scraper"))) {
            String scraperId = command.args.get("scraper");
            MediaScraper scraper = MediaScraper.getMediaScraperById(scraperId, ScraperType.MOVIE);
            if (scraper != null && scraper.isEnabled()) {
              options.setMetadataScraper(scraper);
            }
          }

          MovieScrapeTask.MovieScrapeParams movieScrapeParams = new MovieScrapeTask.MovieScrapeParams(new ArrayList<>(moviesToScrape), options,
              config);
          movieScrapeParams.setOverwriteExistingItems(!settings.isDoNotOverwriteExistingData());
          MovieScrapeTask task = new MovieScrapeTask(movieScrapeParams);
          task.setRunInBackground(true); // to avoid smart scrape dialog

          activeTask = task;
          activeTask.run(); // blocking

          // wait for other tmm threads (artwork download et all)
          while (TmmTaskManager.getInstance().isPoolRunning()) {
            try {
              Thread.sleep(2000);
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          // done
          activeTask = null;
        }
      }
      else if ("fetchRatings".equals(command.action)) {
        // fetchRatings
        List<Movie> moviesToScrape = getMoviesForScope(command.scope);
        if (!moviesToScrape.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.fetchratings"));
          publishState(TmmResourceBundle.getString("movie.fetchratings"), getProgressDone());

          MovieFetchRatingsTask task = new MovieFetchRatingsTask(moviesToScrape, settings.getFetchRatingSources());

          activeTask = task;
          activeTask.run(); // blocking

          // wait for other tmm threads (artwork download et al.)
          while (TmmTaskManager.getInstance().isPoolRunning()) {
            try {
              Thread.sleep(2000);
            }
            catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          // done
          activeTask = null;
        }
      }
    }
  }

  private void assignMovieSet() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("assignMovieSet".equals(command.action)) {
        LOGGER.debug("HTTP API: Assigning movies to their movie sets - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.assignmovieset"));
          publishState(TmmResourceBundle.getString("movie.assignmovieset"), getProgressDone());

          activeTask = new MovieAssignMovieSetTask(movies);
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private void downloadTrailer() {
    Set<Movie> moviesToProcess = new LinkedHashSet<>();

    for (AbstractCommandHandler.Command command : commands) {
      if ("downloadTrailer".equals(command.action)) {
        LOGGER.debug("HTTP API: Downloading trailers - '{}'", command);

        // get movies with missing trailer in this language
        boolean onlyMissingTrailer = true;

        if (StringUtils.isNotBlank(command.args.get("onlyMissing"))) {
          onlyMissingTrailer = Boolean.parseBoolean(command.args.get("onlyMissing"));
        }

        for (Movie movie : getMoviesForScope(command.scope)) {
          if (onlyMissingTrailer) {
            if (movie.getMediaFiles(MediaFileType.TRAILER).isEmpty()) {
              moviesToProcess.add(movie);
            }
          }
          else {
            moviesToProcess.add(movie);
          }
        }
      }
    }

    if (!moviesToProcess.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("trailer.download"));
      publishState(TmmResourceBundle.getString("trailer.download"), getProgressDone());

      for (Movie movie : moviesToProcess) {
        activeTask = new MovieTrailerDownloadTask(movie);
        activeTask.run(); // blocking

        // done
        activeTask = null;
      }
    }
  }

  private void downloadSubtitles() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("downloadSubtitle".equals(command.action)) {
        String language = command.args.get("language");
        MediaLanguages mediaLanguages = null;

        if (StringUtils.isNotBlank(language)) {
          mediaLanguages = MediaLanguages.get(language);
        }

        // no language yet? take the setting
        if (mediaLanguages == null) {
          mediaLanguages = settings.getScraperLanguage();
        }

        List<Movie> moviesToProcess = new ArrayList<>();
        LOGGER.debug("HTTP API: Downloading missing subtitles - '{}'", command);

        // get movies with missing subtitles in this language
        boolean onlyMissingSubs = true;

        if (StringUtils.isNotBlank(command.args.get("onlyMissing"))) {
          onlyMissingSubs = Boolean.parseBoolean(command.args.get("onlyMissing"));
        }

        for (Movie movie : getMoviesForScope(command.scope)) {
          if (onlyMissingSubs) {
            boolean subtitleFound = false;
            for (MediaFile mf : movie.getMediaFiles(MediaFileType.VIDEO, MediaFileType.SUBTITLE)) {
              for (MediaFileSubtitle subtitle : mf.getSubtitles()) {
                if (StringUtils.isNotBlank(subtitle.getLanguage())) {
                  MediaLanguages subtitleLanguage = MediaLanguages.get(subtitle.getLanguage());
                  if (subtitleLanguage == mediaLanguages) {
                    subtitleFound = true;
                    break;
                  }
                }
              }

              if (subtitleFound) {
                break;
              }
            }

            if (!subtitleFound) {
              moviesToProcess.add(movie);
            }
          }
          else {
            moviesToProcess.add(movie);
          }
        }

        if (!moviesToProcess.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.download.subtitles"));
          publishState(TmmResourceBundle.getString("movie.download.subtitles"), getProgressDone());

          activeTask = new MovieSubtitleSearchAndDownloadTask(moviesToProcess, mediaLanguages);
          activeTask.run();

          // done
          activeTask = null;
        }
      }
    }
  }

  private void downloadMissingArtwork() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("downloadMissingArtwork".equals(command.action)) {
        setTaskName(TmmResourceBundle.getString("movie.downloadmissingartwork"));
        publishState(TmmResourceBundle.getString("movie.downloadmissingartwork"), getProgressDone());

        MovieSearchAndScrapeOptions movieSearchAndScrapeConfig = new MovieSearchAndScrapeOptions();
        movieSearchAndScrapeConfig.setCertificationCountry(settings.getCertificationCountry());
        movieSearchAndScrapeConfig.setReleaseDateCountry(settings.getReleaseDateCountry());

        // artwork scrapers
        List<MediaScraper> selectedArtworkScrapers = new ArrayList<>();
        for (MediaScraper artworkScraper : MovieModuleManager.getInstance().getMovieList().getAvailableArtworkScrapers()) {
          if (settings.getArtworkScrapers().contains(artworkScraper.getId())) {
            selectedArtworkScrapers.add(artworkScraper);
          }
        }

        // override default scrapers?
        if (StringUtils.isNotBlank(command.args.get("scraper"))) {
          selectedArtworkScrapers.clear();

          List<String> scraperIds = ParserUtils.split(command.args.get("scraper"));
          for (String id : scraperIds) {
            MediaScraper scraper = MediaScraper.getMediaScraperById(id, ScraperType.MOVIE_ARTWORK);
            if (scraper != null && scraper.isEnabled()) {
              selectedArtworkScrapers.add(scraper);
            }
          }
        }

        movieSearchAndScrapeConfig.setArtworkScraper(selectedArtworkScrapers);

        activeTask = new MovieMissingArtworkDownloadTask(getMoviesForScope(command.scope), movieSearchAndScrapeConfig,
            settings.getScraperMetadataConfig());
        activeTask.run();

        // done
        activeTask = null;

        // wait for other tmm threads (artwork download)
        while (TmmTaskManager.getInstance().isPoolRunning()) {
          try {
            Thread.sleep(2000);
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

        // add all movies from the scope
        List<Movie> moviesToRename = getMoviesForScope(command.scope);
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
        List<Movie> toExport = getMoviesForScope(command.scope);

        if (toExport.isEmpty()) {
          continue;
        }

        String templateName = command.args.get("template");
        if (StringUtils.isBlank(templateName)) {
          continue;
        }

        ExportTemplate template = MediaEntityExporter.findTemplates(MediaEntityExporter.TemplateType.MOVIE)
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
          LOGGER.debug("HTTP API: Exporting movies - '{}'", command);
          setTaskName(TmmResourceBundle.getString("movie.export"));
          publishState(TmmResourceBundle.getString("movie.export"), getProgressDone());

          activeTask = new ExportTask(TmmResourceBundle.getString("movie.export"), new MovieExporter(Paths.get(template.getPath())), toExport,
              Paths.get(exportPath));
          activeTask.run(); // blocking

        }
        catch (Exception e) {
          LOGGER.debug("Could not export movies - '{}'", e.getMessage());
        }

        // done
        activeTask = null;
      }
    }
  }

  private void writeNfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("writeNfo".equals(command.action)) {
        LOGGER.debug("HTTP API: Writing NFO files - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.rewritenfo"));
          publishState(TmmResourceBundle.getString("movie.rewritenfo"), getProgressDone());

          int i = 0;
          for (Movie movie : movies) {
            movie.writeNFO();
            movie.saveToDb();

            publishState(TmmResourceBundle.getString("movie.rewritenfo"), ++i);
            if (cancel) {
              return;
            }
          }
        }
      }
    }
  }

  private void cleanup() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("cleanup".equals(command.action)) {
        LOGGER.debug("HTTP API: Cleaning up unwanted files - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          boolean dryRun = getBoolArg(command, "dryRun", false);

          setTaskName(TmmResourceBundle.getString("cleanupfiles"));
          publishState(TmmResourceBundle.getString("cleanupfiles"), getProgressDone());

          activeTask = new CleanUpUnwantedFilesTask(movies, dryRun);
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private void postProcess() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("postProcess".equals(command.action)) {
        LOGGER.debug("HTTP API: Post processing - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          for (PostProcess process : new ArrayList<>(settings.getPostProcess())) {
            if (cancel) {
              return;
            }

            setTaskName(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName());
            publishState(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName(), getProgressDone());

            activeTask = new MoviePostProcessExecutor(process, movies);
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
        List<Movie> movies = getMoviesForScope(command.scope);

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
        LOGGER.debug("HTTP API: Syncing to Trakt.tv - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

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

  private void syncSimkl() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("syncSimkl".equals(command.action)) {
        LOGGER.debug("HTTP API: Syncing to Simkl.com - '{}'", command);
        List<Movie> movies = getMoviesForScope(command.scope);

        if (!movies.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("movie.syncsimkl.watched"));
          publishState(TmmResourceBundle.getString("movie.syncsimkl.watched"), getProgressDone());

          MovieSyncSimklTask task = new MovieSyncSimklTask(movies);
          task.setSyncWatched(true);

          activeTask = task;
          activeTask.run(); // blocking

          // done
          activeTask = null;
        }
      }
    }
  }

  private boolean getBoolArg(AbstractCommandHandler.Command command, String key, boolean defaultValue) {
    String value = command.args.get(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value);
  }

  private List<Movie> getMoviesForScope(CommandScope scope) {
    List<Movie> moviesToProcess = new ArrayList<>();
    if (StringUtils.isBlank(scope.name)) {
      scope.name = "new";
    }

    switch (scope.name) {
      case "path":
        if (scope.args != null && scope.args.length > 0) {
          List<Path> paths = new ArrayList<>();
          for (String path : scope.args) {
            paths.add(Path.of(path).toAbsolutePath());
          }

          moviesToProcess.addAll(movieList.getMovies().stream().filter(movie -> paths.contains(movie.getPathNIO().toAbsolutePath())).toList());
        }
        break;

      case "dataSource":
        if (scope.args != null && scope.args.length > 0) {
          List<String> dataSources = new ArrayList<>();
          for (String arg : scope.args) {
            // check if this could be an index
            try {
              dataSources.add(settings.getMovieDataSource().get(Integer.parseInt(arg)));
            }
            catch (Exception e) {
              // just add it as a path
              dataSources.add(arg);
            }
          }

          moviesToProcess.addAll(movieList.getMovies().stream().filter(movie -> dataSources.contains(movie.getDataSource())).toList());
        }
        break;

      case "new":
        moviesToProcess.addAll(newMovies);
        break;

      case "unscraped":
        moviesToProcess.addAll(movieList.getUnscrapedMovies());
        break;

      case "all":
      default:
        moviesToProcess.addAll(movieList.getMovies());
        break;
    }

    // filter out locked ones
    return moviesToProcess.stream().filter(movie -> !movie.isLocked()).toList();
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
