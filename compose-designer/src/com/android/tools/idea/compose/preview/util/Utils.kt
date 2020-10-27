/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.uast.UElement

fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}

/**
 * Extension method that returns if the file is a Kotlin file. This method first checks for the extension to fail fast without having to
 * actually trigger the potentially costly [VirtualFile#fileType] call.
 */
internal fun VirtualFile.isKotlinFileType(): Boolean =
  extension == KotlinFileType.INSTANCE.defaultExtension && fileType == KotlinFileType.INSTANCE

/**
 * Returns an index indicating how close the given model is to the given [PreviewElementInstance] 0 meaning they are equal and higher the
 * more dissimilar they are. This allows that, when re-using models, the most similar model is re-used. When the user is just switching
 * groups or selecting a specific model, this allows switching to the existing preview faster.
 */
@VisibleForTesting
fun modelAffinity(e0: PreviewElement?, e1: PreviewElement): Int {
  if (e0 == null) return  3 // There is no PreviewElement associated to this method

  return when {
    // These are the same
    e0 == e1 -> 0

    // The method and display settings are the same
    e0.composableMethodFqn == e1.composableMethodFqn &&
      e0.displaySettings == e1.displaySettings->  1

    // The name of the @Composable method matches but other settings might be different
    e0.composableMethodFqn == e1.composableMethodFqn ->  2

    // No match
    else -> 4
  }
}

internal fun modelAffinity(dataContext: DataContext, element: PreviewElementInstance): Int {
  val modelPreviewElement = dataContext.getData(COMPOSE_PREVIEW_ELEMENT)
                            ?: return  3 // There is no PreviewElement associated to this method

  return modelAffinity(modelPreviewElement, element)
}