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

import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.xml.PreviewXmlBuilder
import com.intellij.psi.PsiFile

private const val VIEW_ADAPTER = "androidx.glance.wear.tiles.preview.GlanceTileServiceViewAdapter"

/** [PreviewRepresentation] for the Glance App Widget elements. */
internal class TilePreviewRepresentation(
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<GlancePreviewElement>,
) : GlancePreviewRepresentation<GlancePreviewElement>(psiFile, previewProvider) {
  override fun toPreviewXmlString(previewElement: GlancePreviewElement) =
    PreviewXmlBuilder(VIEW_ADAPTER)
      .androidAttribute(ATTR_LAYOUT_WIDTH, "wrap_content")
      .androidAttribute(ATTR_LAYOUT_HEIGHT, "wrap_content")
      .toolsAttribute("composableName", previewElement.methodFqcn)
      .buildString()
}
