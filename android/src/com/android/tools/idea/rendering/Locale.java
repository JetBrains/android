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
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.google.common.base.Objects;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

import static com.android.ide.common.resources.configuration.LanguageQualifier.FAKE_LANG_VALUE;
import static com.android.ide.common.resources.configuration.RegionQualifier.FAKE_REGION_VALUE;

/**
 * A language,region pair
 */
public class Locale {
  /**
   * A special marker region qualifier representing any region
   */
  private static final RegionQualifier ANY_REGION = new RegionQualifier(FAKE_REGION_VALUE);

  /**
   * A special marker language qualifier representing any language
   */
  private static final LanguageQualifier ANY_LANGUAGE = new LanguageQualifier(FAKE_LANG_VALUE);

  /**
   * A locale which matches any language and region
   */
  public static final Locale ANY = new Locale(ANY_LANGUAGE, ANY_REGION);

  /**
   * The language qualifier, or {@link #ANY_LANGUAGE} if this locale matches any language
   */
  @NotNull
  public final LanguageQualifier language;

  /**
   * The language qualifier, or {@link #ANY_REGION} if this locale matches any region
   */
  @NotNull
  public final RegionQualifier region;

  /**
   * Constructs a new {@linkplain Locale} matching a given language in a given locale.
   *
   * @param language the language
   * @param region   the region
   */
  private Locale(@NotNull LanguageQualifier language, @NotNull RegionQualifier region) {
    if (language.getValue().equals(FAKE_LANG_VALUE)) {
      language = ANY_LANGUAGE;
    }
    if (region.getValue().equals(FAKE_REGION_VALUE)) {
      region = ANY_REGION;
    }
    this.language = language;
    this.region = region;
  }

  /**
   * Constructs a new {@linkplain Locale} matching a given language in a given specific locale.
   *
   * @param language the language
   * @param region   the region
   * @return a locale with the given language and region
   */
  @NotNull
  public static Locale create(@NotNull LanguageQualifier language, @Nullable RegionQualifier region) {
    return new Locale(language, region != null ? region : ANY_REGION);
  }

  /**
   * Constructs a new {@linkplain Locale} for the given language, matching any regions.
   *
   * @param language the language
   * @return a locale with the given language and region
   */
  @NotNull
  public static Locale create(@NotNull LanguageQualifier language) {
    return new Locale(language, ANY_REGION);
  }

  /**
   * Constructs a new {@linkplain Locale} for the given locale string, e.g. "zh" or "en-rUS".
   *
   * @param localeString the locale description
   * @return the corresponding locale
   */
  @NotNull
  public static Locale create(@NotNull String localeString) {
    LanguageQualifier language;
    RegionQualifier region;

    // Load locale. Note that this can get overwritten by the
    // project-wide settings read below.
    int index = localeString.indexOf('-');
    if (index != -1) {
      language = new LanguageQualifier(localeString.substring(0, index));
      assert localeString.charAt(index + 1) == 'r' : localeString;
      region = new RegionQualifier(localeString.substring(index + 2));
    } else {
      assert localeString.length() == 2 : localeString;
      assert !localeString.equals(LanguageQualifier.FAKE_LANG_VALUE);
      language = new LanguageQualifier(localeString);
      region = ANY_REGION;
    }

    return new Locale(language, region);
  }

  /**
   * Returns a flag image to use for this locale
   *
   * @return a flag image, or a default globe icon
   */
  @NotNull
  public Icon getFlagImage() {
    String languageCode = hasLanguage() ? language.getValue() : null;
    String regionCode = hasRegion() ? region.getValue() : null;
    FlagManager icons = FlagManager.get();
    if (languageCode == null && regionCode == null) {
      return AndroidIcons.Globe;
    }
    else {
      Icon image = icons.getFlag(languageCode, regionCode);
      if (image == null) {
        image = AndroidIcons.EmptyFlag;
      }

      return image;
    }
  }

  /**
   * Returns true if this locale specifies a specific language. This is true
   * for all locales except {@link #ANY}.
   *
   * @return true if this locale specifies a specific language
   */
  public boolean hasLanguage() {
    return language != ANY_LANGUAGE;
  }

  /**
   * Returns true if this locale specifies a specific region
   *
   * @return true if this locale specifies a region
   */
  public boolean hasRegion() {
    return region != ANY_REGION;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + language.hashCode();
    result = prime * result + region.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Locale other = (Locale)obj;
    if (!language.equals(other.language)) return false;
    if (!region.equals(other.region)) return false;
    return true;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).omitNullValues().addValue(language.getValue()).addValue(region.getValue()).toString();
  }

  /**
   * Comparator for comparing locales by language names (and as a secondary key, the region names)
   */
  public static final Comparator<Locale> LANGUAGE_NAME_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale locale1, Locale locale2) {
      LanguageQualifier language1 = locale1.language;
      LanguageQualifier language2 = locale2.language;
      if (language1 == ANY_LANGUAGE) {
        return language2 == ANY_LANGUAGE ? 0 : -1;
      }
      else if (language2 == ANY_LANGUAGE) {
        return 1;
      }
      String name1 = LocaleManager.getLanguageName(language1.getValue());
      String name2 = LocaleManager.getLanguageName(language2.getValue());
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
      LanguageQualifier language1 = locale1.language;
      LanguageQualifier language2 = locale2.language;
      if (language1 == ANY_LANGUAGE) {
        return language2 == ANY_LANGUAGE ? 0 : -1;
      }
      else if (language2 == ANY_LANGUAGE) {
        return 1;
      }
      String code1 = language1.getValue();
      String code2 = language2.getValue();
      int compare = StringUtil.compare(code1, code2, false);
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
      RegionQualifier region1 = locale1.region;
      RegionQualifier region2 = locale2.region;
      if (region1 == ANY_REGION) {
        return region2 == ANY_REGION ? 0 : -1;
      } else if (region2 == ANY_REGION) {
        return 1;
      }
      String language1 = LocaleManager.getRegionName(region1.getValue());
      String language2 = LocaleManager.getRegionName(region2.getValue());
      return StringUtil.compare(language1, language2, false);
    }
  };

  /**
   * Comparator for comparing locales by region ISO codes
   */
  public static final Comparator<Locale> REGION_CODE_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale locale1, Locale locale2) {
      RegionQualifier region1 = locale1.region;
      RegionQualifier region2 = locale2.region;
      if (region1 == ANY_REGION) {
        return region2 == ANY_REGION ? 0 : -1;
      } else if (region2 == ANY_REGION) {
        return 1;
      }
      String code1 = region1.getValue();
      String code2 = region2.getValue();
      return StringUtil.compare(code1, code2, false);
    }
  };
}
