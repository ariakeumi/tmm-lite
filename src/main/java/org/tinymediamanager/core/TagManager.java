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
package org.tinymediamanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;

/**
 * The class {@link TagManager} provides centralized tag management operations for any collection of {@link MediaEntity} instances. It supports
 * querying, deleting, and renaming tags across movies, TV shows, episodes, or any combination.
 * <p>
 * Use the factory methods {@link #forMovies()}, {@link #forTvShows()} or {@link #forEpisodes()} to create an instance bound to a specific entity
 * source.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TagManager {

  private static final Logger                         LOGGER = LoggerFactory.getLogger(TagManager.class);

  private final Supplier<List<? extends MediaEntity>> entitySupplier;

  /**
   * Creates a new {@link TagManager} backed by the given entity supplier.
   *
   * @param entitySupplier
   *          supplies the live list of entities to manage tags on
   */
  private TagManager(Supplier<List<? extends MediaEntity>> entitySupplier) {
    this.entitySupplier = Objects.requireNonNull(entitySupplier, "entitySupplier must not be null");
  }

  /**
   * Returns a {@link TagManager} for all movies.
   *
   * @return the tag manager
   */
  public static TagManager forMovies() {
    return new TagManager(() -> MovieModuleManager.getInstance().getMovieList().getMovies());
  }

  /**
   * Returns a {@link TagManager} for all TV shows.
   *
   * @return the tag manager
   */
  public static TagManager forTvShows() {
    return new TagManager(() -> TvShowModuleManager.getInstance().getTvShowList().getTvShows());
  }

  /**
   * Returns a {@link TagManager} for all TV show episodes.
   *
   * @return the tag manager
   */
  public static TagManager forEpisodes() {
    return new TagManager(() -> {
      List<MediaEntity> episodes = new ArrayList<>();
      for (var tvShow : TvShowModuleManager.getInstance().getTvShowList().getTvShows()) {
        episodes.addAll(tvShow.getEpisodes());
      }
      return episodes;
    });
  }

  /**
   * Returns all tags with their usage count, sorted by count descending then alphabetically.
   *
   * @return list of {@link TagInfo}
   */
  public List<TagInfo> getAllTags() {
    List<? extends MediaEntity> entities = entitySupplier.get();

    return entities.stream()
        .flatMap(e -> e.getTags().stream())
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()))
        .entrySet()
        .stream()
        .map(entry -> new TagInfo(entry.getKey(), entry.getValue().intValue()))
        .sorted(Comparator.comparingInt(TagInfo::count).reversed().thenComparing(TagInfo::tag))
        .collect(Collectors.toList());
  }

  /**
   * Returns all entities that have the given tag (case-insensitive lookup).
   *
   * @param tag
   *          the tag to search for
   * @return list of matching entities
   */
  public List<MediaEntity> getUsages(String tag) {
    if (StringUtils.isBlank(tag)) {
      return Collections.emptyList();
    }

    List<? extends MediaEntity> entities = entitySupplier.get();
    List<MediaEntity> result = new ArrayList<>();

    for (MediaEntity entity : entities) {
      for (String t : entity.getTags()) {
        if (tag.equalsIgnoreCase(t)) {
          result.add(entity);
          break;
        }
      }
    }

    return result;
  }

  /**
   * Returns the number of entities that have the given tag.
   *
   * @param tag
   *          the tag to count
   * @return the usage count
   */
  public int getTagCount(String tag) {
    return getUsages(tag).size();
  }

  /**
   * Removes the given tag from all entities that have it (case-insensitive match). Each modified entity is saved to the database.
   *
   * @param tag
   *          the tag to remove
   */
  public void deleteTag(String tag) {
    if (StringUtils.isBlank(tag)) {
      return;
    }

    deleteTags(List.of(tag));
  }

  /**
   * Removes all given tags from all entities (case-insensitive match) in a single pass. More efficient than calling {@link #deleteTag(String)}
   * repeatedly.
   *
   * @param tags
   *          the tags to remove
   */
  public void deleteTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return;
    }

    List<? extends MediaEntity> entities = entitySupplier.get();
    int totalRemoved = 0;

    for (MediaEntity entity : entities) {
      boolean changed = false;
      List<String> toRemove = new ArrayList<>();
      for (String t : entity.getTags()) {
        for (String tag : tags) {
          if (tag.equalsIgnoreCase(t)) {
            toRemove.add(t);
            break;
          }
        }
      }
      for (String t : toRemove) {
        entity.removeFromTags(t);
        changed = true;
      }
      if (changed) {
        entity.saveToDb();
        entity.writeNFO();
        totalRemoved += toRemove.size();
      }
    }

    LOGGER.info("Deleted tags {} from {} entities ({} occurrences)", tags, totalRemoved > 0 ? "some" : "no", totalRemoved);
  }

  /**
   * Renames a tag across all entities (case-insensitive source match). If an entity already has the new tag, the old tag is simply removed (no
   * duplicate created). Each modified entity is saved to the database.
   *
   * @param oldTag
   *          the current tag name
   * @param newTag
   *          the desired tag name
   */
  public void renameTag(String oldTag, String newTag) {
    if (StringUtils.isBlank(oldTag) || StringUtils.isBlank(newTag)) {
      return;
    }
    if (oldTag.equalsIgnoreCase(newTag)) {
      return;
    }

    List<? extends MediaEntity> entities = entitySupplier.get();
    int renamedCount = 0;

    for (MediaEntity entity : entities) {
      boolean hasOld = false;
      boolean hasNew = false;
      for (String t : entity.getTags()) {
        if (oldTag.equalsIgnoreCase(t)) {
          hasOld = true;
        }
        if (newTag.equalsIgnoreCase(t)) {
          hasNew = true;
        }
      }

      if (!hasOld) {
        continue;
      }

      // remove the old tag
      List<String> toRemove = new ArrayList<>();
      for (String t : entity.getTags()) {
        if (oldTag.equalsIgnoreCase(t)) {
          toRemove.add(t);
        }
      }
      toRemove.forEach(entity::removeFromTags);

      // add the new tag if not already present
      if (!hasNew) {
        entity.addToTags(List.of(newTag));
      }

      entity.saveToDb();
      entity.writeNFO();
      renamedCount++;
    }

    LOGGER.info("Renamed tag '{}' to '{}' on {} entities", oldTag, newTag, renamedCount);
  }

  /**
   * Renames all given old tags to the same new tag across all entities (case-insensitive source match). Each modified entity is saved to the
   * database. This effectively merges multiple tags into one.
   *
   * @param oldTags
   *          the tags to rename
   * @param newTag
   *          the target tag name
   */
  public void renameTags(List<String> oldTags, String newTag) {
    if (oldTags == null || oldTags.isEmpty() || StringUtils.isBlank(newTag)) {
      return;
    }

    for (String oldTag : oldTags) {
      if (StringUtils.isNotBlank(oldTag) && !oldTag.equalsIgnoreCase(newTag)) {
        renameTag(oldTag, newTag);
      }
    }
  }

  /**
   * A simple record holding a tag name and its usage count.
   *
   * @param tag
   *          the tag name
   * @param count
   *          how many entities use this tag
   */
  public record TagInfo(String tag, int count) implements Comparable<TagInfo> {
    @Override
    public int compareTo(@NonNull TagInfo other) {
      return tag.compareTo(other.tag);
    }
  }
}
