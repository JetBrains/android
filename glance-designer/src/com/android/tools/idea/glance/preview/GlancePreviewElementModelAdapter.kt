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
import com.android.tools.idea.common.model.DataContextHolder
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.xml.PreviewXmlBuilder
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

private const val PREFIX = "GlancePreview"
private val GLANCE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<MethodPreviewElement>("$PREFIX.PreviewElement")

/** [PreviewElementModelAdapter] adapting [GlancePreviewElement] to [NlModel]. */
abstract class GlancePreviewElementModelAdapter<T : MethodPreviewElement, M : DataContextHolder> :
  PreviewElementModelAdapter<T, M> {
  override fun calcAffinity(el1: T, el2: T?): Int {
    if (el2 == null) return 3

    return when {
      // These are the same
      el1 == el2 -> 0

      // The method and display settings are the same
      el1.methodFqcn == el2.methodFqcn && el1.displaySettings == el2.displaySettings -> 1

      // The name of the @Composable method matches but other settings might be different
      el1.methodFqcn == el2.methodFqcn -> 2

      // No match
      else -> 4
    }
  }

  override fun applyToConfiguration(previewElement: T, configuration: Configuration) {
    configuration.target = configuration.configurationManager.highestApiTarget
  }

  override fun modelToElement(model: M): T? =
    if (!Disposer.isDisposed(model)) {
      model.dataContext.getData(GLANCE_PREVIEW_ELEMENT_INSTANCE) as? T
    } else null

  /**
   * Creates a [DataContext] that is when assigned to [NlModel] can be retrieved with
   * [modelToElement] call against that model.
   */
  override fun createDataContext(previewElement: T) = DataContext { dataId ->
    when (dataId) {
      GLANCE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      else -> null
    }
  }

  override fun toLogString(previewElement: T) = "displayName=${previewElement.displaySettings.name}"
}

private const val APP_WIDGET_VIEW_ADAPTER =
  "androidx.glance.appwidget.preview.GlanceAppWidgetViewAdapter"

object AppWidgetModelAdapter : GlancePreviewElementModelAdapter<GlancePreviewElement, NlModel>() {
  override fun toXml(previewElement: GlancePreviewElement) =
    PreviewXmlBuilder(APP_WIDGET_VIEW_ADAPTER)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, "wrap_content")
      .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, "wrap_content")
      .toolsAttribute("composableName", previewElement.methodFqcn)
      .buildString()

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long
  ): LightVirtualFile =
    GlanceAppWidgetAdapterLightVirtualFile("model-appwidget-$id.xml", content) { backedFile }
}

private const val TILE_VIEW_ADAPTER =
  "androidx.glance.wear.tiles.preview.GlanceTileServiceViewAdapter"

object WearTilesModelAdapter : GlancePreviewElementModelAdapter<GlancePreviewElement, NlModel>() {
  override fun toXml(previewElement: GlancePreviewElement) =
    PreviewXmlBuilder(TILE_VIEW_ADAPTER)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, "wrap_content")
      .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, "wrap_content")
      .toolsAttribute("composableName", previewElement.methodFqcn)
      .buildString()

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long
  ): LightVirtualFile =
    GlanceTileAdapterLightVirtualFile("model-weartile-$id.xml", content) { backedFile }
}
