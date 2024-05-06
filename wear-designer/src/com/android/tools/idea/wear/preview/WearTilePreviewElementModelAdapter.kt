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
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.DataContextHolder
import com.android.tools.idea.preview.MethodPreviewElementModelAdapter
import com.android.tools.preview.PreviewXmlBuilder
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

private const val PREFIX = "WearTilePreview"
private val WEAR_TILE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<WearTilePreviewElement>("$PREFIX.PreviewElement")

internal const val TILE_SERVICE_VIEW_ADAPTER = "androidx.wear.tiles.tooling.TileServiceViewAdapter"
internal const val DEFAULT_WEAR_TILE_BACKGROUND = "#ff000000"

internal class WearTilePreviewElementModelAdapter<M : DataContextHolder> :
  MethodPreviewElementModelAdapter<WearTilePreviewElement, M>(WEAR_TILE_PREVIEW_ELEMENT_INSTANCE) {
    override fun toXml(previewElement: WearTilePreviewElement) =
    PreviewXmlBuilder(TILE_SERVICE_VIEW_ADAPTER)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
      .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT)
      .androidAttribute(
        SdkConstants.ATTR_BACKGROUND,
        previewElement.displaySettings.backgroundColor ?: DEFAULT_WEAR_TILE_BACKGROUND
      )
      .toolsAttribute("tilePreviewMethodFqn", previewElement.methodFqn)
      .buildString()

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long
  ): LightVirtualFile =
    WearTileAdapterLightVirtualFile("model-weartile-$id.xml", content) { backedFile }

  override fun applyToConfiguration(
    previewElement: WearTilePreviewElement,
    configuration: Configuration
  ) {
    configuration.apply {
      startBulkEditing()
      target = configuration.settings.highestApiTarget
      val device = settings.devices.findById(previewElement.configuration.device)
      device?.let {
        setEffectiveDevice(null, null)
        setDevice(device, false)
      }
      previewElement.configuration.locale?.let {
        locale = it
      }
      fontScale = previewElement.configuration.fontScale
      finishBulkEditing()
    }
  }
}

private fun Collection<Device>.findById(deviceDefinition: String): Device? {
  val id = deviceDefinition.removePrefix("id:")
  return firstOrNull { it.id == id }
}
