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
@file:JvmName("ThemeUtils")

package com.android.tools.idea.actions

import com.android.SdkConstants
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.ResourceResolver.THEME_NAME
import com.android.tools.idea.configurations.ThemeStyleFilter
import com.android.tools.idea.editors.theme.ThemeResolver
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

private const val ANDROID_THEME = SdkConstants.PREFIX_ANDROID + "Theme"
private const val ANDROID_THEME_PREFIX = SdkConstants.PREFIX_ANDROID + "Theme."
private const val PROJECT_THEME_PREFIX = "Theme."
private const val PROJECT_THEME = "Theme"

private const val RECENTLY_USED_THEMES_PROPERTY = "android.recentlyUsedThemes"
private const val MAX_RECENTLY_USED_THEMES = 5

/**
 * If the [theme] is called "Theme" or "android:Theme", returns "Theme". Otherwise, if the [theme]
 * has prefix "android:Theme.", "Theme.", or "@style/", removes it.
 */
internal fun getPreferredThemeName(theme: String): String {
  if (theme == ANDROID_THEME || theme == PROJECT_THEME) {
    return THEME_NAME
  }

  return theme
    .removePrefix(ANDROID_THEME_PREFIX)
    .removePrefix(PROJECT_THEME_PREFIX)
    .removePrefix(STYLE_RESOURCE_PREFIX)
}

fun createFilter(
  resolver: ThemeResolver,
  excludedNames: Set<String>,
  vararg baseThemes: StyleResourceValue,
): ThemeStyleFilter {
  if (baseThemes.isEmpty()) {
    return { style: ConfiguredThemeEditorStyle -> !excludedNames.contains(style.qualifiedName) }
  } else {
    return { style: ConfiguredThemeEditorStyle ->
      !excludedNames.contains(style.qualifiedName) &&
        resolver.themeIsChildOfAny(style.styleResourceValue, *baseThemes)
    }
  }
}

fun getFrameworkThemes(themeResolver: ThemeResolver): List<ConfiguredThemeEditorStyle> =
  getFilteredByPrefixSortedByName(getPublicThemes(themeResolver.frameworkThemes))

fun getFrameworkThemeNames(themeResolver: ThemeResolver, filter: ThemeStyleFilter) =
  getFilteredNames(getFrameworkThemes(themeResolver), filter)

fun getProjectThemes(themeResolver: ThemeResolver): List<ConfiguredThemeEditorStyle> =
  getFilteredByPrefixSortedByName(getPublicThemes(themeResolver.localThemes))

fun getProjectThemeNames(themeResolver: ThemeResolver, filter: ThemeStyleFilter) =
  getFilteredNames(getProjectThemes(themeResolver), filter)

fun getLibraryThemes(themeResolver: ThemeResolver): List<ConfiguredThemeEditorStyle> =
  getFilteredByPrefixSortedByName(
    getPublicThemes(themeResolver.externalLibraryThemes),
    setOf("Base.", "Platform."),
  )

fun getLibraryThemeNames(themeResolver: ThemeResolver, filter: ThemeStyleFilter) =
  getFilteredNames(getPublicThemes(themeResolver.externalLibraryThemes), filter)

fun getRecommendedThemes(themeResolver: ThemeResolver): List<ConfiguredThemeEditorStyle> {
  val recommendedThemes = themeResolver.recommendedThemes
  return sequenceOf(getLibraryThemes(themeResolver), getFrameworkThemes(themeResolver))
    .flatten()
    .filter { it.styleReference in recommendedThemes }
    .toList()
}

fun getRecommendedThemeNames(themeResolver: ThemeResolver, filter: ThemeStyleFilter) =
  getFilteredNames(getRecommendedThemes(themeResolver), filter)

// TODO: Handle namespace issues around recently used themes
@JvmOverloads
fun getRecentlyUsedThemes(project: Project, excludedNames: Set<String> = emptySet()) =
  PropertiesComponent.getInstance(project)
    .getList(RECENTLY_USED_THEMES_PROPERTY)
    ?.minus(excludedNames) ?: emptyList()

fun addRecentlyUsedTheme(project: Project, theme: String) {
  // The recently used themes are not shared between different projects.
  val old =
    PropertiesComponent.getInstance(project).getList(RECENTLY_USED_THEMES_PROPERTY)?.toSet()
      ?: emptySet()
  val new = setOf(theme).plus(old).take(MAX_RECENTLY_USED_THEMES)
  PropertiesComponent.getInstance(project).setList(RECENTLY_USED_THEMES_PROPERTY, new)
}

/** Filters a collection of themes to return a new collection with only the public ones. */
private fun getPublicThemes(themes: List<ConfiguredThemeEditorStyle>) =
  themes.filter { it.isPublic }

/**
 * Returns the [themes] excluding those with names starting with prefixes in [excludedPrefixes]
 * sorted by qualified name.
 */
private fun getFilteredByPrefixSortedByName(
  themes: List<ConfiguredThemeEditorStyle>,
  excludedPrefixes: Set<String> = emptySet(),
): List<ConfiguredThemeEditorStyle> =
  themes
    .filter { theme -> excludedPrefixes.none({ prefix -> theme.name.startsWith(prefix) }) }
    .sortedBy { it.qualifiedName }

/** Returns the names of the [themes] excluding those filtered out by the specified [filter]. */
private fun getFilteredNames(themes: List<ConfiguredThemeEditorStyle>, filter: ThemeStyleFilter) =
  themes.filter(filter).map { it.qualifiedName }
