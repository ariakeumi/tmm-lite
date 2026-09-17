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
package org.tinymediamanager.core.tvshow.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileSubtitle;
import org.tinymediamanager.core.http.AbstractCommandHandler;
import org.tinymediamanager.core.http.AbstractCommandHandler.CommandScope;
import org.tinymediamanager.core.tasks.CleanUpUnwantedFilesTask;
import org.tinymediamanager.core.tasks.ExportTask;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowEpisodePostProcessExecutor;
import org.tinymediamanager.core.tvshow.TvShowEpisodeScraperMetadataConfig;
import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowExporter;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowPostProcessExecutor;
import org.tinymediamanager.core.tvshow.TvShowRenamerProfile;
import org.tinymediamanager.core.tvshow.TvShowScraperMetadataConfig;
import org.tinymediamanager.core.tvshow.TvShowSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.connector.TvShowEpisodeNfoParser;
import org.tinymediamanager.core.tvshow.connector.TvShowNfoParser;
import org.tinymediamanager.core.tvshow.connector.TvShowSeasonNfoParser;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.tvshow.tasks.TvShowARDetectorTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowEpisodeScrapeTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowFetchRatingsTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowMissingArtworkDownloadTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowReloadMediaInformationTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowRenameTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowScrapeTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowSubtitleSearchAndDownloadTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowThemeDownloadTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowTrailerDownloadTask;
import org.tinymediamanager.core.tvshow.tasks.TvShowUpdateDatasourceTask;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.ScraperType;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.scraper.util.VideoPHash;
import org.tinymediamanager.thirdparty.FFmpeg;
import org.tinymediamanager.thirdparty.FFprobe;
import org.tinymediamanager.thirdparty.KodiRPC;
import org.tinymediamanager.thirdparty.simkl.TvShowSyncSimklTask;
import org.tinymediamanager.thirdparty.trakttv.TvShowSyncTraktTvTask;

/**
 * the class {@link TvShowCommandTask} handles movie related API calls
 * 
 * @author Manuel Laggner
 */
class TvShowCommandTask extends TmmThreadPool {
  private static final Logger                        LOGGER      = LoggerFactory.getLogger(TvShowCommandTask.class);

  private final List<AbstractCommandHandler.Command> commands;
  private final TvShowList                           tvShowList  = TvShowModuleManager.getInstance().getTvShowList();
  private final TvShowSettings                       settings    = TvShowModuleManager.getInstance().getSettings();
  private final List<TvShow>                         newTvShows  = new ArrayList<>();
  private final List<TvShowEpisode>                  newEpisodes = new ArrayList<>();

  private TmmTask                                    activeTask;

  public TvShowCommandTask(List<AbstractCommandHandler.Command> commands) {
    super("TV show - HTTP commands");
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

    // 2. scrape commands
    scrape();

    // 2.1 fetch ratings
    fetchRatings();

    // 3. download trailer
    downloadTrailer();

    // 4. download subtitles
    downloadSubtitles();

    // 5. download missing artwork
    downloadMissingArtwork();

    // 5.1 download theme
    downloadTheme();

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

    List<TvShow> existingTvShows = new ArrayList<>(tvShowList.getTvShows());
    List<TvShowEpisode> existingEpisodes = new ArrayList<>();
    for (TvShow tvShow : existingTvShows) {
      existingEpisodes.addAll(tvShow.getEpisodes());
    }

    for (AbstractCommandHandler.Command command : commands) {
      if ("update".equals(command.action)) {
        dataSources.addAll(getDataSourcesForScope(command.scope));
      }
    }

    if (!dataSources.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("update.datasource"));
      publishState(TmmResourceBundle.getString("update.datasource"), getProgressDone());

      activeTask = new TvShowUpdateDatasourceTask(dataSources);
      activeTask.run(); // blocking

      // done
      activeTask = null;
    }

    // store all new movies from this run
    for (TvShow tvShow : tvShowList.getTvShows()) {
      if (!existingTvShows.contains(tvShow)) {
        newTvShows.add(tvShow);
        continue;
      }
      for (TvShowEpisode episode : tvShow.getEpisodes()) {
        if (!existingEpisodes.contains(episode)) {
          newEpisodes.add(episode);
        }
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
        for (String datasource : settings.getTvShowDataSource()) {
          if (StringUtils.isNotBlank(datasource)) {
            dataSources.add(Paths.get(datasource).toAbsolutePath());
          }
        }
        break;

      case "single":
        for (String index : ListUtils.nullSafe(Arrays.asList(scope.args))) {
          try {
            int i = Integer.parseInt(index);
            if (settings.getTvShowDataSource().size() >= i - 1) {
              dataSources.add(Paths.get(settings.getTvShowDataSource().get(i - 1)).toAbsolutePath());
            }

          }
          catch (Exception e) {
            LOGGER.debug("Could not parse index from command - {}", e.getMessage());
          }
        }
        break;

      case "show":
        for (String path : ListUtils.nullSafe(Arrays.asList(scope.args))) {
          for (TvShow tvShow : tvShowList.getTvShows()) {
            if (tvShow.getPathNIO().toAbsolutePath().toString().equals(path)) {
              dataSources.add(tvShow.getPathNIO().toAbsolutePath());
              break;
            }
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

  private void readNfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("readNfo".equals(command.action)) {
        LOGGER.debug("HTTP API: reading NFO files - '{}'", command);
        List<TvShow> tvShows = getTvShowsForScope(command.scope);
        List<TvShowEpisode> episodes = getEpisodesForScope(command.scope);

        if (!tvShows.isEmpty() || !episodes.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("tvshow.readnfo"));
          publishState(TmmResourceBundle.getString("tvshow.readnfo"), getProgressDone());

          int i = 0;
          for (TvShow tvShow : tvShows) {
            boolean dirty = readTvShowNfo(tvShow);

            // and do that for seasons too
            for (TvShowSeason season : tvShow.getSeasons()) {
              dirty |= readSeasonNfo(season);
            }

            if (dirty) {
              tvShow.saveToDb();
            }

            publishState(TmmResourceBundle.getString("tvshow.readnfo"), ++i);
            if (cancel) {
              return;
            }
          }

          if (!episodes.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("tvshowepisode.readnfo"));
            publishState(TmmResourceBundle.getString("tvshowepisode.readnfo"), getProgressDone());

            i = 0;
            for (TvShowEpisode episode : episodes) {
              if (readEpisodeNfo(episode)) {
                episode.saveToDb();
              }

              publishState(TmmResourceBundle.getString("tvshowepisode.readnfo"), ++i);
              if (cancel) {
                return;
              }
            }
          }
        }
      }
    }
  }

  private boolean readTvShowNfo(TvShow tvShow) {
    TvShow tempTvShow = null;

    // process all registered NFOs
    for (MediaFile mf : tvShow.getMediaFiles(MediaFileType.NFO)) {
      // at the first NFO we get a TV show object
      if (tempTvShow == null) {
        try {
          tempTvShow = TvShowNfoParser.parseNfo(mf.getFileAsPath()).toTvShow();
        }
        catch (Exception ignored) {
          // just ignore
        }
      }
      else {
        // every other NFO gets merged into that temp. TV show object
        try {
          tempTvShow.merge(TvShowNfoParser.parseNfo(mf.getFileAsPath()).toTvShow());
        }
        catch (Exception ignored) {
          // just ignore
        }
      }
    }

    // no MF (yet)? try to find NFO...
    // it might have been added w/o UDS, and since we FORCE a read...
    if (tempTvShow == null && tvShow.getPathNIO() != null) {
      Path nfo = tvShow.getPathNIO().resolve("tvshow.nfo");
      if (Files.exists(nfo)) {
        tvShow.addToMediaFiles(new MediaFile(nfo));
        try {
          tempTvShow = TvShowNfoParser.parseNfo(nfo).toTvShow();
        }
        catch (Exception ignored) {
          // just ignore
        }
      }
    }

    // did we get TV show data from our NFOs
    if (tempTvShow != null) {
      // force merge it to the actual TV show object
      tvShow.forceMerge(tempTvShow);
      return true;
    }

    return false;
  }

  private boolean readSeasonNfo(TvShowSeason season) {
    TvShowSeason tempSeason = null;

    // process all registered NFOs
    for (MediaFile mf : season.getMediaFiles(MediaFileType.NFO)) {
      // at the first NFO we get a season object
      if (tempSeason == null) {
        try {
          tempSeason = TvShowSeasonNfoParser.parseNfo(mf.getFileAsPath()).toTvShowSeason();
        }
        catch (Exception ignored) {
          // just ignore
        }
      }
      else {
        // every other NFO gets merged into that temp. season object
        try {
          tempSeason.merge(TvShowSeasonNfoParser.parseNfo(mf.getFileAsPath()).toTvShowSeason());
        }
        catch (Exception ignored) {
          // just ignore
        }
      }
    }

    // did we get season data from our NFOs
    if (tempSeason != null) {
      // force merge it to the actual season object
      season.forceMerge(tempSeason);
      return true;
    }

    return false;
  }

  private boolean readEpisodeNfo(TvShowEpisode episode) {
    TvShowEpisode tempEpisode = null;

    // process all registered NFOs
    for (MediaFile mf : episode.getMediaFiles(MediaFileType.NFO)) {
      try {
        List<TvShowEpisode> episodesFromNfo = TvShowEpisodeNfoParser.parseNfo(mf.getFileAsPath()).toTvShowEpisodes();

        // at the first NFO we get a episode object
        if (tempEpisode == null) {
          tempEpisode = matchEpisode(episode, episodesFromNfo);
          continue;
        }

        // every other NFO gets merged into that temp. episode object
        // but only if we have detected an episode# at first... (do not match -1 EPs)
        if (episode.getEpisode() > 0 && episodesFromNfo.size() > 1) {
          TvShowEpisode fromNfo = matchEpisode(episode, episodesFromNfo);
          if (fromNfo != null) {
            tempEpisode.merge(fromNfo);
          }
        }
      }
      catch (Exception ignored) {
      }
    }

    // no MF (yet)? try to find NFO...
    // it might have been added w/o UDS, and since we FORCE a read...
    if (tempEpisode == null) {
      MediaFile vid = episode.getMainVideoFile();
      if (vid != null) {
        String name = vid.getFilenameWithoutStacking();
        name = FilenameUtils.getBaseName(name) + ".nfo";
        Path nfo = vid.getFileAsPath().getParent().resolve(name);
        if (Files.exists(nfo)) {
          try {
            episode.addToMediaFiles(new MediaFile(nfo));
            List<TvShowEpisode> episodesFromNfo = TvShowEpisodeNfoParser.parseNfo(nfo).toTvShowEpisodes();
            tempEpisode = matchEpisode(episode, episodesFromNfo);
          }
          catch (Exception ignored) {
          }
        }
      }
    }

    // did we get episode data from our NFOs
    if (tempEpisode != null) {
      // force merge it to the actual episode object
      episode.forceMerge(tempEpisode);
      return true;
    }

    return false;
  }

  private TvShowEpisode matchEpisode(TvShowEpisode episode, List<TvShowEpisode> episodesFromNfo) {
    if (episodesFromNfo.size() == 1) {
      return episodesFromNfo.get(0);
    }

    for (TvShowEpisode ep : episodesFromNfo) {
      if (episode.getSeason() == ep.getSeason() && episode.getEpisode() == ep.getEpisode()) {
        return ep;
      }
    }

    return null;
  }

  public void reloadMediaInfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("reloadMediaInfo".equals(command.action)) {
        LOGGER.debug("HTTP API: reload media info - '{}'", command);
        List<TvShow> tvshows = getTvShowsForScope(command.scope);
        List<TvShowEpisode> tvShowEpisodes = getEpisodesForScope(command.scope);

        if (!tvshows.isEmpty() || !tvShowEpisodes.isEmpty()) {

          setTaskName(TmmResourceBundle.getString("tvshow.updatemediainfo"));
          publishState(TmmResourceBundle.getString("tvshow.updatemediainfo"), getProgressDone());

          activeTask = new TvShowReloadMediaInformationTask(tvshows, tvShowEpisodes);
          activeTask.run();

          activeTask = null;
        }
      }
    }
  }

  private void calculateChecksum() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("calculateChecksum".equals(command.action)) {
        LOGGER.debug("HTTP API: calculating checksums - '{}'", command);

        String type = StringUtils.defaultIfBlank(command.args.get("type"), "crc32").toLowerCase();
        boolean crc32 = type.contains("all") || type.contains("crc32");
        boolean phash = type.contains("all") || type.contains("phash");

        if (phash && !FFprobe.isAvailable() && !FFmpeg.isAvailable()) {
          LOGGER.warn("Cannot generate perceptual hash - FFprobe/FFmpeg could not be found");
          phash = false;
        }

        List<TvShowEpisode> episodes = getEpisodesForScope(command.scope);
        if (episodes.isEmpty()) {
          continue;
        }

        if (crc32) {
          calculateCrc32(episodes);
        }
        if (phash) {
          calculatePHash(episodes);
        }
      }
    }
  }

  private void calculateCrc32(List<TvShowEpisode> episodes) {
    String taskName = TmmResourceBundle.getString("checksum.crc32.calculate");
    setTaskName(taskName);
    publishState(taskName, getProgressDone());

    int i = 0;
    for (TvShowEpisode episode : episodes) {
      MediaFile main = episode.getMainVideoFile();
      if (main != null && main.getCRC32().isEmpty()) {
        String crc = Utils.getCRC32(main.getFileAsPath());
        if (!crc.isEmpty()) {
          main.setCRC32(crc);
          episode.saveToDb();
        }
      }

      publishState(taskName, ++i);
      if (cancel) {
        return;
      }
    }
  }

  private void calculatePHash(List<TvShowEpisode> episodes) {
    String taskName = TmmResourceBundle.getString("checksum.phash.calculate");
    setTaskName(taskName);
    publishState(taskName, getProgressDone());

    int i = 0;
    for (TvShowEpisode episode : episodes) {
      MediaFile main = episode.getMainVideoFile();
      if (main != null && main.getPHash().isEmpty()) {
        try {
          String phash = VideoPHash.generate(main.getFileAsPath());
          if (!phash.isEmpty()) {
            main.setPHash(phash);
            episode.saveToDb();
          }
        }
        catch (IOException | InterruptedException e) {
          LOGGER.debug("Error generating PHASH for episode '{}': {}", episode.getTitle(), e.getMessage());
        }
      }

      publishState(taskName, ++i);
      if (cancel) {
        return;
      }
    }
  }

  public void aspectRatioDetection() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("detectAspectRatio".equals(command.action)) {
        LOGGER.debug("HTTP API: detecting aspect ratio - '{}'", command);
        List<TvShowEpisode> tvShowEpisodes = getEpisodesForScope(command.scope);

        if (!tvShowEpisodes.isEmpty()) {

          setTaskName(TmmResourceBundle.getString("tvshow.ard"));
          publishState(TmmResourceBundle.getString("tvshow.ard"), getProgressDone());

          activeTask = new TvShowARDetectorTask(tvShowEpisodes);
          activeTask.run();

          activeTask = null;
        }
      }
    }
  }

  private void scrape() {
    Set<TvShow> tvShowsToScrape = new LinkedHashSet<>();
    Set<TvShowEpisode> episodesToScrape = new LinkedHashSet<>();
    for (AbstractCommandHandler.Command command : commands) {
      if ("scrape".equals(command.action)) {
        tvShowsToScrape.addAll(getTvShowsForScope(command.scope));
        episodesToScrape.addAll(getEpisodesForScope(command.scope));
      }
    }

    // if we scrape already the whole show, no need to scrape dedicated episodes for it
    Set<TvShowEpisode> removedEpisode = new HashSet<>(); // no dupes
    for (TvShowEpisode ep : episodesToScrape) {
      if (tvShowsToScrape.contains(ep.getTvShow())) {
        removedEpisode.add(ep);
      }
    }
    episodesToScrape.removeAll(removedEpisode);

    if (!tvShowsToScrape.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("tvshow.scraping"));
      publishState(TmmResourceBundle.getString("tvshow.scraping"), getProgressDone());

      TvShowSearchAndScrapeOptions options = new TvShowSearchAndScrapeOptions();
      List<TvShowScraperMetadataConfig> tvShowScraperMetadataConfig = settings.getTvShowScraperMetadataConfig();
      List<TvShowEpisodeScraperMetadataConfig> episodeScraperMetadataConfig = settings.getEpisodeScraperMetadataConfig();
      options.loadDefaults();

      TvShowScrapeTask.TvShowScrapeParams tvShowScrapeParams = new TvShowScrapeTask.TvShowScrapeParams(new ArrayList<>(tvShowsToScrape), options,
          tvShowScraperMetadataConfig, episodeScraperMetadataConfig);
      tvShowScrapeParams.setOverwriteExistingItems(!settings.isDoNotOverwriteExistingData());

      activeTask = new TvShowScrapeTask(tvShowScrapeParams);
      activeTask.run(); // blocking

      // wait for all image downloads!
      while (TmmTaskManager.getInstance().isImageDownloadsRunning()) {
        try {
          Thread.sleep(2000);
        }
        catch (Exception e) {
          break;
        }
      }

      // done
      activeTask = null;
    }

    if (!episodesToScrape.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("tvshow.scraping"));
      publishState(TmmResourceBundle.getString("tvshow.scraping"), getProgressDone());

      // re-group the episodes. If there is a "last used" scraper set for the show also take this into account for the episode
      Map<TvShow, List<TvShowEpisode>> groupedEpisodes = new HashMap<>();
      for (TvShowEpisode episode : episodesToScrape) {
        List<TvShowEpisode> episodes = groupedEpisodes.computeIfAbsent(episode.getTvShow(), k -> new ArrayList<>());
        episodes.add(episode);
      }

      // scrape new episodes
      for (Map.Entry<TvShow, List<TvShowEpisode>> entry : groupedEpisodes.entrySet()) {
        TvShow tvShow = entry.getKey();

        TvShowEpisodeSearchAndScrapeOptions options = new TvShowEpisodeSearchAndScrapeOptions();
        options.loadDefaults();

        // so for the known ones, we can directly start scraping
        if (StringUtils.isNoneBlank(tvShow.getLastScraperId(), tvShow.getLastScrapeLanguage())) {
          options.setMetadataScraper(MediaScraper.getMediaScraperById(tvShow.getLastScraperId(), ScraperType.TV_SHOW));
          options.setLanguage(MediaLanguages.valueOf(tvShow.getLastScrapeLanguage()));
        }

        List<TvShowEpisodeScraperMetadataConfig> episodeScraperMetadataConfig = TvShowModuleManager.getInstance()
            .getSettings()
            .getEpisodeScraperMetadataConfig();

        activeTask = new TvShowEpisodeScrapeTask(entry.getValue(), options, episodeScraperMetadataConfig,
            !TvShowModuleManager.getInstance().getSettings().isDoNotOverwriteExistingData());
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

    // sync to trakt?
    if (TvShowModuleManager.getInstance().getSettings().getSyncTrakt()) {
      Set<TvShow> tvShows = new HashSet<>(tvShowsToScrape);
      for (TvShowEpisode episode : episodesToScrape) {
        tvShows.add(episode.getTvShow());
      }

      TvShowSyncTraktTvTask task = new TvShowSyncTraktTvTask(new ArrayList<>(tvShows));
      task.setSyncCollection(TvShowModuleManager.getInstance().getSettings().getSyncTraktCollection());
      task.setSyncWatched(TvShowModuleManager.getInstance().getSettings().getSyncTraktWatched());
      task.setSyncRating(TvShowModuleManager.getInstance().getSettings().getSyncTraktRating());

      TmmTaskManager.getInstance().addUnnamedTask(task);
    }
  }

  private void fetchRatings() {
    Set<TvShow> tvShowsToScrape = new LinkedHashSet<>();
    Set<TvShowEpisode> episodesToScrape = new LinkedHashSet<>();
    for (AbstractCommandHandler.Command command : commands) {
      if ("fetchRatings".equals(command.action)) {
        tvShowsToScrape.addAll(getTvShowsForScope(command.scope));
        episodesToScrape.addAll(getEpisodesForScope(command.scope));
      }
    }

    if (!tvShowsToScrape.isEmpty() || !episodesToScrape.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("tvshow.fetchratings"));
      publishState(TmmResourceBundle.getString("tvshow.fetchratings"), getProgressDone());

      activeTask = new TvShowFetchRatingsTask(tvShowsToScrape, episodesToScrape, settings.getFetchRatingSources());
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

  private void downloadTrailer() {
    Set<TvShow> tvShowsToProcess = new LinkedHashSet<>();

    for (AbstractCommandHandler.Command command : commands) {
      if ("downloadTrailer".equals(command.action)) {
        LOGGER.debug("HTTP API: downloading trailers - '{}'", command);

        // get movies with missing trailer in this language
        boolean onlyMissingTrailer = true;

        if (StringUtils.isNotBlank(command.args.get("onlyMissing"))) {
          onlyMissingTrailer = Boolean.parseBoolean(command.args.get("onlyMissing"));
        }

        for (TvShow tvShow : getTvShowsForScope(command.scope)) {
          if (onlyMissingTrailer) {
            if (tvShow.getMediaFiles(MediaFileType.TRAILER).isEmpty()) {
              tvShowsToProcess.add(tvShow);
            }
          }
          else {
            tvShowsToProcess.add(tvShow);
          }
        }
      }
    }

    if (!tvShowsToProcess.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("trailer.download"));
      publishState(TmmResourceBundle.getString("trailer.download"), getProgressDone());

      for (TvShow tvShow : tvShowsToProcess) {
        activeTask = new TvShowTrailerDownloadTask(tvShow);
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

        List<TvShowEpisode> episodesToProcess = new ArrayList<>();
        LOGGER.debug("HTTP API: downloading missing subtitles - '{}'", command);

        // get movies with missing subtitles in this language
        boolean onlyMissingSubs = true;

        if (StringUtils.isNotBlank(command.args.get("onlyMissing"))) {
          onlyMissingSubs = Boolean.parseBoolean(command.args.get("onlyMissing"));
        }

        for (TvShowEpisode episode : getEpisodesForScope(command.scope)) {
          if (onlyMissingSubs) {
            boolean subtitleFound = false;
            for (MediaFile mf : episode.getMediaFiles(MediaFileType.VIDEO, MediaFileType.SUBTITLE)) {
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
              episodesToProcess.add(episode);
            }
          }
          else {
            episodesToProcess.add(episode);
          }
        }

        if (!episodesToProcess.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("tvshow.download.subtitles"));
          publishState(TmmResourceBundle.getString("tvshow.download.subtitles"), getProgressDone());

          activeTask = new TvShowSubtitleSearchAndDownloadTask(episodesToProcess, mediaLanguages);
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
        setTaskName(TmmResourceBundle.getString("tvshow.downloadmissingartwork"));
        publishState(TmmResourceBundle.getString("tvshow.downloadmissingartwork"), getProgressDone());

        TvShowSearchAndScrapeOptions tvShowSearchAndScrapeConfig = new TvShowSearchAndScrapeOptions();
        tvShowSearchAndScrapeConfig.setCertificationCountry(TvShowModuleManager.getInstance().getSettings().getCertificationCountry());
        tvShowSearchAndScrapeConfig.setReleaseDateCountry(TvShowModuleManager.getInstance().getSettings().getReleaseDateCountry());

        // artwork scrapers
        List<MediaScraper> selectedArtworkScrapers = new ArrayList<>();
        for (MediaScraper artworkScraper : TvShowModuleManager.getInstance().getTvShowList().getAvailableArtworkScrapers()) {
          if (TvShowModuleManager.getInstance().getSettings().getArtworkScrapers().contains(artworkScraper.getId())) {
            selectedArtworkScrapers.add(artworkScraper);
          }
        }

        // override default scrapers?
        if (StringUtils.isNotBlank(command.args.get("scraper"))) {
          selectedArtworkScrapers.clear();

          List<String> scraperIds = ParserUtils.split(command.args.get("scraper"));
          for (String id : scraperIds) {
            MediaScraper scraper = MediaScraper.getMediaScraperById(id, ScraperType.TVSHOW_ARTWORK);
            if (scraper != null && scraper.isEnabled()) {
              selectedArtworkScrapers.add(scraper);
            }
          }
        }

        tvShowSearchAndScrapeConfig.setArtworkScraper(selectedArtworkScrapers);

        activeTask = new TvShowMissingArtworkDownloadTask(getTvShowsForScope(command.scope), Collections.emptyList(),
            getEpisodesForScope(command.scope), tvShowSearchAndScrapeConfig,
            TvShowModuleManager.getInstance().getSettings().getTvShowScraperMetadataConfig(),
            TvShowModuleManager.getInstance().getSettings().getEpisodeScraperMetadataConfig());
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

  private void downloadTheme() {
    Set<TvShow> tvShowsToProcess = new LinkedHashSet<>();
    boolean overwrite = false;

    for (AbstractCommandHandler.Command command : commands) {
      if ("downloadTheme".equals(command.action)) {
        LOGGER.debug("HTTP API: downloading themes - '{}'", command);

        if (StringUtils.isNotBlank(command.args.get("onlyMissing"))) {
          overwrite = overwrite || !Boolean.parseBoolean(command.args.get("onlyMissing"));
        }

        tvShowsToProcess.addAll(getTvShowsForScope(command.scope));
      }
    }

    if (!tvShowsToProcess.isEmpty()) {
      setTaskName(TmmResourceBundle.getString("theme.download"));
      publishState(TmmResourceBundle.getString("theme.download"), getProgressDone());

      // the task itself skips shows with an existing theme when not overwriting
      activeTask = new TvShowThemeDownloadTask(new ArrayList<>(tvShowsToProcess), overwrite);
      activeTask.run(); // blocking

      // done
      activeTask = null;
    }
  }

  private void rename() {
    // process all renaming tasks in the order the user wants to
    // we need that to let the user call a rename task with different profile per call
    for (AbstractCommandHandler.Command command : commands) {
      if ("rename".equals(command.action)) {
        // get the profile
        String profileName = TvShowRenamerProfile.DEFAULT_RENAMER_PROFILE;

        String arg = command.args.get("profile");
        if (StringUtils.isNotBlank(arg)) {
          profileName = arg;
        }

        // get all TV shows/episodes from the scope
        List<TvShow> tvShowsToRename = getTvShowsForScope(command.scope);
        List<TvShowEpisode> episodesToRename = getEpisodesForScope(command.scope);

        if (!tvShowsToRename.isEmpty() || !episodesToRename.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("tvshow.rename"));
          publishState(TmmResourceBundle.getString("tvshow.rename"), getProgressDone());

          TvShowRenamerProfile renamerProfile;
          if (settings.getRenamerProfiles().containsKey(profileName)) {
            renamerProfile = settings.getRenamerProfiles().get(profileName);
          }
          else {
            // we need to fall back here, since the user can send unavailable renamer profile names in the API!
            LOGGER.warn("given profile '{}' not found, using default profile", profileName);
            renamerProfile = settings.getDefaultRenamerProfile();
          }

          activeTask = new TvShowRenameTask(tvShowsToRename, episodesToRename, renamerProfile);
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
        List<TvShow> toExport = getTvShowsForScope(command.scope);

        if (toExport.isEmpty()) {
          continue;
        }

        String templateName = command.args.get("template");
        if (StringUtils.isBlank(templateName)) {
          continue;
        }

        ExportTemplate template = MediaEntityExporter.findTemplates(MediaEntityExporter.TemplateType.TV_SHOW)
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
          LOGGER.debug("HTTP API: exporting TV shows - '{}'", command);
          setTaskName(TmmResourceBundle.getString("tvshow.export"));
          publishState(TmmResourceBundle.getString("tvshow.export"), getProgressDone());

          activeTask = new ExportTask(TmmResourceBundle.getString("tvshow.export"), new TvShowExporter(Paths.get(template.getPath())), toExport,
              Paths.get(exportPath));
          activeTask.run(); // blocking

        }
        catch (Exception e) {
          LOGGER.debug("Could not export TV shows - '{}'", e.getMessage());
        }

        // done
        activeTask = null;
      }
    }
  }

  private void writeNfo() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("writeNfo".equals(command.action)) {
        LOGGER.debug("HTTP API: writing NFO files - '{}'", command);
        List<TvShow> tvShows = getTvShowsForScope(command.scope);
        List<TvShowEpisode> episodes = getEpisodesForScope(command.scope);

        if (!tvShows.isEmpty() || !episodes.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("tvshow.rewritenfo"));
          publishState(TmmResourceBundle.getString("tvshow.rewritenfo"), getProgressDone());

          int i = 0;
          for (TvShow tvShow : tvShows) {
            // the TV show
            tvShow.writeNFO();

            // and all seasons
            for (TvShowSeason season : tvShow.getSeasons()) {
              if (!season.isDummy()) {
                season.writeNFO();
              }
            }

            tvShow.saveToDb();

            publishState(TmmResourceBundle.getString("tvshow.rewritenfo"), ++i);
            if (cancel) {
              return;
            }
          }

          if (!episodes.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("tvshowepisode.rewritenfo"));
            publishState(TmmResourceBundle.getString("tvshowepisode.rewritenfo"), getProgressDone());

            i = 0;
            for (TvShowEpisode episode : episodes) {
              episode.writeNFO();
              episode.saveToDb();

              publishState(TmmResourceBundle.getString("tvshowepisode.rewritenfo"), ++i);
              if (cancel) {
                return;
              }
            }
          }
        }
      }
    }
  }

  private void cleanup() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("cleanup".equals(command.action)) {
        LOGGER.debug("HTTP API: cleaning up unwanted files - '{}'", command);

        List<MediaEntity> entitiesToProcess = new ArrayList<>();
        entitiesToProcess.addAll(getTvShowsForScope(command.scope));
        entitiesToProcess.addAll(getEpisodesForScope(command.scope));

        if (!entitiesToProcess.isEmpty()) {
          boolean dryRun = getBoolArg(command, "dryRun", false);

          setTaskName(TmmResourceBundle.getString("cleanupfiles"));
          publishState(TmmResourceBundle.getString("cleanupfiles"), getProgressDone());

          activeTask = new CleanUpUnwantedFilesTask(entitiesToProcess, dryRun);
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
        LOGGER.debug("HTTP API: post processing - '{}'", command);
        List<TvShow> tvShows = getTvShowsForScope(command.scope);
        List<TvShowEpisode> episodes = getEpisodesForScope(command.scope);

        for (PostProcess process : new ArrayList<>(settings.getPostProcessTvShow())) {
          if (cancel) {
            return;
          }

          if (!tvShows.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName());
            publishState(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName(), getProgressDone());

            activeTask = new TvShowPostProcessExecutor(process, tvShows);
            activeTask.run(); // blocking

            // done
            activeTask = null;
          }
        }

        for (PostProcess process : new ArrayList<>(settings.getPostProcessEpisode())) {
          if (cancel) {
            return;
          }

          if (!episodes.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName());
            publishState(TmmResourceBundle.getString("Settings.postprocessing") + " - " + process.getName(), getProgressDone());

            activeTask = new TvShowEpisodePostProcessExecutor(process, episodes);
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
        LOGGER.debug("HTTP API: refreshing Kodi library - '{}'", command);
        List<TvShow> tvShows = getTvShowsForScope(command.scope);
        List<TvShowEpisode> episodes = getEpisodesForScope(command.scope);

        if (tvShows.isEmpty() && episodes.isEmpty()) {
          continue;
        }

        boolean withEpisodes = getBoolArg(command, "full", true);

        setTaskName(TmmResourceBundle.getString("kodi.rpc.refreshnfo"));
        publishState(TmmResourceBundle.getString("kodi.rpc.refreshnfo"), getProgressDone());

        KodiRPC kodiRPC = KodiRPC.getInstance();
        int i = 0;

        // cache of all processed DbIds (better than whole objects)
        List<UUID> processed = new ArrayList<>(episodes.size());

        boolean remap = false;
        for (TvShow tvShow : tvShows) {
          kodiRPC.refreshFromNfo(tvShow, withEpisodes);
          remap = true;
          processed.addAll(tvShow.getEpisodes().stream().map(MediaEntity::getDbId).toList());

          publishState(TmmResourceBundle.getString("kodi.rpc.refreshnfo"), ++i);
          if (cancel) {
            return;
          }
        }

        // update single EP only, but not if we already had it via show...
        for (TvShowEpisode episode : episodes) {
          if (!processed.contains(episode.getDbId())) {
            kodiRPC.refreshFromNfo(episode);

            publishState(TmmResourceBundle.getString("kodi.rpc.refreshnfo"), ++i);
            if (cancel) {
              return;
            }
          }
        }

        // if we have updated at least one show (but not episode), we need to re-match the shows
        if (remap) {
          try {
            // need some time to propagate the new showId
            Thread.sleep(1000);
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          kodiRPC.updateTvShowMappings();
        }
      }
    }
  }

  private void syncTrakt() {
    for (AbstractCommandHandler.Command command : commands) {
      if ("syncTrakt".equals(command.action)) {
        LOGGER.debug("HTTP API: syncing to Trakt.tv - '{}'", command);
        List<TvShow> tvShows = getTvShowsForScope(command.scope);

        if (!tvShows.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("tvshow.synctrakt.selected"));
          publishState(TmmResourceBundle.getString("tvshow.synctrakt.selected"), getProgressDone());

          TvShowSyncTraktTvTask task = new TvShowSyncTraktTvTask(tvShows);
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
        LOGGER.debug("HTTP API: syncing to Simkl.com - '{}'", command);
        List<TvShow> tvShows = getTvShowsForScope(command.scope);

        if (!tvShows.isEmpty()) {
          setTaskName(TmmResourceBundle.getString("tvshow.syncsimkl.watched"));
          publishState(TmmResourceBundle.getString("tvshow.syncsimkl.watched"), getProgressDone());

          TvShowSyncSimklTask task = new TvShowSyncSimklTask(tvShows);
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

  private List<TvShow> getTvShowsForScope(CommandScope scope) {
    List<TvShow> tvShowsToProcess = new ArrayList<>();
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

          tvShowsToProcess.addAll(tvShowList.getTvShows().stream().filter(movie -> paths.contains(movie.getPathNIO().toAbsolutePath())).toList());
        }
        break;

      case "dataSource":
        if (scope.args != null && scope.args.length > 0) {
          List<String> dataSources = Arrays.asList(scope.args);
          tvShowsToProcess.addAll(tvShowList.getTvShows().stream().filter(movie -> dataSources.contains(movie.getDataSource())).toList());
        }
        break;

      case "all":
        tvShowsToProcess.addAll(tvShowList.getTvShows());
        break;

      case "unscraped":
        tvShowsToProcess.addAll(tvShowList.getUnscrapedTvShows());
        break;

      case "new":
      default:
        tvShowsToProcess.addAll(newTvShows);
        break;
    }

    // filter out locked ones
    return tvShowsToProcess.stream().filter(tvShow -> !tvShow.isLocked()).collect(Collectors.toList());
  }

  private List<TvShowEpisode> getEpisodesForScope(CommandScope scope) {
    List<TvShowEpisode> episodesToProcess = new ArrayList<>();
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

          for (TvShow tvShow : tvShowList.getTvShows().stream().filter(tvShow -> paths.contains(tvShow.getPathNIO().toAbsolutePath())).toList()) {
            episodesToProcess.addAll(tvShow.getEpisodes());
          }
        }
        break;

      case "dataSource":
        if (scope.args != null && scope.args.length > 0) {
          List<String> dataSources = Arrays.asList(scope.args);

          for (TvShow tvShow : tvShowList.getTvShows().stream().filter(tvShow -> dataSources.contains(tvShow.getDataSource())).toList()) {
            episodesToProcess.addAll(tvShow.getEpisodes());
          }
        }
        break;

      case "unscraped":
        episodesToProcess.addAll(tvShowList.getUnscrapedEpisodes());
        break;

      case "all":
        for (TvShow tvShow : tvShowList.getTvShows()) {
          episodesToProcess.addAll(tvShow.getEpisodes());
        }
        break;

      case "new":
      default:
        episodesToProcess.addAll(newEpisodes);
        break;
    }

    // filter out locked ones
    return episodesToProcess.stream().filter(episode -> !episode.isLocked()).collect(Collectors.toList());
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
