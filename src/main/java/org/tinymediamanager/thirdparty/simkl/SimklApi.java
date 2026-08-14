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
package org.tinymediamanager.thirdparty.simkl;

import org.tinymediamanager.thirdparty.simkl.model.SimklAccessTokenResponse;
import org.tinymediamanager.thirdparty.simkl.model.SimklActivities;
import org.tinymediamanager.thirdparty.simkl.model.SimklAllItemsResponse;
import org.tinymediamanager.thirdparty.simkl.model.SimklMoviesResponse;
import org.tinymediamanager.thirdparty.simkl.model.SimklPinCodeResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * The Retrofit service interface for the Simkl API.
 *
 * @author Manuel Laggner
 */
interface SimklApi {

  @GET("oauth/pin")
  Call<SimklPinCodeResponse> getPinCode();

  @GET("oauth/pin/{userCode}")
  Call<SimklAccessTokenResponse> pollForToken(@Path("userCode") String userCode);

  @GET("sync/activities")
  Call<SimklActivities> getActivities();

  @GET("sync/all-items/movies/completed")
  Call<SimklMoviesResponse> getCompletedMovies(@Query("date_from") String dateFrom);

  @GET("sync/all-items/movies")
  Call<SimklMoviesResponse> getMovies(@Query("date_from") String dateFrom, @Query("extended") String extended);

  @GET("sync/all-items")
  Call<SimklAllItemsResponse> getAllItems(@Query("date_from") String dateFrom, @Query("extended") String extended,
      @Query("episode_watched_at") String episodeWatchedAt, @Query("include_all_episodes") String includeAllEpisodes,
      @Query("episode_tvdb_id") String episodeTvdbId);

  @POST("sync/history")
  Call<Void> addToHistory(@Body Object body);

  @POST("sync/history/remove")
  Call<Void> removeFromHistory(@Body Object body);
}
