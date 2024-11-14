/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.intellij.psi.PsiElement

/**
 * This class was created to make a base for empty models that
 * are not available in Declarative but exists in Groovy/Kts
 * Such models stub model interfaces by returning empty list of child elements
 * and throwing exception for any mutations.
 *
 * This way we preserve existing API for now and make it usable for
 * checks that random code can do without knowing type of build.
 */
open class EmptyGradleBlockModel: GradleBlockModel {
  override fun getPsiElement(): PsiElement? = null

  override fun delete() =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun getRawPropertyHolder(): GradleDslElement =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun getHolder(): GradleDslElement =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun getRawElement(): GradleDslElement? = null

  override fun getFullyQualifiedName(): String =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun getInScopeProperties(): Map<String, GradlePropertyModel> = mapOf()

  override fun getDeclaredProperties(): List<GradlePropertyModel> = listOf()

  override fun <T : GradleDslModel> getModel(klass: Class<T>): T =
    throw UnsupportedOperationException("Call is not supported for Declarative")
}