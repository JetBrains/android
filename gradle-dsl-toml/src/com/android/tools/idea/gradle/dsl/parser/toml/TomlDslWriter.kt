/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.toml

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.psi.PsiElement

class TomlDslWriter(private val context: BuildModelContext): GradleDslWriter, TomlDslNameConverter {
  override fun getContext(): BuildModelContext = context

  override fun moveDslElement(element: GradleDslElement): PsiElement? = null
  override fun createDslElement(element: GradleDslElement): PsiElement? = null
  override fun deleteDslElement(element: GradleDslElement): Unit = Unit
  override fun createDslLiteral(literal: GradleDslLiteral): PsiElement? = null
  override fun deleteDslLiteral(literal: GradleDslLiteral): Unit = Unit
  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement? = null
  override fun applyDslMethodCall(methodCall: GradleDslMethodCall): Unit = Unit
  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement? = null
  override fun applyDslExpressionList(expressionList: GradleDslExpressionList): Unit = Unit
  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement? = null
  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap): Unit = Unit
  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement): Unit = Unit

  override fun applyDslLiteral(literal: GradleDslLiteral) {
    val psiElement = literal.psiElement ?: return
    val newElement = literal.unsavedValue ?: return

    val element = psiElement.replace(newElement)
    literal.setExpression(element)
    literal.reset()
    literal.commit()
  }
}