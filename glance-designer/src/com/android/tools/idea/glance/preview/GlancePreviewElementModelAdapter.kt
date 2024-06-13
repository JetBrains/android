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
package com.android.tools.idea.glance.preview

import com.android.SdkConstants
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.DataContextHolder
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.preview.ConfigurablePreviewElementModelAdapter
import com.android.tools.idea.preview.MethodPreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.preview.PreviewXmlBuilder
import com.android.tools.preview.applyTo
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.android.tools.preview.dimensionToString
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

private const val PREFIX = "GlancePreview"
private val PSI_GLANCE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<PsiGlancePreviewElement>("$PREFIX.PreviewElement")

/** [PreviewElementModelAdapter] adapting [GlancePreviewElement] to [NlModel]. */
abstract class GlancePreviewElementModelAdapter<M : DataContextHolder> :
  ConfigurablePreviewElementModelAdapter<PsiGlancePreviewElement, M>,
  MethodPreviewElementModelAdapter<PsiGlancePreviewElement, M>(
    PSI_GLANCE_PREVIEW_ELEMENT_INSTANCE
  ) {

  override fun applyToConfiguration(
    previewElement: PsiGlancePreviewElement,
    configuration: Configuration,
  ) = previewElement.applyTo(configuration) { it.settings.getDefaultPreviewDevice() }
}

internal const val APP_WIDGET_VIEW_ADAPTER =
  "androidx.glance.appwidget.preview.GlanceAppWidgetViewAdapter"

object AppWidgetModelAdapter : GlancePreviewElementModelAdapter<NlModel>() {
  override fun toXml(previewElement: PsiGlancePreviewElement): String {
    val configuration = previewElement.configuration
    val width = dimensionToString(configuration.width, SdkConstants.VALUE_WRAP_CONTENT)
    val height = dimensionToString(configuration.height, SdkConstants.VALUE_WRAP_CONTENT)
    return PreviewXmlBuilder(APP_WIDGET_VIEW_ADAPTER)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, width)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, height)
      .androidAttribute(SdkConstants.ATTR_MIN_WIDTH, "1px")
      .androidAttribute(SdkConstants.ATTR_MIN_HEIGHT, "1px")
      .toolsAttribute("composableName", previewElement.methodFqn)
      .buildString()
  }

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long,
  ): LightVirtualFile =
    GlanceAppWidgetAdapterLightVirtualFile("model-glance-appwidget-$id.xml", content) { backedFile }
}
