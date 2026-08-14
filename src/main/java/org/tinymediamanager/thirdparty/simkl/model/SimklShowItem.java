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

import java.util.List;

/**
 * A TV show from Simkl.com - either a watched show returned by the API or a show sent to the sync endpoints.
 *
 * @author Manuel Laggner
 */
public class SimklShowItem {
  public SimklIds          ids;
  public List<SimklSeason> seasons;
  public String            status;
  public Show              show;

  /**
   * The nested media object as returned by the sync endpoints (the ids/seasons are nested here, not on the top level).
   */
  public static class Show {
    public SimklIds          ids;
    public List<SimklSeason> seasons;
    public String            title;
    public String            poster;
    public Integer           year;
  }

  /**
   * Promote the data of the nested media object to the top level - needed after parsing API responses.
   */
  public void normalize() {
    if (show != null) {
      if (show.ids != null) {
        ids = show.ids;
      }
      if (show.seasons != null) {
        seasons = show.seasons;
      }
    }
  }
}
