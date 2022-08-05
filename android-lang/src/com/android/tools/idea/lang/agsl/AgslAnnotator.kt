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
package com.android.tools.idea.lang.agsl

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.lang.agsl.psi.AgslGlslIdentifier
import com.android.tools.idea.lang.agsl.psi.AgslReservedKeyword
import com.android.tools.idea.lang.agsl.psi.AgslUnsupportedKeyword
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.psi.PsiElement

class AgslAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!StudioFlags.AGSL_LANGUAGE_SUPPORT.get()) {
      return
    }
    when (element) {
      is AgslUnsupportedKeyword -> {
        holder.newAnnotation(
          ERROR,
          "`${element.text}` is not allowed in ${AgslLanguage.PRIVATE_AGSL_LANGUAGE.displayName}"
        ).create()
      }
      is AgslReservedKeyword -> {
        holder.newAnnotation(
          ERROR,
          "`${element.text}` is a reserved future keyword"
        ).create()
      }
      is AgslGlslIdentifier -> {
        holder.newAnnotation(
          ERROR,
          "GLSL predefined variables (`gl_*`) are not allowed in ${AgslLanguage.PRIVATE_AGSL_LANGUAGE.displayName}"
        ).create()
      }
    }
  }
}