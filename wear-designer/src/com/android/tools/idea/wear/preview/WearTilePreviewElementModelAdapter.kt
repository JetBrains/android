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
package com.android.tools.idea.wear.preview

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

private const val PREFIX = "WearTilePreview"
private val WEAR_TILE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<ClassPreviewElement>("$PREFIX.PreviewElement")

/** [PreviewElementModelAdapter] adapting [ClassPreviewElement] to [NlModel]. */
internal abstract class ClassPreviewElementModelAdapter<T : ClassPreviewElement, M : DataContextHolder> :
  PreviewElementModelAdapter<T, M> {
  override fun calcAffinity(el1: T, el2: T?): Int {
    if (el2 == null) return 3

    return when {
      // These are the same
      el1 == el2 -> 0

      // The class name and display settings are the same
      el1.fqcn == el2.fqcn && el1.displaySettings == el2.displaySettings -> 1

      // The name names match but other settings might be different
      el1.fqcn == el2.fqcn -> 2

      // No match
      else -> 4
    }
  }

  override fun applyToConfiguration(previewElement: T, configuration: Configuration) {
    configuration.target = configuration.configurationManager.highestApiTarget
  }

  override fun modelToElement(model: M): T? =
    if (!Disposer.isDisposed(model)) {
      model.dataContext.getData(WEAR_TILE_PREVIEW_ELEMENT_INSTANCE) as? T
    } else null

  /**
   * Creates a [DataContext] that is when assigned to [NlModel] can be retrieved with
   * [modelToElement] call against that model.
   */
  override fun createDataContext(previewElement: T) = DataContext { dataId ->
    when (dataId) {
      WEAR_TILE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      else -> null
    }
  }

  override fun toLogString(previewElement: T) = "displayName=${previewElement.displaySettings.name}"
}

internal const val TILE_SERVICE_VIEW_ADAPTER = "androidx.wear.tiles.tooling.TileServiceViewAdapter"

internal class WearTilePreviewElementModelAdapter<M : DataContextHolder> : ClassPreviewElementModelAdapter<WearTilePreviewElement, M>() {
  override fun toXml(previewElement: WearTilePreviewElement) =
    PreviewXmlBuilder(TILE_SERVICE_VIEW_ADAPTER)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, "wrap_content")
      .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, "wrap_content")
      .toolsAttribute("tileServiceName", previewElement.fqcn)
      .buildString()

  override fun createLightVirtualFile(content: String, backedFile: VirtualFile, id: Long): LightVirtualFile =
    WearTileAdapterLightVirtualFile("model-weartile-$id.xml", content) { backedFile }
}