/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import android.view.accessibility.AccessibilityNodeInfo
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.ResourceUrl
import com.android.tools.configurations.Configuration
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.parsers.TagSnapshot
import com.android.tools.visuallint.VisualLintConfiguration
import com.android.tools.visuallint.VisualLintRenderResult
import com.android.tools.visuallint.VisualLintViewInfoProvider

/**
 * Converts a [RenderResult] to a [VisualLintRenderResult].
 *
 * This extension function maps the rendering output, including root views and validator results, into a format suitable for visual linting
 * analysis.
 */
fun RenderResult.toVisualLintRenderResult(): VisualLintRenderResult {
  return VisualLintRenderResult(
    rootViews = rootViews,
    configuration = renderContext?.configuration?.toVisualLintConfiguration(),
    validatorResult = validatorResult,
  )
}

/**
 * Converts a [Configuration] to a [VisualLintConfiguration].
 *
 * This helper function extracts device, state, density, and locale information from the rendering configuration to be used during visual
 * linting.
 */
fun Configuration.toVisualLintConfiguration(): VisualLintConfiguration =
  VisualLintConfiguration(device = device, deviceState = deviceState, density = density, locale = locale)

object CustomVisualLintViewInfoProvider : VisualLintViewInfoProvider {
  override fun simpleName(viewInfo: ViewInfo): String {
    if (viewInfo.cookie is TagSnapshot) {
      return (viewInfo.cookie as TagSnapshot).tagName.substringAfterLast('.')
    } else if (viewInfo.accessibilityObject is AccessibilityNodeInfo && viewInfo.className == "android.view.View") {
      return "Composable"
    }
    return viewInfo.className.substringAfterLast('.')
  }

  override fun nameWithId(viewInfo: ViewInfo): String {
    val tagSnapshot = (viewInfo.cookie as? TagSnapshot)
    val name = simpleName(viewInfo)
    val id = tagSnapshot?.getAttribute(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)?.let { ResourceUrl.parse(it)?.name }
    return id?.let { "$id <$name>" } ?: name
  }
}
