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

import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.psi.KtAnnotated

interface DaggerIndexAnnotatedWrapper : DaggerIndexPsiWrapper {
  /**
   * Returns whether the element might be annotated by the given type (specified by fully-qualified
   * name).
   *
   * For example, given an element with the annotation "@Inject",
   * `getIsAnnotatedWith("javax.inject.Inject", ...)` would return true as long as the file has
   * appropriate import statements.
   *
   * Because this evaluation is happening at index time, it's not possible to say that the type
   * actually matches. This function indicates at best that an annotation *may* be of the given
   * type. A return value of `false` means that it definitively will not be of that type.
   */
  fun getIsAnnotatedWith(fqName: String) = getAnnotationsByName(fqName).any()

  /**
   * Gets a list of annotations on this element that might correspond to the given fully-qualified
   * name. (See [getIsAnnotatedWith] for an explanation of why this is not a guaranteed match.)
   */
  fun getAnnotationsByName(fqName: String): Sequence<DaggerIndexAnnotationWrapper>
}

internal abstract class DaggerIndexAnnotatedKotlinWrapper(
  private val ktAnnotated: KtAnnotated,
  private val importHelper: KotlinImportHelper
) : DaggerIndexAnnotatedWrapper {
  override fun getAnnotationsByName(fqName: String) =
    ktAnnotated.annotationEntries.asSequence().map { KtAnnotationEntryWrapper(it) }.filter {
      importHelper.getPossibleAnnotationText(fqName).contains(it.getAnnotationNameInSource())
    }
}

internal abstract class DaggerIndexAnnotatedJavaWrapper(
  private val psiModifierListOwner: PsiModifierListOwner,
  private val importHelper: JavaImportHelper
) : DaggerIndexAnnotatedWrapper {
  override fun getAnnotationsByName(fqName: String) =
    psiModifierListOwner.annotations.asSequence().map { PsiAnnotationWrapper(it) }.filter {
      importHelper.getPossibleAnnotationText(fqName).contains(it.getAnnotationNameInSource())
    }
}
