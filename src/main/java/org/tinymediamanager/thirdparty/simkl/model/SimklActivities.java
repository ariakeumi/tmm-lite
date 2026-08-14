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
 * The response of the <code>/sync/activities</code> endpoint - used to determine if anything changed since the last sync.
 *
 * @author Manuel Laggner
 */
public class SimklActivities {
  public String        all;
  public ActivityBlock settings;
  public ActivityBlock tv_shows;
  public ActivityBlock anime;
  public ActivityBlock movies;

  /**
   * A single domain block of the activities response (timestamp per bucket).
   */
  public static class ActivityBlock {
    public String all;
    public String rated_at;
    public String playback;
    public String plantowatch;
    public String watching;
    public String completed;
    public String hold;
    public String dropped;
    public String removed_from_list;
  }
}
