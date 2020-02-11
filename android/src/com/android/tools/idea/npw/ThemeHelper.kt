/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.logManifestIndexQueryError
import com.android.tools.idea.model.queryApplicationThemeFromManifestIndex
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil.trimStart
import org.jetbrains.android.facet.AndroidFacet

private const val DEFAULT_THEME_NAME = "AppTheme"
private const val ALTERNATE_THEME_NAME = "Theme.App"

/**
 * Theme utility class for use with templates.
 */
class ThemeHelper(private val module: Module) {
  private val projectRepository: LocalResourceRepository? = ResourceRepositoryManager.getProjectResources(module)
  val appThemeName: String?
    get() {
      val manifestTheme = appThemeNameFromManifest
      return when {
        manifestTheme != null -> trimStart(manifestTheme, SdkConstants.STYLE_RESOURCE_PREFIX)
        DEFAULT_THEME_NAME.toProjectStyleResource() != null -> DEFAULT_THEME_NAME
        ALTERNATE_THEME_NAME.toProjectStyleResource() != null -> ALTERNATE_THEME_NAME
        else -> null
      }
    }

  /**
   *  Try to get application theme from [AndroidManifestIndex] if index is enabled. And it falls back to the merged
   *  manifest snapshot if necessary.
   */
  val appThemeNameFromManifest: String?
    get() {
      if (AndroidManifestIndex.indexEnabled()) {
        try {
          val facet = AndroidFacet.getInstance(module)
          if (facet != null) {
            return DumbService.getInstance(module.project).runReadActionInSmartMode(Computable {
              facet.queryApplicationThemeFromManifestIndex()
            })
          }
        }
        catch (e: IndexNotReadyException) {
          // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
          //  We need to refactor the callers of this to require a *smart*
          //  read action, at which point we can remove this try-catch.
          logManifestIndexQueryError(e);
        }
      }

      return MergedManifestManager.getFreshSnapshot(module).manifestTheme
    }

  fun isLocalTheme(themeName: String) = themeName.toProjectStyleResource() != null

  private fun String.toProjectStyleResource(): StyleResourceValue? =
    projectRepository!!.getResources(ResourceNamespace.TODO(), ResourceType.STYLE, this)
      .firstOrNull()?.resourceValue as StyleResourceValue?

  companion object {
    fun themeExists(configuration: Configuration, themeName: String): Boolean = getStyleResource(configuration, themeName) != null

    fun hasActionBar(configuration: Configuration, themeName: String): Boolean? {
      val theme = getStyleResource(configuration, themeName) ?: return null
      val resolver = configuration.resourceResolver
      // TODO(namespaces): resolve themeName in the context of the right manifest file.
      val value = resolver.resolveResValue(
        resolver.findItemInStyle(theme, ResourceReference.attr(ResourceNamespace.TODO(), "windowActionBar")))
      return (value == null || value.value == null) || SdkConstants.VALUE_TRUE == value.value
    }

    private fun getStyleResource(configuration: Configuration, themeName: String): StyleResourceValue? {
      configuration.setTheme(themeName)
      val url = ResourceUrl.parse(themeName) ?: return null
      // TODO(namespaces): resolve themeName in the context of the right manifest file.
      val reference = url.resolve(ResourceNamespace.TODO(), Resolver.EMPTY_RESOLVER) ?: return null
      return configuration.resourceResolver.getStyle(reference)
    }
  }
}