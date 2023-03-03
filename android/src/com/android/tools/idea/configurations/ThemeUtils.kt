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

import com.android.SdkConstants
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
import com.android.ide.common.rendering.HardwareConfigHelper
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.ResourceResolver.THEME_NAME
import com.android.resources.ScreenSize
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.idea.editors.theme.ThemeResolver
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle
import com.android.tools.idea.model.ActivityAttributesSnapshot
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.logManifestIndexQueryError
import com.android.tools.idea.model.queryActivitiesFromManifestIndex
import com.android.tools.idea.model.queryApplicationThemeFromManifestIndex
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.SlowOperations
import org.jetbrains.android.facet.AndroidFacet

private const val ANDROID_THEME = PREFIX_ANDROID + "Theme"
private const val ANDROID_THEME_PREFIX = PREFIX_ANDROID + "Theme."
private const val PROJECT_THEME_PREFIX = "Theme."
private const val PROJECT_THEME = "Theme"

private const val RECENTLY_USED_THEMES_PROPERTY = "android.recentlyUsedThemes"
private const val MAX_RECENTLY_USED_THEMES = 5

typealias ThemeStyleFilter = (ConfiguredThemeEditorStyle) -> Boolean

/**
 * If the [theme] is called "Theme" or "android:Theme", returns "Theme".
 * Otherwise, if the [theme] has prefix "android:Theme.", "Theme.", or "@style/", removes it.
 */
internal fun getPreferredThemeName(theme: String): String {
  if (theme == ANDROID_THEME || theme == PROJECT_THEME) {
    return THEME_NAME
  }

  return theme.removePrefix(ANDROID_THEME_PREFIX).removePrefix(PROJECT_THEME_PREFIX).removePrefix(STYLE_RESOURCE_PREFIX)
}

fun createFilter(resolver: ThemeResolver, excludedNames: Set<String>, vararg baseThemes: StyleResourceValue): ThemeStyleFilter {
  if (baseThemes.isEmpty()) {
    return { style: ConfiguredThemeEditorStyle -> !excludedNames.contains(style.qualifiedName)}
  }
  else {
    return { style: ConfiguredThemeEditorStyle -> !excludedNames.contains(style.qualifiedName) &&
                                                  resolver.themeIsChildOfAny(style.styleResourceValue, *baseThemes)}
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
  getFilteredByPrefixSortedByName(getPublicThemes(themeResolver.externalLibraryThemes), setOf("Base.", "Platform."))

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
  val old = PropertiesComponent.getInstance(project).getList(RECENTLY_USED_THEMES_PROPERTY)?.toSet() ?: emptySet()
  val new = setOf(theme).plus(old).take(MAX_RECENTLY_USED_THEMES)
  PropertiesComponent.getInstance(project).setList(RECENTLY_USED_THEMES_PROPERTY, new)
}

/**
 * Filters a collection of themes to return a new collection with only the public ones.
 */
private fun getPublicThemes(themes: List<ConfiguredThemeEditorStyle>) = themes.filter { it.isPublic }

/**
 * Returns the [themes] excluding those with names starting with prefixes in [excludedPrefixes] sorted by qualified name.
 */
private fun getFilteredByPrefixSortedByName(themes: List<ConfiguredThemeEditorStyle>,
                                            excludedPrefixes: Set<String> = emptySet()): List<ConfiguredThemeEditorStyle> =
  themes
    .filter { theme -> excludedPrefixes.none({ prefix -> theme.name.startsWith(prefix) })}
    .sortedBy { it.qualifiedName }

/**
 * Returns the names of the [themes] excluding those filtered out by the specified [filter].
 */
private fun getFilteredNames(themes: List<ConfiguredThemeEditorStyle>, filter: ThemeStyleFilter) =
  themes
    .filter(filter)
    .map { it.qualifiedName }

/**
 *  Try to get application theme from [AndroidManifestIndex]. And it falls back to the merged
 *  manifest snapshot if necessary.
 */
fun Module.getAppThemeName(): String? {
  try {
    val facet = AndroidFacet.getInstance(this)
    if (facet != null) {
      return DumbService.getInstance(this.project).runReadActionInSmartMode(Computable {
        SlowOperations.allowSlowOperations(ThrowableComputable { facet.queryApplicationThemeFromManifestIndex() })
      })
    }
  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    logManifestIndexQueryError(e);
  }

  return MergedManifestManager.getFreshSnapshot(this).manifestTheme
}

/**
 *  Try to get activity themes from [AndroidManifestIndex]. And it falls back to the merged
 *  manifest snapshot if necessary.
 */
fun Module.getAllActivityThemeNames(): Set<String> {
  try {
    val facet = AndroidFacet.getInstance(this)
    if (facet != null) {
      return DumbService.getInstance(this.project).runReadActionInSmartMode(Computable {
        val activities = SlowOperations.allowSlowOperations(ThrowableComputable { facet.queryActivitiesFromManifestIndex().activities })
        activities.asSequence()
          .mapNotNull(DefaultActivityLocator.ActivityWrapper::getTheme)
          .toSet()
      })
    }
  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    logManifestIndexQueryError(e);
  }

  val manifest = MergedManifestManager.getSnapshot(this)
  return manifest.activityAttributesMap.values.asSequence()
    .mapNotNull(ActivityAttributesSnapshot::getTheme)
    .toSet()
}

/**
 * Try to get value of theme corresponding to the given activity from {@link AndroidManifestIndex}.
 * And it falls back to merged manifest snapshot if necessary.
 */
fun Module.getThemeNameForActivity(activityFqcn: String): String? {
  try {
    val facet = AndroidFacet.getInstance(this)
    if (facet != null) {
      return DumbService.getInstance(this.project).runReadActionInSmartMode(Computable {
        val activities = SlowOperations.allowSlowOperations(ThrowableComputable { facet.queryActivitiesFromManifestIndex().activities })
        activities.asSequence()
          .filter { it.qualifiedName == activityFqcn }
          .mapNotNull(DefaultActivityLocator.ActivityWrapper::getTheme)
          .filter { it.startsWith(SdkConstants.PREFIX_RESOURCE_REF) }
          .firstOrNull()
      })
    }
  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //  We need to refactor the callers of this to require a *smart*
    //  read action, at which point we can remove this try-catch.
    logManifestIndexQueryError(e);
  }

  val manifest = MergedManifestManager.getSnapshot(this)
  return manifest.getActivityAttributes(activityFqcn)
    ?.theme
    ?.takeIf { it.startsWith(SdkConstants.PREFIX_RESOURCE_REF) }
}

/**
 * Returns a default theme
 */
fun Module.getDefaultTheme(renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String {
  // Facet being null should not happen, but has been observed to happen in rare scenarios (such as 73332530), probably
  // related to race condition between Gradle sync and layout rendering
  val moduleInfo = AndroidFacet.getInstance(this)?.let { StudioAndroidModuleInfo.getInstance(it) }
  return getDefaultTheme(moduleInfo, renderingTarget, screenSize, device)
}

fun getDefaultTheme(moduleInfo: AndroidModuleInfo?, renderingTarget: IAndroidTarget?, screenSize: ScreenSize?, device: Device?): String {
  // For Android Wear and Android TV, the defaults differ
  if (device != null) {
    if (HardwareConfigHelper.isWear(device)) {
      return "@android:style/Theme.DeviceDefault"
    }
    else if (HardwareConfigHelper.isTv(device)) {
      return "@style/Theme.Leanback"
    }
  }

  if (moduleInfo == null) {
    return SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Material.Light"
  }

  // From manifest theme documentation: "If that attribute is also not set, the default system theme is used."
  val targetSdk = moduleInfo.targetSdkVersion.apiLevel

  val renderingTargetSdk = renderingTarget?.version?.apiLevel ?: targetSdk

  val apiLevel = targetSdk.coerceAtMost(renderingTargetSdk)
  return SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + when {
    apiLevel >= 21 -> "Theme.Material.Light"
    apiLevel >= 14 || apiLevel >= 11 && screenSize == ScreenSize.XLARGE -> "Theme.Holo"
    else -> "Theme"
  }
}