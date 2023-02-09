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

import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtParameter

/** A [DaggerIndexPsiWrapper] representing a parameter. */
interface DaggerIndexParameterWrapper : DaggerIndexPsiWrapper {
  /** Simple name of the parameter. Eg: "someParameterName" */
  fun getSimpleName(): String
  fun getType(): DaggerIndexTypeWrapper
}

internal class KtParameterWrapper(
  private val ktParameter: KtParameter,
  private val importHelper: KotlinImportHelper
) : DaggerIndexParameterWrapper {
  override fun getSimpleName(): String = ktParameter.name!!

  override fun getType(): DaggerIndexTypeWrapper =
    KtTypeReferenceWrapper(ktParameter.typeReference!!, importHelper)
}

internal class PsiParameterWrapper(private val psiParameter: PsiParameter) :
  DaggerIndexParameterWrapper {
  override fun getSimpleName(): String = psiParameter.name

  override fun getType(): DaggerIndexTypeWrapper = PsiTypeElementWrapper(psiParameter.typeElement!!)
}
