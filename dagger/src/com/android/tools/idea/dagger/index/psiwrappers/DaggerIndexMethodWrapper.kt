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

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/** A [DaggerIndexPsiWrapper] representing a method (or function in Kotlin). */
interface DaggerIndexMethodWrapper : DaggerIndexPsiWrapper {
  /** Simple name of the method. Eg: "someMethodName" */
  fun getSimpleName(): String
  fun getReturnType(): DaggerIndexTypeWrapper?
  fun getParameters(): List<DaggerIndexParameterWrapper>
  fun getIsConstructor(): Boolean
  fun getContainingClass(): DaggerIndexClassWrapper?
  /** Gets whether the class might be annotated with the given annotation. */
  fun getIsAnnotatedWith(fqName: String): Boolean
}

internal class KtFunctionWrapper(private val ktFunction: KtFunction,
                                 private val importHelper: KotlinImportHelper) : DaggerIndexMethodWrapper {
  override fun getSimpleName() = ktFunction.name!!

  override fun getReturnType(): DaggerIndexTypeWrapper? = ktFunction.getReturnTypeReference()?.let {
    KtTypeReferenceWrapper(it, importHelper)
  }

  override fun getParameters(): List<DaggerIndexParameterWrapper> = ktFunction.valueParameters.map { KtParameterWrapper(it, importHelper) }

  override fun getIsConstructor() = ktFunction is KtConstructor<*>

  override fun getContainingClass(): DaggerIndexClassWrapper? = ktFunction.containingClassOrObject?.let {
    KtClassOrObjectWrapper(it, importHelper)
  }

  override fun getIsAnnotatedWith(fqName: String) = ktFunction.getIsAnnotatedWith(fqName, importHelper)
}

internal class PsiMethodWrapper(private val psiMethod: PsiMethod,
                                private val importHelper: JavaImportHelper) : DaggerIndexMethodWrapper {
  override fun getSimpleName() = psiMethod.name

  override fun getReturnType(): DaggerIndexTypeWrapper? = psiMethod.returnTypeElement?.let { PsiTypeElementWrapper(it) }

  override fun getParameters(): List<DaggerIndexParameterWrapper> = psiMethod.parameterList.parameters.map { PsiParameterWrapper(it) }

  override fun getIsConstructor() = psiMethod.isConstructor

  override fun getContainingClass(): DaggerIndexClassWrapper? = psiMethod.containingClass?.let { PsiClassWrapper(it, importHelper) }

  override fun getIsAnnotatedWith(fqName: String) = psiMethod.getIsAnnotatedWith(fqName, importHelper)
}
