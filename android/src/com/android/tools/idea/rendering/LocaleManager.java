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

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.IconLoader;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * The {@linkplain LocaleManager} provides access to locale information such as
 * language names and flag icons for the various locales.
 * <p/>
 * All the flag images came from the WindowBuilder subversion repository
 * http://dev.eclipse.org/svnroot/tools/org.eclipse.windowbuilder/trunk (and in
 * particular, a snapshot of revision 424). However, it appears that the icons
 * are from http://www.famfamfam.com/lab/icons/flags/ which states that "these
 * flag icons are available for free use for any purpose with no requirement for
 * attribution." Adding the URL here such that we can check back occasionally
 * and see if there are corrections or updates. Also note that the flag names
 * are in ISO 3166-1 alpha-2 country codes.
 */
@SuppressWarnings("SpellCheckingInspection")
public class LocaleManager {
  private static final LocaleManager ourInstance = new LocaleManager();

  /**
   * Returns the {@linkplain LocaleManager} singleton
   *
   * @return the {@linkplain LocaleManager} singleton, never null
   */
  @NotNull
  public static LocaleManager get() {
    return ourInstance;
  }

  /**
   * Use the {@link #get()} factory method
   */
  private LocaleManager() {
  }

  /**
   * Map from region to flag icon
   */
  @NotNull
  private final Map<String, Icon> myImageMap = Maps.newHashMap();

  /**
   * Map of default bindings from language to country (if a region is not
   * specified). Note that if a given language is the language of the default
   * locale on the user's machine, then the country corresponding to that
   * locale is used. Thus, even if for example the default binding of the "en"
   * language is "US", if the current locale has language="en" and the country
   * for that locale is "GB", then "GB" will be used.
   */
  @NotNull
  private static final Map<String, String> ourLanguageToCountry = Maps.newHashMapWithExpectedSize(177);

  /**
   * Names of the various languages according to ISO 639-1
   */
  @NotNull
  private static final Map<String, String> ourLanguageNames = Maps.newHashMapWithExpectedSize(187);

  /**
   * Names of the various regions according to ISO 3166-1
   */
  @NotNull
  private static final Map<String, String> ourRegionNames = Maps.newHashMapWithExpectedSize(249);

  /**
   * Returns the flag for the given language and region.
   *
   * @param language the language, or null (if null, region must not be null),
   *                 the 2 letter language code (ISO 639-1), in lower case
   * @param region   the region, or null (if null, language must not be null),
   *                 the 2 letter region code (ISO 3166-1 alpha-2), in upper case
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@Nullable String language, @Nullable String region) {
    assert region != null || language != null;
    if (region == null || region.isEmpty()) {
      // Look up the region for a given language
      assert language != null;

      // Prefer the local registration of the current locale; even if
      // for example the default locale for English is the US, if the current
      // default locale is English, then use its associated country, which could
      // for example be Australia.
      @SuppressWarnings("UnnecessaryFullyQualifiedName")
      java.util.Locale locale = java.util.Locale.getDefault();
      if (language.equals(locale.getLanguage())) {
        Icon flag = getFlag(locale.getCountry());
        if (flag != null) {
          return flag;
        }
      }

      // Special cases where we have a dedicated flag available:
      if (language.equals("ca")) {        //$NON-NLS-1$
        region = "catalonia";           //$NON-NLS-1$
      }
      else if (language.equals("gd")) { //$NON-NLS-1$
        region = "scotland";            //$NON-NLS-1$
      }
      else if (language.equals("cy")) { //$NON-NLS-1$
        region = "wales";               //$NON-NLS-1$
      }
      else {
        // Attempt to look up the country from the language
        region = ourLanguageToCountry.get(language);
      }
    }

    if (region == null || region.isEmpty()) {
      // No country specified, and the language is for a country we
      // don't have a flag for
      return null;
    }

    return getIcon(region);
  }

  /**
   * Returns the flag for the given language and region.
   *
   * @param language the language qualifier, or null (if null, region must not be null),
   * @param region   the region, or null (if null, language must not be null),
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@Nullable LanguageQualifier language, @Nullable RegionQualifier region) {
    String languageCode = language != null ? language.getValue() : null;
    String regionCode = region != null ? region.getValue() : null;
    if (LanguageQualifier.FAKE_LANG_VALUE.equals(languageCode)) {
      languageCode = null;
    }
    if (RegionQualifier.FAKE_REGION_VALUE.equals(regionCode)) {
      regionCode = null;
    }
    return getFlag(languageCode, regionCode);
  }

  /**
   * Returns a flag for a given resource folder name (such as
   * {@code values-en-rUS}), or null
   *
   * @param folder the folder name
   * @return a corresponding flag icon, or null if none was found
   */
  @Nullable
  public Icon getFlagForFolderName(@NotNull String folder) {
    RegionQualifier region = null;
    LanguageQualifier language = null;
    for (String qualifier : Splitter.on('-').split(folder)) {
      if (qualifier.length() == 3) {
        region = RegionQualifier.getQualifier(qualifier);
        if (region != null) {
          break;
        }
      }
      else if (qualifier.length() == 2 && language == null) {
        language = LanguageQualifier.getQualifier(qualifier);
      }
    }
    if (region != null || language != null) {
      return get().getFlag(language, region);
    }

    return null;
  }

  /**
   * Returns the flag for the given region.
   *
   * @param region the 2 letter region code (ISO 3166-1 alpha-2), in upper case
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@NotNull String region) {
    assert region.length() == 2 && Character.isUpperCase(region.charAt(0)) && Character.isUpperCase(region.charAt(1)) : region;

    return getIcon(region);
  }

  @Nullable
  private Icon getIcon(@NotNull String base) {
    Icon flagImage = myImageMap.get(base);
    if (flagImage == null) {
      // TODO: Special case locale currently running on system such
      // that the current country matches the current locale
      if (myImageMap.containsKey(base)) {
        // Already checked: there's just no image there
        return null;
      }
      @SuppressWarnings("UnnecessaryFullyQualifiedName")
      String flagFileName = base.toLowerCase(java.util.Locale.US) + ".png"; //$NON-NLS-1$
      flagImage = IconLoader.getIcon("/icons/flags/" + flagFileName, AndroidIcons.class);
      myImageMap.put(base, flagImage);
    }

    return flagImage;
  }

  /**
   * Returns the name of the given region for a 2 letter region code, in English.
   *
   * @param regionCode the 2 letter region code (ISO 3166-1 alpha-2)
   * @return the name of the given region for a region code, in English, or
   *         null if not known
   */
  @Nullable
  public static String getRegionName(@NotNull String regionCode) {
    assert regionCode.length() == 2 &&
           Character.isUpperCase(regionCode.charAt(0)) &&
           Character.isUpperCase(regionCode.charAt(1)) : regionCode;

    return ourRegionNames.get(regionCode);
  }

  /**
   * Returns the name of the given language for a language code, in English.
   *
   * @param languageCode the 2 letter language code (ISO 639-1)
   * @return the name of the given language for a language code, in English, or
   *         null if not known
   */
  @Nullable
  public static String getLanguageName(@NotNull String languageCode) {
    assert languageCode.length() == 2 &&
           Character.isLowerCase(languageCode.charAt(0)) &&
           Character.isLowerCase(languageCode.charAt(1)) : languageCode;

    return ourLanguageNames.get(languageCode);
  }

  /**
   * Returns all the known language codes
   *
   * @return all the known language codes
   */
  @NotNull
  public static Set<String> getLanguageCodes() {
    return Collections.unmodifiableSet(ourLanguageNames.keySet());
  }

  /**
   * Returns all the known region codes
   *
   * @return all the known region codes
   */
  @NotNull
  public static Set<String> getRegionCodes() {
    return Collections.unmodifiableSet(ourRegionNames.keySet());
  }

  /**
   * Populate the various maps.
   * <p>
   * The language to region mapping was constructed by using the ISO 639-1 table from
   * http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
   * and for each language, looking up the corresponding Wikipedia entry
   * and picking the first mentioned or in some cases largest country where
   * the language is spoken, then mapping that back to the corresponding ISO 3166-1
   * code.
   */
  static {
    // Afar -> Ethiopia
    ourLanguageToCountry.put("aa", "ET"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("aa", "Afar"); //$NON-NLS-1$

    // "ab": Abkhaz -> Abkhazia, Georgia
    ourLanguageToCountry.put("ab", "GE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ab", "Abkhaz"); //$NON-NLS-1$

    // "af": Afrikaans  -> South Africa, Namibia
    ourLanguageToCountry.put("af", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("af", "Afrikaans"); //$NON-NLS-1$

    // "ak": Akan -> Ghana, Ivory Coast
    ourLanguageToCountry.put("ak", "GH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ak", "Akan"); //$NON-NLS-1$

    // "am": Amharic -> Ethiopia
    ourLanguageToCountry.put("am", "ET"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("am", "Amharic"); //$NON-NLS-1$

    // "an": Aragonese  -> Aragon in Spain
    ourLanguageToCountry.put("an", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("an", "Aragonese"); //$NON-NLS-1$

    // "ar": Arabic -> United Arab Emirates, Kuwait, Oman, Saudi Arabia, Qatar, and Bahrain
    ourLanguageToCountry.put("ar", "AE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ar", "Arabic"); //$NON-NLS-1$

    // "as": Assamese -> India
    ourLanguageToCountry.put("as", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("as", "Assamese"); //$NON-NLS-1$

    // "av": Avaric -> Azerbaijan
    ourLanguageToCountry.put("av", "AZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("av", "Avaric"); //$NON-NLS-1$

    // "ay": Aymara -> Bolivia
    ourLanguageToCountry.put("ay", "BO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ay", "Aymara"); //$NON-NLS-1$

    // "az": Azerbaijani -> Azerbaijan
    ourLanguageToCountry.put("az", "AZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("az", "Azerbaijani"); //$NON-NLS-1$

    // "ba": Bashkir -> Russia
    ourLanguageToCountry.put("ba", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ba", "Bashkir"); //$NON-NLS-1$

    // "be": Belarusian -> Belarus
    ourLanguageToCountry.put("be", "BY"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("be", "Belarusian"); //$NON-NLS-1$

    // "bg": Bulgarian -> Bulgaria
    ourLanguageToCountry.put("bg", "BG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bg", "Bulgarian"); //$NON-NLS-1$

    // "bh": Bihari languages -> India, Nepal
    ourLanguageToCountry.put("bh", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bh", "Bihari languages"); //$NON-NLS-1$

    // "bi": Bislama -> Vanatu
    ourLanguageToCountry.put("bi", "VU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bi", "Bislama"); //$NON-NLS-1$

    // "bm": Bambara -> Mali
    ourLanguageToCountry.put("bm", "ML"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bm", "Bambara"); //$NON-NLS-1$

    // "bn": Bengali -> Bangladesh, India
    ourLanguageToCountry.put("bn", "BD"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bn", "Bengali"); //$NON-NLS-1$

    // "bo": Tibetan -> China
    ourLanguageToCountry.put("bo", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bo", "Tibetan"); //$NON-NLS-1$

    // "br": Breton -> France
    ourLanguageToCountry.put("br", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("br", "Breton"); //$NON-NLS-1$

    // "bs": Bosnian -> Bosnia and Herzegovina
    ourLanguageToCountry.put("bs", "BA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("bs", "Bosnian"); //$NON-NLS-1$

    // "ca": Catalan -> Andorra, Catalonia
    ourLanguageToCountry.put("ca", "AD"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ca", "Catalan"); //$NON-NLS-1$

    // "ce": Chechen -> Russia
    ourLanguageToCountry.put("ce", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ce", "Chechen"); //$NON-NLS-1$

    // "ch": Chamorro -> Guam, Northern Mariana Islands
    ourLanguageToCountry.put("ch", "GU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ch", "Chamorro"); //$NON-NLS-1$

    // "co": Corsican -> France
    ourLanguageToCountry.put("co", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("co", "Corsican"); //$NON-NLS-1$

    // "cr": Cree -> Canada and United States
    ourLanguageToCountry.put("cr", "CA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("cr", "Cree"); //$NON-NLS-1$

    // "cs": Czech -> Czech Republic
    ourLanguageToCountry.put("cs", "CZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("cs", "Czech"); //$NON-NLS-1$

    // "cv": Chuvash -> Russia, Kazakhstan, Ukraine, Uzbekistan...
    ourLanguageToCountry.put("cv", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("cv", "Chuvash"); //$NON-NLS-1$

    // "cy": Welsh -> Wales (no 3166 code; using GB)
    ourLanguageToCountry.put("cy", "GB"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("cy", "Welsh"); //$NON-NLS-1$

    // "da": Danish -> Denmark
    ourLanguageToCountry.put("da", "DK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("da", "Danish"); //$NON-NLS-1$

    // "de": German -> Germany
    ourLanguageToCountry.put("de", "DE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("de", "German"); //$NON-NLS-1$

    // "dv": Divehi -> Maldives
    ourLanguageToCountry.put("dv", "MV"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("dv", "Divehi"); //$NON-NLS-1$

    // "dz": Dzongkha -> Bhutan
    ourLanguageToCountry.put("dz", "BT"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("dz", "Dzongkha"); //$NON-NLS-1$

    // "ee": Ewe -> Ghana, Togo
    ourLanguageToCountry.put("ee", "GH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ee", "Ewe"); //$NON-NLS-1$

    // "el": Greek -> Greece
    ourLanguageToCountry.put("el", "GR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("el", "Greek"); //$NON-NLS-1$

    // "en": English -> United States, United Kingdom, Australia, ...
    ourLanguageToCountry.put("en", "US"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("en", "English"); //$NON-NLS-1$

    // "es": Spanish -> Spain, Mexico, ...
    ourLanguageToCountry.put("es", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("es", "Spanish"); //$NON-NLS-1$

    // "et": Estonian ->
    ourLanguageToCountry.put("et", "EE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("et", "Estonian"); //$NON-NLS-1$

    // "eu": Basque -> Spain, France
    ourLanguageToCountry.put("eu", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("eu", "Basque"); //$NON-NLS-1$

    // "fa": Persian -> Iran, Afghanistan
    ourLanguageToCountry.put("fa", "IR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("fa", "Persian"); //$NON-NLS-1$

    // "ff": Fulah -> Mauritania, Senegal, Mali, Guinea, Burkina Faso, ...
    ourLanguageToCountry.put("ff", "MR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ff", "Fulah"); //$NON-NLS-1$

    // "fi": Finnish -> Finland
    ourLanguageToCountry.put("fi", "FI"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("fi", "Finnish"); //$NON-NLS-1$

    // "fj": Fijian -> Fiji
    ourLanguageToCountry.put("fj", "FJ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("fj", "Fijian"); //$NON-NLS-1$

    // "fo": Faroese -> Denmark
    ourLanguageToCountry.put("fo", "DK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("fo", "Faroese"); //$NON-NLS-1$

    // "fr": French -> France
    ourLanguageToCountry.put("fr", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("fr", "French"); //$NON-NLS-1$

    // "fy": Western Frisian -> Netherlands
    ourLanguageToCountry.put("fy", "NL"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("fy", "Western Frisian"); //$NON-NLS-1$

    // "ga": Irish -> Ireland
    ourLanguageToCountry.put("ga", "IE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ga", "Irish"); //$NON-NLS-1$

    // "gd": Gaelic -> Scotland
    ourLanguageToCountry.put("gd", "GB"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("gd", "Gaelic"); //$NON-NLS-1$

    // "gl": Galician -> Galicia/Spain
    ourLanguageToCountry.put("gl", "ES"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("gl", "Galician"); //$NON-NLS-1$

    // "gn": Guaraní -> Paraguay
    ourLanguageToCountry.put("gn", "PY"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("gn", "Guaran\u00ed" /*Guaraní*/); //$NON-NLS-1$

    // "gu": Gujarati -> India
    ourLanguageToCountry.put("gu", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("gu", "Gujarati"); //$NON-NLS-1$

    // "gv": Manx -> Isle of Man
    // We don't have an icon for IM
    //ourLanguageToCountry.put("gv", "IM"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("gv", "Manx"); //$NON-NLS-1$

    // "ha": Hausa -> Nigeria, Niger
    ourLanguageToCountry.put("ha", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ha", "Hausa"); //$NON-NLS-1$

    // "he": Hebrew -> Israel
    ourLanguageToCountry.put("he", "IL"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("he", "Hebrew"); //$NON-NLS-1$

    // "hi": Hindi -> India
    ourLanguageToCountry.put("hi", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("hi", "Hindi"); //$NON-NLS-1$

    // "ho": Hiri Motu -> Papua New Guinea
    ourLanguageToCountry.put("ho", "PG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ho", "Hiri Motu"); //$NON-NLS-1$

    // "hr": Croatian ->
    ourLanguageToCountry.put("hr", "HR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("hr", "Croatian"); //$NON-NLS-1$

    // "ht": Haitian -> Haiti
    ourLanguageToCountry.put("ht", "HT"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ht", "Haitian"); //$NON-NLS-1$

    // "hu": Hungarian -> Hungary
    ourLanguageToCountry.put("hu", "HU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("hu", "Hungarian"); //$NON-NLS-1$

    // "hy": Armenian -> Armenia
    ourLanguageToCountry.put("hy", "AM"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("hy", "Armenian"); //$NON-NLS-1$

    // "hz": Herero -> Namibia, Botswana
    ourLanguageToCountry.put("hz", "NA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("hz", "Herero"); //$NON-NLS-1$

    // "id": Indonesian -> Indonesia
    ourLanguageToCountry.put("id", "ID"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("id", "Indonesian"); //$NON-NLS-1$

    // "ig": Igbo ->
    ourLanguageToCountry.put("ig", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ig", "Igbo"); //$NON-NLS-1$

    // "ii": Nuosu -> China
    ourLanguageToCountry.put("ii", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ii", "Nuosu"); //$NON-NLS-1$

    // "ik": Inupiaq -> USA
    ourLanguageToCountry.put("ik", "US"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ik", "Inupiaq"); //$NON-NLS-1$

    // "is": Icelandic -> Iceland
    ourLanguageToCountry.put("is", "IS"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("is", "Icelandic"); //$NON-NLS-1$

    // "it": Italian -> Italy
    ourLanguageToCountry.put("it", "IT"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("it", "Italian"); //$NON-NLS-1$

    // "iu": Inuktitut -> Canada
    ourLanguageToCountry.put("iu", "CA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("iu", "Inuktitut"); //$NON-NLS-1$

    // "ja": Japanese -> Japan
    ourLanguageToCountry.put("ja", "JP"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ja", "Japanese"); //$NON-NLS-1$

    // "jv": Javanese -> Indonesia
    ourLanguageToCountry.put("jv", "ID"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("jv", "Javanese"); //$NON-NLS-1$

    // "ka": Georgian -> Georgia
    ourLanguageToCountry.put("ka", "GE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ka", "Georgian"); //$NON-NLS-1$

    // "kg": Kongo -> Angola, Congo
    ourLanguageToCountry.put("kg", "AO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kg", "Kongo"); //$NON-NLS-1$

    // "ki": Kikuyu -> Kenya
    ourLanguageToCountry.put("ki", "KE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ki", "Kikuyu"); //$NON-NLS-1$

    // "kj": Kwanyama -> Angola, Namibia
    ourLanguageToCountry.put("kj", "AO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kj", "Kwanyama"); //$NON-NLS-1$

    // "kk": Kazakh -> Kazakhstan
    ourLanguageToCountry.put("kk", "KZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kk", "Kazakh"); //$NON-NLS-1$

    // "kl": Kalaallisut -> Denmark
    ourLanguageToCountry.put("kl", "DK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kl", "Kalaallisut"); //$NON-NLS-1$

    // "km": Khmer -> Cambodia
    ourLanguageToCountry.put("km", "KH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("km", "Khmer"); //$NON-NLS-1$

    // "kn": Kannada -> India
    ourLanguageToCountry.put("kn", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kn", "Kannada"); //$NON-NLS-1$

    // "ko": Korean -> Korea
    ourLanguageToCountry.put("ko", "KR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ko", "Korean"); //$NON-NLS-1$

    // "kr": Kanuri -> Nigeria
    ourLanguageToCountry.put("kr", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kr", "Kanuri"); //$NON-NLS-1$

    // "ks": Kashmiri -> India
    ourLanguageToCountry.put("ks", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ks", "Kashmiri"); //$NON-NLS-1$

    // "ku": Kurdish -> Turkey, Iran, Iraq, Syria, Armenia, Azerbaijan
    ourLanguageToCountry.put("ku", "TR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ku", "Kurdish"); //$NON-NLS-1$

    // "kv": Komi -> Russia
    ourLanguageToCountry.put("kv", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kv", "Komi"); //$NON-NLS-1$

    // "kw": Cornish -> UK
    ourLanguageToCountry.put("kw", "GB"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("kw", "Cornish"); //$NON-NLS-1$

    // "ky": Kyrgyz -> Kyrgyzstan
    ourLanguageToCountry.put("ky", "KG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ky", "Kyrgyz"); //$NON-NLS-1$

    // "lb": Luxembourgish -> Luxembourg
    ourLanguageToCountry.put("lb", "LU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("lb", "Luxembourgish"); //$NON-NLS-1$

    // "lg": Ganda -> Uganda
    ourLanguageToCountry.put("lg", "UG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("lg", "Ganda"); //$NON-NLS-1$

    // "li": Limburgish -> Netherlands
    ourLanguageToCountry.put("li", "NL"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("li", "Limburgish"); //$NON-NLS-1$

    // "ln": Lingala -> Congo
    ourLanguageToCountry.put("ln", "CD"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ln", "Lingala"); //$NON-NLS-1$

    // "lo": Lao -> Laos
    ourLanguageToCountry.put("lo", "LA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("lo", "Lao"); //$NON-NLS-1$

    // "lt": Lithuanian -> Lithuania
    ourLanguageToCountry.put("lt", "LT"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("lt", "Lithuanian"); //$NON-NLS-1$

    // "lu": Luba-Katanga -> Congo
    ourLanguageToCountry.put("lu", "CD"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("lu", "Luba-Katanga"); //$NON-NLS-1$

    // "lv": Latvian -> Latvia
    ourLanguageToCountry.put("lv", "LV"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("lv", "Latvian"); //$NON-NLS-1$

    // "mg": Malagasy -> Madagascar
    ourLanguageToCountry.put("mg", "MG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mg", "Malagasy"); //$NON-NLS-1$

    // "mh": Marshallese -> Marshall Islands
    ourLanguageToCountry.put("mh", "MH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mh", "Marshallese"); //$NON-NLS-1$

    // "mi": Maori -> New Zealand
    ourLanguageToCountry.put("mi", "NZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mi", "M\u0101ori"); //$NON-NLS-1$

    // "mk": Macedonian -> Macedonia
    ourLanguageToCountry.put("mk", "MK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mk", "Macedonian"); //$NON-NLS-1$

    // "ml": Malayalam -> India
    ourLanguageToCountry.put("ml", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ml", "Malayalam"); //$NON-NLS-1$

    // "mn": Mongolian -> Mongolia
    ourLanguageToCountry.put("mn", "MN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mn", "Mongolian"); //$NON-NLS-1$

    // "mr": Marathi -> India
    ourLanguageToCountry.put("mr", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mr", "Marathi"); //$NON-NLS-1$

    // "ms": Malay -> Malaysia, Indonesia ...
    ourLanguageToCountry.put("ms", "MY"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ms", "Malay"); //$NON-NLS-1$

    // "mt": Maltese -> Malta
    ourLanguageToCountry.put("mt", "MT"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("mt", "Maltese"); //$NON-NLS-1$

    // "my": Burmese -> Myanmar
    ourLanguageToCountry.put("my", "MM"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("my", "Burmese"); //$NON-NLS-1$

    // "na": Nauru -> Nauru
    ourLanguageToCountry.put("na", "NR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("na", "Nauru"); //$NON-NLS-1$

    // "nb": Norwegian -> Norway
    ourLanguageToCountry.put("nb", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("nb", "Norwegian Bokm\u00e5l" /*Norwegian Bokmål*/); //$NON-NLS-1$

    // "nd": North Ndebele -> Zimbabwe
    ourLanguageToCountry.put("nd", "ZW"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("nd", "North Ndebele"); //$NON-NLS-1$

    // "ne": Nepali -> Nepal
    ourLanguageToCountry.put("ne", "NP"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ne", "Nepali"); //$NON-NLS-1$

    // "ng":Ndonga  -> Namibia
    ourLanguageToCountry.put("ng", "NA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ng", "Ndonga"); //$NON-NLS-1$

    // "nl": Dutch -> Netherlands
    ourLanguageToCountry.put("nl", "NL"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("nl", "Dutch"); //$NON-NLS-1$

    // "nn": Norwegian Nynorsk -> Norway
    ourLanguageToCountry.put("nn", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("nn", "Norwegian Nynorsk"); //$NON-NLS-1$

    // "no": Norwegian -> Norway
    ourLanguageToCountry.put("no", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("no", "Norwegian"); //$NON-NLS-1$

    // "nr": South Ndebele -> South Africa
    ourLanguageToCountry.put("nr", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("nr", "South Ndebele"); //$NON-NLS-1$

    // "nv": Navajo -> USA
    ourLanguageToCountry.put("nv", "US"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("nv", "Navajo"); //$NON-NLS-1$

    // "ny": Chichewa -> Malawi, Zambia
    ourLanguageToCountry.put("ny", "MW"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ny", "Chichewa"); //$NON-NLS-1$

    // "oc": Occitan -> France, Italy, Spain, Monaco
    ourLanguageToCountry.put("oc", "FR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("oc", "Occitan"); //$NON-NLS-1$

    // "oj": Ojibwe -> Canada, United States
    ourLanguageToCountry.put("oj", "CA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("oj", "Ojibwe"); //$NON-NLS-1$

    // "om": Oromo -> Ethiopia
    ourLanguageToCountry.put("om", "ET"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("om", "Oromo"); //$NON-NLS-1$

    // "or": Oriya -> India
    ourLanguageToCountry.put("or", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("or", "Oriya"); //$NON-NLS-1$

    // "os": Ossetian -> Russia (North Ossetia), Georgia
    ourLanguageToCountry.put("os", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("os", "Ossetian"); //$NON-NLS-1$

    // "pa": Panjabi, -> Pakistan, India
    ourLanguageToCountry.put("pa", "PK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("pa", "Panjabi"); //$NON-NLS-1$

    // "pl": Polish -> Poland
    ourLanguageToCountry.put("pl", "PL"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("pl", "Polish"); //$NON-NLS-1$

    // "ps": Pashto -> Afghanistan, Pakistan
    ourLanguageToCountry.put("ps", "AF"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ps", "Pashto"); //$NON-NLS-1$

    // "pt": Portuguese -> Brazil, Portugal, ...
    ourLanguageToCountry.put("pt", "BR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("pt", "Portuguese"); //$NON-NLS-1$

    // "qu": Quechua -> Peru, Bolivia
    ourLanguageToCountry.put("qu", "PE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("qu", "Quechua"); //$NON-NLS-1$

    // "rm": Romansh -> Switzerland
    ourLanguageToCountry.put("rm", "CH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("rm", "Romansh"); //$NON-NLS-1$

    // "rn": Kirundi -> Burundi, Uganda
    ourLanguageToCountry.put("rn", "BI"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("rn", "Kirundi"); //$NON-NLS-1$

    // "ro": Romanian -> Romania, Republic of Moldova
    ourLanguageToCountry.put("ro", "RO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ro", "Romanian"); //$NON-NLS-1$

    // "ru": Russian -> Russia
    ourLanguageToCountry.put("ru", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ru", "Russian"); //$NON-NLS-1$

    // "rw": Kinyarwanda -> Rwanda, Uganda, Democratic Republic of the Congo
    ourLanguageToCountry.put("rw", "RW"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("rw", "Kinyarwanda"); //$NON-NLS-1$

    // "sa": Sanskrit -> India
    ourLanguageToCountry.put("sa", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sa", "Sanskrit"); //$NON-NLS-1$

    // "sc": Sardinian -> Italy
    ourLanguageToCountry.put("sc", "IT"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sc", "Sardinian"); //$NON-NLS-1$

    // "sd": Sindhi -> Pakistan, India
    ourLanguageToCountry.put("sd", "PK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sd", "Sindhi"); //$NON-NLS-1$

    // "se": Northern Sami -> Norway, Sweden, Finland
    ourLanguageToCountry.put("se", "NO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("se", "Northern Sami"); //$NON-NLS-1$

    // "sg": Sango -> Central African Republic
    ourLanguageToCountry.put("sg", "CF"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sg", "Sango"); //$NON-NLS-1$

    // "si": Sinhala ->  Sri Lanka
    ourLanguageToCountry.put("si", "LK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("si", "Sinhala"); //$NON-NLS-1$

    // "sk": Slovak -> Slovakia
    ourLanguageToCountry.put("sk", "SK"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sk", "Slovak"); //$NON-NLS-1$

    // "sl": Slovene -> Slovenia
    ourLanguageToCountry.put("sl", "SI"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sl", "Slovene"); //$NON-NLS-1$

    // "sm": Samoan -> Samoa
    ourLanguageToCountry.put("sm", "WS"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sm", "Samoan"); //$NON-NLS-1$

    // "sn": Shona -> Zimbabwe
    ourLanguageToCountry.put("sn", "ZW"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sn", "Shona"); //$NON-NLS-1$

    // "so": Somali -> Somalia
    ourLanguageToCountry.put("so", "SO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("so", "Somali"); //$NON-NLS-1$

    // "sq": Albanian -> Albania
    ourLanguageToCountry.put("sq", "AL"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sq", "Albanian"); //$NON-NLS-1$

    // "sr": Serbian -> Serbia, Bosnia and Herzegovina
    ourLanguageToCountry.put("sr", "RS"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sr", "Serbian"); //$NON-NLS-1$

    // "ss": Swati -> Swaziland
    ourLanguageToCountry.put("ss", "SZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ss", "Swati"); //$NON-NLS-1$

    // "st": Southern Sotho -> Lesotho, South Africa
    ourLanguageToCountry.put("st", "LS"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("st", "Southern Sotho"); //$NON-NLS-1$

    // "su": Sundanese -> Indoniesia
    ourLanguageToCountry.put("su", "ID"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("su", "Sundanese"); //$NON-NLS-1$

    // "sv": Swedish -> Sweden
    ourLanguageToCountry.put("sv", "SE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sv", "Swedish"); //$NON-NLS-1$

    // "sw": Swahili -> Tanzania, Kenya, and Congo (DRC)
    ourLanguageToCountry.put("sw", "TZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("sw", "Swahili"); //$NON-NLS-1$

    // "ta": Tamil -> India, Sri Lanka
    ourLanguageToCountry.put("ta", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ta", "Tamil"); //$NON-NLS-1$

    // "te": Telugu -> India
    ourLanguageToCountry.put("te", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("te", "Telugu"); //$NON-NLS-1$

    // "tg": Tajik -> Tajikistan, Uzbekistan, Russia, Afghanistan
    ourLanguageToCountry.put("tg", "TJ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tg", "Tajik"); //$NON-NLS-1$

    // "th": Thai -> Thailand
    ourLanguageToCountry.put("th", "TH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("th", "Thai"); //$NON-NLS-1$

    // "ti": Tigrinya -> Eritrea, Ethiopia
    ourLanguageToCountry.put("ti", "ER"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ti", "Tigrinya"); //$NON-NLS-1$

    // "tk": Turkmen -> Turkmenistan
    ourLanguageToCountry.put("tk", "TM"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tk", "Turkmen"); //$NON-NLS-1$

    // "tl": Tagalog -> Philippines
    ourLanguageToCountry.put("tl", "PH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tl", "Tagalog"); //$NON-NLS-1$

    // "tn": Tswana -> Botswana, South Africa,
    ourLanguageToCountry.put("tn", "BW"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tn", "Tswana"); //$NON-NLS-1$

    // "to": Tonga -> Tonga
    ourLanguageToCountry.put("to", "TO"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("to", "Tonga"); //$NON-NLS-1$

    // "tr": Turkish -> Turkey
    ourLanguageToCountry.put("tr", "TR"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tr", "Turkish"); //$NON-NLS-1$

    // "ts": Tsonga -> Mozambique, South Africa
    ourLanguageToCountry.put("ts", "MZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ts", "Tsonga"); //$NON-NLS-1$

    // "tt": Tatar -> Russia
    ourLanguageToCountry.put("tt", "RU"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tt", "Tatar"); //$NON-NLS-1$

    // "tw": Twi -> Ghana, Ivory Coast
    ourLanguageToCountry.put("tw", "GH"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("tw", "Twi"); //$NON-NLS-1$

    // "ty": Tahitian -> French Polynesia
    ourLanguageToCountry.put("ty", "PF"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ty", "Tahitian"); //$NON-NLS-1$

    // "ug": Uighur -> China, Kazakhstan
    ourLanguageToCountry.put("ug", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ug", "Uighur"); //$NON-NLS-1$

    // "uk": Ukrainian -> Ukraine
    ourLanguageToCountry.put("uk", "UA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("uk", "Ukrainian"); //$NON-NLS-1$

    // "ur": Urdu -> India, Pakistan
    ourLanguageToCountry.put("ur", "IN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ur", "Urdu"); //$NON-NLS-1$

    // "uz": Uzbek -> Uzbekistan
    ourLanguageToCountry.put("uz", "UZ"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("uz", "Uzbek"); //$NON-NLS-1$

    // "ve": Venda -> South Africa, Zimbabwe
    ourLanguageToCountry.put("ve", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ve", "Venda"); //$NON-NLS-1$

    // "vi": Vietnamese -> Vietnam
    ourLanguageToCountry.put("vi", "VN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("vi", "Vietnamese"); //$NON-NLS-1$

    // "wa": Walloon -> Belgium, France
    ourLanguageToCountry.put("wa", "BE"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("wa", "Walloon"); //$NON-NLS-1$

    // "wo": Wolof -> Senegal, Gambia, Mauritania
    ourLanguageToCountry.put("wo", "SN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("wo", "Wolof"); //$NON-NLS-1$

    // "xh": Xhosa -> South Africa, Lesotho
    ourLanguageToCountry.put("xh", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("xh", "Xhosa"); //$NON-NLS-1$

    // "yi": Yiddish -> United States, Israel, Argentina, Brazil, ...
    ourLanguageToCountry.put("yi", "US"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("yi", "Yiddish"); //$NON-NLS-1$

    // "yo": Yorùbá -> Nigeria, Togo, Benin
    ourLanguageToCountry.put("yo", "NG"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("yo", "Yor\u00f9b\u00e1" /*Yorùbá*/); //$NON-NLS-1$

    // "za": Zhuang -> China
    ourLanguageToCountry.put("za", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("za", "Zhuang"); //$NON-NLS-1$

    // "zh": Chinese -> China, Taiwan, Singapore
    ourLanguageToCountry.put("zh", "CN"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("zh", "Chinese"); //$NON-NLS-1$

    // "zu": Zulu -> South Africa
    ourLanguageToCountry.put("zu", "ZA"); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("zu", "Zulu"); //$NON-NLS-1$

    // Region Name Map, ISO_3166-1, alpha-2
    ourRegionNames.put("AD", "Andorra"); //$NON-NLS-1$
    ourRegionNames.put("AE", "United Arab Emirates"); //$NON-NLS-1$
    ourRegionNames.put("AF", "Afghanistan"); //$NON-NLS-1$
    ourRegionNames.put("AG", "Antigua and Barbuda"); //$NON-NLS-1$
    ourRegionNames.put("AI", "Anguilla"); //$NON-NLS-1$
    ourRegionNames.put("AL", "Albania"); //$NON-NLS-1$
    ourRegionNames.put("AM", "Armenia"); //$NON-NLS-1$
    ourRegionNames.put("AO", "Angola"); //$NON-NLS-1$
    ourRegionNames.put("AQ", "Antarctica"); //$NON-NLS-1$
    ourRegionNames.put("AR", "Argentina"); //$NON-NLS-1$
    ourRegionNames.put("AS", "American Samoa"); //$NON-NLS-1$
    ourRegionNames.put("AT", "Austria"); //$NON-NLS-1$
    ourRegionNames.put("AU", "Australia"); //$NON-NLS-1$
    ourRegionNames.put("AW", "Aruba"); //$NON-NLS-1$
    ourRegionNames.put("AX", "\u00c5land Islands" /*Åland Islands*/); //$NON-NLS-1$
    ourRegionNames.put("AZ", "Azerbaijan"); //$NON-NLS-1$
    ourRegionNames.put("BA", "Bosnia and Herzegovina"); //$NON-NLS-1$
    ourRegionNames.put("BB", "Barbados"); //$NON-NLS-1$
    ourRegionNames.put("BD", "Bangladesh"); //$NON-NLS-1$
    ourRegionNames.put("BE", "Belgium"); //$NON-NLS-1$
    ourRegionNames.put("BF", "Burkina Faso"); //$NON-NLS-1$
    ourRegionNames.put("BG", "Bulgaria"); //$NON-NLS-1$
    ourRegionNames.put("BH", "Bahrain"); //$NON-NLS-1$
    ourRegionNames.put("BI", "Burundi"); //$NON-NLS-1$
    ourRegionNames.put("BJ", "Benin"); //$NON-NLS-1$
    ourRegionNames.put("BL", "Saint Barth\u00e9lemy" /*Saint Barthélemy*/); //$NON-NLS-1$
    ourRegionNames.put("BM", "Bermuda"); //$NON-NLS-1$
    ourRegionNames.put("BN", "Brunei Darussalam"); //$NON-NLS-1$
    ourRegionNames.put("BO", "Bolivia, Plurinational State of"); //$NON-NLS-1$
    ourRegionNames.put("BQ", "Bonaire, Sint Eustatius and Saba"); //$NON-NLS-1$
    ourRegionNames.put("BR", "Brazil"); //$NON-NLS-1$
    ourRegionNames.put("BS", "Bahamas"); //$NON-NLS-1$
    ourRegionNames.put("BT", "Bhutan"); //$NON-NLS-1$
    ourRegionNames.put("BV", "Bouvet Island"); //$NON-NLS-1$
    ourRegionNames.put("BW", "Botswana"); //$NON-NLS-1$
    ourRegionNames.put("BY", "Belarus"); //$NON-NLS-1$
    ourRegionNames.put("BZ", "Belize"); //$NON-NLS-1$
    ourRegionNames.put("CA", "Canada"); //$NON-NLS-1$
    ourRegionNames.put("CC", "Cocos (Keeling) Islands"); //$NON-NLS-1$
    ourRegionNames.put("CD", "Congo, the Democratic Republic of the"); //$NON-NLS-1$
    ourRegionNames.put("CF", "Central African Republic"); //$NON-NLS-1$
    ourRegionNames.put("CG", "Congo"); //$NON-NLS-1$
    ourRegionNames.put("CH", "Switzerland"); //$NON-NLS-1$
    ourRegionNames.put("CI", "C\u00f4te d'Ivoire" /*Côte d'Ivoire*/); //$NON-NLS-1$
    ourRegionNames.put("CK", "Cook Islands"); //$NON-NLS-1$
    ourRegionNames.put("CL", "Chile"); //$NON-NLS-1$
    ourRegionNames.put("CM", "Cameroon"); //$NON-NLS-1$
    ourRegionNames.put("CN", "China"); //$NON-NLS-1$
    ourRegionNames.put("CO", "Colombia"); //$NON-NLS-1$
    ourRegionNames.put("CR", "Costa Rica"); //$NON-NLS-1$
    ourRegionNames.put("CU", "Cuba"); //$NON-NLS-1$
    ourRegionNames.put("CV", "Cape Verde"); //$NON-NLS-1$
    ourRegionNames.put("CW", "Cura\u00e7ao" /*Curaçao*/); //$NON-NLS-1$
    ourRegionNames.put("CX", "Christmas Island"); //$NON-NLS-1$
    ourRegionNames.put("CY", "Cyprus"); //$NON-NLS-1$
    ourRegionNames.put("CZ", "Czech Republic"); //$NON-NLS-1$
    ourRegionNames.put("DE", "Germany"); //$NON-NLS-1$
    ourRegionNames.put("DJ", "Djibouti"); //$NON-NLS-1$
    ourRegionNames.put("DK", "Denmark"); //$NON-NLS-1$
    ourRegionNames.put("DM", "Dominica"); //$NON-NLS-1$
    ourRegionNames.put("DO", "Dominican Republic"); //$NON-NLS-1$
    ourRegionNames.put("DZ", "Algeria"); //$NON-NLS-1$
    ourRegionNames.put("EC", "Ecuador"); //$NON-NLS-1$
    ourRegionNames.put("EE", "Estonia"); //$NON-NLS-1$
    ourRegionNames.put("EG", "Egypt"); //$NON-NLS-1$
    ourRegionNames.put("EH", "Western Sahara"); //$NON-NLS-1$
    ourRegionNames.put("ER", "Eritrea"); //$NON-NLS-1$
    ourRegionNames.put("ES", "Spain"); //$NON-NLS-1$
    ourRegionNames.put("ET", "Ethiopia"); //$NON-NLS-1$
    ourRegionNames.put("FI", "Finland"); //$NON-NLS-1$
    ourRegionNames.put("FJ", "Fiji"); //$NON-NLS-1$
    ourRegionNames.put("FK", "Falkland Islands (Malvinas)"); //$NON-NLS-1$
    ourRegionNames.put("FM", "Micronesia, Federated States of"); //$NON-NLS-1$
    ourRegionNames.put("FO", "Faroe Islands"); //$NON-NLS-1$
    ourRegionNames.put("FR", "France"); //$NON-NLS-1$
    ourRegionNames.put("GA", "Gabon"); //$NON-NLS-1$
    ourRegionNames.put("GB", "United Kingdom"); //$NON-NLS-1$
    ourRegionNames.put("GD", "Grenada"); //$NON-NLS-1$
    ourRegionNames.put("GE", "Georgia"); //$NON-NLS-1$
    ourRegionNames.put("GF", "French Guiana"); //$NON-NLS-1$
    ourRegionNames.put("GG", "Guernsey"); //$NON-NLS-1$
    ourRegionNames.put("GH", "Ghana"); //$NON-NLS-1$
    ourRegionNames.put("GI", "Gibraltar"); //$NON-NLS-1$
    ourRegionNames.put("GL", "Greenland"); //$NON-NLS-1$
    ourRegionNames.put("GM", "Gambia"); //$NON-NLS-1$
    ourRegionNames.put("GN", "Guinea"); //$NON-NLS-1$
    ourRegionNames.put("GP", "Guadeloupe"); //$NON-NLS-1$
    ourRegionNames.put("GQ", "Equatorial Guinea"); //$NON-NLS-1$
    ourRegionNames.put("GR", "Greece"); //$NON-NLS-1$
    ourRegionNames.put("GS", "South Georgia and the South Sandwich Islands"); //$NON-NLS-1$
    ourRegionNames.put("GT", "Guatemala"); //$NON-NLS-1$
    ourRegionNames.put("GU", "Guam"); //$NON-NLS-1$
    ourRegionNames.put("GW", "Guinea-Bissau"); //$NON-NLS-1$
    ourRegionNames.put("GY", "Guyana"); //$NON-NLS-1$
    ourRegionNames.put("HK", "Hong Kong"); //$NON-NLS-1$
    ourRegionNames.put("HM", "Heard Island and McDonald Islands"); //$NON-NLS-1$
    ourRegionNames.put("HN", "Honduras"); //$NON-NLS-1$
    ourRegionNames.put("HR", "Croatia"); //$NON-NLS-1$
    ourRegionNames.put("HT", "Haiti"); //$NON-NLS-1$
    ourRegionNames.put("HU", "Hungary"); //$NON-NLS-1$
    ourRegionNames.put("ID", "Indonesia"); //$NON-NLS-1$
    ourRegionNames.put("IE", "Ireland"); //$NON-NLS-1$
    ourRegionNames.put("IL", "Israel"); //$NON-NLS-1$
    ourRegionNames.put("IM", "Isle of Man"); //$NON-NLS-1$
    ourRegionNames.put("IN", "India"); //$NON-NLS-1$
    ourRegionNames.put("IO", "British Indian Ocean Territory"); //$NON-NLS-1$
    ourRegionNames.put("IQ", "Iraq"); //$NON-NLS-1$
    ourRegionNames.put("IR", "Iran, Islamic Republic of"); //$NON-NLS-1$
    ourRegionNames.put("IS", "Iceland"); //$NON-NLS-1$
    ourRegionNames.put("IT", "Italy"); //$NON-NLS-1$
    ourRegionNames.put("JE", "Jersey"); //$NON-NLS-1$
    ourRegionNames.put("JM", "Jamaica"); //$NON-NLS-1$
    ourRegionNames.put("JO", "Jordan"); //$NON-NLS-1$
    ourRegionNames.put("JP", "Japan"); //$NON-NLS-1$
    ourRegionNames.put("KE", "Kenya"); //$NON-NLS-1$
    ourRegionNames.put("KG", "Kyrgyzstan"); //$NON-NLS-1$
    ourRegionNames.put("KH", "Cambodia"); //$NON-NLS-1$
    ourRegionNames.put("KI", "Kiribati"); //$NON-NLS-1$
    ourRegionNames.put("KM", "Comoros"); //$NON-NLS-1$
    ourRegionNames.put("KN", "Saint Kitts and Nevis"); //$NON-NLS-1$
    ourRegionNames.put("KP", "Korea, Democratic People's Republic of"); //$NON-NLS-1$
    ourRegionNames.put("KR", "Korea, Republic of"); //$NON-NLS-1$
    ourRegionNames.put("KW", "Kuwait"); //$NON-NLS-1$
    ourRegionNames.put("KY", "Cayman Islands"); //$NON-NLS-1$
    ourRegionNames.put("KZ", "Kazakhstan"); //$NON-NLS-1$
    ourRegionNames.put("LA", "Lao People's Democratic Republic"); //$NON-NLS-1$
    ourRegionNames.put("LB", "Lebanon"); //$NON-NLS-1$
    ourRegionNames.put("LC", "Saint Lucia"); //$NON-NLS-1$
    ourRegionNames.put("LI", "Liechtenstein"); //$NON-NLS-1$
    ourRegionNames.put("LK", "Sri Lanka"); //$NON-NLS-1$
    ourRegionNames.put("LR", "Liberia"); //$NON-NLS-1$
    ourRegionNames.put("LS", "Lesotho"); //$NON-NLS-1$
    ourRegionNames.put("LT", "Lithuania"); //$NON-NLS-1$
    ourRegionNames.put("LU", "Luxembourg"); //$NON-NLS-1$
    ourRegionNames.put("LV", "Latvia"); //$NON-NLS-1$
    ourRegionNames.put("LY", "Libya"); //$NON-NLS-1$
    ourRegionNames.put("MA", "Morocco"); //$NON-NLS-1$
    ourRegionNames.put("MC", "Monaco"); //$NON-NLS-1$
    ourRegionNames.put("MD", "Moldova, Republic of"); //$NON-NLS-1$
    ourRegionNames.put("ME", "Montenegro"); //$NON-NLS-1$
    ourRegionNames.put("MF", "Saint Martin (French part)"); //$NON-NLS-1$
    ourRegionNames.put("MG", "Madagascar"); //$NON-NLS-1$
    ourRegionNames.put("MH", "Marshall Islands"); //$NON-NLS-1$
    ourRegionNames.put("MK", "Macedonia, the former Yugoslav Republic of"); //$NON-NLS-1$
    ourRegionNames.put("ML", "Mali"); //$NON-NLS-1$
    ourRegionNames.put("MM", "Myanmar"); //$NON-NLS-1$
    ourRegionNames.put("MN", "Mongolia"); //$NON-NLS-1$
    ourRegionNames.put("MO", "Macao"); //$NON-NLS-1$
    ourRegionNames.put("MP", "Northern Mariana Islands"); //$NON-NLS-1$
    ourRegionNames.put("MQ", "Martinique"); //$NON-NLS-1$
    ourRegionNames.put("MR", "Mauritania"); //$NON-NLS-1$
    ourRegionNames.put("MS", "Montserrat"); //$NON-NLS-1$
    ourRegionNames.put("MT", "Malta"); //$NON-NLS-1$
    ourRegionNames.put("MU", "Mauritius"); //$NON-NLS-1$
    ourRegionNames.put("MV", "Maldives"); //$NON-NLS-1$
    ourRegionNames.put("MW", "Malawi"); //$NON-NLS-1$
    ourRegionNames.put("MX", "Mexico"); //$NON-NLS-1$
    ourRegionNames.put("MY", "Malaysia"); //$NON-NLS-1$
    ourRegionNames.put("MZ", "Mozambique"); //$NON-NLS-1$
    ourRegionNames.put("NA", "Namibia"); //$NON-NLS-1$
    ourRegionNames.put("NC", "New Caledonia"); //$NON-NLS-1$
    ourRegionNames.put("NE", "Niger"); //$NON-NLS-1$
    ourRegionNames.put("NF", "Norfolk Island"); //$NON-NLS-1$
    ourRegionNames.put("NG", "Nigeria"); //$NON-NLS-1$
    ourRegionNames.put("NI", "Nicaragua"); //$NON-NLS-1$
    ourRegionNames.put("NL", "Netherlands"); //$NON-NLS-1$
    ourRegionNames.put("NO", "Norway"); //$NON-NLS-1$
    ourRegionNames.put("NP", "Nepal"); //$NON-NLS-1$
    ourRegionNames.put("NR", "Nauru"); //$NON-NLS-1$
    ourRegionNames.put("NU", "Niue"); //$NON-NLS-1$
    ourRegionNames.put("NZ", "New Zealand"); //$NON-NLS-1$
    ourRegionNames.put("OM", "Oman"); //$NON-NLS-1$
    ourRegionNames.put("PA", "Panama"); //$NON-NLS-1$
    ourRegionNames.put("PE", "Peru"); //$NON-NLS-1$
    ourRegionNames.put("PF", "French Polynesia"); //$NON-NLS-1$
    ourRegionNames.put("PG", "Papua New Guinea"); //$NON-NLS-1$
    ourRegionNames.put("PH", "Philippines"); //$NON-NLS-1$
    ourRegionNames.put("PK", "Pakistan"); //$NON-NLS-1$
    ourRegionNames.put("PL", "Poland"); //$NON-NLS-1$
    ourRegionNames.put("PM", "Saint Pierre and Miquelon"); //$NON-NLS-1$
    ourRegionNames.put("PN", "Pitcairn"); //$NON-NLS-1$
    ourRegionNames.put("PR", "Puerto Rico"); //$NON-NLS-1$
    ourRegionNames.put("PS", "Palestinian Territory, Occupied"); //$NON-NLS-1$
    ourRegionNames.put("PT", "Portugal"); //$NON-NLS-1$
    ourRegionNames.put("PW", "Palau"); //$NON-NLS-1$
    ourRegionNames.put("PY", "Paraguay"); //$NON-NLS-1$
    ourRegionNames.put("QA", "Qatar"); //$NON-NLS-1$
    ourRegionNames.put("RE", "R\u00e9union" /*Réunion*/); //$NON-NLS-1$
    ourRegionNames.put("RO", "Romania"); //$NON-NLS-1$
    ourRegionNames.put("RS", "Serbia"); //$NON-NLS-1$
    ourRegionNames.put("RU", "Russian Federation"); //$NON-NLS-1$
    ourRegionNames.put("RW", "Rwanda"); //$NON-NLS-1$
    ourRegionNames.put("SA", "Saudi Arabia"); //$NON-NLS-1$
    ourRegionNames.put("SB", "Solomon Islands"); //$NON-NLS-1$
    ourRegionNames.put("SC", "Seychelles"); //$NON-NLS-1$
    ourRegionNames.put("SD", "Sudan"); //$NON-NLS-1$
    ourRegionNames.put("SE", "Sweden"); //$NON-NLS-1$
    ourRegionNames.put("SG", "Singapore"); //$NON-NLS-1$
    ourRegionNames.put("SH", "Saint Helena, Ascension and Tristan da Cunha"); //$NON-NLS-1$
    ourRegionNames.put("SI", "Slovenia"); //$NON-NLS-1$
    ourRegionNames.put("SJ", "Svalbard and Jan Mayen"); //$NON-NLS-1$
    ourRegionNames.put("SK", "Slovakia"); //$NON-NLS-1$
    ourRegionNames.put("SL", "Sierra Leone"); //$NON-NLS-1$
    ourRegionNames.put("SM", "San Marino"); //$NON-NLS-1$
    ourRegionNames.put("SN", "Senegal"); //$NON-NLS-1$
    ourRegionNames.put("SO", "Somalia"); //$NON-NLS-1$
    ourRegionNames.put("SR", "Suriname"); //$NON-NLS-1$
    ourRegionNames.put("SS", "South Sudan"); //$NON-NLS-1$
    ourRegionNames.put("ST", "Sao Tome and Principe"); //$NON-NLS-1$
    ourRegionNames.put("SV", "El Salvador"); //$NON-NLS-1$
    ourRegionNames.put("SX", "Sint Maarten (Dutch part)"); //$NON-NLS-1$
    ourRegionNames.put("SY", "Syrian Arab Republic"); //$NON-NLS-1$
    ourRegionNames.put("SZ", "Swaziland"); //$NON-NLS-1$
    ourRegionNames.put("TC", "Turks and Caicos Islands"); //$NON-NLS-1$
    ourRegionNames.put("TD", "Chad"); //$NON-NLS-1$
    ourRegionNames.put("TF", "French Southern Territories"); //$NON-NLS-1$
    ourRegionNames.put("TG", "Togo"); //$NON-NLS-1$
    ourRegionNames.put("TH", "Thailand"); //$NON-NLS-1$
    ourRegionNames.put("TJ", "Tajikistan"); //$NON-NLS-1$
    ourRegionNames.put("TK", "Tokelau"); //$NON-NLS-1$
    ourRegionNames.put("TL", "Timor-Leste"); //$NON-NLS-1$
    ourRegionNames.put("TM", "Turkmenistan"); //$NON-NLS-1$
    ourRegionNames.put("TN", "Tunisia"); //$NON-NLS-1$
    ourRegionNames.put("TO", "Tonga"); //$NON-NLS-1$
    ourRegionNames.put("TR", "Turkey"); //$NON-NLS-1$
    ourRegionNames.put("TT", "Trinidad and Tobago"); //$NON-NLS-1$
    ourRegionNames.put("TV", "Tuvalu"); //$NON-NLS-1$
    ourRegionNames.put("TW", "Taiwan, Province of China"); //$NON-NLS-1$
    ourRegionNames.put("TZ", "Tanzania, United Republic of"); //$NON-NLS-1$
    ourRegionNames.put("UA", "Ukraine"); //$NON-NLS-1$
    ourRegionNames.put("UG", "Uganda"); //$NON-NLS-1$
    ourRegionNames.put("UM", "United States Minor Outlying Islands"); //$NON-NLS-1$
    ourRegionNames.put("US", "United States"); //$NON-NLS-1$
    ourRegionNames.put("UY", "Uruguay"); //$NON-NLS-1$
    ourRegionNames.put("UZ", "Uzbekistan"); //$NON-NLS-1$
    ourRegionNames.put("VA", "Holy See (Vatican City State)"); //$NON-NLS-1$
    ourRegionNames.put("VC", "Saint Vincent and the Grenadines"); //$NON-NLS-1$
    ourRegionNames.put("VE", "Venezuela, Bolivarian Republic of"); //$NON-NLS-1$
    ourRegionNames.put("VG", "Virgin Islands, British"); //$NON-NLS-1$
    ourRegionNames.put("VI", "Virgin Islands, U.S."); //$NON-NLS-1$
    ourRegionNames.put("VN", "Viet Nam"); //$NON-NLS-1$
    ourRegionNames.put("VU", "Vanuatu"); //$NON-NLS-1$
    ourRegionNames.put("WF", "Wallis and Futuna"); //$NON-NLS-1$
    ourRegionNames.put("WS", "Samoa"); //$NON-NLS-1$
    ourRegionNames.put("YE", "Yemen"); //$NON-NLS-1$
    ourRegionNames.put("YT", "Mayotte"); //$NON-NLS-1$
    ourRegionNames.put("ZA", "South Africa"); //$NON-NLS-1$
    ourRegionNames.put("ZM", "Zambia"); //$NON-NLS-1$
    ourRegionNames.put("ZW", "Zimbabwe"); //$NON-NLS-1$

    // Aliases
    // http://developer.android.com/reference/java/util/Locale.html
    // Apparently we're using some old aliases for some languages
    //  The Hebrew ("he") language code is rewritten as "iw", Indonesian ("id") as "in",
    // and Yiddish ("yi") as "ji".
    ourLanguageToCountry.put("iw", ourLanguageToCountry.get("he")); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageToCountry.put("in", ourLanguageToCountry.get("id")); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageToCountry.put("ji", ourLanguageToCountry.get("yi")); //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("iw", ourLanguageNames.get("he"));         //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("in", ourLanguageNames.get("id"));         //$NON-NLS-1$ //$NON-NLS-2$
    ourLanguageNames.put("ji", ourLanguageNames.get("yi"));         //$NON-NLS-1$ //$NON-NLS-2$

    // The following miscellaneous languages have no binding to a region
    // in ourLanguageToCountry, since they are either extinct or constructed or
    // only in literary use:
    ourLanguageNames.put("pi", "Pali"); //$NON-NLS-1$
    ourLanguageNames.put("vo", "Volap\u00fck" /*Volapük*/); //$NON-NLS-1$
    ourLanguageNames.put("eo", "Esperanto"); //$NON-NLS-1$
    ourLanguageNames.put("la", "Latin"); //$NON-NLS-1$
    ourLanguageNames.put("ia", "Interlingua"); //$NON-NLS-1$
    ourLanguageNames.put("ie", "Interlingue"); //$NON-NLS-1$
    ourLanguageNames.put("io", "Ido"); //$NON-NLS-1$
    ourLanguageNames.put("ae", "Avestan"); //$NON-NLS-1$
    ourLanguageNames.put("cu", "Church Slavic"); //$NON-NLS-1$

    // To check initial capacities of the maps and avoid dynamic resizing:
    //System.out.println("Language count = " + ourLanguageNames.size());
    //System.out.println("Language Binding count = " + ourLanguageToCountry.size());
    //System.out.println("Region count = " + ourRegionNames.size());
  }

  @VisibleForTesting
  static Map<String, String> getLanguageToCountryMap() {
    return ourLanguageToCountry;
  }

  @VisibleForTesting
  static Map<String, String> getLanguageNamesMap() {
    return ourLanguageNames;
  }

  @VisibleForTesting
  static Map<String, String> getRegionNamesMap() {
    return ourRegionNames;
  }
}
