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
package org.tinymediamanager.core.movie.tasks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.TrailerQuality;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaTrailer;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.filenaming.MovieTrailerNaming;
import org.tinymediamanager.core.tasks.TrailerDownloadTask;
import org.tinymediamanager.core.tasks.YtDownloadTask;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.scraper.entities.MediaLanguages;

/**
 * The class {@link MovieTrailerDownloadTask} is used to download the "best" trailer for a movie
 * 
 * @author Manuel Laggner
 */
public class MovieTrailerDownloadTask extends TmmTask {
  private static final Logger            LOGGER       = LoggerFactory.getLogger(MovieTrailerDownloadTask.class);

  private final Movie                    movie;
  private final List<MovieTrailerNaming> trailernames = new ArrayList<>();
  private final TrailerQuality           desiredQuality;
  private final MediaLanguages           desiredLanguage;

  private TmmTask                        task;

  public MovieTrailerDownloadTask(Movie movie) {
    super(TmmResourceBundle.getString("trailer.download") + " - " + movie.getTitle(), 100, TaskType.BACKGROUND_TASK);

    this.movie = movie;

    MovieSettings settings = MovieModuleManager.getInstance().getSettings();

    // store the trailer settings at the start of this task (to do not suffer from changes while the task is running)
    if (movie.isMultiMovieDir()) {
      // in a MMD we can only use this naming
      trailernames.add(MovieTrailerNaming.FILENAME_TRAILER);
    }
    else {
      trailernames.addAll(settings.getTrailerFilenames());
    }

    desiredQuality = settings.getTrailerQuality();
    desiredLanguage = settings.getTrailerLanguage();
  }

  @Override
  protected void doInBackground() {
    Set<MediaTrailer> trailers = new LinkedHashSet<>();

    // prepare the list of desired trailers
    // search for language and quality
    String language = null;
    try {
      Locale locale = desiredLanguage.toLocale();
      if (locale != null) {
        language = locale.getISO3Language();
      }
    }
    catch (Exception e) {
      LOGGER.debug("No valid language chosen for trailer download - '{}'", e.getMessage());
    }
    if (StringUtils.isNotBlank(language)) {
      // search for language and quality
      for (MediaTrailer trailer : movie.getTrailer()) {
        if (language.equals(trailer.getLanguage())) {
          // language match

          // YouTube probably offers all desired qualities (at least for newer trailers)
          if ("youtube".equalsIgnoreCase(trailer.getProvider()) || desiredQuality.containsQuality(trailer.getQuality())) {
            trailers.add(trailer);
          }
        }
      }
    }
    else {
      // only search for quality
      for (MediaTrailer trailer : movie.getTrailer()) {
        // YouTube probably offers all desired qualities (at least for newer trailers)
        if ("youtube".equalsIgnoreCase(trailer.getProvider()) || desiredQuality.containsQuality(trailer.getQuality())) {
          trailers.add(trailer);
        }
      }
    }

    // remove invalid MediaTrailers
    trailers.removeIf(trailer -> {
      String url = trailer.getUrl();
      if (StringUtils.isAllBlank(trailer.getUrl(), trailer.getId())) {
        return true;
      }

      if (url.startsWith("file:")) {
        return true;
      }

      return false;
    });

    if (trailers.isEmpty()) {
      LOGGER.info("No trailers available for '{}'", movie.getTitle());
      return;
    }

    // now try to download the trailers until we get one ;)
    LOGGER.info("Downloading trailer for '{}'", movie.getTitle());

    for (MediaTrailer trailer : trailers) {
      String url = trailer.getUrl();

      if (StringUtils.isBlank(url) && StringUtils.isBlank(trailer.getId())) {
        // no url - no download
        // OR: an IMDB trailer, were we ONLY collect the ID, because we must generate the url on-the-fly
        LOGGER.trace("Trailer has neither an ID, nor an url: {}", trailer);
        continue;
      }

      try {
        LOGGER.debug("try to download trailer '{}'", trailer);

        if (StringUtils.isNotBlank(url) && Utils.YOUTUBE_PATTERN.matcher(url).matches()) {
          task = new YtDownloadTask(trailer, desiredQuality, MovieModuleManager.getInstance().getSettings().isUseYtDlp()) {
            @Override
            protected Path getDestinationWoExtension() {
              return getDestination();
            }

            @Override
            protected MediaEntity getMediaEntityToAdd() {
              return movie;
            }
          };
        }
        else {
          task = new TrailerDownloadTask(trailer) {
            @Override
            protected Path getDestinationWoExtension() {
              return getDestination();
            }

            @Override
            protected MediaEntity getMediaEntityToAdd() {
              return movie;
            }
          };
        }

        if (cancel) {
          return;
        }

        // delegate events
        task.addListener(taskEvent -> {
          setProgressDone(taskEvent.getProgressDone());
          setTaskDescription(taskEvent.getTaskDescription());
          setWorkUnits(taskEvent.getWorkUnits());
          informListeners();
        });

        task.run();

        // check if the task has been finishes successfully
        if (task.getState() == TaskState.FINISHED || task.getState() == TaskState.CANCELLED) {
          break;
        }
      }
      catch (Exception e) {
        LOGGER.debug("could download trailer - {}", e.getMessage());
      }
    }

    LOGGER.info("Finished downloading trailer - took {} ms", getRuntime());
  }

  @Override
  public void cancel() {
    super.cancel();
    if (task != null) {
      task.cancel();
    }
  }

  protected Path getDestination() {
    // hmm... at the moment we can only download ONE trailer, so both patterns won't work
    // just take the first one (or the default if there is no entry whyever)
    String filename;
    if (!trailernames.isEmpty()) {
      filename = movie.getTrailerFilename(trailernames.get(0));
    }
    else {
      filename = movie.getTrailerFilename(MovieTrailerNaming.FILENAME_TRAILER);
    }

    // DVD/BluRay folders can have trailers within!
    // check for discFolders and/or files
    final Path outputFolder;
    if (movie.isDisc() && MovieModuleManager.getInstance().getSettings().isTrailerDiscFolderInside()) {
      if (MediaFileHelper.isDiscFolder(movie.getMainFile().getFilename())) {
        outputFolder = movie.getMainFile().getFileAsPath();
      }
      else {
        outputFolder = movie.getPathNIO(); // not a virtual "MF folder"? use default
      }
    }
    else {
      outputFolder = movie.getPathNIO(); // default
    }

    return outputFolder.resolve(filename);
  }
}
