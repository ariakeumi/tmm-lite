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

package org.tinymediamanager.scraper.thetvdb.service;

import org.tinymediamanager.scraper.thetvdb.entities.BaseResponse;
import org.tinymediamanager.scraper.thetvdb.entities.BaseResponseList;
import org.tinymediamanager.scraper.thetvdb.entities.SeasonType;
import org.tinymediamanager.scraper.thetvdb.entities.SeriesBaseRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SeriesEpisodesRecord;
import org.tinymediamanager.scraper.thetvdb.entities.SeriesExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.Translation;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SeriesService {
  /**
   * 
   * returns list of series base records
   * 
   * @param page
   *          page number (optional)
   * @return Call&lt;AllSeriesResponse&gt;
   */
  @GET("series")
  Call<BaseResponseList<SeriesBaseRecord>> getAllSeries(@Query("page") long page);

  /**
   * 
   * Returns series base record
   * 
   * @param id
   *          id
   * @return Call&lt;SeriesBaseResponse&gt;
   */
  @GET("series/{id}")
  Call<BaseResponse<SeriesBaseRecord>> getSeriesBase(@Path("id") long id);

  /**
   * 
   * Returns series extended record
   * 
   * @param id
   *          id
   * @return Call&lt;SeriesExtendedResponse&gt;
   */
  @GET("series/{id}/extended")
  Call<BaseResponse<SeriesExtendedRecord>> getSeriesExtended(@Path("id") long id);

  /**
   *
   * Returns series episodes
   *
   * @param id
   *          id
   * @param seasonType
   *          the season type
   * @param page
   *          the page
   * @return Call&lt;SeriesExtendedResponse&gt;
   */
  @GET("series/{id}/episodes/{season-type}")
  Call<BaseResponse<SeriesEpisodesRecord>> getSeriesEpisodes(@Path("id") long id, @Path("season-type") SeasonType seasonType,
      @Query("page") long page);

  /**
   *
   * Returns series episodes with translated data
   *
   * @param id
   *          id
   * @param seasonType
   *          the season type
   * @param page
   *          the page
   * @param language
   *          the language
   * @return Call&lt;SeriesExtendedResponse&gt;
   */
  @GET("series/{id}/episodes/{season-type}/{lang}")
  Call<BaseResponse<SeriesEpisodesRecord>> getSeriesEpisodes(@Path("id") long id, @Path("season-type") SeasonType seasonType,
      @Path("lang") String language, @Query("page") long page);

  /**
   * 
   * Returns series translation record
   * 
   * @param id
   *          id
   * @param language
   *          language
   * @return Call&lt;SeriesTranslationResponse&gt;
   */
  @GET("series/{id}/translations/{language}")
  Call<BaseResponse<Translation>> getSeriesTranslation(@Path("id") long id, @Path("language") String language);
}
