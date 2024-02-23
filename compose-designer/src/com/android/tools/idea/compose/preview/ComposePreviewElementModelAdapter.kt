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

import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.preview.ConfigurablePreviewElementModelAdapter
import com.android.tools.idea.preview.MethodPreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.applyTo
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/** [PreviewElementModelAdapter] adapting [ComposePreviewElementInstance] to [NlModel]. */
abstract class ComposePreviewElementModelAdapter :
  ConfigurablePreviewElementModelAdapter<PsiComposePreviewElementInstance, NlModel>,
  MethodPreviewElementModelAdapter<PsiComposePreviewElementInstance, NlModel>(
    PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
  ) {

  override fun applyToConfiguration(
    previewElement: PsiComposePreviewElementInstance,
    configuration: Configuration,
  ) = previewElement.applyTo(configuration) { it.settings.getDefaultPreviewDevice() }

  override fun createLightVirtualFile(
    content: String,
    backedFile: VirtualFile,
    id: Long,
  ): LightVirtualFile =
    ComposeAdapterLightVirtualFile("compose-model-$id.xml", content) { backedFile }
}
