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

package org.tinymediamanager.scraper.tmdb.services;

import org.tinymediamanager.scraper.tmdb.entities.AppendToResponse;
import org.tinymediamanager.scraper.tmdb.entities.Changes;
import org.tinymediamanager.scraper.tmdb.entities.ExternalIds;
import org.tinymediamanager.scraper.tmdb.entities.Person;
import org.tinymediamanager.scraper.tmdb.entities.PersonCredits;
import org.tinymediamanager.scraper.tmdb.entities.PersonImages;
import org.tinymediamanager.scraper.tmdb.entities.PersonResultsPage;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * The interface Person service for accessing TMDB person API endpoints.
 */
public interface PersonService {

  /**
   * Get the primary person details by id.
   *
   * @param personId
   *          A Person TMDb id.
   * @param language
   *          <em>Optional.</em> ISO 639-1 code.
   */
  @GET("person/{person_id}")
  Call<Person> details(@Path("person_id") int personId, @Query("language") String language);

  /**
   * Get the primary person details by id.
   *
   * @param personId
   *          A Person TMDb id.
   * @param language
   *          <em>Optional.</em> ISO 639-1 code.
   * @param appendToResponse
   *          <em>Optional.</em> extra requests to append to the result. <b>Accepted Value(s):</b> images, external_ids, combined_credits,
   *          movie_credits, tv_credits, changes
   */
  @GET("person/{person_id}")
  Call<Person> details(@Path("person_id") int personId, @Query("language") String language,
      @Query("append_to_response") AppendToResponse appendToResponse);

  /**
   * Get the combined movie and TV credits for a person by id.
   *
   * @param personId
   *          A Person TMDb id.
   * @param language
   *          <em>Optional.</em> ISO 639-1 code.
   */
  @GET("person/{person_id}/combined_credits")
  Call<PersonCredits> combinedCredits(@Path("person_id") int personId, @Query("language") String language);

  /**
   * Get the movie credits for a person by id.
   *
   * @param personId
   *          A Person TMDb id.
   * @param language
   *          <em>Optional.</em> ISO 639-1 code.
   */
  @GET("person/{person_id}/movie_credits")
  Call<PersonCredits> movieCredits(@Path("person_id") int personId, @Query("language") String language);

  /**
   * Get the TV credits for a person by id.
   *
   * @param personId
   *          A Person TMDb id.
   * @param language
   *          <em>Optional.</em> ISO 639-1 code.
   */
  @GET("person/{person_id}/tv_credits")
  Call<PersonCredits> tvCredits(@Path("person_id") int personId, @Query("language") String language);

  /**
   * Get the images for a person by id.
   *
   * @param personId
   *          A Person TMDb id.
   */
  @GET("person/{person_id}/images")
  Call<PersonImages> images(@Path("person_id") int personId);

  /**
   * Get the external ids for a person by id.
   *
   * @param personId
   *          A Person TMDb id.
   */
  @GET("person/{person_id}/external_ids")
  Call<ExternalIds> externalIds(@Path("person_id") int personId);

  /**
   * Get the changes for a person by id.
   *
   * @param personId
   *          A Person TMDb id.
   */
  @GET("person/{person_id}/changes")
  Call<Changes> changes(@Path("person_id") int personId);

  /**
   * Get the latest person id.
   */
  @GET("person/latest")
  Call<Person> latest();

  /**
   * Get a list of popular people on The Movie Database. This list updates daily.
   *
   * @param page
   *          <em>Optional.</em> Minimum value is 1, expected value is an integer.
   * @param language
   *          <em>Optional.</em> ISO 639-1 code.
   */
  @GET("person/popular")
  Call<PersonResultsPage> popular(@Query("page") Integer page, @Query("language") String language);
}
