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
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/** A [DaggerIndexPsiWrapper] representing a class. */
interface DaggerIndexClassWrapper : DaggerIndexAnnotatedWrapper {
  /**
   * Returns the fully-qualified name of the class. Eg: "com.example.Foo"
   *
   * If the class has generic type arguments, they are omitted from the returned name.
   */
  fun getFqName(): String

  /**
   * Returns whether the specified annotation is present on:
   * 1. the wrapped class, or
   * 2. the parent of the wrapped object, when the wrapped object is a companion.
   */
  fun getIsSelfOrCompanionParentAnnotatedWith(fqName: String): Boolean
}

internal class KtClassOrObjectWrapper(
  private val ktClassOrObject: KtClassOrObject,
  private val importHelper: KotlinImportHelper
) : DaggerIndexAnnotatedKotlinWrapper(ktClassOrObject, importHelper), DaggerIndexClassWrapper {
  override fun getFqName(): String = ktClassOrObject.fqName!!.asString()

  override fun getIsSelfOrCompanionParentAnnotatedWith(fqName: String): Boolean =
    getIsAnnotatedWith(fqName) ||
      (ktClassOrObject is KtObjectDeclaration &&
        ktClassOrObject.isCompanion() &&
        ktClassOrObject.containingClassOrObject?.let {
          KtClassOrObjectWrapper(it, importHelper).getIsAnnotatedWith(fqName)
        } == true)
}

internal class PsiClassWrapper(private val psiClass: PsiClass, importHelper: JavaImportHelper) :
  DaggerIndexAnnotatedJavaWrapper(psiClass, importHelper), DaggerIndexClassWrapper {
  override fun getFqName(): String = psiClass.qualifiedName!!

  override fun getIsSelfOrCompanionParentAnnotatedWith(fqName: String): Boolean =
    getIsAnnotatedWith(fqName)
}
