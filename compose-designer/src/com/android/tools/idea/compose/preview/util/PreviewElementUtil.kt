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
package com.android.tools.idea.compose.preview.util

import com.android.tools.idea.preview.PsiPreviewElement
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile

/**
 * [PsiFile] containing this PreviewElement. null if there is no source file, like in synthetic
 * preview elements.
 */
val PsiPreviewElement.containingFile: PsiFile?
  get() = runReadAction { previewBody?.containingFile ?: previewElementDefinition?.containingFile }
