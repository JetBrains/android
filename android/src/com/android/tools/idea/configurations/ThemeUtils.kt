/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.tools.idea.editors.theme.ThemeResolver
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle
import java.util.*

internal fun getFrameworkThemes(themeResolver: ThemeResolver, excludedThemes: Set<String> = emptySet()) =
  getFilteredSortedNames(getPublicThemes(themeResolver.frameworkThemes), excludedThemes)

internal fun getProjectThemes(themeResolver: ThemeResolver, excludedThemes: Set<String> = emptySet()) =
  getFilteredSortedNames(getPublicThemes(themeResolver.localThemes), excludedThemes)

internal fun getLibraryThemes(themeResolver: ThemeResolver, excludedThemes: Set<String> = emptySet()) =
  getFilteredPrefixesSortedNames(getPublicThemes(themeResolver.externalLibraryThemes), excludedThemes, setOf("Base.", "Platform."))

/**
 * Filters a collection of themes to return a new collection with only the public ones.
 */
private fun getPublicThemes(themes: List<ConfiguredThemeEditorStyle>) = themes.filter { it.isPublic }

/**
 * Sorts the [themes] excluding those in [excludedThemes]
 * @return the sorted themes excluding those in excludedThemes
 */
private fun getFilteredSortedNames(themes: List<ConfiguredThemeEditorStyle>, excludedThemes: Set<String>) =
  getFilteredPrefixesSortedNames(themes, excludedThemes, emptySet())

/**
 * Sorts the [themes] excluding those in [excludedThemes] and those starting with prefixes in [excludedPrefixes]
 * @return the sorted themes excluding those in excludedThemes or starting with a prefix in excludedPrefixes
 */
private fun getFilteredPrefixesSortedNames(themes: List<ConfiguredThemeEditorStyle>,
                                           excludedThemes: Set<String>,
                                           excludedPrefixes: Set<String>) =
  themes.filterNot { it.qualifiedName in excludedThemes }
    .filter { theme -> excludedPrefixes.none({ prefix -> theme.name.startsWith(prefix) })}
    .map { it -> it.qualifiedName }
    .sorted()
