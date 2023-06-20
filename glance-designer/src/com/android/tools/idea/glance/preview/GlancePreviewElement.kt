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

import com.android.tools.idea.preview.PreviewDisplaySettings
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * Information required to display the preview of a Glance UI element.
 *
 * TODO(b/239802877): Create 2 different classes instead for appwidget and tile cases with different
 *   PreviewConfigurations reflecting the specifics of those.
 */
class GlancePreviewElement(
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
  override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
  override val methodFqcn: String
) : MethodPreviewElement
