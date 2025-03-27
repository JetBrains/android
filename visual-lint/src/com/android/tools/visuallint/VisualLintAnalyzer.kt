/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.visuallint

import android.view.accessibility.AccessibilityNodeInfo
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.Density.DEFAULT_DENSITY
import com.android.resources.ResourceUrl
import com.android.tools.configurations.Configuration
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.parsers.TagSnapshot
import com.android.utils.HtmlBuilder

/** Base class for all Visual Linting analyzers. */
abstract class VisualLintAnalyzer {
  abstract val type: VisualLintErrorType

  /**
   * Analyze the given [RenderResult] for visual lint issues and return found
   * [VisualLintIssueContent]s
   */
  fun analyze(renderResult: RenderResult): List<VisualLintIssueContent> {
    val configuration = renderResult.renderContext?.configuration ?: return emptyList()
    return findIssues(renderResult, configuration)
  }

  abstract fun findIssues(
    renderResult: RenderResult,
    configuration: Configuration,
  ): List<VisualLintIssueContent>

  data class VisualLintIssueContent(
    val view: ViewInfo?,
    val message: String,
    val atfIssue: ValidatorData.Issue? = null,
    val descriptionProvider: (Int) -> HtmlBuilder,
  )

  companion object {
    fun previewConfigurations(count: Int): String {
      return if (count == 1) "a preview configuration" else "$count preview configurations"
    }

    fun simpleName(view: ViewInfo): String {
      if (view.cookie is TagSnapshot) {
        return (view.cookie as TagSnapshot).tagName.substringAfterLast('.')
      } else if (
        view.accessibilityObject is AccessibilityNodeInfo && view.className == "android.view.View"
      ) {
        return "Composable"
      }
      return view.className.substringAfterLast('.')
    }

    fun nameWithId(viewInfo: ViewInfo): String {
      val tagSnapshot = (viewInfo.cookie as? TagSnapshot)
      val name = simpleName(viewInfo)
      val id =
        tagSnapshot?.getAttribute(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)?.let {
          ResourceUrl.parse(it)?.name
        }
      return id?.let { "$id <$name>" } ?: name
    }

    fun checkIsClass(viewInfo: ViewInfo, clazz: Class<*>): Boolean {
      return clazz.isInstance(viewInfo.viewObject) || clazz.canonicalName == viewInfo.className
    }

    fun pxToDp(config: Configuration, androidPx: Int): Int {
      val dpiValue = config.density.dpiValue
      return androidPx * DEFAULT_DENSITY / dpiValue
    }
  }
}
