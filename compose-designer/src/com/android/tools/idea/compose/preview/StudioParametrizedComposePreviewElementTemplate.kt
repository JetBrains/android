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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.preview.util.containingFile
import com.android.tools.idea.rendering.StudioModuleRenderContext
import com.android.tools.preview.ParametrizedComposePreviewElementTemplate
import com.android.tools.preview.PreviewParameter
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * [ParametrizedComposePreviewElementTemplate] based on studio-specific [ModuleRenderContext] from
 * [PsiComposePreviewElement] constructor.
 */
class StudioParametrizedComposePreviewElementTemplate(
  basePreviewElement: PsiComposePreviewElement,
  parameterProviders: Collection<PreviewParameter>,
) :
  ParametrizedComposePreviewElementTemplate<SmartPsiElementPointer<PsiElement>>(
    basePreviewElement,
    parameterProviders,
    StudioParametrizedComposePreviewElementTemplate::class.java.classLoader,
    { element -> element.containingFile?.let { StudioModuleRenderContext.forFile(it) } },
  )
