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
import org.tinymediamanager.scraper.thetvdb.entities.EpisodeBaseRecord;
import org.tinymediamanager.scraper.thetvdb.entities.EpisodeExtendedRecord;
import org.tinymediamanager.scraper.thetvdb.entities.Translation;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface EpisodesService {
  /**
   * 
   * Returns episode base record
   * 
   * @param id
   *          id (required)
   * @return Call&lt;EpisodeBaseResponse&gt;
   */
  @GET("episodes/{id}")
  Call<BaseResponse<EpisodeBaseRecord>> getEpisodeBase(@Path("id") long id);

  /**
   * 
   * Returns episode extended record
   * 
   * @param id
   *          id (required)
   * @return Call&lt;EpisodeExtendedResponse&gt;
   */
  @GET("episodes/{id}/extended")
  Call<BaseResponse<EpisodeExtendedRecord>> getEpisodeExtended(@Path("id") long id);

  /**
   * 
   * Returns episode translation record
   * 
   * @param id
   *          id (required)
   * @param language
   *          language (required)
   * @return Call&lt;TranslationResponse&gt;
   */
  @GET("episodes/{id}/translations/{language}")
  Call<BaseResponse<Translation>> getEpisodeTranslation(@Path("id") long id, @Path("language") String language);
}
