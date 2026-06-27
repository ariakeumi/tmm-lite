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
package org.tinymediamanager.core.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.entities.MovieSet;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.scraper.util.UrlUtil;

/**
 * The class {@link ImageCacheCleanupTask} is used to clean up the image cache by removing cached files which are not referenced from any entity in
 * the library anymore. It checks all movies, TV shows, seasons, episodes, movie sets and their persons for referenced images and removes orphaned
 * cache files.
 *
 * @author Manuel Laggner
 */
public class ImageCacheCleanupTask extends TmmTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageCacheCleanupTask.class);

  public ImageCacheCleanupTask() {
    super(TmmResourceBundle.getString("tmm.cleanupimagecache"), 0, TaskType.BACKGROUND_TASK);
  }

  @Override
  protected void doInBackground() {
    Set<Path> cachedFiles = new HashSet<>();

    // 1. read all files from the image cache
    Path cacheDir = ImageCache.getCacheDir();
    if (!Files.exists(cacheDir)) {
      return;
    }

    try (Stream<Path> walk = Files.walk(cacheDir)) {
      walk.filter(Files::isRegularFile).forEach(cachedFiles::add);
    }
    catch (IOException e) {
      LOGGER.warn("Failed to walk image cache directory", e);
      return;
    }

    if (cachedFiles.isEmpty()) {
      return;
    }

    setWorkUnits(cachedFiles.size());
    LOGGER.debug("Found {} files in image cache", cachedFiles.size());

    Set<Path> referencedFiles = new HashSet<>();

    // 2. check all movies
    for (Movie movie : MovieModuleManager.getInstance().getMovieList().getMovies()) {
      if (cancel) {
        break;
      }

      checkEntity(movie, referencedFiles);
      for (Person person : movie.getActors()) {
        checkPerson(person, referencedFiles);
      }
      for (Person person : movie.getCrew()) {
        checkPerson(person, referencedFiles);
      }
    }

    if (cancel) {
      return;
    }

    // 3. check all movie sets
    for (MovieSet movieSet : MovieModuleManager.getInstance().getMovieList().getMovieSetList()) {
      if (cancel)
        break;

      checkEntity(movieSet, referencedFiles);
    }

    if (cancel) {
      return;
    }

    // 4. check all TV shows
    for (TvShow tvShow : TvShowModuleManager.getInstance().getTvShowList().getTvShows()) {
      if (cancel) {
        break;
      }

      checkEntity(tvShow, referencedFiles);
      for (Person person : tvShow.getActors()) {
        checkPerson(person, referencedFiles);
      }
      for (Person person : tvShow.getCrew()) {
        checkPerson(person, referencedFiles);
      }

      // 4a. check seasons
      for (TvShowSeason season : tvShow.getSeasons()) {
        if (cancel) {
          break;
        }

        checkEntity(season, referencedFiles);
      }

      // 4b. check episodes
      for (TvShowEpisode episode : tvShow.getEpisodes()) {
        if (cancel) {
          break;
        }

        checkEntity(episode, referencedFiles);
        for (Person person : episode.getActors()) {
          checkPerson(person, referencedFiles);
        }
        for (Person person : episode.getCrew()) {
          checkPerson(person, referencedFiles);
        }
      }
    }

    if (cancel) {
      return;
    }

    // 5. remove unreferenced cache files
    int removed = 0;
    for (Path file : cachedFiles) {
      if (cancel) {
        break;
      }

      if (!referencedFiles.contains(file)) {
        if (Utils.deleteFileSafely(file)) {
          removed++;
        }
      }
    }

    LOGGER.info("Cleaned up image cache - removed {} unreferenced files", removed);
  }

  /**
   * Checks all media files and artwork URLs of the given entity and adds their expected cache paths to the referenced set.
   *
   * @param entity
   *          the media entity to check
   * @param referencedFiles
   *          the set of referenced cache files
   */
  private void checkEntity(MediaEntity entity, Set<Path> referencedFiles) {
    // check all graphic media files
    for (MediaFile mf : entity.getMediaFiles()) {
      if (mf.isGraphic()) {
        addReferencedFile(referencedFiles, mf);
      }
    }

    // check all artwork URLs
    for (String url : entity.getArtworkUrls().values()) {
      addReferencedUrl(referencedFiles, url);
    }
  }

  /**
   * Checks the person's image URLs and adds their expected cache paths to the referenced set.
   *
   * @param person
   *          the person to check
   * @param referencedFiles
   *          the set of referenced cache files
   */
  private void checkPerson(Person person, Set<Path> referencedFiles) {
    addReferencedUrl(referencedFiles, person.getThumbUrl());
  }

  /**
   * Computes the expected cache path for the given media file and adds it to the referenced set.
   *
   * @param referencedFiles
   *          the set of referenced cache files
   * @param mf
   *          the media file
   */
  private void addReferencedFile(Set<Path> referencedFiles, MediaFile mf) {
    Path path = mf.getFileAsPath();
    if (path == null) {
      return;
    }

    String md5WithSubfolder = ImageCache.getMD5WithSubfolder(path.toString());
    if (md5WithSubfolder != null) {
      referencedFiles.add(ImageCache.getCacheDir().resolve(md5WithSubfolder + "." + mf.getExtension()));
    }
  }

  /**
   * Computes the expected cache path for the given URL and adds it to the referenced set.
   *
   * @param referencedFiles
   *          the set of referenced cache files
   * @param url
   *          the URL
   */
  private void addReferencedUrl(Set<Path> referencedFiles, String url) {
    if (StringUtils.isBlank(url)) {
      return;
    }

    String ext = UrlUtil.getExtension(url);
    if (ext.isEmpty()) {
      ext = "jpg";
    }

    String md5WithSubfolder = ImageCache.getMD5WithSubfolder(url);
    if (md5WithSubfolder != null) {
      referencedFiles.add(ImageCache.getCacheDir().resolve(md5WithSubfolder + "." + ext));
    }
  }
}
