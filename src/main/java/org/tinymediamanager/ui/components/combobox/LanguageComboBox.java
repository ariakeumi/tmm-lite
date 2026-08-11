/*
 * Copyright 2012 - 2020 Manuel Laggner
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
package org.tinymediamanager.ui.components.combobox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.tinymediamanager.scraper.util.LanguageUtils;
import org.tinymediamanager.ui.components.table.TmmTableFormat;

public class LanguageComboBox extends AutocompleteComboBox<LanguageComboBox.LanguageContainer> {

  public LanguageComboBox() {
    super();

    TmmTableFormat.StringComparator stringComparator = new TmmTableFormat.StringComparator();

    List<LanguageContainer> languages = new ArrayList<>();
    for (Locale locale : Locale.getAvailableLocales()) {
      LanguageContainer localeContainer = new LanguageContainer(locale);
      if (!languages.contains(localeContainer)) {
        languages.add(localeContainer);
      }
    }
    languages.sort((o1, o2) -> stringComparator.compare(o1.toString(), o2.toString()));

    this.items.addAll(languages);
    init();
  }

  public void setSelectedLanguage(String language) {
    Optional<LanguageContainer> foundByValue = items.stream().filter(v -> v.value.equalsIgnoreCase(language)).findFirst();
    if (foundByValue.isPresent()) {
      super.setSelectedItem(foundByValue.get());
    }
    else {
      super.setSelectedItem(language);
    }
  }

  public String getSelectedLanguage() {
    Object obj = super.getSelectedItem();
    if (obj instanceof LanguageContainer localeContainer) {
      return localeContainer.value.strip();
    }
    else if (obj instanceof String language) {
      return language.strip();
    }
    return "";
  }

  public static class LanguageContainer {
    private final String value;
    private Locale       locale;
    private final String description;

    public LanguageContainer(@NotNull Locale locale) {
      this.locale = locale;
      this.value = locale.getISO3Language();
      this.description = StringUtils.isNotBlank(locale.getDisplayLanguage()) ? locale.getDisplayLanguage() + " (" + this.value + ")" : "";
    }

    public LanguageContainer(@NotNull String locale) {
      Locale tmp = LanguageUtils.KEY_TO_LOCALE_MAP.get(locale);
      // if WE dont have it in our array, it is not valid!
      if (tmp != null) {
        this.locale = tmp;
      }
      this.value = locale;
      this.description = this.locale != null ? value + " (" + this.locale.getISO3Language() + ")" : value;
    }

    @Override
    public String toString() {
      return description;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LanguageContainer that = (LanguageContainer) o;
      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }
}
