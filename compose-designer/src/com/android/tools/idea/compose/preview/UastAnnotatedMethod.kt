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

import com.android.tools.compose.COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN
import com.android.tools.idea.preview.qualifiedName
import com.android.tools.idea.preview.toSmartPsiPointer
import com.android.tools.preview.AnnotatedMethod
import com.android.tools.preview.AnnotationAttributesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.uast.UMethod

/** [AnnotatedMethod] implementation based on [UMethod]. */
class UastAnnotatedMethod(private val method: UMethod) :
  AnnotatedMethod<SmartPsiElementPointer<PsiElement>> {
  override val name: String
    get() = method.name

  override val qualifiedName: String
    get() = method.qualifiedName

  override val methodBody: SmartPsiElementPointer<PsiElement>?
    get() = method.uastBody.toSmartPsiPointer()

  override val parameterAnnotations: List<Pair<String, AnnotationAttributesProvider>>
    get() =
      method.uastParameters.mapNotNull { parameter ->
        parameter.uAnnotations
          .firstOrNull { COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN == it.qualifiedName }
          ?.let { parameter.name to UastAnnotationAttributesProvider(it, emptyMap()) }
      }
}
