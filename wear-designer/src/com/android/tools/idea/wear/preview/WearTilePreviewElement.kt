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

import com.android.ide.common.resources.Locale
import com.android.tools.preview.MethodPreviewElement
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlin.math.max

/** Preview elements implementation for a wear tile. */
open class WearTilePreviewElement(
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
  override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
  override val methodFqn: String,
  val configuration: WearTilePreviewConfiguration
) : MethodPreviewElement

data class WearTilePreviewConfiguration
internal constructor(
  val device: String,
  val locale: Locale?,
  val fontScale: Float,
) {
  companion object {
    fun forValues(device: String? = null, locale: Locale? = null, fontScale: Float? = null) =
      WearTilePreviewConfiguration(
        device = device ?: "",
        locale = locale,
        fontScale = max(0f, fontScale ?: 1f)
      )
  }
}