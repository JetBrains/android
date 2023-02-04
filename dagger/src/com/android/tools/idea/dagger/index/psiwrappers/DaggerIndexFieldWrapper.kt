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

import com.intellij.psi.PsiField
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/** A [DaggerIndexPsiWrapper] representing a field (or property in Kotlin). */
interface DaggerIndexFieldWrapper : DaggerIndexPsiWrapper {
  /** Simple name of the field. Eg: "someFieldName" */
  fun getSimpleName(): String
  fun getType(): DaggerIndexTypeWrapper?
  fun getContainingClass(): DaggerIndexClassWrapper
  /** Gets whether the class might be annotated with the given annotation. */
  fun getIsAnnotatedWith(fqName: String): Boolean
}

internal class KtPropertyWrapper(private val ktProperty: KtProperty,
                                 private val importHelper: KotlinImportHelper) : DaggerIndexFieldWrapper {
  override fun getSimpleName() = ktProperty.name!!

  override fun getType(): DaggerIndexTypeWrapper? = ktProperty.typeReference?.let { KtTypeReferenceWrapper(it, importHelper) }

  override fun getContainingClass(): DaggerIndexClassWrapper = KtClassOrObjectWrapper(ktProperty.containingClassOrObject!!, importHelper)

  override fun getIsAnnotatedWith(fqName: String) = ktProperty.getIsAnnotatedWith(fqName, importHelper)
}

internal class PsiFieldWrapper(private val psiField: PsiField,
                               private val importHelper: JavaImportHelper) : DaggerIndexFieldWrapper {
  override fun getSimpleName() = psiField.name

  override fun getType(): DaggerIndexTypeWrapper? = psiField.typeElement?.let { PsiTypeElementWrapper(it) }

  override fun getContainingClass(): DaggerIndexClassWrapper = PsiClassWrapper(psiField.containingClass!!, importHelper)

  override fun getIsAnnotatedWith(fqName: String) = psiField.getIsAnnotatedWith(fqName, importHelper)
}
