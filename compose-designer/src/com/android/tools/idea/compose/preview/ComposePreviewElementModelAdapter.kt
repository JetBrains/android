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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.util.applyTo
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/** [PreviewElementModelAdapter] adapting [ComposePreviewElementInstance] to [NlModel]. */
abstract class ComposePreviewElementModelAdapter :
  PreviewElementModelAdapter<ComposePreviewElementInstance, NlModel> {
  override fun calcAffinity(
    el1: ComposePreviewElementInstance,
    el2: ComposePreviewElementInstance?
  ): Int {
    if (el2 == null) return 3

    return when {
      // These are the same
      el1 == el2 -> 0

      // The method and display settings are the same
      el1.composableMethodFqn == el2.composableMethodFqn &&
        el1.displaySettings == el2.displaySettings -> 1

      // The name of the @Composable method matches but other settings might be different
      el1.composableMethodFqn == el2.composableMethodFqn -> 2

      // No match
      else -> 4
    }
  }

  override fun applyToConfiguration(
    previewElement: ComposePreviewElementInstance,
    configuration: Configuration
  ) = previewElement.applyTo(configuration)

  override fun modelToElement(model: NlModel): ComposePreviewElementInstance? =
    if (!Disposer.isDisposed(model)) {
      model.dataContext.getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE)
    } else null

  override fun toLogString(previewElement: ComposePreviewElementInstance): String =
    """
        displayName=${previewElement.displaySettings.name}
        methodName=${previewElement.composableMethodFqn}
  """
      .trimIndent()

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long
  ): LightVirtualFile =
    ComposeAdapterLightVirtualFile("compose-model-$id.xml", content) { backedFile }
}
