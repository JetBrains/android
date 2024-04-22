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
package com.android.tools.idea.gradle.dsl.parser.declarative

import com.android.tools.idea.gradle.declarative.psi.DeclarativeFile
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.declarative.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFactory
import com.android.tools.idea.gradle.declarative.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.declarative.psi.DeclarativePsiFactory
import com.android.tools.idea.gradle.declarative.psi.DeclarativeRecursiveVisitor
import com.android.tools.idea.gradle.declarative.psi.kind
import com.intellij.psi.PsiElement

class DeclarativeDslParser(
  private val psiFile: DeclarativeFile,
  private val context: BuildModelContext,
  private val dslFile: GradleDslFile
) : GradleDslParser, DeclarativeDslNameConverter {
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

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? {
    val factory = DeclarativePsiFactory(context.dslFile.project)
    return when (literal) {
      is String -> factory.createStringLiteral("$literal")
      is Int -> factory.createIntLiteral(literal)
      is Boolean -> factory.createBooleanLiteral(literal)
      else -> null
    }
  }
  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) = Unit

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? =
    (literal as? DeclarativeLiteral)?.let { it.kind?.value } ?: literal.text

  override fun getContext(): BuildModelContext = context
  override fun parse() {
    fun getVisitor(context: GradlePropertiesDslElement, nameElement: GradleNameElement): DeclarativeRecursiveVisitor =
      object : DeclarativeRecursiveVisitor() {
        override fun visitBlock(psi: DeclarativeBlock) {
          val name = psi.identifier?.name ?: return
          val description = context.getChildPropertiesElementDescription(this@DeclarativeDslParser, name) ?: return
          val block: GradlePropertiesDslElement = context.ensurePropertyElement(description)
          block.psiElement = psi
          psi.blockGroup.entries.forEach { entry -> entry.accept(getVisitor(block, GradleNameElement.empty())) }
        }

        override fun visitAssignment(psi: DeclarativeAssignment) {
          psi.value?.accept(getVisitor(context, GradleNameElement.from(psi.identifier, this@DeclarativeDslParser)))
        }

        override fun visitFactory(psi: DeclarativeFactory) {
          val methodCall = parseFactory(psi, context) ?: return
          context.addParsedElement(methodCall)
        }

        override fun visitLiteral(psi: DeclarativeLiteral) {
          val literal = GradleDslLiteral(context, psi.parent, nameElement, psi, LITERAL)
          literal.externalSyntax = ASSIGNMENT
          context.addParsedElement(literal)
        }
      }
    psiFile.accept(getVisitor(dslFile, GradleNameElement.empty()))
  }

  private fun getFunctionParametersVisitor(parentMethodCall: GradleDslMethodCall): DeclarativeRecursiveVisitor =
    object : DeclarativeRecursiveVisitor() {
      override fun visitLiteral(psi: DeclarativeLiteral) {
        parentMethodCall.addNewArgument(GradleDslLiteral(parentMethodCall, psi, GradleNameElement.empty(), psi, LITERAL))
      }
      override fun visitFactory(psi: DeclarativeFactory) {
        val methodCall = parseFactory(psi, parentMethodCall) ?: return
        parentMethodCall.addNewArgument(methodCall)
      }
    }

  private fun parseFactory(psi: DeclarativeFactory, parent: GradleDslElement): GradleDslMethodCall? {
    val name = psi.identifier.name ?: return null
    val nameElement = GradleNameElement.from(psi.identifier, this@DeclarativeDslParser)
    val methodCall = GradleDslMethodCall(parent, nameElement, name)
    methodCall.psiElement = psi
    methodCall.argumentsElement.psiElement = psi.argumentsList

    psi.argumentsList?.arguments?.forEach { it.accept(getFunctionParametersVisitor(methodCall)) }
    return methodCall
  }
}