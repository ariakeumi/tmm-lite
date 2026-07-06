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
package org.tinymediamanager.core.tvshow;

import org.tinymediamanager.core.AbstractModelObject;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Class {@link TvShowRenamerProfile} holds a named snapshot of all TV show renamer settings.
 *
 * @author Manuel Laggner
 */
public class TvShowRenamerProfile extends AbstractModelObject {
  public static final String      DEFAULT_RENAMER_PROFILE                = "Default";

  @JsonProperty
  private String                  name;

  @JsonProperty
  private boolean                 renamerTvShowFoldernameEnabled         = true;
  @JsonProperty
  private String                  renamerTvShowFoldername                = TvShowSettings.DEFAULT_RENAMER_FOLDER_PATTERN;
  @JsonProperty
  private boolean                 renamerSeasonFoldernameEnabled         = true;
  @JsonProperty
  private String                  renamerSeasonFoldername                = TvShowSettings.DEFAULT_RENAMER_SEASON_PATTERN;
  @JsonProperty
  private boolean                 renamerFilenameEnabled                 = true;
  @JsonProperty
  private String                  renamerFilename                        = TvShowSettings.DEFAULT_RENAMER_FILE_PATTERN;
  @JsonProperty
  private TvShowMultiEpisodeStyle renamerMultiEpisodeStyle               = TvShowMultiEpisodeStyle.REPEAT;
  @JsonProperty
  private boolean                 renamerShowPathnameSpaceSubstitution   = false;
  @JsonProperty
  private String                  renamerShowPathnameSpaceReplacement    = "_";
  @JsonProperty
  private boolean                 renamerSeasonPathnameSpaceSubstitution = false;
  @JsonProperty
  private String                  renamerSeasonPathnameSpaceReplacement  = "_";
  @JsonProperty
  private boolean                 renamerFilenameSpaceSubstitution       = false;
  @JsonProperty
  private String                  renamerFilenameSpaceReplacement        = "_";
  @JsonProperty
  private String                  renamerColonReplacement                = "";
  @JsonProperty
  private boolean                 renamerCleanupUnwanted                 = false;
  @JsonProperty
  private String                  renamerFirstCharacterNumberReplacement = "#";
  @JsonProperty
  private boolean                 asciiReplacement                       = false;
  @JsonProperty
  private boolean                 unicodeReplacement                     = false;
  @JsonProperty
  private boolean                 specialSeason                          = true;
  @JsonProperty
  private boolean                 createMissingSeasonItems               = false;

  /**
   * Creates a new profile with the default name.
   */
  public TvShowRenamerProfile() {
    this(DEFAULT_RENAMER_PROFILE);
  }

  /**
   * Creates a new profile with the given name
   * 
   * @param name
   *          the name
   */
  public TvShowRenamerProfile(String name) {
    this.name = name;
  }

  /**
   * Copy constructor
   *
   * @param profile
   *          the {@link TvShowRenamerProfile} to copy
   */
  public TvShowRenamerProfile(TvShowRenamerProfile profile) {
    this(profile.name, profile);
  }

  /**
   * Copy constructor - new name
   * 
   * @param name
   *          the name of the new {@link TvShowRenamerProfile}
   * @param profile
   *          the {@link TvShowRenamerProfile} to copy
   */
  public TvShowRenamerProfile(String name, TvShowRenamerProfile profile) {
    this(name);
    this.renamerTvShowFoldernameEnabled = profile.renamerTvShowFoldernameEnabled;
    this.renamerTvShowFoldername = profile.renamerTvShowFoldername;
    this.renamerSeasonFoldernameEnabled = profile.renamerSeasonFoldernameEnabled;
    this.renamerSeasonFoldername = profile.renamerSeasonFoldername;
    this.renamerFilenameEnabled = profile.renamerFilenameEnabled;
    this.renamerFilename = profile.renamerFilename;
    this.renamerMultiEpisodeStyle = profile.renamerMultiEpisodeStyle;
    this.renamerShowPathnameSpaceSubstitution = profile.renamerShowPathnameSpaceSubstitution;
    this.renamerShowPathnameSpaceReplacement = profile.renamerShowPathnameSpaceReplacement;
    this.renamerSeasonPathnameSpaceSubstitution = profile.renamerSeasonPathnameSpaceSubstitution;
    this.renamerSeasonPathnameSpaceReplacement = profile.renamerSeasonPathnameSpaceReplacement;
    this.renamerFilenameSpaceSubstitution = profile.renamerFilenameSpaceSubstitution;
    this.renamerFilenameSpaceReplacement = profile.renamerFilenameSpaceReplacement;
    this.renamerColonReplacement = profile.renamerColonReplacement;
    this.renamerCleanupUnwanted = profile.renamerCleanupUnwanted;
    this.renamerFirstCharacterNumberReplacement = profile.renamerFirstCharacterNumberReplacement;
    this.asciiReplacement = profile.asciiReplacement;
    this.unicodeReplacement = profile.unicodeReplacement;
    this.specialSeason = profile.specialSeason;
    this.createMissingSeasonItems = profile.createMissingSeasonItems;
  }

  public String getName() {
    return name;
  }

  public void setName(String newValue) {
    String oldValue = this.name;
    this.name = newValue;
    firePropertyChange("name", oldValue, newValue);
  }

  public String getRenamerTvShowFoldername() {
    return renamerTvShowFoldername;
  }

  public void setRenamerTvShowFoldername(String newValue) {
    String oldValue = this.renamerTvShowFoldername;
    this.renamerTvShowFoldername = newValue;
    firePropertyChange("renamerTvShowFoldername", oldValue, newValue);
  }

  public String getRenamerSeasonFoldername() {
    return renamerSeasonFoldername;
  }

  public void setRenamerSeasonFoldername(String newValue) {
    String oldValue = this.renamerSeasonFoldername;
    this.renamerSeasonFoldername = newValue;
    firePropertyChange("renamerSeasonFoldername", oldValue, newValue);
  }

  public String getRenamerFilename() {
    return renamerFilename;
  }

  public void setRenamerFilename(String newValue) {
    String oldValue = this.renamerFilename;
    this.renamerFilename = newValue;
    firePropertyChange("renamerFilename", oldValue, newValue);
  }

  public TvShowMultiEpisodeStyle getRenamerMultiEpisodeStyle() {
    return renamerMultiEpisodeStyle;
  }

  public void setRenamerMultiEpisodeStyle(TvShowMultiEpisodeStyle newValue) {
    TvShowMultiEpisodeStyle oldValue = this.renamerMultiEpisodeStyle;
    this.renamerMultiEpisodeStyle = newValue;
    firePropertyChange("renamerMultiEpisodeStyle", oldValue, newValue);
  }

  public boolean isRenamerShowPathnameSpaceSubstitution() {
    return renamerShowPathnameSpaceSubstitution;
  }

  public void setRenamerShowPathnameSpaceSubstitution(boolean newValue) {
    boolean oldValue = this.renamerShowPathnameSpaceSubstitution;
    this.renamerShowPathnameSpaceSubstitution = newValue;
    firePropertyChange("renamerShowPathnameSpaceSubstitution", oldValue, newValue);
  }

  public String getRenamerShowPathnameSpaceReplacement() {
    return renamerShowPathnameSpaceReplacement;
  }

  public void setRenamerShowPathnameSpaceReplacement(String newValue) {
    String oldValue = this.renamerShowPathnameSpaceReplacement;
    this.renamerShowPathnameSpaceReplacement = newValue;
    firePropertyChange("renamerShowPathnameSpaceReplacement", oldValue, newValue);
  }

  public boolean isRenamerSeasonPathnameSpaceSubstitution() {
    return renamerSeasonPathnameSpaceSubstitution;
  }

  public void setRenamerSeasonPathnameSpaceSubstitution(boolean newValue) {
    boolean oldValue = this.renamerSeasonPathnameSpaceSubstitution;
    this.renamerSeasonPathnameSpaceSubstitution = newValue;
    firePropertyChange("renamerSeasonPathnameSpaceSubstitution", oldValue, newValue);
  }

  public String getRenamerSeasonPathnameSpaceReplacement() {
    return renamerSeasonPathnameSpaceReplacement;
  }

  public void setRenamerSeasonPathnameSpaceReplacement(String newValue) {
    String oldValue = this.renamerSeasonPathnameSpaceReplacement;
    this.renamerSeasonPathnameSpaceReplacement = newValue;
    firePropertyChange("renamerSeasonPathnameSpaceReplacement", oldValue, newValue);
  }

  public boolean isRenamerFilenameSpaceSubstitution() {
    return renamerFilenameSpaceSubstitution;
  }

  public void setRenamerFilenameSpaceSubstitution(boolean newValue) {
    boolean oldValue = this.renamerFilenameSpaceSubstitution;
    this.renamerFilenameSpaceSubstitution = newValue;
    firePropertyChange("renamerFilenameSpaceSubstitution", oldValue, newValue);
  }

  public String getRenamerFilenameSpaceReplacement() {
    return renamerFilenameSpaceReplacement;
  }

  public void setRenamerFilenameSpaceReplacement(String newValue) {
    String oldValue = this.renamerFilenameSpaceReplacement;
    this.renamerFilenameSpaceReplacement = newValue;
    firePropertyChange("renamerFilenameSpaceReplacement", oldValue, newValue);
  }

  public String getRenamerColonReplacement() {
    return renamerColonReplacement;
  }

  public void setRenamerColonReplacement(String newValue) {
    String oldValue = this.renamerColonReplacement;
    this.renamerColonReplacement = newValue;
    firePropertyChange("renamerColonReplacement", oldValue, newValue);
  }

  public boolean isRenamerCleanupUnwanted() {
    return renamerCleanupUnwanted;
  }

  public void setRenamerCleanupUnwanted(boolean newValue) {
    boolean oldValue = this.renamerCleanupUnwanted;
    this.renamerCleanupUnwanted = newValue;
    firePropertyChange("renamerCleanupUnwanted", oldValue, newValue);
  }

  public String getRenamerFirstCharacterNumberReplacement() {
    return renamerFirstCharacterNumberReplacement;
  }

  public void setRenamerFirstCharacterNumberReplacement(String newValue) {
    String oldValue = this.renamerFirstCharacterNumberReplacement;
    this.renamerFirstCharacterNumberReplacement = newValue;
    firePropertyChange("renamerFirstCharacterNumberReplacement", oldValue, newValue);
  }

  public boolean isAsciiReplacement() {
    return asciiReplacement;
  }

  public void setAsciiReplacement(boolean newValue) {
    boolean oldValue = this.asciiReplacement;
    this.asciiReplacement = newValue;
    firePropertyChange("asciiReplacement", oldValue, newValue);
  }

  public boolean isUnicodeReplacement() {
    return unicodeReplacement;
  }

  public void setUnicodeReplacement(boolean newValue) {
    boolean oldValue = this.unicodeReplacement;
    this.unicodeReplacement = newValue;
    firePropertyChange("unicodeReplacement", oldValue, newValue);
  }

  public boolean isSpecialSeason() {
    return specialSeason;
  }

  public void setSpecialSeason(boolean newValue) {
    boolean oldValue = this.specialSeason;
    this.specialSeason = newValue;
    firePropertyChange("specialSeason", oldValue, newValue);
  }

  public boolean isCreateMissingSeasonItems() {
    return createMissingSeasonItems;
  }

  public void setCreateMissingSeasonItems(boolean newValue) {
    boolean oldValue = this.createMissingSeasonItems;
    this.createMissingSeasonItems = newValue;
    firePropertyChange("createMissingSeasonItems", oldValue, newValue);
  }

  public boolean isRenamerTvShowFoldernameEnabled() {
    return renamerTvShowFoldernameEnabled;
  }

  public void setRenamerTvShowFoldernameEnabled(boolean newValue) {
    boolean oldValue = this.renamerTvShowFoldernameEnabled;
    this.renamerTvShowFoldernameEnabled = newValue;
    firePropertyChange("renamerTvShowFoldernameEnabled", oldValue, newValue);
  }

  public boolean isRenamerSeasonFoldernameEnabled() {
    return renamerSeasonFoldernameEnabled;
  }

  public void setRenamerSeasonFoldernameEnabled(boolean newValue) {
    boolean oldValue = this.renamerSeasonFoldernameEnabled;
    this.renamerSeasonFoldernameEnabled = newValue;
    firePropertyChange("renamerSeasonFoldernameEnabled", oldValue, newValue);
  }

  public boolean isRenamerFilenameEnabled() {
    return renamerFilenameEnabled;
  }

  public void setRenamerFilenameEnabled(boolean newValue) {
    boolean oldValue = this.renamerFilenameEnabled;
    this.renamerFilenameEnabled = newValue;
    firePropertyChange("renamerFilenameEnabled", oldValue, newValue);
  }
}
