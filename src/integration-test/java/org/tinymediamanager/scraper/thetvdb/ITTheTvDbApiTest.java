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
package org.tinymediamanager.scraper.thetvdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.core.BasicITest;
import org.tinymediamanager.scraper.entities.CountryCode;
import org.tinymediamanager.scraper.entities.MediaCertification;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.thetvdb.entities.ArtworkTypeRecord;
import org.tinymediamanager.scraper.thetvdb.entities.BaseResponse;
import org.tinymediamanager.scraper.thetvdb.entities.BaseResponseArray;
import org.tinymediamanager.scraper.thetvdb.entities.BaseResponseList;
import org.tinymediamanager.scraper.thetvdb.entities.ContentRating;
import org.tinymediamanager.scraper.thetvdb.entities.EpisodeBaseRecord;
import org.tinymediamanager.scraper.thetvdb.entities.EpisodeExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.MovieExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SearchByRemoteIdResult;
import org.tinymediamanager.scraper.thetvdb.entities.SearchResultRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SearchType;
import org.tinymediamanager.scraper.thetvdb.entities.SeasonBaseRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SeasonExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SeasonType;
import org.tinymediamanager.scraper.thetvdb.entities.SeriesBaseRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SeriesEpisodesRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SeriesExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.Translation;

import retrofit2.Response;

public class ITTheTvDbApiTest extends BasicITest {
  private TheTvDbController theTvDbController;

  @Before
  public void setup() throws Exception {
    super.setup();
    TheTvDbMetadataProvider md = new TheTvDbTvShowMetadataProvider();

    theTvDbController = new TheTvDbController();
    theTvDbController.setAuthToken(md.getAuthToken());
  }

  @Test
  public void matchCerts() throws ScrapeException, IOException {
    TheTvDbMovieMetadataProvider mp = new TheTvDbMovieMetadataProvider();
    mp.initAPI();
    int cntCerts = 0;
    int unknownCerts = 0;

    Response<BaseResponseList<ContentRating>> response = mp.tvdb.getConfigService().getCertifications().execute();
    if (response.isSuccessful()) {
      for (ContentRating cert : response.body().data) {
        cntCerts++;
        // sanity checks
        CountryCode cc = CountryCode.getByCode(cert.country);
        if (cc == null) {
          System.out.println("Country not found: " + cert.country);
        }
        if (cert.fullname != null && !cert.name.equalsIgnoreCase(cert.fullname)) {
          System.out.println(cc.getAlpha2() + ": name differs: " + cert.name + " - " + cert.fullname);
        }
        if (!cert.contentType.equalsIgnoreCase("movie") && !cert.contentType.equalsIgnoreCase("episode")) {
          System.out.println("contentType no yet known :)" + cert.contentType);
        }

        // now check for TMM match
        MediaCertification tmmCert = MediaCertification.getCertification(cc.getAlpha2(), cert.name);
        if (tmmCert == MediaCertification.UNKNOWN) {
          unknownCerts++;
          System.out.println("Did not find a TMM cert for TVDBs " + cc.getAlpha2() + " - " + cert.name);
        }
      }
    }

    System.out.println("Found " + cntCerts + " certs in TVDB, could match " + (cntCerts - unknownCerts) + " to TMM (missing " + unknownCerts + ")");
    System.out.println();

  }

  @Test
  public void testAllSeries() throws Exception {
    BaseResponseList<SeriesBaseRecord> response = theTvDbController.getSeriesService().getAllSeries(1).execute().body();
    assertThat(response.data).isNotEmpty();
  }

  @Test
  public void testSeriesBase() throws Exception {
    BaseResponse<SeriesBaseRecord> response = theTvDbController.getSeriesService().getSeriesBase(79335).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.id).isEqualTo(79335);
    assertThat(response.data.name).isEqualTo("Psych");
  }

  @Test
  public void testSeriesExtended() throws Exception {
    BaseResponse<SeriesExtendedRecord> response = theTvDbController.getSeriesService().getSeriesExtended(79335).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.id).isEqualTo(79335);
    assertThat(response.data.name).isEqualTo("Psych");
  }

  @Test
  public void testSeriesEpisodes() throws Exception {
    BaseResponse<SeriesEpisodesRecord> response = theTvDbController.getSeriesService()
        .getSeriesEpisodes(79335, SeasonType.DEFAULT, 0)
        .execute()
        .body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.series).isNotNull();
    assertThat(response.data.series.id).isEqualTo(79335);
    assertThat(response.data.series.name).isEqualTo("Psych");
    assertThat(response.data.episodes).isNotEmpty();
  }

  @Test
  public void testSeasonsBase() throws Exception {
    BaseResponse<SeasonBaseRecord> response = theTvDbController.getSeasonsService().getSeasonBase(16284).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.number).isEqualTo(1);
    assertThat(response.data.seriesId).isEqualTo(79335);
  }

  @Test
  public void testSeasonsExtended() throws Exception {
    BaseResponse<SeasonExtendedRecord> response = theTvDbController.getSeasonsService().getSeasonExtended(16284).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.number).isEqualTo(1);
    assertThat(response.data.seriesId).isEqualTo(79335);
    assertThat(response.data.episodes).isNotEmpty();
    assertThat(response.data.episodes.get(0).episodeNumber).isEqualTo(1);
    assertThat(response.data.episodes.get(0).seasonNumber).isEqualTo(1);
  }

  @Test
  public void testEpisodesBase() throws Exception {
    BaseResponse<EpisodeBaseRecord> response = theTvDbController.getEpisodesService().getEpisodeBase(307497).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.episodeNumber).isEqualTo(1);
    assertThat(response.data.seasonNumber).isEqualTo(1);
  }

  @Test
  public void testEpisodesExtended() throws Exception {
    BaseResponse<EpisodeExtendedRecord> response = theTvDbController.getEpisodesService().getEpisodeExtended(307497).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.episodeNumber).isEqualTo(1);
    assertThat(response.data.seasonNumber).isEqualTo(1);
  }

  @Test
  public void testSearchSeries() throws Exception {
    BaseResponseList<SearchResultRecord> response = theTvDbController.getSearchService().getSearch("Psych", SearchType.SERIES).execute().body();
    assertThat(response.data).isNotEmpty();
    SearchResultRecord first = response.data.get(0);
    assertThat(first.name).isEqualTo("Psych");
    assertThat(first.tvdbId).isEqualTo("79335");
    assertThat(first.type).isEqualTo("series");
  }

  @Test
  public void testSearchMovie() throws Exception {
    BaseResponseList<SearchResultRecord> response = theTvDbController.getSearchService().getSearch("12 Monkeys", SearchType.MOVIE).execute().body();
    assertThat(response.data).isNotEmpty();
    SearchResultRecord first = response.data.get(0);
    assertThat(first.name).contains("12 Monkeys");
    assertThat(first.type).isEqualTo("movie");
  }

  @Test
  public void testRemoteIdSearch() throws Exception {
    BaseResponseArray<SearchByRemoteIdResult> response = theTvDbController.getSearchService().remoteIdSearch("tt0491738").execute().body();
    assertThat(response.data).isNotEmpty();
    // Psych should be found via its IMDB ID
    boolean found = false;
    for (SearchByRemoteIdResult r : response.data) {
      if (r.series != null && r.series.id == 79335) {
        found = true;
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  public void testSeriesTranslation() throws Exception {
    BaseResponse<Translation> response = theTvDbController.getSeriesService().getSeriesTranslation(79335, "deu").execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.name).isNotEmpty();
    assertThat(response.data.language).isEqualTo("deu");
  }

  @Test
  public void testSeriesEpisodesWithLanguage() throws Exception {
    BaseResponse<SeriesEpisodesRecord> response = theTvDbController.getSeriesService()
        .getSeriesEpisodes(79335, SeasonType.DEFAULT, "deu", 0)
        .execute()
        .body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.episodes).isNotEmpty();
    // episodes should have German data (language parameter was passed)
    assertThat(response.data.episodes.get(0).episodeNumber).isGreaterThan(0);
  }

  @Test
  public void testSeasonTranslation() throws Exception {
    BaseResponse<Translation> response = theTvDbController.getSeasonsService().getSeasonTranslation(16284, "deu").execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.name).isBlank(); // no translation available
    assertThat(response.data.language).isEqualTo("deu");
  }

  @Test
  public void testMovieExtended() throws Exception {
    BaseResponse<MovieExtendedRecord> response = theTvDbController.getMoviesService().getMovieExtended(706).execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.name).isEqualTo("12 Monkeys");
    assertThat(response.data.id).isEqualTo(706);
    assertThat(response.data.genres).isNotEmpty();
    assertThat(response.data.remoteIds).isNotEmpty();
  }

  @Test
  public void testMoviesTranslation() throws Exception {
    BaseResponse<Translation> response = theTvDbController.getMoviesService().getMoviesTranslation(706, "deu").execute().body();
    assertThat(response.data).isNotNull();
    assertThat(response.data.name).isNotEmpty();
    assertThat(response.data.language).isEqualTo("deu");
  }

  @Test
  public void testArtworkTypes() throws Exception {
    BaseResponseList<ArtworkTypeRecord> response = theTvDbController.getConfigService().getArtworkTypes().execute().body();
    assertThat(response.data).isNotEmpty();
    assertThat(response.data).allMatch(t -> t.id > 0 && t.name != null && !t.name.isEmpty());
  }
}
