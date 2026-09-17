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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.threading.TmmTask;

/**
 * the class {@link CleanUpUnwantedFilesTask} searches for unwanted files (matched by the configured cleanup file types) in the folders of the given
 * entities and deletes them (or just logs them when running in dry mode)
 *
 * @author Manuel Laggner
 */
public class CleanUpUnwantedFilesTask extends TmmTask {
  private static final Logger     LOGGER = LoggerFactory.getLogger(CleanUpUnwantedFilesTask.class);

  private final List<MediaEntity> entities;
  private final boolean           dryRun;

  public CleanUpUnwantedFilesTask(List<? extends MediaEntity> entities, boolean dryRun) {
    super(TmmResourceBundle.getString("cleanupfiles"), entities.size(), TaskType.MAIN_TASK);
    this.entities = new ArrayList<>(entities);
    this.dryRun = dryRun;
  }

  @Override
  protected void doInBackground() {
    // Get Cleanup File Types from the settings
    List<String> regexPatterns = Settings.getInstance().getCleanupFileType();
    LOGGER.info("Start cleanup of unwanted file types: {}", regexPatterns);

    entities.sort(Comparator.comparing(MediaEntity::getTitle));

    Set<Path> handledFiles = new HashSet<>();
    int i = 0;

    for (MediaEntity entity : entities) {
      Path root = entity.getPathNIO();
      if (root == null) {
        continue;
      }

      for (Path file : Utils.getUnknownFilesByRegex(root, regexPatterns)) {
        if (root.equals(file)) {
          // do not remove the whole root of the media entity
          continue;
        }

        // entities can share the same folder (e.g. TV shows/episodes) - handle every file just once
        if (!handledFiles.add(file)) {
          continue;
        }

        if (dryRun) {
          LOGGER.info("Would delete unwanted file - {}", file);
        }
        else {
          delete(file, entity);
        }

        publishState(++i);
        if (cancel) {
          return;
        }
      }
    }
  }

  private void delete(Path file, MediaEntity entity) {
    try {
      if (Files.isDirectory(file)) {
        LOGGER.debug("Deleting folder - {}", file);
        Utils.deleteDirectoryRecursive(file);
      }
      else {
        MediaFile mf = new MediaFile(file);
        if (mf.getType() == MediaFileType.VIDEO) {
          // prevent from doing something stupid
          LOGGER.warn("Refusing to delete video file - {}", file);
          return;
        }

        LOGGER.debug("Deleting file - {}", file);
        Utils.deleteFileWithBackup(file, entity.getDataSource());
        if (entity.getMediaFiles().contains(mf)) {
          entity.removeFromMediaFiles(mf);
          entity.saveToDb();
        }
      }
    }
    catch (Exception e) {
      LOGGER.error("Could not delete '{}' - '{}'", file, e.getMessage());
    }
  }
}
