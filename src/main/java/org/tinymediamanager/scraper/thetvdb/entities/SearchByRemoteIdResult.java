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
package org.tinymediamanager.scraper.thetvdb.entities;

import org.tinymediamanager.scraper.tmdb.entities.Company;

import com.google.gson.annotations.SerializedName;

public class SearchByRemoteIdResult {

  @SerializedName("series")
  public SeriesBaseRecord  series  = null;
  @SerializedName("people")
  public PeopleBaseRecord  people  = null;
  @SerializedName("movie")
  public MovieBaseRecord   movie   = null;
  @SerializedName("episode")
  public EpisodeBaseRecord episode = null;
  @SerializedName("company")
  public Company           company = null;
}
