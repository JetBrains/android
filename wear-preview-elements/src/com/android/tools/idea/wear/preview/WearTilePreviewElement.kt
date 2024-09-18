/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_MIN_HEIGHT
import com.android.SdkConstants.ATTR_MIN_WIDTH
import com.android.SdkConstants.CLASS_TILE_SERVICE_VIEW_ADAPTER
import com.android.tools.preview.ConfigurablePreviewElement
import com.android.tools.preview.MethodPreviewElement
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElementInstance
import com.android.tools.preview.PreviewXmlBuilder
import com.android.tools.preview.XmlSerializable
import kotlinx.coroutines.flow.MutableStateFlow

private const val DEFAULT_WEAR_TILE_BACKGROUND = "#ff000000"

/** Preview elements implementation for a wear tile. */
data class WearTilePreviewElement<T>(
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinition: T?,
  override val previewBody: T?,
  override val methodFqn: String,
  override val configuration: PreviewConfiguration,
  override val instanceId: String = methodFqn,
) :
  MethodPreviewElement<T>,
  ConfigurablePreviewElement<T>,
  PreviewElementInstance<T>,
  XmlSerializable {
  /**
   * Contains the link to the most recently inflated view. Should be an instance of
   * TileServiceViewAdapter. see [CLASS_TILE_SERVICE_VIEW_ADAPTER] class
   */
  val tileServiceViewAdapter: MutableStateFlow<Any?> = MutableStateFlow(null)
  override var hasAnimations = false

  override fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ) = copy(displaySettings = displaySettings, configuration = config)

  override fun toPreviewXml() =
    PreviewXmlBuilder(CLASS_TILE_SERVICE_VIEW_ADAPTER)
      .androidAttribute(ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT)
      .androidAttribute(
        ATTR_BACKGROUND,
        displaySettings.backgroundColor ?: DEFAULT_WEAR_TILE_BACKGROUND,
      )
      .androidAttribute(ATTR_MIN_WIDTH, "1px")
      .androidAttribute(ATTR_MIN_HEIGHT, "1px")
      .toolsAttribute("tilePreviewMethodFqn", methodFqn)
}
