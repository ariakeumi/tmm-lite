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

package org.tinymediamanager.core.entities;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.MissingResourceException;
import java.util.Set;

import org.tinymediamanager.core.AbstractModelObject;
import org.tinymediamanager.core.TmmResourceBundle;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MediaStreamInfo extends AbstractModelObject {

  /**
   * <a href="https://github.com/xbmc/xbmc/blob/master/xbmc/cores/VideoPlayer/Interface/StreamInfo.h">Kodi reference</a>
   */
  public enum Flags {
    // FLAG_NONE, // just empty
    /**
     * The Default flag is a hint for a Matroska Player indicating that a given track SHOULD be eligible to be automatically selected as the default
     * track for a given language. If no tracks in a given language have the Default flag set, then all tracks in that language are eligible for
     * automatic selection. This can be used to indicate that a track provides "regular service" that is suitable for users with default settings, as
     * opposed to specialized services, such as commentary, captions for users with hearing impairments, or descriptive audio.
     * 
     * The Matroska Player MAY override the Default flag for any reason, including user preferences to prefer tracks providing accessibility services.
     */
    @JsonEnumDefaultValue
    FLAG_DEFAULT("Default", "metatag.default"),
    FLAG_DUB("Dubbed"),
    /**
     * The Original flag tells the Matroska Player that this track is in the original language and that it SHOULD prefer this track if configured to
     * prefer original-language tracks of this track's type.
     */
    FLAG_ORIGINAL("Original"),
    /**
     * The Commentary flag tells the Matroska Player that this track contains commentary on the content.
     */
    FLAG_COMMENT("Commentary"),
    FLAG_LYRICS("Lyrics"),
    FLAG_KARAOKE("Karaoke"),
    /**
     * The Forced flag tells the Matroska Player that it SHOULD display this subtitle track, even if user preferences usually would not call for any
     * subtitles to be displayed alongside the audio track that is currently selected. This can be used to indicate that a track contains translations
     * of on-screen text or dialogue spoken in a different language than the track's primary language.
     */
    FLAG_FORCED("Forced", "metatag.forced"),
    /**
     * The Hearing-Impaired flag tells the Matroska Player that it SHOULD prefer this track when selecting a default track for a user with a hearing
     * impairment and that it MAY prefer to select a different track when selecting a default track for a user that is not hearing impaired.
     */
    FLAG_HEARING_IMPAIRED("SDH", "metatag.sdh"),
    /**
     * The Visual-Impaired flag tells the Matroska Player that it SHOULD prefer this track when selecting a default track for a user with a visual
     * impairment and that it MAY prefer to select a different track when selecting a default track for a user that is not visually impaired.
     */
    FLAG_VISUAL_IMPAIRED("Audio description for the visually impaired");

    private final String displayName;
    private final String translationKey;

    Flags(String displayName) {
      this(displayName, null);
    }

    Flags(String displayName, String translationKey) {
      this.displayName = displayName;
      this.translationKey = translationKey;
    }

    @Override
    public String toString() {
      String name = displayName;

      if (translationKey != null) {
        try {
          name = TmmResourceBundle.getStringUnsafe(translationKey);
        }
        catch (MissingResourceException e) {
          // default is already set to displayName, so we can ignore this exception
        }
      }

      return name;
    }
  }

  @JsonProperty
  protected String     codec       = "";
  @JsonProperty
  protected String     language    = "";
  @JsonProperty
  protected String     title       = "";

  @JsonProperty
  protected Set<Flags> streamFlags = EnumSet.noneOf(Flags.class);

  // the stream id for locally mixin in DVD information
  public String        id          = "";

  public String getCodec() {
    return codec;
  }

  public void setCodec(String codec) {
    this.codec = codec;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public boolean has(Flags flag) {
    return streamFlags.contains(flag);
  }

  public void set(Flags... flags) {
    streamFlags.addAll(Arrays.asList(flags));
  }

  public Set<Flags> getFlags() {
    return streamFlags;
  }

  public void set(Collection<Flags> flags) {
    streamFlags.addAll(flags);
  }

  public void remove(Flags... flags) {
    for (Flags f : flags) {
      streamFlags.remove(f);
    }
  }

  public boolean isDefaultStream() {
    return streamFlags.contains(Flags.FLAG_DEFAULT);
  }

  public void setDefaultStream(boolean defaultStream) {
    if (defaultStream) {
      streamFlags.add(Flags.FLAG_DEFAULT);
    }
    else {
      streamFlags.remove(Flags.FLAG_DEFAULT);
    }
  }

  public boolean isForced() {
    return streamFlags.contains(Flags.FLAG_FORCED);
  }

  public void setForced(boolean forced) {
    if (forced) {
      streamFlags.add(Flags.FLAG_FORCED);
    }
    else {
      streamFlags.remove(Flags.FLAG_FORCED);
    }
  }

  public boolean isSdh() {
    return streamFlags.contains(Flags.FLAG_HEARING_IMPAIRED);
  }

  public void setSdh(boolean sdh) {
    if (sdh) {
      streamFlags.add(Flags.FLAG_HEARING_IMPAIRED);
    }
    else {
      streamFlags.remove(Flags.FLAG_HEARING_IMPAIRED);
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((language == null) ? 0 : language.hashCode());
    result = prime * result + ((streamFlags == null) ? 0 : streamFlags.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null) {
      return false;
    }

    if (getClass() != obj.getClass()) {
      return false;
    }

    MediaStreamInfo other = (MediaStreamInfo) obj;
    if (language == null) {
      if (other.language != null)
        return false;
    }
    else if (!language.equals(other.language)) {
      return false;
    }
    if (streamFlags == null) {
      if (other.streamFlags != null) {
        return false;
      }
    }
    else if (!streamFlags.equals(other.streamFlags)) {
      return false;
    }

    return true;
  }
}
