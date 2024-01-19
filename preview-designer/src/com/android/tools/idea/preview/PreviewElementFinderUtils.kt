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
package com.android.tools.idea.preview

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/** Helper method that returns a map with all the default values of a preview annotation */
fun UAnnotation.findPreviewDefaultValues(): Map<String, String?> =
  when (val resolvedImplementation = this.resolve()) {
    is ClsClassImpl ->
      resolvedImplementation.methods.associate { psiMethod ->
        Pair(psiMethod.name, (psiMethod as ClsMethodImpl).defaultValue?.text?.trim('"')?.nullize())
      }
    is KtLightClass ->
      resolvedImplementation.methods.associate { psiMethod ->
        Pair(psiMethod.name, (psiMethod as KtLightMethod).defaultValue?.text?.trim('"')?.nullize())
      }
    else -> mapOf()
  }

/** Helper getter that returns a qualified name for a given [UMethod] */
val UMethod.qualifiedName: String
  get() = "${(this.uastParent as UClass).qualifiedName}.${this.name}"

/** Helper method that creates a smart PSI pointer from a given [UElement] */
fun UElement?.toSmartPsiPointer(): SmartPsiElementPointer<PsiElement>? {
  val bodyPsiElement = this?.sourcePsi ?: return null
  return SmartPointerManager.createPointer(bodyPsiElement)
}
