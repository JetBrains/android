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
package com.android.tools.idea.dagger.index.psiwrappers

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression

/** A [DaggerIndexPsiWrapper] representing an annotation. */
interface DaggerIndexAnnotationWrapper : DaggerIndexPsiWrapper {
  /** Name of the annotation in source. Can be a fully-qualified or simple name. */
  fun getAnnotationNameInSource(): String

  /**
   * Various Dagger attributes have arguments that list out class names. As an example:
   * `@Component(modules = [Module1::class, Module2::class])`
   *
   * This method returns the names of the classes that are listed in a given argument, in the form
   * they're written. The above example would return `["Module1", "Module2"]`.
   */
  fun getArgumentClassNames(argumentName: String): List<String>
}

internal class KtAnnotationEntryWrapper(private val ktAnnotationEntry: KtAnnotationEntry) :
  DaggerIndexAnnotationWrapper {
  override fun getAnnotationNameInSource(): String = ktAnnotationEntry.typeReference!!.text

  override fun getArgumentClassNames(argumentName: String): List<String> {
    val argument =
      ktAnnotationEntry.valueArgumentList?.arguments?.firstOrNull {
        it.getArgumentName()?.text == argumentName
      }
    return argument
      ?.getArgumentExpression()
      ?.childrenOfType<KtClassLiteralExpression>()
      ?.mapNotNull { it.lhs?.text }
      ?: emptyList()
  }
}

internal class PsiAnnotationWrapper(private val psiAnnotation: PsiAnnotation) :
  DaggerIndexAnnotationWrapper {
  override fun getAnnotationNameInSource(): String = psiAnnotation.nameReferenceElement!!.text

  override fun getArgumentClassNames(argumentName: String): List<String> {
    val argumentValue =
      psiAnnotation.parameterList.attributes.firstOrNull { it.name == argumentName }?.value
    return if (argumentValue is PsiClassObjectAccessExpression) {
      listOf(argumentValue.operand.text)
    } else {
      argumentValue?.childrenOfType<PsiClassObjectAccessExpression>()?.map { it.operand.text }
        ?: emptyList()
    }
  }
}
