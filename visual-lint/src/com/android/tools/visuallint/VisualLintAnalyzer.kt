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
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.Density.DEFAULT_DENSITY
import com.android.tools.idea.validator.ValidatorData
import com.android.utils.HtmlBuilder
import kotlin.collections.emptyList

/** Base class for all Visual Linting analyzers. */
abstract class VisualLintAnalyzer {
  abstract val type: VisualLintErrorType

  /** Analyze the given [VisualLintRenderResult] for visual lint issues and return found [VisualLintIssueContent]s */
  fun analyze(renderResult: VisualLintRenderResult): List<VisualLintIssueContent> {
    val configuration = renderResult.configuration ?: return emptyList()
    return findIssues(renderResult, configuration)
  }

  abstract fun findIssues(renderResult: VisualLintRenderResult, configuration: VisualLintConfiguration): List<VisualLintIssueContent>

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

    fun simpleName(view: ViewInfo): String = ViewInfoProvider.simpleName(view)

    fun nameWithId(viewInfo: ViewInfo): String = ViewInfoProvider.nameWithId(viewInfo)

    fun checkIsClass(viewInfo: ViewInfo, clazz: Class<*>): Boolean {
      return clazz.isInstance(viewInfo.viewObject) || clazz.canonicalName == viewInfo.className
    }

    fun pxToDp(config: VisualLintConfiguration, androidPx: Int): Int {
      val dpiValue = config.density.dpiValue
      return androidPx * DEFAULT_DENSITY / dpiValue
    }
  }
}

/** [VisualLintViewInfoProvider] used by [VisualLintAnalyzer]. */
object ViewInfoProvider : VisualLintViewInfoProvider {
  /** Custom [VisualLintViewInfoProvider] that can be used to override the default behavior. */
  private var customProvider: VisualLintViewInfoProvider? = null

  /** Sets a custom [VisualLintViewInfoProvider]. */
  @JvmStatic
  fun setCustomProvider(provider: VisualLintViewInfoProvider?) {
    customProvider = provider
  }

  /** Returns a simple name for the given [viewInfo]. */
  override fun simpleName(viewInfo: ViewInfo): String {
    customProvider?.let {
      return it.simpleName(viewInfo)
    }
    if (viewInfo.accessibilityObject is AccessibilityNodeInfo && viewInfo.className == "android.view.View") {
      return "Composable"
    }
    return viewInfo.className.substringAfterLast('.')
  }

  /** Returns a name with ID for the given [viewInfo]. */
  override fun nameWithId(viewInfo: ViewInfo): String = customProvider?.nameWithId(viewInfo) ?: simpleName(viewInfo)
}
