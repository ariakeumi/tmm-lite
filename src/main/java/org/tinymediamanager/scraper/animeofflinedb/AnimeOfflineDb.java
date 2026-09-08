package org.tinymediamanager.scraper.animeofflinedb;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.scraper.http.OnDiskCachedUrl;
import org.tinymediamanager.scraper.http.Url;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class AnimeOfflineDb {
  private static final Logger   LOGGER    = LoggerFactory.getLogger(AnimeOfflineDb.class);
  private static final String   JSON_FILE = "anime-offline-database.jsonl";
  private static AnimeOfflineDb instance;
  private List<String>          ids       = new ArrayList<String>();

  private AnimeOfflineDb() {
  }

  public static synchronized AnimeOfflineDb getInstance() {
    if (instance == null) {
      instance = new AnimeOfflineDb();
      instance.initDataset();
    }
    return instance;
  }

  private void initDataset() {
    Path databaseFile = Path.of(Globals.CACHE_FOLDER, JSON_FILE);
    long cut = LocalDateTime.now().minusMonths(1).toEpochSecond(ZoneOffset.UTC);
    try {
      if (!Files.exists(databaseFile) || Files.getLastModifiedTime(databaseFile).to(TimeUnit.SECONDS) < cut) {
        LOGGER.debug("Preparing AnimeOfflineDB dataset...");

        String tagName = "";
        // get Github release tag
        try {
          Url u = new OnDiskCachedUrl("https://api.github.com/repos/manami-project/anime-offline-database/releases/latest", 14, TimeUnit.DAYS);
          ObjectMapper mapper = new ObjectMapper();
          JsonNode node = mapper.readTree(u.getInputStream());
          tagName = node.get("tag_name").asText();
        }
        catch (Exception e) {
          LOGGER.debug("Getting tag failed; fallback to known tag - {}", e.getMessage());
          tagName = "2026-27"; // fallback to a known tag/week; abandoned; use latest available
        }

        // not need to cache by OkHttp
        Url u = new Url("https://github.com/manami-project/anime-offline-database/releases/download/" + tagName + "/" + JSON_FILE);
        boolean ok = u.download(databaseFile);
        if (ok) {
          loadJsonL(JSON_FILE);
        }
        else {
          if (Files.exists(databaseFile)) {
            LOGGER.debug("download failed - using old copy");
            loadJsonL(JSON_FILE);
          }
        }
      }
      else {
        LOGGER.debug("Dataset young enough, no need to download...");
        // just open it!
        loadJsonL(JSON_FILE);
      }
    }
    catch (Exception e) {
      LOGGER.debug("Error initializing dataset: {} - {}", databaseFile, e.getMessage());
      Utils.deleteFileSafely(databaseFile);
    }
  }

  private void loadJsonL(String file) throws Exception {
    LOGGER.debug("Loading from file...");
    JsonMapper mapper = new JsonMapper();
    try (MappingIterator<AnimeOfflineDbJsonEntity> it = mapper.readerFor(AnimeOfflineDbJsonEntity.class)
        .readValues(new File(Globals.CACHE_FOLDER, file))) {
      while (it.hasNextValue()) {
        AnimeOfflineDbJsonEntity entity = it.nextValue();
        if (entity.title == null || entity.title.isEmpty()) {
          continue; // skip empty titles
        }
        ids.add(parseIds(entity));
      }
    }
    LOGGER.debug("Loaded {} entries from file: {}", ids.size(), file);
  }

  // for simpler storage, our IDs are stored in a single string, separated by |
  // Do the reverse and return a map of key/values
  private String parseIds(AnimeOfflineDbJsonEntity anime) {
    List<String> ids = new ArrayList<>();
    for (String id : anime.sources) {
      URI u = URI.create(id);
      String h = u.getHost();
      h = h.substring(0, h.lastIndexOf('.'));
      h = h.replaceAll("[^a-zA-Z0-9]", "");
      h = h.strip();

      try {
        int num = Integer.parseInt(id.substring(id.lastIndexOf('/') + 1));
        ids.add(h + ":" + num);
      }
      catch (Exception e) {
        String num = "";
        if (h.contains("animeplanet")) { // already stripped dash in hostname!
          num = id.substring(id.lastIndexOf('/') + 1);
        }
        if (h.contains("notify")) {
          num = id.substring(id.lastIndexOf('/') + 1);
        }
        if (h.contains("animenewsnetwork")) {
          num = id.substring(id.lastIndexOf('=') + 1);
        }
        ids.add(h + ":" + num);
      }
    }
    return String.join("|", ids) + "|"; // last entry ALSO needs a delimiter!
  }

  /**
   * Returns our ID map, where the key/value is found
   * 
   * @param id
   *          the ID to search for (anidb, tmdb, imdb, ...)
   * @param value
   *          the value of the ID to search for
   * @return matching IDs or empty map
   */
  public Map<String, Object> getIdsFor(String id, String value) {
    Map<String, Object> ret = Collections.emptyMap();

    Optional<String> result = ids.stream().filter(s -> s.contains(id + ":" + value + "|")).findFirst();
    if (result.isPresent()) {
      ret = stringToMap(result.get());
    }

    return ret;
  }

  private Map<String, Object> stringToMap(String str) {
    Map<String, Object> map = new HashMap<>();
    String[] pairs = str.split("\\|");
    for (String pair : pairs) {
      String[] keyValue = pair.split(":");
      if (keyValue.length == 2) {
        try {
          int num = Integer.parseInt(keyValue[1]);
          map.put(keyValue[0], num);

        }
        catch (Exception e) {
          map.put(keyValue[0], keyValue[1]);
        }
      }
    }
    return map;
  }
}
