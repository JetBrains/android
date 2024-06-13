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

import com.android.tools.preview.ConfigurablePreviewElement
import com.android.tools.preview.MethodPreviewElement
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElementInstance
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/** Preview elements implementation for a wear tile. */
data class WearTilePreviewElement<T>(
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinition: T?,
  override val previewBody: T?,
  override val methodFqn: String,
  override val configuration: PreviewConfiguration,
  override val hasAnimations: Boolean = false,
  override val instanceId: String = methodFqn,
) : MethodPreviewElement<T>, ConfigurablePreviewElement<T>, PreviewElementInstance<T> {
  override fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ) = copy(displaySettings = displaySettings, configuration = config)
}

typealias PsiWearTilePreviewElement = WearTilePreviewElement<SmartPsiElementPointer<PsiElement>>
