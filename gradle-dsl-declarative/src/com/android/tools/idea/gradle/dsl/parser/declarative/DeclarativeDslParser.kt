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

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeReceiverPrefixedFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeRecursiveVisitor
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeSimpleFactory
import com.android.tools.idea.gradle.dcl.lang.psi.kind
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
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
          val block: GradlePropertiesDslElement? =
            if (GradleDslNamedDomainElement::class.java.isAssignableFrom(description.clazz) &&
                description.namedObjectAssociatedName == name) {
              // named object - it's always `function("name") {} ` syntax
              val element = getDomainNameDslElement(psi, description, context)
              (element as? GradleDslNamedDomainElement)?.methodName = description.namedObjectAssociatedName
              element
            }
            else {
              val identifier = psi.identifier ?: return
              getOrCreateElement(description, context, identifier, psi)
            }
          if (block != null) {
            psi.blockGroup.entries.forEach { entry -> entry.accept(getVisitor(block, GradleNameElement.empty())) }
          }
        }

        override fun visitAssignment(psi: DeclarativeAssignment) {
          psi.value?.accept(getVisitor(context, GradleNameElement.from(psi.assignableProperty, this@DeclarativeDslParser)))
        }

        override fun visitSimpleFactory(psi: DeclarativeSimpleFactory) {
          val methodCall = parseFactory(psi, context, nameElement) ?: return
          context.addParsedElement(methodCall)
        }

        override fun visitReceiverPrefixedFactory(factory: DeclarativeReceiverPrefixedFactory) {
          val expression = GradleDslInfixExpression(context, factory)
          //parse factory if expression consists only one element
          factory.getReceiver()?.let { receiver ->
            if (receiver.getReceiver() != null) return // handle only two call a().b() max
            val list = listOf(receiver, factory)
            if (list.any { it.argumentsList?.arguments?.size == 1 }) {
              list.forEach { factoryElement ->
                val name = factoryElement.identifier.name
                val arg = factoryElement.argumentsList?.arguments?.first()
                if (name != null && arg != null && arg is DeclarativeLiteral)
                  arg.value?.let {
                    GradleDslLiteral(context, factoryElement, GradleNameElement.from(factoryElement.identifier, this@DeclarativeDslParser), arg, LITERAL).also {
                      it.externalSyntax = ExternalNameInfo.ExternalNameSyntax.METHOD
                      expression.addParsedElement(it)
                    }
                  }
              }
            }
            context.addParsedElement(expression)
          }
        }

        override fun visitLiteral(psi: DeclarativeLiteral) {
          val newLiteral = GradleDslLiteral(context, psi.parent, nameElement, psi, LITERAL).also {
            it.externalSyntax = ASSIGNMENT
          }
          context.addParsedElement(newLiteral)
        }
      }
    psiFile.accept(getVisitor(dslFile, GradleNameElement.empty()))
  }

  private fun getDomainNameDslElement(
    psi: DeclarativeBlock,
    description: PropertiesElementDescription<*>,
    context: GradlePropertiesDslElement
  ): GradlePropertiesDslElement? {
    val arguments = psi.embeddedFactory?.argumentsList
    return arguments?.argumentList?.firstOrNull()?.let { literal ->
      val value = (literal.value as? DeclarativeLiteral)?.value
      if (value is String) {
        getOrCreateElement(description, context, literal.value, psi)
      }
      else null
    }
  }

  private fun getOrCreateElement(
    description: PropertiesElementDescription<*>,
    context: GradlePropertiesDslElement,
    identifier: PsiElement,
    psi: PsiElement
  ): GradlePropertiesDslElement {

    val existingElement = if (description.name == null) {
      //domain name object
      context.getPropertyElement(identifier.text, description.clazz)
    }
    else {
      context.getPropertyElement(description)
    }

    if (existingElement != null) {
      existingElement.setParent(context)
      existingElement.psiElement = psi
      return existingElement
    }
    else {
      // new element
      return description.constructor.construct(context, GradleNameElement.from(identifier, this@DeclarativeDslParser)).also {
        it.psiElement = psi
        context.addParsedElement(it)
      }

    }
  }

  private fun getFunctionParametersVisitor(list: GradleDslExpressionList,
                                           context: GradlePropertiesDslElement): DeclarativeRecursiveVisitor =
    object : DeclarativeRecursiveVisitor() {
      override fun visitLiteral(psi: DeclarativeLiteral) {
        val literal = GradleDslLiteral(list, psi, GradleNameElement.empty(), psi, LITERAL)
        list.addParsedExpression(literal)
      }

      override fun visitFactory(psi: DeclarativeFactory) {
        val methodCall = parseFactory(psi, context, GradleNameElement.empty()) ?: return
        list.addParsedExpression(methodCall)
      }
    }

  private fun parseFactory(psi: DeclarativeFactory,
                           context: GradlePropertiesDslElement,
                           currentNameElement: GradleNameElement): GradleDslMethodCall? {
    val name = psi.identifier.name ?: return null

    val nameElement = if (currentNameElement.isEmpty)
      GradleNameElement.from(psi.identifier, this@DeclarativeDslParser)
    else
      currentNameElement
    val methodCall = GradleDslMethodCall(context, psi, nameElement, name, false)

    val argumentList = psi.argumentsList ?: return null
    val arguments = GradleDslExpressionList(methodCall, argumentList, false, GradleNameElement.empty())
    argumentList.arguments.forEach {
      it.accept(getFunctionParametersVisitor(arguments, context))
    }

    methodCall.setParsedArgumentList(arguments)
    return methodCall
  }
}