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

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/** A [DaggerIndexPsiWrapper] representing a class. */
interface DaggerIndexClassWrapper : DaggerIndexAnnotatedWrapper {
  /**
   * Returns the fully-qualified class ID of the class, including package and containing classes.
   */
  fun getClassId(): ClassId

  /**
   * Returns whether the specified annotation is present on:
   * 1. the wrapped class, or
   * 2. the parent of the wrapped object, when the wrapped object is a companion.
   */
  fun getIsSelfOrCompanionParentAnnotatedWith(annotation: DaggerAnnotation): Boolean
}

internal class KtClassOrObjectWrapper(
  private val ktClassOrObject: KtClassOrObject,
  private val importHelper: KotlinImportHelper
) : DaggerIndexAnnotatedKotlinWrapper(ktClassOrObject, importHelper), DaggerIndexClassWrapper {
  override fun getClassId(): ClassId = ktClassOrObject.getClassId()!!

  override fun getIsSelfOrCompanionParentAnnotatedWith(annotation: DaggerAnnotation): Boolean =
    getIsAnnotatedWith(annotation) ||
      (ktClassOrObject is KtObjectDeclaration &&
        ktClassOrObject.isCompanion() &&
        ktClassOrObject.containingClassOrObject?.let {
          KtClassOrObjectWrapper(it, importHelper).getIsAnnotatedWith(annotation)
        } == true)
}

internal class PsiClassWrapper(private val psiClass: PsiClass, importHelper: JavaImportHelper) :
  DaggerIndexAnnotatedJavaWrapper(psiClass, importHelper), DaggerIndexClassWrapper {
  override fun getClassId(): ClassId = psiClass.classIdIfNonLocal!!

  override fun getIsSelfOrCompanionParentAnnotatedWith(annotation: DaggerAnnotation): Boolean =
    getIsAnnotatedWith(annotation)
}
