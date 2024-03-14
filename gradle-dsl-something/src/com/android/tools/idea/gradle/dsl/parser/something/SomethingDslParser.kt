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
package com.android.tools.idea.gradle.dsl.parser.something

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.something.psi.SomethingFile
import com.intellij.psi.PsiElement

class SomethingDslParser(
  private val psiFile: SomethingFile,
  private val context: BuildModelContext
) : GradleDslParser, SomethingDslNameConverter {
  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean = false

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    mutableListOf()

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    mutableListOf()

  override fun getPropertiesElement(
    nameParts: MutableList<String>,
    parentElement: GradlePropertiesDslElement,
    nameElement: GradleNameElement?
  ): GradlePropertiesDslElement? {
    return null
  }

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? = null

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) = Unit

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? = null

  override fun getContext(): BuildModelContext = context
  override fun parse() {
    // TODO implement
  }

}