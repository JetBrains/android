/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.data

import com.android.ide.common.resources.LocaleManager
import com.android.ide.common.resources.configuration.LocaleQualifier

internal val DEFAULT_LANGUAGE = AppLanguage(null, "System default")

/**
 * A language consisting of a locale [tag] like "en-US" or "" for the default locale and a human readable language name.
 */
internal data class AppLanguage(val locale: LocaleQualifier?, val name: String) {

  val tag: String
    get() = locale?.tag ?: ""

  /**
   * This is the value displayed by default in a dropdown with [AppLanguage] values.
   */
  override fun toString(): String = name
}

/**
 * Returns a list of [AppLanguage] items sorted by [AppLanguage.name] except for the DEFAULT_LANGUAGE which is always first.
 */
internal fun convertFromLocaleConfig(localeConfig: Set<LocaleQualifier>): List<AppLanguage> {
  val result = mutableListOf<AppLanguage>()
  localeConfig.mapNotNullTo(result) { createAppLanguageFromLocale(it) }.sortWith(appLanguageComparator)
  result.add(index = 0, DEFAULT_LANGUAGE)
  return result
}

private fun createAppLanguageFromLocale(locale: LocaleQualifier): AppLanguage? {
  val language = locale.language?.let { LocaleManager.getLanguageName(it) } ?: return null
  val region = locale.region?.let { LocaleManager.getRegionName(it) }
  return when {
    region != null -> AppLanguage(locale, "$language in $region")
    locale.isPseudoLocale -> AppLanguage(locale, "Pseudo $language")
    else -> AppLanguage(locale, language)
  }
}

private val appLanguageComparator = Comparator<AppLanguage> { language1, language2 ->
  val pseudo = language1.isPseudoLocale.compareTo(language2.isPseudoLocale)
  when {
    pseudo != 0 -> pseudo
    language1.isPseudoLocale -> language1.locale?.region?.compareTo(language2.locale?.region ?: "") ?: -1
    else -> language1.name.compareTo(language2.name)
  }
}

private val PSEUDO_LOCALE_REGIONS = setOf("XA", "XB", "XC")

private val LocaleQualifier.isPseudoLocale: Boolean
  get() = region in PSEUDO_LOCALE_REGIONS

private val AppLanguage.isPseudoLocale: Boolean
  get() = locale?.isPseudoLocale ?: false