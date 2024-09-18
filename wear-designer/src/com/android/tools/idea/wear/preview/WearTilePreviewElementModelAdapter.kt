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

import com.android.tools.idea.common.model.NlDataProviderHolder
import com.android.tools.idea.preview.ConfigurablePreviewElementModelAdapter
import com.android.tools.idea.preview.MethodPreviewElementModelAdapter
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

private const val PREFIX = "WearTilePreview"
private val PSI_WEAR_TILE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<PsiWearTilePreviewElement>("$PREFIX.PreviewElement")

internal class WearTilePreviewElementModelAdapter<M : NlDataProviderHolder> :
  ConfigurablePreviewElementModelAdapter<PsiWearTilePreviewElement, M>,
  MethodPreviewElementModelAdapter<PsiWearTilePreviewElement, M>(
    PSI_WEAR_TILE_PREVIEW_ELEMENT_INSTANCE
  ) {
  override fun toXml(previewElement: PsiWearTilePreviewElement) =
    previewElement.toPreviewXml().buildString()

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long,
  ): LightVirtualFile =
    WearTileAdapterLightVirtualFile("model-weartile-$id.xml", content, backedFile)
}
