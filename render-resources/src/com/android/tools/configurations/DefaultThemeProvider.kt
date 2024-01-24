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
package com.android.tools.configurations

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceUrl
import com.android.tools.environment.Logger

/** [ResourceReference] to the postSplashScreenTheme. */
private val postSplashAttrReference = ResourceReference.attr(
  ResourceNamespace.RES_AUTO, "postSplashScreenTheme"
)

/**
 * Finds the post splash theme if there is any. Themes used in splash screens can have a post splash theme declared.
 * When a splash screen theme is used in the manifest, the tools should probably not use that one unless the user has explicitly selected
 * it. For "preferred theme" computation purposes, we try to find the post splash screen theme.
 * See [splash screen documentation.](https://developer.android.com/reference/kotlin/androidx/core/splashscreen/SplashScreen)
 * @param themeStyle the default theme found in the manifest.
 * @return the post activity splash screen if any or `themeStyle` otherwise.
 */
private fun findPostSplashTheme(themeStyle: String, configuration: Configuration): String {
  val log = Logger.getInstance(Configuration::class.java)
  val themeUrl = ResourceUrl.parseStyleParentReference(themeStyle)
  if (themeUrl == null) {
    if (log.isDebugEnabled) log.debug(String.format("Unable to parse theme %s", themeStyle))
    return themeStyle
  }
  val namespace = ResourceNamespace.fromNamespacePrefix(
    themeUrl.namespace, ResourceNamespace.RES_AUTO, ResourceNamespace.Resolver.EMPTY_RESOLVER
  )
  val reference = themeUrl.resolve(
    namespace ?: ResourceNamespace.RES_AUTO,
    ResourceNamespace.Resolver.EMPTY_RESOLVER
  )
  if (reference == null) {
    if (log.isDebugEnabled) log.debug(String.format("Unable to resolve reference for theme %s", themeUrl))
    return themeStyle
  }
  val resolverCache: ResourceResolverCache = configuration.settings.resolverCache
  val resourceResolver = resolverCache.getResourceResolver(configuration.target, themeUrl.toString(), configuration.fullConfig)
  val theme = resourceResolver.getStyle(reference)
  if (theme == null) {
    if (log.isDebugEnabled) log.debug(String.format("Unable to resolve theme %s", themeUrl))
    return themeStyle
  }
  val value = resourceResolver.findItemInStyle(theme, postSplashAttrReference)
  val resolvedValue = resourceResolver.resolveResValue(value)
  val postSplashTheme = resolvedValue?.resourceUrl?.toString()
  val resolveTheme = postSplashTheme ?: themeStyle
  if (log.isDebugEnabled) log.debug(String.format("Post splash resolved=%s, original theme=%s", postSplashTheme, themeUrl))
  return resolveTheme
}

object DefaultThemeProvider {
  @Slow
  @JvmStatic
  fun computeDefaultThemeForConfiguration(configuration: Configuration): String {
    // TODO: If we are rendering a layout in included context, pick the theme from the outer layout instead.
    val activityName: String? = configuration.activity
    val themeInfo: ThemeInfoProvider = configuration.settings.configModule.themeInfoProvider
    if (activityName != null) {
      var activityFqcn = activityName
      if (activityName.startsWith(".")) {
        val packageName: String? = configuration.settings.configModule.resourcePackage
        activityFqcn = packageName + activityName
      }
      val theme = themeInfo.getThemeNameForActivity(activityFqcn);
      if (theme != null) {
        return theme
      }
    }

    // Returns an app theme if possible
    val manifestTheme =
      themeInfo.appThemeName
      // Look up the default/fallback theme to use for this project (which depends on the screen size when no particular
      // theme is specified in the manifest).
      ?: themeInfo.getDeviceDefaultTheme(configuration.target, configuration.screenSize, configuration.cachedDevice)

    return findPostSplashTheme(manifestTheme, configuration)
  }
}