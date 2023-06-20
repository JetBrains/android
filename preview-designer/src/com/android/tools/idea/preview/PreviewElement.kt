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
package com.android.tools.idea.preview

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

enum class DisplayPositioning {
  TOP, // Previews with this priority will be displayed at the top
  NORMAL
}

/**
 * Settings that modify how a [PreviewElement] is rendered
 *
 * @param name display name of this preview element
 * @param group name that allows multiple previews in separate groups
 * @param showDecoration when true, the system decorations (navigation and status bars) should be displayed as part of the render
 * @param showBackground when true, the preview will be rendered with the material background as background color by default
 * @param backgroundColor when [showBackground] is true, this is the background color to be used by the preview. If null, the default
 * activity background specified in the system theme will be used.
 */
data class PreviewDisplaySettings(val name: String,
                                  val group: String?,
                                  val showDecoration: Boolean,
                                  val showBackground: Boolean,
                                  val backgroundColor: String?,
                                  val displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL)

/**
 * Definition of a preview element
 */
interface PreviewElement : PreviewNode {
  /** Settings that affect how the [PreviewElement] is presented in the preview surface */
  val displaySettings: PreviewDisplaySettings

  /** [SmartPsiElementPointer] to the preview element definition.
   *  This means the code that indicates that [previewBodyPsi] should be previewed. This might be the [previewBodyPsi]
   *  itself or an annotation (annotating the composable method, that won't necessarily be a '@Preview' when
   *  Multipreview is enabled).
   */
  val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?

  /** [SmartPsiElementPointer] to the preview body. This is the code that will be run during preview */
  val previewBodyPsi: SmartPsiElementPointer<PsiElement>?

  /** [PsiFile] containing this PreviewElement. null if there is not source file, like in synthetic preview elements */
  val containingFile: PsiFile?
    get() = runReadAction {
      previewBodyPsi?.containingFile ?: previewElementDefinitionPsi?.containingFile
    }
}