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

import org.tinymediamanager.scraper.thetvdb.entities.ArtworkTypeRecord;
import org.tinymediamanager.scraper.thetvdb.entities.BaseResponseList;
import org.tinymediamanager.scraper.thetvdb.entities.ContentRating;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ConfigService {
  /**
   * Returns all artwork types
   *
   * @return Call&lt;ArtworkTypeResponse&gt;
   */
  @GET("artwork/types")
  Call<BaseResponseList<ArtworkTypeRecord>> getArtworkTypes();

  /**
   * Returns all rating types
   *
   * @return Call&lt;ContentRatingResponse&gt;
   */
  @GET("content/ratings")
  Call<BaseResponseList<ContentRating>> getCertifications();
}
