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

/** A [DaggerIndexPsiWrapper] representing a class. */
interface DaggerIndexClassWrapper : DaggerIndexPsiWrapper {
  /** Fully-qualified name of the class. Eg: "com.example.Foo" */
  fun getFqName(): String
  /** Gets whether the class might be annotated with the given annotation. */
  fun getIsAnnotatedWith(fqName: String): Boolean
}

internal class KtClassOrObjectWrapper(
  private val ktClassOrObject: KtClassOrObject,
  private val importHelper: KotlinImportHelper
) : DaggerIndexClassWrapper {
  override fun getFqName(): String = ktClassOrObject.fqName!!.asString()

  override fun getIsAnnotatedWith(fqName: String) =
    ktClassOrObject.getIsAnnotatedWith(fqName, importHelper)
}

internal class PsiClassWrapper(
  private val psiClass: PsiClass,
  private val importHelper: JavaImportHelper
) : DaggerIndexClassWrapper {
  override fun getFqName(): String = psiClass.qualifiedName!!

  override fun getIsAnnotatedWith(fqName: String) =
    psiClass.getIsAnnotatedWith(fqName, importHelper)
}
