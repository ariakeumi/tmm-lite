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
package org.tinymediamanager.core.movie;

import org.tinymediamanager.core.AbstractModelObject;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Class {@link MovieRenamerProfile} holds a named snapshot of all movie renamer settings.
 *
 * @author Manuel Laggner
 */
public class MovieRenamerProfile extends AbstractModelObject {
  public static final String DEFAULT_RENAMER_PROFILE                = "Default";

  @JsonProperty
  private String             name;

  @JsonProperty
  private boolean            renamerPathnameEnabled                 = true;
  @JsonProperty
  private String             renamerPathname                        = MovieSettings.DEFAULT_RENAMER_FOLDER_PATTERN;
  @JsonProperty
  private boolean            renamerFilenameEnabled                 = true;
  @JsonProperty
  private String             renamerFilename                        = MovieSettings.DEFAULT_RENAMER_FILE_PATTERN;
  @JsonProperty
  private boolean            renamerPathnameSpaceSubstitution       = false;
  @JsonProperty
  private String             renamerPathnameSpaceReplacement        = "_";
  @JsonProperty
  private boolean            renamerFilenameSpaceSubstitution       = false;
  @JsonProperty
  private String             renamerFilenameSpaceReplacement        = "_";
  @JsonProperty
  private String             renamerColonReplacement                = "-";
  @JsonProperty
  private boolean            renamerNfoCleanup                      = false;
  @JsonProperty
  private boolean            renamerCleanupUnwanted                 = false;
  @JsonProperty
  private boolean            renamerCreateMoviesetForSingleMovie    = false;
  @JsonProperty
  private String             renamerFirstCharacterNumberReplacement = "#";
  @JsonProperty
  private boolean            asciiReplacement                       = false;
  @JsonProperty
  private boolean            unicodeReplacement                     = false;
  @JsonProperty
  private boolean            allowMultipleMoviesInSameDir           = false;

  /**
   * Creates a new profile with the default name.
   */
  public MovieRenamerProfile() {
    // needed for JSON unmarshalling
    this(DEFAULT_RENAMER_PROFILE);
  }

  /**
   * Creates a new profile with the given name
   * 
   * @param name
   *          the name
   */
  public MovieRenamerProfile(String name) {
    this.name = name;
  }

  /**
   * Copy constructor
   *
   * @param profile
   *          the {@link MovieRenamerProfile} to copy
   */
  public MovieRenamerProfile(MovieRenamerProfile profile) {
    this(profile.name, profile);
  }

  /**
   * Copy constructor - new name
   * 
   * @param name
   *          the name of the new {@link MovieRenamerProfile}
   * @param profile
   *          the {@link MovieRenamerProfile} to copy
   */
  public MovieRenamerProfile(String name, MovieRenamerProfile profile) {
    this(name);
    this.renamerPathname = profile.renamerPathname;
    this.renamerFilename = profile.renamerFilename;
    this.renamerPathnameSpaceSubstitution = profile.renamerPathnameSpaceSubstitution;
    this.renamerPathnameSpaceReplacement = profile.renamerPathnameSpaceReplacement;
    this.renamerFilenameSpaceSubstitution = profile.renamerFilenameSpaceSubstitution;
    this.renamerFilenameSpaceReplacement = profile.renamerFilenameSpaceReplacement;
    this.renamerColonReplacement = profile.renamerColonReplacement;
    this.renamerNfoCleanup = profile.renamerNfoCleanup;
    this.renamerCleanupUnwanted = profile.renamerCleanupUnwanted;
    this.renamerCreateMoviesetForSingleMovie = profile.renamerCreateMoviesetForSingleMovie;
    this.renamerFirstCharacterNumberReplacement = profile.renamerFirstCharacterNumberReplacement;
    this.asciiReplacement = profile.asciiReplacement;
    this.unicodeReplacement = profile.unicodeReplacement;
    this.renamerPathnameEnabled = profile.renamerPathnameEnabled;
    this.renamerFilenameEnabled = profile.renamerFilenameEnabled;
    this.allowMultipleMoviesInSameDir = profile.allowMultipleMoviesInSameDir;
  }

  public String getName() {
    return name;
  }

  public void setName(String newValue) {
    String oldValue = this.name;
    this.name = newValue;
    firePropertyChange("name", oldValue, newValue);
  }

  public String getRenamerPathname() {
    return renamerPathname;
  }

  public void setRenamerPathname(String newValue) {
    String oldValue = this.renamerPathname;
    this.renamerPathname = newValue;
    firePropertyChange("renamerPathname", oldValue, newValue);
  }

  public String getRenamerFilename() {
    return renamerFilename;
  }

  public void setRenamerFilename(String newValue) {
    String oldValue = this.renamerFilename;
    this.renamerFilename = newValue;
    firePropertyChange("renamerFilename", oldValue, newValue);
  }

  public boolean isRenamerPathnameSpaceSubstitution() {
    return renamerPathnameSpaceSubstitution;
  }

  public void setRenamerPathnameSpaceSubstitution(boolean newValue) {
    boolean oldValue = this.renamerPathnameSpaceSubstitution;
    this.renamerPathnameSpaceSubstitution = newValue;
    firePropertyChange("renamerPathnameSpaceSubstitution", oldValue, newValue);
  }

  public String getRenamerPathnameSpaceReplacement() {
    return renamerPathnameSpaceReplacement;
  }

  public void setRenamerPathnameSpaceReplacement(String newValue) {
    String oldValue = this.renamerPathnameSpaceReplacement;
    this.renamerPathnameSpaceReplacement = newValue;
    firePropertyChange("renamerPathnameSpaceReplacement", oldValue, newValue);
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

  public boolean isRenamerNfoCleanup() {
    return renamerNfoCleanup;
  }

  public void setRenamerNfoCleanup(boolean newValue) {
    boolean oldValue = this.renamerNfoCleanup;
    this.renamerNfoCleanup = newValue;
    firePropertyChange("renamerNfoCleanup", oldValue, newValue);
  }

  public boolean isRenamerCleanupUnwanted() {
    return renamerCleanupUnwanted;
  }

  public void setRenamerCleanupUnwanted(boolean newValue) {
    boolean oldValue = this.renamerCleanupUnwanted;
    this.renamerCleanupUnwanted = newValue;
    firePropertyChange("renamerCleanupUnwanted", oldValue, newValue);
  }

  public boolean isRenamerCreateMoviesetForSingleMovie() {
    return renamerCreateMoviesetForSingleMovie;
  }

  public void setRenamerCreateMoviesetForSingleMovie(boolean newValue) {
    boolean oldValue = this.renamerCreateMoviesetForSingleMovie;
    this.renamerCreateMoviesetForSingleMovie = newValue;
    firePropertyChange("renamerCreateMoviesetForSingleMovie", oldValue, newValue);
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

  public boolean isAllowMultipleMoviesInSameDir() {
    return allowMultipleMoviesInSameDir;
  }

  public void setAllowMultipleMoviesInSameDir(boolean newValue) {
    boolean oldValue = this.allowMultipleMoviesInSameDir;
    this.allowMultipleMoviesInSameDir = newValue;
    firePropertyChange("allowMultipleMoviesInSameDir", oldValue, newValue);
  }

  public boolean isRenamerPathnameEnabled() {
    return renamerPathnameEnabled;
  }

  public void setRenamerPathnameEnabled(boolean newValue) {
    boolean oldValue = this.renamerPathnameEnabled;
    this.renamerPathnameEnabled = newValue;
    firePropertyChange("renamerPathnameEnabled", oldValue, newValue);
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
