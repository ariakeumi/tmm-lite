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
package org.tinymediamanager.thirdparty.simkl.model;

/**
 * An episode of a Simkl TV show season.
 * 
 * @author Manuel Laggner
 */
public class SimklEpisode {
  public Integer         number;
  public String          watched_at;
  public SimklEpisodeIds ids;

  /**
   * Episode-level IDs. Simkl only supports the tvdb episode id here (request-only). When read back with {@code episode_tvdb_id=yes} the id is
   * returned under the {@code tvdb_id} key.
   */
  public static class SimklEpisodeIds {
    public String tvdb;
    public String tvdb_id;
  }
}
