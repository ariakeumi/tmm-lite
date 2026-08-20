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

package org.tinymediamanager.core.tvshow.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmModuleManager;
import org.tinymediamanager.core.tvshow.BasicTvShowTest;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;

public class TvShowUpdateDatasourceTaskTest extends BasicTvShowTest {
  private static final int NUMBER_OF_EXPECTED_SHOWS      = 19;
  private static final int NUMBER_OF_EXPECTED_EPISODES   = 166;
  private static final int NUMBER_OF_EXPECTED_MEDIAFILES = 414;

  @Before
  public void setup() throws Exception {
    super.setup();

    TmmModuleManager.getInstance().startUp();
    TvShowModuleManager.getInstance().startUp();

    // just a copy; we might have another movie test which uses these files
    copyResourceFolderToWorkFolder("testtvshows");
    TvShowModuleManager.getInstance().getSettings().addTvShowDataSources(getWorkFolder().resolve("testtvshows").toAbsolutePath().toString());
  }

  @After
  public void tearDownAfterTest() throws Exception {
    TvShowModuleManager.getInstance().shutDown();
    TmmModuleManager.getInstance().shutDown();
  }

  @Test
  public void udsNew() throws Exception {
    TvShowUpdateDatasourceTask task = new TvShowUpdateDatasourceTask();
    task.run();

    check();
  }

  @Test
  public void udsFolder() throws Exception {
    TvShowUpdateDatasourceTask task = new TvShowUpdateDatasourceTask(List.of(getWorkFolder().resolve("testtvshows").resolve("Breaking Bad")));
    task.run();

    TvShowList tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    assertThat(tvShowList.getTvShows()).hasSize(1);
    assertThat(tvShowList.getTvShows().get(0).getTitle()).isEqualTo("Breaking Bad");
  }

  @Test
  public void udsNestedDatasource() throws Exception {
    TvShowSettings settings = TvShowModuleManager.getInstance().getSettings();
    TvShowList tvShowList = TvShowModuleManager.getInstance().getTvShowList();

    Path parentDs = getWorkFolder().resolve("testtvshows").toAbsolutePath();
    Path nestedDs = parentDs.resolve("Breaking Bad");

    // adding a nested data source via the settings API must be rejected
    assertThat(settings.addTvShowDataSources(nestedDs.toString())).isFalse();
    assertThat(settings.getTvShowDataSource()).doesNotContain(nestedDs.toString());

    // simulate an already broken setup (nested data source added before the guard existed)
    settings.setTvShowDataSources(List.of(parentDs.toString(), nestedDs.toString()));

    // and a bogus show which has been created by the nested data source on a previous run
    TvShow bogusShow = new TvShow();
    bogusShow.setDataSource(nestedDs.toString());
    bogusShow.setPath(nestedDs.resolve("Season 01").toString());
    bogusShow.setTitle("Season 01");
    tvShowList.addTvShow(bogusShow);

    TvShowUpdateDatasourceTask task = new TvShowUpdateDatasourceTask();
    task.run();

    // the nested data source must not create bogus TV shows from its season folders
    assertThat(tvShowList.getTvShowByPath(nestedDs.resolve("Season 01"))).isNull();
    assertThat(tvShowList.getTvShowByPath(nestedDs.resolve("Season 02"))).isNull();

    // the bogus show from a previous run must have been removed
    assertThat(tvShowList.getTvShows()).noneMatch(tvShow -> nestedDs.toString().equals(tvShow.getDataSource()));

    // the real TV show must still be present (found via the parent data source)
    TvShow show = tvShowList.getTvShowByPath(nestedDs);
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Breaking Bad");
    assertThat(show.getEpisodes().size()).isEqualTo(62);
    assertThat(show.getSeasons().size()).isEqualTo(5);
  }

  @Test
  public void udsNestedDatasourceSingleShowUpdate() throws Exception {
    TvShowSettings settings = TvShowModuleManager.getInstance().getSettings();
    TvShowList tvShowList = TvShowModuleManager.getInstance().getTvShowList();

    // first do a full scan to populate the TV show list
    TvShowUpdateDatasourceTask task = new TvShowUpdateDatasourceTask();
    task.run();

    // verify Breaking Bad exists
    Path parentDs = getWorkFolder().resolve("testtvshows").toAbsolutePath();
    Path nestedDs = parentDs.resolve("Breaking Bad");

    TvShow show = tvShowList.getTvShowByPath(nestedDs);
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Breaking Bad");

    // simulate an already broken setup (nested data source added before the guard existed)
    settings.setTvShowDataSources(List.of(parentDs.toString(), nestedDs.toString()));

    // now update only the single TV show (like TvShowUpdateAction would)
    TvShowUpdateDatasourceTask singleTask = new TvShowUpdateDatasourceTask(List.of(nestedDs));
    singleTask.run();

    // the show must still exist exactly once - no duplication
    assertThat(tvShowList.getTvShows()).hasSizeGreaterThanOrEqualTo(1);

    // Breaking Bad must be present (not duplicated or removed)
    show = tvShowList.getTvShowByPath(nestedDs);
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Breaking Bad");
    assertThat(show.getEpisodes().size()).isEqualTo(62);

    // there must be no duplicate - count shows with the same path
    long count = tvShowList.getTvShows().stream().filter(s -> s.getPathNIO().equals(nestedDs)).count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  public void udsXmlDateAdded() throws Exception {
    TvShowUpdateDatasourceTask task = new TvShowUpdateDatasourceTask(List.of(getWorkFolder().resolve("testtvshows").resolve("XmlDateAdded")));
    task.run();

    TvShowList tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    TvShow show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/XmlDateAdded"));
    assertThat(show).isNotNull();
    assertThat(show.getEpisodes()).hasSize(1);

    TvShowEpisode episode = show.getEpisodes().get(0);
    // the NFO has no <dateadded>, but the .xml file does - must be adopted instead of now()
    Date expectedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2019-01-02 03:04:05");
    assertThat(episode.getDateAdded()).isEqualTo(expectedDate);
  }

  private void check() throws Exception {
    TvShowList tvShowList = TvShowModuleManager.getInstance().getTvShowList();

    // wait until all TV shows have been added (let propertychanges finish)
    for (int i = 0; i < 3; i++) {
      if (tvShowList.getTvShows().size() == NUMBER_OF_EXPECTED_SHOWS) {
        break;
      }

      // not all here yet? wait for a second
      System.out.println("waiting for 1000 ms");
      Thread.sleep(1000);
    }

    int episodeCnt = 0;
    int mfCnt = 0;
    // do some checks before shutting down the database
    for (TvShow show : tvShowList.getTvShows()) {
      System.out.println(show.getPath());
      mfCnt += show.getMediaFiles().size();

      for (TvShowSeason season : show.getSeasons()) {
        mfCnt += season.getMediaFiles().size();
      }

      // check for every found episode that it has at least one VIDEO file
      for (TvShowEpisode episode : show.getEpisodes()) {
        assertThat(episode.getMediaFiles(MediaFileType.VIDEO)).isNotEmpty();
        episodeCnt++;
        mfCnt += episode.getMediaFiles().size();
      }
    }

    assertThat(tvShowList.getTvShows().size()).isEqualTo(NUMBER_OF_EXPECTED_SHOWS);
    assertThat(episodeCnt).isEqualTo(NUMBER_OF_EXPECTED_EPISODES);
    assertThat(mfCnt).isEqualTo(NUMBER_OF_EXPECTED_MEDIAFILES);

    ///////////////////////////////////////////////////////////////////////////////////////
    // Breaking Bad
    ///////////////////////////////////////////////////////////////////////////////////////
    TvShow show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/Breaking Bad"));
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Breaking Bad");
    assertThat(show.getEpisodes().size()).isEqualTo(62);
    assertThat(show.getSeasons().size()).isEqualTo(5);

    List<TvShowSeason> seasons = new ArrayList<>(show.getSeasons());
    // Collections.sort(seasons, seasonComparator);
    Object[] a = seasons.toArray();
    Arrays.sort(a);
    for (int i = 0; i < a.length; i++) {
      seasons.set(i, (TvShowSeason) a[i]);
    }

    assertThat(seasons.get(0).getSeason()).isEqualTo(1);
    assertThat(seasons.get(0).getEpisodes().size()).isEqualTo(7);
    assertThat(seasons.get(1).getSeason()).isEqualTo(2);
    assertThat(seasons.get(1).getEpisodes().size()).isEqualTo(13);
    assertThat(seasons.get(2).getSeason()).isEqualTo(3);
    assertThat(seasons.get(2).getEpisodes().size()).isEqualTo(13);
    assertThat(seasons.get(3).getSeason()).isEqualTo(4);
    assertThat(seasons.get(3).getEpisodes().size()).isEqualTo(13);
    assertThat(seasons.get(4).getSeason()).isEqualTo(5);
    assertThat(seasons.get(4).getEpisodes().size()).isEqualTo(16);

    ///////////////////////////////////////////////////////////////////////////////////////
    // Firefly
    ///////////////////////////////////////////////////////////////////////////////////////
    show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/Firefly"));
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Firefly");
    assertThat(show.getEpisodes().size()).isEqualTo(14);
    assertThat(show.getSeasons().size()).isEqualTo(1);

    seasons = new ArrayList<>(show.getSeasons());
    // Collections.sort(seasons, seasonComparator);
    a = seasons.toArray();
    Arrays.sort(a);
    for (int i = 0; i < a.length; i++) {
      seasons.set(i, (TvShowSeason) a[i]);
    }

    assertThat(seasons.get(0).getSeason()).isEqualTo(1);
    assertThat(seasons.get(0).getEpisodes().size()).isEqualTo(14);

    ///////////////////////////////////////////////////////////////////////////////////////
    // Futurama
    ///////////////////////////////////////////////////////////////////////////////////////
    show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/Futurama (1999)"));
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Futurama");
    assertThat(show.getEpisodes().size()).isEqualTo(44);
    assertThat(show.getSeasons().size()).isEqualTo(5); // 3 with episodes, 2 more with only artwork

    seasons = new ArrayList<>(show.getSeasons());
    // Collections.sort(seasons, seasonComparator);
    a = seasons.toArray();
    Arrays.sort(a);
    for (int i = 0; i < a.length; i++) {
      seasons.set(i, (TvShowSeason) a[i]);
    }

    assertThat(seasons.get(1).getSeason()).isEqualTo(1);
    assertThat(seasons.get(1).getEpisodes().size()).isEqualTo(9);
    assertThat(seasons.get(2).getSeason()).isEqualTo(2);
    assertThat(seasons.get(2).getEpisodes().size()).isEqualTo(20);
    assertThat(seasons.get(3).getSeason()).isEqualTo(3);
    assertThat(seasons.get(3).getEpisodes().size()).isEqualTo(15);

    ///////////////////////////////////////////////////////////////////////////////////////
    // Janosik DVD - show dateAdded must come from the NFO, not now()
    ///////////////////////////////////////////////////////////////////////////////////////
    show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/Janosik DVD"));
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("Janosik");
    Date expectedJanosikDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2017-03-16 23:28:08");
    assertThat(show.getDateAdded()).isEqualTo(expectedJanosikDate);

    ///////////////////////////////////////////////////////////////////////////////////////
    // unknown -1/-1 detection
    ///////////////////////////////////////////////////////////////////////////////////////
    show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/unknown"));
    assertThat(show).isNotNull();
    assertThat(show.getTitle()).isEqualTo("unknown");
    assertThat(show.getEpisodes().size()).isEqualTo(1);
    assertThat(show.getEpisodes().get(0).getSeason() == -1);
    assertThat(show.getEpisodes().get(0).getEpisode() == -1);

    ///////////////////////////////////////////////////////////////////////////////////////
    // Dr. Who
    ///////////////////////////////////////////////////////////////////////////////////////
    show = tvShowList.getTvShowByPath(getWorkFolder().resolve("testtvshows/Dr.Who"));
    assertThat(show).isNotNull();
    assertThat(show.getEpisodes().size()).isEqualTo(1);

    TvShowEpisode episode = show.getEpisodes().get(0);
    assertThat(episode.getSeason()).isEqualTo(1);
    assertThat(episode.getEpisode()).isEqualTo(1);
    assertThat(episode.getMediaFiles(MediaFileType.NFO)).isNotEmpty();
  }
}
