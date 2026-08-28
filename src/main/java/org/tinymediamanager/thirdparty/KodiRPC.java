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

package org.tinymediamanager.thirdparty;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.jsonrpc.api.AbstractCall;
import org.tinymediamanager.jsonrpc.api.call.Application;
import org.tinymediamanager.jsonrpc.api.call.AudioLibrary;
import org.tinymediamanager.jsonrpc.api.call.Files;
import org.tinymediamanager.jsonrpc.api.call.System;
import org.tinymediamanager.jsonrpc.api.call.VideoLibrary;
import org.tinymediamanager.jsonrpc.api.model.ApplicationModel;
import org.tinymediamanager.jsonrpc.api.model.FilesModel;
import org.tinymediamanager.jsonrpc.api.model.GlobalModel;
import org.tinymediamanager.jsonrpc.api.model.ListModel;
import org.tinymediamanager.jsonrpc.api.model.VideoModel;
import org.tinymediamanager.jsonrpc.api.model.VideoModel.EpisodeDetail;
import org.tinymediamanager.jsonrpc.api.model.VideoModel.EpisodeFields;
import org.tinymediamanager.jsonrpc.api.model.VideoModel.MovieDetail;
import org.tinymediamanager.jsonrpc.api.model.VideoModel.MovieFields;
import org.tinymediamanager.jsonrpc.api.model.VideoModel.TVShowDetail;
import org.tinymediamanager.jsonrpc.api.model.VideoModel.TVShowFields;
import org.tinymediamanager.jsonrpc.config.HostConfig;
import org.tinymediamanager.jsonrpc.io.ApiCallback;
import org.tinymediamanager.jsonrpc.io.ApiException;
import org.tinymediamanager.jsonrpc.io.ConnectionListener;
import org.tinymediamanager.jsonrpc.io.JavaConnectionManager;
import org.tinymediamanager.jsonrpc.io.JsonApiRequest;
import org.tinymediamanager.jsonrpc.notification.AbstractEvent;
import org.tinymediamanager.scraper.util.DateUtils;

public class KodiRPC {
  private static final Logger         LOGGER            = LoggerFactory.getLogger(KodiRPC.class);
  private static KodiRPC              instance;

  private final JavaConnectionManager connectionManager = new JavaConnectionManager();

  private final Map<String, String>   videodatasources  = new LinkedHashMap<>();                 // dir, label
  private final List<String>          audiodatasources  = new ArrayList<>();

  // TMM DbId-to-KodiId mappings
  private final Map<UUID, Integer>    moviemappings     = new HashMap<>();
  private final Map<UUID, Integer>    tvshowmappings    = new HashMap<>();
  private final Map<UUID, Integer>    episodemappings   = new HashMap<>();                       // on demand

  private String                      kodiVersion       = "";

  private KodiRPC() {
    connectionManager.registerConnectionListener(new ConnectionListener() {

      @Override
      public void notificationReceived(AbstractEvent event) {
        LOGGER.debug("Event received: {}", event);
      }

      @Override
      public void disconnected() {
        LOGGER.info("Kodi RPC: Disconnected");
        MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.INFO, "Kodi disconnected"));
      }

      @Override
      public void connected() {
        LOGGER.info("Kodi RPC: Connected to {}", connectionManager.getHostConfig().getAddress());
        MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.INFO, "Kodi connected"));
      }
    });
  }

  public static synchronized KodiRPC getInstance() {
    if (instance == null) {
      instance = new KodiRPC();
    }

    return instance;
  }

  public boolean isConnected() {
    return connectionManager.isConnected();
  }

  // -----------------------------------------------------------------------------------

  /**
   * gets the Kodi version (cached on connect)
   * 
   * @return
   */
  public String getVersion() {
    return "Kodi " + kodiVersion;
  }

  // -----------------------------------------------------------------------------------

  public void cleanVideoLibrary() {
    final VideoLibrary.Clean call = new VideoLibrary.Clean(true);
    sendWoResponse(call);
  }

  public void scanVideoLibrary() {
    final VideoLibrary.Scan call = new VideoLibrary.Scan(null, true);
    sendWoResponse(call);
  }

  public void scanVideoLibrary(String dir) {
    final VideoLibrary.Scan call = new VideoLibrary.Scan(dir, true);
    sendWoResponse(call);
  }

  public Map<String, String> getVideoDataSources() {
    return this.videodatasources;
  }

  /**
   * unneeded, invalidated.<br>
   * If you have your datasources auto-mounted via fstab, Kodi does NOT list em!<br>
   * We just get a list of absolute filenames, and cannot reliably determine it, thus removed.<br>
   * We DO keep this, for all users who have them correctly set, to use the Kodi UDS call...
   */
  @Deprecated
  private void getAndSetVideoDataSources() {
    final Files.GetSources call = new Files.GetSources(FilesModel.Media.VIDEO); // movies + tv !!!
    send(call);
    if (call.getResults() != null && !call.getResults().isEmpty()) {
      this.videodatasources.clear();
      try {
        for (ListModel.SourceItem res : call.getResults()) {
          LOGGER.debug("Kodi datasource: {}", res.file);
          if (res.file.startsWith("multipath")) {
            // more than one source mapped to a single Kodi datasource
            // multipath://%2fmedia%2f8TB%2fFilme%2fKino%2f/%2fmedia%2fWD-4TB%2f!Kino2%2f/
            String mp = res.file.replace("multipath://", ""); // remove prefix
            String[] source = mp.split("/"); // split on slash
            for (String ds : source) {
              String s = URLDecoder.decode(ds, StandardCharsets.UTF_8);
              this.videodatasources.put(s, res.label);
              LOGGER.debug("     {}", s);
            }
          }
          else {
            this.videodatasources.put(res.file, res.label);
          }
        }
      }
      catch (Exception e) {
        LOGGER.debug("could not process Kodi RPC response - '{}'", e.getMessage());
      }
    }
  }

  // we need to sort the datasources by length (longest first) to find the best match!
  // but keep the order of the LinkedHashMap
  @Deprecated
  private String detectDatasource(String file) {
    ArrayList<String> list = new ArrayList<>(this.videodatasources.keySet());
    Collections.sort(list);
    Collections.reverse(list);

    for (String ds : list) {
      if (file.startsWith(ds)) {
        return ds;
      }
    }
    return "";
  }

  /**
   * given a Kodi path (String), we return the parent folder + filename, separated with "|"<br>
   * (parent can be empty, thus starting with delimiter)
   * 
   * @param path
   * @throws Exception,
   *           if Path cannot be constructed
   * @return
   */
  private String getParentAndFileDelimited(String path) throws Exception {
    Path p = Path.of(path); // can throw
    if (p.getParent() == null) {
      return "|" + p.getFileName().toString();
    }
    else {
      return p.getParent().getFileName().toString() + "|" + p.getFileName().toString();
    }
  }

  /**
   * builds the moviemappings: DBid -> Kodi ID
   */
  protected void getAndSetMovieMappings() {
    final VideoLibrary.GetMovies call = new VideoLibrary.GetMovies(MovieFields.FILE);
    send(call);
    if (call.getResults() != null && !call.getResults().isEmpty()) {
      getAndSetMovieMappings(call.getResults());
    }
  }

  protected int getMappedMoviesSize() {
    return moviemappings.size();
  }

  /**
   * this wrapper method has just been added for unit testing, injecting own values<br>
   * Not intended to use in normal calls, use {@link #getAndSetMovieMappings() getAndSetMovieMappings()} without params instead!
   * 
   * @param movies
   *          call results JSON
   */
  protected void getAndSetMovieMappings(ArrayList<MovieDetail> movies) {
    moviemappings.clear();
    LOGGER.info("KODI {} movies", movies.size()); // stacked movies are multiple times in here

    // 1. prepare a map of all TMM Mfs, rel path from DS -> entity DBID (less memory than complete entity)
    Map<String, UUID> tmmMovies = new HashMap<>();
    for (Movie movie : MovieModuleManager.getInstance().getMovieList().getMovies()) {
      tmmMovies.putAll(parseTmmEntity(movie, movie.isDisc(), false));
    }

    // 2. for every Kodi result, loop over TMM entries and find which matches with "endsWith"
    for (MovieDetail kodiMovie : movies) {
      if (kodiMovie.file == null || kodiMovie.file.isEmpty() || kodiMovie.movieid <= 0) {
        continue;
      }

      // strip-off schema as we only ned the last path;
      // it always comes with slash (even on windows)
      String normalizedKodiFile = kodiMovie.file.replaceAll("^\\w+://+", "/");

      try {
        // stacking only supported on movies
        if (normalizedKodiFile.startsWith("stack")) {
          String[] files = normalizedKodiFile.split(" , ");
          for (String kodiFile : files) {
            normalizedKodiFile = kodiFile.replaceAll("^\\w+://+", "/"); // once again, for others
            // find TMM id
            for (String tmmPath : tmmMovies.keySet()) {
              // need to use Path for delimiter normalization
              if (Path.of(kodiFile).endsWith(Path.of(tmmPath))) {
                // we have a match!
                UUID uuid = tmmMovies.get(tmmPath);
                if (!moviemappings.containsKey(uuid)) {
                  moviemappings.put(uuid, kodiMovie.movieid);
                }
                else {
                  // no putIfAbsent since i wanna have a log!
                  LOGGER.warn("Kodi movie '{}' already attached to another datasource - skipping", kodiMovie.label);
                }
                break; // no need to check the rest
              }
            }
          }
        }
        else {
          // find TMM id
          for (String tmmPath : tmmMovies.keySet()) {
            // need to use Path for delimiter normalization
            if (Path.of(normalizedKodiFile).endsWith(Path.of(tmmPath))) {
              // we have a match!
              UUID uuid = tmmMovies.get(tmmPath);
              if (!moviemappings.containsKey(uuid)) {
                moviemappings.put(uuid, kodiMovie.movieid);
              }
              else {
                // no putIfAbsent since i wanna have a log!
                LOGGER.warn("Kodi movie '{}' already attached to another datasource - skipping", kodiMovie.label);
              }
              break; // no need to check the rest
            }
          }
        }
      }
      catch (Exception e) {
        LOGGER.warn("Kodi movie '{}' error on mapping - skipping", kodiMovie.file);
      }
    }

    LOGGER.info("mapped {} movies", moviemappings.size());
    for (Movie movie : MovieModuleManager.getInstance().getMovieList().getMovies()) {
      if (!moviemappings.containsKey(movie.getDbId())) {
        LOGGER.debug("could not map '{}'", movie.getMainFile().getFileAsPath());
      }
    }
  }

  @Deprecated
  private String parseDatasourceName(Path ds) {
    // get the name of the datasource folder
    // unfortunately, for UNC paths like \\server\share i cannot get the share name from Path
    // and URI is so slow
    String dsName = "";
    if (ds.getFileName() != null) {
      dsName = ds.getFileName().toString();
    }
    else {
      // try with good old file, which is not so bitchy
      File f = ds.toFile();
      dsName = f.getName();
    }
    if (dsName.isEmpty()) {
      // happens when only a drive letter like M:\ is set - return 1:1
      dsName = ds.toString();
    }
    return dsName;
  }

  private Map<String, UUID> parseTmmEntity(MediaEntity entity, boolean isDisc, boolean isMultiEp) {
    Map<String, UUID> fileMap = new HashMap<>();
    Path ds = Paths.get(entity.getDataSource());
    if (ds == null || ds.toString().isBlank()) {
      LOGGER.debug("Datasource was empty? Ignoring {}", entity);
      return fileMap;
    }
    ds = ds.toAbsolutePath(); // we do this for MFs, so to compare them in rel() we need to do this here as well
    MediaFile main = entity.getMainFile();

    // when having a multi EP, we need to process all eps with the same main file
    List<MediaEntity> entitiesToProcess = new ArrayList<>();
    if (isMultiEp) {
      // multi-ep - we have multiple main files
      TvShowEpisode ep = (TvShowEpisode) entity;
      List<TvShowEpisode> eps = TvShowList.getTvEpisodesByFile(ep.getTvShow(), main.getFile());
      entitiesToProcess.addAll(eps);
    }
    else {
      entitiesToProcess.add(entity);
    }

    for (MediaEntity me : entitiesToProcess) {
      try {
        if (isDisc) {
          // Kodi RPC sends what we call the main disc identifier, but we have disc folder only
          for (MediaFile mf : me.getMediaFiles(MediaFileType.VIDEO)) {

            Path file = null;
            // append MainDiscIdentifier to our folder MF
            if (mf.getFilename().equalsIgnoreCase(MediaFileHelper.VIDEO_TS)) {
              file = mf.getFileAsPath().resolve("VIDEO_TS.IFO");
            }
            else if (mf.getFilename().equalsIgnoreCase(MediaFileHelper.HVDVD_TS)) {
              file = mf.getFileAsPath().resolve("HV000I01.IFO");
            }
            else if (mf.getFilename().equalsIgnoreCase(MediaFileHelper.BDMV)) {
              file = mf.getFileAsPath().resolve("index.bdmv");
            }
            else if (mf.isMainDiscIdentifierFile()) {
              // just add MainDiscIdentifier
              file = mf.getFileAsPath();
            }

            if (file != null) {
              String rel = Utils.relPath(Path.of(entity.getDataSource()), file);
              if (!fileMap.containsKey(rel)) {
                fileMap.put(rel, me.getDbId());
              }
              else {
                // no putIfAbsent since i wanna have a log!
                LOGGER.warn("File '{}' already attached to another datasource - skipping", rel);
              }
            }
          }
        }
        else {
          String rel = Utils.relPath(Path.of(entity.getDataSource()), me.getMainFile().getFileAsPath());
          if (!fileMap.containsKey(rel)) {
            fileMap.put(rel, me.getDbId());
          }
          else {
            // can only happen on multi EPs (or maybe parted, if getMain returns multiple)
            int i = 2; // start with #2 ^^
            while (fileMap.containsKey(rel + "#" + i)) {
              i++;
            }
            LOGGER.debug("Adding multi-EP for {} as {}", rel, rel + "#" + i);
            rel = rel + "#" + i;
            fileMap.put(rel, me.getDbId());
          }
        }
      }
      catch (Exception e) {
        LOGGER.warn("File '{}' error on mapping - skipping", e.getMessage());
      }
    }
    return fileMap;
  }

  /**
   * builds the show/episode mappings: DBid -> Kodi ID
   */
  protected void getAndSetTvShowMappings() {
    final VideoLibrary.GetTVShows tvShowCall = new VideoLibrary.GetTVShows(TVShowFields.FILE);
    send(tvShowCall);
    if (tvShowCall.getResults() != null && !tvShowCall.getResults().isEmpty()) {
      getAndSetTvShowMappings(tvShowCall.getResults());
    }
  }

  protected int getMappedTvShowsSize() {
    return tvshowmappings.size();
  }

  /**
   * this wrapper method has just been added for unit testing, injecting own values<br>
   * Not intended to use in normal calls, use {@link #getAndSetTvShowMappings() getAndSetTvShowMappings()} without params instead!
   * 
   * @param shows
   *          call results JSON
   */
  protected void getAndSetTvShowMappings(ArrayList<TVShowDetail> shows) {
    tvshowmappings.clear();
    episodemappings.clear();
    LOGGER.info("KODI {} shows", shows.size());

    // 1. prepare a map of all TMM Mfs, rel path from DS -> entity DBID (less memory than complete entity)
    Map<String, UUID> tmmShows = new HashMap<>();
    for (TvShow show : TvShowModuleManager.getInstance().getTvShowList().getTvShows()) {
      String rel = Utils.relPath(show.getDataSource(), show.getPath());
      tmmShows.put(rel, show.getDbId());
    }

    // 2. for every Kodi result, loop over TMM entries and find which matches with "endsWith"
    for (TVShowDetail kodiShow : shows) {
      if (kodiShow.file == null || kodiShow.file.isEmpty() || kodiShow.tvshowid <= 0) {
        continue;
      }

      // strip-off schema as we only ned the last path;
      // it always comes with slash (even on windows)
      String normalizedKodiFile = kodiShow.file.replaceAll("^\\w+://+", "/");

      try {
        // find TMM id
        for (String tmmPath : tmmShows.keySet()) {
          // need to use Path for delimiter normalization
          if (Path.of(normalizedKodiFile).endsWith(Path.of(tmmPath))) {
            // we have a match!
            UUID uuid = tmmShows.get(tmmPath);
            if (!tvshowmappings.containsKey(uuid)) {
              tvshowmappings.put(uuid, kodiShow.tvshowid);
            }
            else {
              // no putIfAbsent since i wanna have a log!
              LOGGER.warn("Kodi show '{}' already attached to another datasource - skipping", kodiShow.label);
            }
            break; // no need to check the rest
          }
        }
      }
      catch (Exception e) {
        LOGGER.warn("Kodi show '{}' error on mapping - skipping", kodiShow.file);
      }
    }

    LOGGER.info("mapped {} shows", tvshowmappings.size());
    for (TvShow show : TvShowModuleManager.getInstance().getTvShowList().getTvShows()) {
      if (!tvshowmappings.containsKey(show.getDbId())) {
        LOGGER.debug("could not map '{}'", show.getPathNIO());
      }
    }
  }

  public void refreshFromNfo(Movie movie) {
    Integer kodiID = moviemappings.get(movie.getDbId());

    if (kodiID != null) {
      List<MediaFile> nfo = movie.getMediaFiles(MediaFileType.NFO);
      if (!nfo.isEmpty()) {
        LOGGER.debug("Kodi RPC: Refreshing from NFO: {}", nfo.get(0).getFileAsPath());
      }
      else {
        LOGGER.debug("Kodi RPC: No NFO file found to refresh! {}", movie.getTitle());
        // we do NOT return here, maybe Kodi will do something even w/o nfo...
      }

      final VideoLibrary.RefreshMovie call = new VideoLibrary.RefreshMovie(kodiID, false); // always refresh from NFO
      sendWoResponse(call);
    }
    else {
      LOGGER.warn("Kodi RPC: Unable to refresh - could not map movie '{}' to Kodi library!", movie.getTitle());
    }
  }

  public void refreshFromNfo(TvShow tvShow, boolean withEpisodes) {
    Integer kodiID = tvshowmappings.get(tvShow.getDbId());

    if (kodiID != null) {
      List<MediaFile> nfo = tvShow.getMediaFiles(MediaFileType.NFO);
      if (!nfo.isEmpty()) {
        LOGGER.debug("Kodi RPC: Refreshing from NFO: {}", nfo.get(0).getFileAsPath());
      }
      else {
        LOGGER.debug("Kodi RPC: No NFO file found to refresh! {}", tvShow.getTitle());
        // we do NOT return here, maybe Kodi will do something even w/o nfo...
      }

      final VideoLibrary.RefreshTVShow call = new VideoLibrary.RefreshTVShow(kodiID, false, withEpisodes); // always refresh from NFO
      sendWoResponse(call);
    }
    else {
      LOGGER.warn("Kodi RPC: Unable to refresh - could not map TV show '{}' to Kodi library!", tvShow.getTitle());
    }
  }

  public void refreshFromNfo(TvShowEpisode episode) {
    Integer kodiID = getEpisodeId(episode);
    List<MediaFile> nfo = episode.getMediaFiles(MediaFileType.NFO);
    if (!nfo.isEmpty()) {
      LOGGER.debug("Kodi RPC: Refreshing from NFO: {}", nfo.get(0).getFileAsPath());
    }
    else {
      LOGGER.debug("Kodi RPC: No NFO file found to refresh! {}", episode.getTitle());
      // we do NOT return here, maybe Kodi will do something even w/o nfo...
    }

    final VideoLibrary.RefreshEpisode call = new VideoLibrary.RefreshEpisode(kodiID, false); // always refresh from NFO
    sendWoResponse(call);
  }

  public void readWatchedState(Movie movie) {
    Integer kodiID = moviemappings.get(movie.getDbId());

    if (kodiID != null) {
      final VideoLibrary.GetMovieDetails call = new VideoLibrary.GetMovieDetails(kodiID, VideoModel.MovieDetail.PLAYCOUNT,
          VideoModel.MovieDetail.LASTPLAYED);
      send(call);
      if (call.getResult() != null && call.getResult().playcount != null) {
        movie.setPlaycount(call.getResult().playcount);
        if (call.getResult().playcount > 0) {
          movie.setWatched(true);
          try {
            movie.setLastWatched(DateUtils.parseDate(call.getResult().lastplayed));
          }
          catch (Exception e) {
            movie.setLastWatched(new Date());
          }
        }
        else {
          // Kodi saids so
          movie.setWatched(false);
          movie.setLastWatched(null);
        }

        movie.writeNFO();
        movie.saveToDb();
      }
    }
    else {
      LOGGER.warn("Kodi RPC: Unable get playcount - could not map movie '{}' to Kodi library!", movie.getTitle());
    }
  }

  public void readWatchedState(TvShowEpisode episode) {
    Integer kodiID = getEpisodeId(episode);

    final VideoLibrary.GetEpisodeDetails call = new VideoLibrary.GetEpisodeDetails(kodiID, VideoModel.EpisodeDetail.PLAYCOUNT,
        VideoModel.EpisodeDetail.LASTPLAYED);
    send(call);
    if (call.getResult() != null && call.getResult().playcount != null) {
      episode.setPlaycount(call.getResult().playcount);
      if (call.getResult().playcount > 0) {
        episode.setWatched(true);
        try {
          episode.setLastWatched(DateUtils.parseDate(call.getResult().lastplayed));
        }
        catch (Exception e) {
          episode.setLastWatched(new Date());
        }
      }
      else {
        // Kodi saids so
        episode.setWatched(false);
        episode.setLastWatched(null);
      }

      episode.writeNFO();
      episode.saveToDb();
    }
    else {
      LOGGER.warn("Kodi RPC: Unable get playcount - could not map episode '{}' to Kodi library!", episode.getTitle());
    }
  }

  /**
   * Returns a Kodi episodeId, from our TmmEpisode
   * 
   * @param episode
   * @return
   */
  public Integer getEpisodeId(TvShowEpisode episode) {
    Integer kodiShowId = tvshowmappings.get(episode.getTvShowDbId());
    if (kodiShowId == null) {
      return null;
    }

    Integer kodiEpId = episodemappings.get(episode.getDbId());
    if (kodiEpId == null) {
      // cache show
      getAndSetTvShowEpisodeMappings(episode.getTvShow(), kodiShowId);
      // retry
      kodiEpId = episodemappings.get(episode.getDbId());
    }

    return kodiEpId;
  }

  /**
   * We prepare the Tmm.dbId to Kodi.episodeId mapping<br>
   * Solely done via S/EE numbers, NOT files!!!
   * 
   * @param tmmShow
   * @param kodiShowId
   */
  protected synchronized void getAndSetTvShowEpisodeMappings(TvShow tmmShow, Integer kodiShowId) {
    // tvshow has not been cached - do it once
    final VideoLibrary.GetEpisodes episodeCall = new VideoLibrary.GetEpisodes(kodiShowId, EpisodeFields.FILE, EpisodeFields.SEASON,
        EpisodeFields.EPISODE);
    send(episodeCall);
    if (episodeCall.getResults() != null && !episodeCall.getResults().isEmpty()) {
      LOGGER.debug("KODI {} episodes", episodeCall.getResults().size());

      // 1. prepare a map of all TMM EPs, to lookup S/EE number to dbId
      Map<String, UUID> tmmEpisodes = new HashMap<>();
      for (TvShowEpisode ep : tmmShow.getEpisodes()) {
        tmmEpisodes.put("S" + ep.getSeason() + "_E" + ep.getEpisode(), ep.getDbId());
      }

      // 2. for every Kodi result, match a corresponding S/EE number to TMM
      // FIXME: what about different episode groups? We assume it to be same...
      int mappedCnt = 0;
      for (EpisodeDetail kodiEp : episodeCall.getResults()) {
        if (kodiEp.file == null || kodiEp.file.isEmpty() || kodiEp.episodeid <= 0) {
          continue;
        }

        UUID uuid = tmmEpisodes.get("S" + kodiEp.season + "_E" + kodiEp.episode);
        if (uuid != null) {
          // we have a match!
          mappedCnt++;
          episodemappings.put(uuid, kodiEp.episodeid);
        }
      }
      LOGGER.debug("mapped {} episodes for {}", mappedCnt, tmmShow.getTitle());
    }
  }

  // -----------------------------------------------------------------------------------

  public void cleanAudioLibrary() {
    final AudioLibrary.Clean call = new AudioLibrary.Clean(true);
    sendWoResponse(call);
  }

  public void scanAudioLibrary() {
    final AudioLibrary.Scan call = new AudioLibrary.Scan(null);
    sendWoResponse(call);
  }

  public void scanAudioLibrary(String dir) {
    final AudioLibrary.Scan call = new AudioLibrary.Scan(dir);
    sendWoResponse(call);
  }

  public List<String> getAudioDataSources() {
    return this.audiodatasources;
  }

  private void getAndSetAudioDataSources() {
    final Files.GetSources call = new Files.GetSources(FilesModel.Media.MUSIC);
    send(call);
    if (call.getResults() != null && !call.getResults().isEmpty()) {
      this.audiodatasources.clear();
      try {
        for (ListModel.SourceItem res : call.getResults()) {
          this.audiodatasources.add(res.file);
        }
      }
      catch (Exception e) {
        LOGGER.debug("could not process Kodi RPC response - '{}'", e.getMessage());
      }
    }
  }

  // -----------------------------------------------------------------------------------

  /**
   * Kodi version
   */
  public String getKodiVersion() {
    final Application.GetProperties call = new Application.GetProperties("version");
    send(call);
    try {
      ApplicationModel.PropertyValue res = call.getResult();
      int maj = res.version.major;
      int min = res.version.minor;
      return maj + "." + min;
    }
    catch (Exception ignored) {
      // just ignore
    }
    return "";
  }

  /**
   * quit remote Kodi instance
   */
  public void quitApplication() {
    final Application.Quit call = new Application.Quit();
    sendWoResponse(call);
  }

  /**
   * Toggles mute on/off
   */
  public void muteApplication() {
    final Application.GetProperties props = new Application.GetProperties("muted");
    send(props); // get current
    if (props.getResults() != null && !props.getResults().isEmpty()) {
      final Application.SetMute call = new Application.SetMute(new GlobalModel.Toggle(!props.getResult().muted));
      sendWoResponse(call); // toggle true/false
    }
  }

  /**
   * set volume 0-100
   * 
   * @param vol
   */
  public void setVolume(int vol) {
    final Application.SetVolume call = new Application.SetVolume(vol);
    sendWoResponse(call);
  }

  // -----------------------------------------------------------------------------------

  public void SystemEjectOpticalDrive() {
    final System.EjectOpticalDrive call = new System.EjectOpticalDrive();
    sendWoResponse(call);
  }

  public void SystemHibernate() {
    final System.EjectOpticalDrive call = new System.EjectOpticalDrive();
    sendWoResponse(call);
  }

  public void SystemShutdown() {
    final System.Shutdown call = new System.Shutdown();
    sendWoResponse(call);
  }

  public void SystemReboot() {
    final System.Reboot call = new System.Reboot();
    sendWoResponse(call);
  }

  public void SystemSuspend() {
    final System.Suspend call = new System.Suspend();
    sendWoResponse(call);
  }

  // -----------------------------------------------------------------------------------

  /**
   * Sends a call to Kodi and waits for the response.<br />
   * Call getResult() / getResults() afterwards
   * 
   * @param call
   *          the call to send
   */
  public void send(AbstractCall<?> call) {
    if (!isConnected()) {
      LOGGER.warn("Kodi RPC: Cannot send RPC call - not connected");
      return;
    }
    try {
      call.setResponse(JsonApiRequest.execute(connectionManager.getHostConfig(), call.getRequest()));
    }
    catch (ApiException e) {
      LOGGER.error("Kodi RPC: Error calling Kodi - '{}'", e.getMessage());
    }
  }

  /**
   * Sends the call to Kodi without waiting for a response (fire and forget)
   * 
   * @param call
   *          the call to send
   */
  public void sendWoResponse(AbstractCall<?> call) {
    if (!isConnected()) {
      LOGGER.warn("Kodi RPC: Cannot send RPC call - not connected");
      return;
    }

    try {
      JsonApiRequest.execute(connectionManager.getHostConfig(), call.getRequest());
    }
    catch (ApiException e) {
      LOGGER.error("Kodi RPC: Error calling Kodi - '{}'", e.getMessage());
    }
  }

  /**
   * Connect to Kodi with specified TCP port
   * 
   * @param config
   *          Host configuration
   * @throws Exception
   *           Throws {@link Exception} when something goes wrong with the initialization of the API.
   */
  public void connect(HostConfig config) throws Exception {
    if (isConnected()) {
      connectionManager.disconnect();
    }

    new Thread(() -> {
      try {
        LOGGER.info("Kodi RPC: Connecting to {}...", config.getAddress());
        connectionManager.connect(config);

        if (isConnected()) {
          this.kodiVersion = getKodiVersion();
          getAndSetVideoDataSources();
          getAndSetAudioDataSources();
          getAndSetMovieMappings();
          getAndSetTvShowMappings();
        }
      }
      catch (Exception e) {
        LOGGER.error("Kodi RPC: Error connecting to Kodi - '{}'", e);
      }
    }).start();
  }

  public void connect() {
    Settings s = Settings.getInstance();
    if (s.getKodiHost().isEmpty()) {
      return;
    }

    try {
      connect(new HostConfig(s.getKodiHost(), s.getKodiHttpPort(), s.getKodiTcpPort(), s.getKodiUsername(), s.getKodiPassword()));
    }
    catch (Exception cex) {
      LOGGER.error("Kodi RPC: Error connecting to Kodi instance - '{}'", cex.getMessage());
      MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, "KodiRPC", "Could not connect to Kodi: " + cex.getMessage()));
    }
  }

  public void disconnect() {
    connectionManager.disconnect();
    this.kodiVersion = "";
  }

  public void updateMovieMappings() {
    if (isConnected()) {
      getAndSetMovieMappings();
    }
  }

  public void updateTvShowMappings() {
    if (isConnected()) {
      getAndSetTvShowMappings();
    }
  }

  /**
   * @return json movie list or NULL
   */
  public List<MovieDetail> getAllMoviesSYNC() {
    final VideoLibrary.GetMovies call = new VideoLibrary.GetMovies(MovieFields.FILE);
    send(call);
    return call.getResults();
  }

  public void getAllMoviesASYNC() {
    // MovieFields.values.toArray(new String[0]) // all values
    final VideoLibrary.GetMovies vl = new VideoLibrary.GetMovies(MovieFields.FILE); // ID & label are always set; just add additional
    connectionManager.call(vl, new ApiCallback<>() {

      @Override
      public void onResponse(AbstractCall<MovieDetail> call) {
        LOGGER.info("Kodi RPC: found " + call.getResults().size() + " movies");
        for (MovieDetail res : call.getResults()) {
          LOGGER.debug(res.toString());
        }
      }

      @Override
      public void onError(int code, String message, String hint) {
        LOGGER.error("Kodi RPC: Error {} - '{}'", code, message);
      }
    });
  }

  /**
   * Forces Kodi to reload movie from NFO
   * 
   * @param movie
   */
  public void triggerReload(Movie movie) {
    // MovieFields.values.toArray(new String[0]) // all values
    final VideoLibrary.GetMovies vl = new VideoLibrary.GetMovies(MovieFields.FILE); // ID & label are always set; just add additional
    connectionManager.call(vl, new ApiCallback<>() {

      @Override
      public void onResponse(AbstractCall<MovieDetail> call) {
        LOGGER.info("Kodi RPC: found " + call.getResults().size() + " movies");
        for (MovieDetail res : call.getResults()) {
          LOGGER.debug(res.toString());
        }
      }

      @Override
      public void onError(int code, String message, String hint) {
        LOGGER.error("Kodi RPC: Error {} - '{}'", code, message);
      }
    });
  }

  public void getAllTvShows() {
    final VideoLibrary.GetTVShows vl = new VideoLibrary.GetTVShows();
    connectionManager.call(vl, new ApiCallback<>() {

      @Override
      public void onResponse(AbstractCall<TVShowDetail> call) {
        LOGGER.info("Kodi RPC: found " + call.getResults().size() + " shows");
        for (TVShowDetail res : call.getResults()) {
          LOGGER.debug(res.toString());
        }
      }

      @Override
      public void onError(int code, String message, String hint) {
        LOGGER.error("Kodi RPC: Error {} - '{}'", code, message);
      }
    });
  }
}
