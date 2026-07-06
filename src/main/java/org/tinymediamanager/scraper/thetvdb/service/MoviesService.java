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
import org.tinymediamanager.scraper.thetvdb.entities.MovieBaseRecord;
import org.tinymediamanager.scraper.thetvdb.entities.MovieExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.Translation;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface MoviesService {
  /**
   * Returns movie base record
   *
   * @param id
   *          id
   * @return Call&lt;MovieBaseResponse&gt;
   */
  @GET("movies/{id}")
  Call<BaseResponse<MovieBaseRecord>> getMovieBase(@Path("id") long id);

  /**
   * Returns movie extended record
   *
   * @param id
   *          id
   * @return Call&lt;MovieExtendedResponse&gt;
   */
  @GET("movies/{id}/extended")
  Call<BaseResponse<MovieExtendedRecord>> getMovieExtended(@Path("id") long id);

  /**
   * Returns movie translations
   *
   * @param id
   *          id
   * @param language
   *          language
   * @return Call&lt;SeriesTranslationResponse&gt;
   */
  @GET("movies/{id}/translations/{language}")
  Call<BaseResponse<Translation>> getMoviesTranslation(@Path("id") long id, @Path("language") String language);
}
