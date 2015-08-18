/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

import static com.android.ide.common.resources.configuration.LocaleQualifier.FAKE_VALUE;

/**
 * A language,region pair
 */
public class Locale {
  /**
   * A special marker region qualifier representing any region
   */
  private static final LocaleQualifier ANY_QUALIFIER = new LocaleQualifier(FAKE_VALUE);

  /**
   * A locale which matches any language and region
   */
  public static final Locale ANY = new Locale(ANY_QUALIFIER);

  /**
   * The locale qualifier, or {@link #ANY_QUALIFIER} if this locale matches any locale
   */
  @NotNull
  public final LocaleQualifier qualifier;

  /**
   * Constructs a new {@linkplain Locale} matching a given language in a given locale.
   *
   * @param locale the locale
   */
  private Locale(@NotNull LocaleQualifier locale) {
    qualifier = locale;
  }

  /**
   * Constructs a new {@linkplain Locale} matching a given language in a given specific locale.
   *
   * @param locale the locale
   * @return a locale with the given locale
   */
  @NotNull
  public static Locale create(@NotNull LocaleQualifier locale) {
    return new Locale(locale);
  }

  /**
   * Constructs a new {@linkplain Locale} for the given folder configuration
   *
   * @param folder the folder configuration
   * @return a locale with the given language and region
   */
  public static Locale create(FolderConfiguration folder) {
    LocaleQualifier locale = folder.getLocaleQualifier();
    if (locale == null) {
      return ANY;
    } else {
      return new Locale(locale);
    }
  }

  /**
   * Constructs a new {@linkplain Locale} for the given locale string, e.g. "zh", "en-rUS", or "b+eng+US".
   *
   * @param localeString the locale description
   * @return the corresponding locale
   */
  @NotNull
  public static Locale create(@NotNull String localeString) {
    // Load locale. Note that this can get overwritten by the
    // project-wide settings read below.

    LocaleQualifier qualifier = LocaleQualifier.getQualifier(localeString);
    if (qualifier != null) {
      return new Locale(qualifier);
    } else {
      return ANY;
    }
  }

  /**
   * Returns a flag image to use for this locale
   *
   * @return a flag image, or a default globe icon
   */
  @NotNull
  public Icon getFlagImage() {
    String languageCode = qualifier.hasLanguage() ? qualifier.getLanguage() : null;
    if (languageCode == null) {
      return AndroidIcons.EmptyFlag;
    }
    String regionCode = hasRegion() ? qualifier.getRegion() : null;
    FlagManager icons = FlagManager.get();
    Icon image = icons.getFlag(languageCode, regionCode);
    if (image == null) {
      image = AndroidIcons.EmptyFlag;
    }

    return image;
  }

  /**
   * Returns true if this locale specifies a specific language. This is true
   * for all locales except {@link #ANY}.
   *
   * @return true if this locale specifies a specific language
   */
  public boolean hasLanguage() {
    return !qualifier.hasFakeValue();
  }

  /**
   * Returns true if this locale specifies a specific region
   *
   * @return true if this locale specifies a region
   */
  public boolean hasRegion() {
    return qualifier.getRegion() != null && !FAKE_VALUE.equals(qualifier.getRegion());
  }

  /**
   * Returns the locale formatted as language-region. If region is not set,
   * language is returned. If language is not set, empty string is returned.
   */
  public String toLocaleId() {
    return qualifier == ANY_QUALIFIER ? "" : qualifier.getTag();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + qualifier.hashCode();
    return result;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Locale other = (Locale)obj;
    if (!qualifier.equals(other.qualifier)) return false;
    return true;
  }

  @Override
  public String toString() {
    return qualifier.getTag();
  }

  /**
   * Comparator for comparing locales by language names (and as a secondary key, the region names)
   */
  public static final Comparator<Locale> LANGUAGE_NAME_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale locale1, Locale locale2) {
      String language1 = locale1.qualifier.getLanguage();
      String language2 = locale2.qualifier.getLanguage();
      if (locale1.qualifier.hasFakeValue()) {
        return locale2.qualifier.hasFakeValue() ? 0 : -1;
      }
      else if (locale2.qualifier.hasFakeValue()) {
        return 1;
      }
      String name1 = LocaleManager.getLanguageName(language1);
      String name2 = LocaleManager.getLanguageName(language2);
      int compare = StringUtil.compare(name1, name2, false);
      if (compare == 0) {
        return REGION_NAME_COMPARATOR.compare(locale1, locale2);
      }
      return compare;
    }
  };

  /**
   * Comparator for comparing locales by language ISO codes (and as a secondary key, the region ISO codes)
   */
  public static final Comparator<Locale> LANGUAGE_CODE_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale locale1, Locale locale2) {
      String language1 = locale1.qualifier.getLanguage();
      String language2 = locale2.qualifier.getLanguage();
      if (locale1.qualifier.hasFakeValue()) {
        return locale2.qualifier.hasFakeValue() ? 0 : -1;
      }
      else if (locale2.qualifier.hasFakeValue()) {
        return 1;
      }
      int compare = StringUtil.compare(language1, language2, false);
      if (compare == 0) {
        return REGION_CODE_COMPARATOR.compare(locale1, locale2);
      }
      return compare;
    }
  };

  /**
   * Comparator for comparing locales by region names
   */
  public static final Comparator<Locale> REGION_NAME_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale locale1, Locale locale2) {
      String region1 = locale1.qualifier.getRegion();
      String region2 = locale2.qualifier.getRegion();
      if (region1 == null) {
        return region2 == null ? 0 : -1;
      } else if (region2 == null) {
        return 1;
      }
      String regionName1 = LocaleManager.getRegionName(region1);
      String regionName2 = LocaleManager.getRegionName(region2);
      return StringUtil.compare(regionName1, regionName2, false);
    }
  };

  /**
   * Comparator for comparing locales by region ISO codes
   */
  public static final Comparator<Locale> REGION_CODE_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale locale1, Locale locale2) {
      String region1 = locale1.qualifier.getRegion();
      String region2 = locale2.qualifier.getRegion();
      if (region1 == null) {
        return region2 == null ? 0 : -1;
      } else if (region2 == null) {
        return 1;
      }
      return StringUtil.compare(region1, region2, false);
    }
  };
}
