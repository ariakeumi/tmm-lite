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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.ReleaseInfo;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.TmmStore;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.license.TmmFeature;
import org.tinymediamanager.scraper.http.TmmHttpClient;
import org.tinymediamanager.thirdparty.simkl.model.SimklAccessTokenResponse;
import org.tinymediamanager.thirdparty.simkl.model.SimklPinCodeResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Sync your watched status with Simkl.com<br>
 * Using PIN auth flow according to https://api.simkl.org/api-reference/pin<br>
 * 
 * @author Manuel Laggner
 */
public class Simkl implements TmmFeature {
  private static final Logger LOGGER           = LoggerFactory.getLogger(Simkl.class);

  private static final String API_BASE         = "https://api.simkl.com/";
  private static final String APP_NAME         = "tinyMediaManager";

  private static final String ACCESS_TOKEN_KEY = "simkl.access_token.secret";
  private static final String SYNC_STATE_KEY   = "simkl.sync.state";

  private static final int    MAX_RETRIES      = 5;
  private static final long   RETRY_BASE_DELAY = 1000;
  private static final long   RETRY_MAX_DELAY  = 60000;

  private static Simkl        instance;

  private Retrofit            retrofit;
  private SimklApi            api;
  private Gson                gson;

  private Simkl() {
    // private constructor - internal usage
  }

  /**
   * Get an instance of {@link Simkl}
   * 
   * @return the instance
   */
  public static synchronized Simkl getInstance() {
    if (instance == null) {
      instance = new Simkl();
    }
    return instance;
  }

  /**
   * Returns the stored access token, or empty string.
   */
  public String getAccessToken() {
    String token = TmmStore.getInstance().get(ACCESS_TOKEN_KEY);
    return StringUtils.isNotBlank(token) ? token : "";
  }

  /**
   * Sets the access token in the encrypted TmmStore.
   */
  public void setAccessToken(String token) {
    if (StringUtils.isBlank(token)) {
      TmmStore.getInstance().remove(ACCESS_TOKEN_KEY);
    }
    else {
      TmmStore.getInstance().put(ACCESS_TOKEN_KEY, token);
    }
  }

  /**
   * Load the persisted incremental sync state (watermarks + cached data), or a fresh state if none has been stored yet.
   */
  public SimklSyncState getSyncState() {
    String json = TmmStore.getInstance().getPlain(SYNC_STATE_KEY);
    if (StringUtils.isBlank(json)) {
      return new SimklSyncState();
    }

    try {
      return getGson().fromJson(json, SimklSyncState.class);
    }
    catch (Exception e) {
      LOGGER.debug("Could not parse stored Simkl.com sync state - '{}'", e.getMessage());
      return new SimklSyncState();
    }
  }

  /**
   * Persist the incremental sync state (watermarks + cached data).
   */
  public void saveSyncState(SimklSyncState state) {
    TmmStore.getInstance().put(SYNC_STATE_KEY, getGson().toJson(state));
  }

  /**
   * Reset the incremental sync state (watermarks + cached data), so the next sync performs a full re-sync.
   */
  public void clearSyncState() {
    TmmStore.getInstance().remove(SYNC_STATE_KEY);
    LOGGER.info("Cleared Simkl.com incremental sync state");
  }

  private Gson getGson() {
    if (gson == null) {
      gson = new Gson();
    }
    return gson;
  }

  private synchronized void initAPI() {
    if (retrofit == null) {
      OkHttpClient httpClient = TmmHttpClient.newBuilder(true).addInterceptor(chain -> {
        Request original = chain.request();
        Request.Builder request = original.newBuilder().method(original.method(), original.body());

        // add the common query parameters
        request.url(original.url()
            .newBuilder()
            .addQueryParameter("client_id", getApiKey())
            .addQueryParameter("app-name", APP_NAME)
            .addQueryParameter("app-version", ReleaseInfo.getRealVersion())
            .build());

        // add the auth header if a token is available
        if (StringUtils.isNotBlank(getAccessToken())) {
          request.addHeader("Authorization", "Bearer " + getAccessToken());
        }
        request.header("User-Agent", APP_NAME + "/" + ReleaseInfo.getRealVersion());

        return chain.proceed(request.build());
      }).build();

      GsonBuilder gsonBuilder = new GsonBuilder().setLenient();
      gsonBuilder.registerTypeAdapter(Integer.class, (JsonDeserializer<Integer>) (json, typeOfT, context) -> {
        try {
          return json.getAsInt();
        }
        catch (NumberFormatException e) {
          return 0;
        }
      });

      retrofit = new Retrofit.Builder().baseUrl(API_BASE)
          .addConverterFactory(GsonConverterFactory.create(gsonBuilder.create()))
          .client(httpClient)
          .build();
      api = retrofit.create(SimklApi.class);
    }
  }

  SimklApi getApi() {
    initAPI();
    return api;
  }

  /**
   * Step 1 of PIN flow: request a PIN code from Simkl.com. Returns a map with keys: user_code, verification_uri, expires_in, interval.
   */
  public Map<String, String> getPinCode() throws Exception {
    initAPI();

    Response<SimklPinCodeResponse> response = api.getPinCode().execute();
    if (!response.isSuccessful() || response.body() == null) {
      throw new IOException("Failed to get PIN code: " + response.code());
    }

    SimklPinCodeResponse pinCode = response.body();
    Map<String, String> result = new HashMap<>();
    result.put("user_code", pinCode.user_code);
    result.put("verification_uri", pinCode.verification_uri);
    result.put("expires_in", pinCode.expires_in);
    result.put("interval", pinCode.interval);
    return result;
  }

  /**
   * Step 3 of PIN flow: poll for the access token using the user_code. Returns the access_token string, or null if still pending.
   */
  public String pollForToken(String userCode) throws Exception {
    initAPI();

    Response<SimklAccessTokenResponse> response = api.pollForToken(userCode).execute();
    if (!response.isSuccessful() || response.body() == null) {
      throw new IOException("Failed to poll for token: " + response.code());
    }

    SimklAccessTokenResponse tokenResponse = response.body();
    if ("OK".equals(tokenResponse.result) && StringUtils.isNotBlank(tokenResponse.access_token)) {
      return tokenResponse.access_token;
    }
    // Still pending or error
    return null;
  }

  /**
   * Test the connection by calling /sync/activities.
   */
  public boolean testConnection() {
    initAPI();

    try {
      // just to test if the connection works - no need to analyze the response
      executeCall(api.getActivities());
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  /**
   * Check if the feature is enabled and we have a valid access token.
   */
  private boolean isEnabled() {
    if (!isFeatureEnabled()) {
      return false;
    }

    if (StringUtils.isNotBlank(getAccessToken())) {
      return true;
    }

    return false;
  }

  /**
   * Execute a Simkl API call, handling errors and 401 (token revoked).
   */
  <T> T executeCall(Call<T> call) throws IOException {
    return executeCall(call, 1, RETRY_BASE_DELAY);
  }

  private <T> T executeCall(Call<T> call, int attempt, long waitMs) throws IOException {
    Response<T> response = call.execute();
    int code = response.code();

    if (code == 401) {
      // Token revoked - clear it
      setAccessToken("");
      String msg = TmmResourceBundle.getString("simkl.error.unauthorized");
      MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, "simkl.sync", msg, null));
      throw new IOException("Simkl.com access token rejected (401)");
    }

    // Retry transient errors with exponential backoff (per Simkl API rules)
    if ((code == 429 || code == 500 || code == 502 || code == 503) && attempt <= MAX_RETRIES) {
      LOGGER.warn("Simkl.com request failed with '{}' - retrying in {} ms (attempt {}/{})", code, waitMs, attempt, MAX_RETRIES);
      try {
        Thread.sleep(waitMs);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting to retry the Simkl.com request", e);
      }
      return executeCall(call.clone(), attempt + 1, Math.min(waitMs * 2, RETRY_MAX_DELAY));
    }

    if (!response.isSuccessful()) {
      String message = "Simkl.com request failed: " + code + " " + response.message();
      LOGGER.error(message);
      throw new IOException(message);
    }

    return response.body();
  }

  // @formatter:off
  // ███╗   ███╗ ██████╗ ██╗   ██╗██╗███████╗███████╗
  // ████╗ ████║██╔═══██╗██║   ██║██║██╔════╝██╔════╝
  // ██╔████╔██║██║   ██║██║   ██║██║█████╗  ███████╗
  // ██║╚██╔╝██║██║   ██║╚██╗ ██╔╝██║██╔══╝  ╚════██║
  // ██║ ╚═╝ ██║╚██████╔╝ ╚████╔╝ ██║███████╗███████║
  // ╚═╝     ╚═╝ ╚═════╝   ╚═══╝  ╚═╝╚══════╝╚══════╝
  // @formatter:on

  /**
   * Syncs the Simkl movie watched state (2-way).
   */
  public void syncMovieWatched(List<Movie> moviesInTmm) throws Exception {
    if (!isEnabled()) {
      return;
    }

    new SimklMovie(this).syncWatched(moviesInTmm);
  }

  /**
   * Removes specified movies from Simkl watched history.
   */
  public void removeFromSimklMovieWatched(List<Movie> moviesInTmm) throws Exception {
    if (!isEnabled()) {
      return;
    }

    new SimklMovie(this).removeFromWatched(moviesInTmm);
  }

  /**
   * Clears all Simkl movie data.
   */
  void clearSimklMovies() throws Exception {
    if (!isEnabled()) {
      return;
    }

    new SimklMovie(this).clearMovies();
  }

  // @formatter:off
  //  ████████╗██╗   ██╗███████╗██╗  ██╗ ██████╗ ██╗    ██╗███████╗
  //  ╚══██╔══╝██║   ██║██╔════╝██║  ██║██╔═══██╗██║    ██║██╔════╝
  //     ██║   ██║   ██║███████╗███████║██║   ██║██║ █╗ ██║███████╗
  //     ██║   ╚██╗ ██╔╝╚════██║██╔══██║██║   ██║██║███╗██║╚════██║
  //     ██║    ╚████╔╝ ███████║██║  ██║╚██████╔╝╚███╔███╔╝███████║
  //     ╚═╝     ╚═══╝  ╚══════╝╚═╝  ╚═╝ ╚═════╝  ╚══╝╚══╝ ╚══════╝
  // @formatter:on

  /**
   * Syncs the Simkl TV show watched state (2-way).
   */
  public void syncTvShowWatched(List<TvShow> tvShowsInTmm) throws Exception {
    if (!isEnabled()) {
      return;
    }

    new SimklTvShow(this).syncWatched(tvShowsInTmm);
  }

  /**
   * Removes specified TV shows from Simkl watched history.
   */
  public void removeFromSimklTvShowWatched(List<TvShow> tvShowsInTmm) throws Exception {
    if (!isEnabled()) {
      return;
    }

    new SimklTvShow(this).removeFromWatched(tvShowsInTmm);
  }

  /**
   * Clears all Simkl TV show data.
   */
  public void clearSimklTvShows() throws Exception {
    if (!isEnabled()) {
      return;
    }

    new SimklTvShow(this).clearTvShows();
  }
}
