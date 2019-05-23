/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslUnknownElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.getBlockElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.INVALID_EXPRESSION
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression
import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

/**
 * Parser for .gradle.kt files. This method produces a [GradleDslElement] tree.
 */
class KotlinDslParser(val psiFile : KtFile, val dslFile : GradleDslFile): KtVisitor<Unit, GradlePropertiesDslElement>(), GradleDslParser {

  //
  // Methods for GradleDslParser
  //
  override fun parse() {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    psiFile.script!!.blockExpression.statements.map {
      if (it is KtScriptInitializer) it.body else it
    }.requireNoNulls().forEach {
      it.accept(this, dslFile)
    }
  }

  override fun convertToPsiElement(literal: Any): PsiElement? {
    return try {
      createLiteral(dslFile, literal)
    }
    catch (e : IncorrectOperationException) {
      dslFile.context.getNotificationForType(dslFile, INVALID_EXPRESSION).addError(e)
      null
    }
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) {
    val isReference = newValue is KtReferenceExpression || newValue is KtDotQualifiedExpression || newValue is KtClassLiteralExpression
    context.isReference = isReference
  }

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? {
    when (literal) {
      // Ex: KotlinCompilerVersion
      is KtNameReferenceExpression -> {
        if (resolve) {
          val gradleDslElement = context.resolveReference(literal.text, true)
          // Only get the value if the element is a GradleDslSimpleExpression.
          if (gradleDslElement is GradleDslSimpleExpression) {
            return gradleDslElement.value
          }
        }
        return unquoteString(literal.text)
      }
      // For String and constant literals. Ex : Integers, single-quoted Strings.
      is KtStringTemplateExpression, is KtStringTemplateEntry, is KtConstantExpression -> {
        if (!resolve || context.hasCycle()) {
          return unquoteString(literal.text)
        }
        val injections = context.resolvedVariables
        return GradleReferenceInjection.injectAll(literal, injections)
      }
      else -> return ReferenceTo(literal.text)
    }
  }

  override fun convertToExcludesBlock(excludes: List<ArtifactDependencySpec>): PsiElement? {
    val factory = KtPsiFactory(dslFile.project)
    val block = factory.createBlock("")
    excludes.forEach {
      val group = if (FakeArtifactElement.shouldInterpolate(it.group)) iStr(it.group ?: "''") else "'$it.group'"
      val name = if (FakeArtifactElement.shouldInterpolate(it.name)) iStr(it.name) else "'$it.name'"
      val text = "exclude(group = $group, module = $name)"
      val expression = factory.createExpressionIfPossible(text)
      if (expression != null) {
        block.addAfter(expression, block.lastChild)
        block.addBefore(factory.createNewLine(), block.lastChild)
      }
    }
    return block
  }

  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean {
    return when (elementToCheck) {
      is GradleDslSettableExpression -> elementToCheck.currentElement is KtStringTemplateExpression
      is GradleDslSimpleExpression -> elementToCheck.expression is KtStringTemplateExpression
      else -> elementToCheck.psiElement is KtStringTemplateExpression
    }
  }

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): List<GradleReferenceInjection> {
    return findInjections(context, psiElement, false)
  }

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): List<GradleReferenceInjection> {
    return findInjections(context, psiElement, true)
  }

  override fun getBlockElement(nameParts: List<String>,
                               parentElement: GradlePropertiesDslElement,
                               nameElement: GradleNameElement?): GradlePropertiesDslElement? {
    return dslFile.getBlockElement(nameParts, parentElement, nameElement)
  }

  //
  // Methods to perform the parsing on the KtFile
  //
  override fun visitCallExpression(expression: KtCallExpression, parent: GradlePropertiesDslElement) {
    // If the call expression has no name, we don't know how to handle it.
    val referenceName = expression.name() ?: return
    // If expression is a pure block element and not an expression.
    if (expression.isBlockElement()) {
      val blockElement = dslFile.getBlockElement(listOf(referenceName), parent) ?: return
      // Visit the children of this element, with the block set as parent.
      expression.lambdaArguments[0]!!.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()?.forEach {
        it.accept(this, blockElement)
      }
    }
    else {
      // Get args and block.
      val argumentsList = expression.valueArgumentList
      val argumentsBlock = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression
      val name = GradleNameElement.from(expression.referenceExpression()!!)
      if (argumentsList != null) {
        val callExpression = getCallExpression(parent, expression, name, argumentsList, referenceName, true)
        if (argumentsBlock != null) {
          callExpression.setParsedClosureElement(getClosureBlock(callExpression, argumentsBlock, name))
        }

        callExpression.elementType = REGULAR
        parent.addParsedElement(callExpression)
      }

    }
  }

  private fun getCallExpression(
    parentElement : GradleDslElement,
    psiElement : PsiElement,
    name : GradleNameElement,
    argumentsList : KtValueArgumentList,
    methodName : String,
    isFirstCall : Boolean
  ) : GradleDslExpression {
    if (methodName == "mapOf") {
      return getExpressionMap(parentElement, argumentsList, name, argumentsList.arguments)
    }
    else if (methodName == "listOf") {
      return getExpressionList(parentElement, argumentsList, name, argumentsList.arguments, false)
    }

    // If the CallExpression has one argument only that is a callExpression, we skip the current CallExpression.
    val arguments = argumentsList.arguments
    if (arguments.size != 1) {
      return getMethodCall(parentElement, psiElement, name, methodName)
    }
    else {
      val argumentExpression = arguments[0].getArgumentExpression()
      if (argumentExpression is KtCallExpression) {
        val argumentsName = (arguments[0].getArgumentExpression() as KtCallExpression).name()!!
        if (isFirstCall) {
          return getCallExpression(
            parentElement, argumentExpression, name, argumentExpression.valueArgumentList!!, argumentsName, false)
        }
        return getMethodCall(parentElement, psiElement, name, methodName)
      }
      if (isFirstCall) {
        return getExpressionElement(parentElement, psiElement, name, arguments[0].getArgumentExpression() as KtElement)
      }
      return getMethodCall(parentElement, psiElement, name, methodName)
    }
  }

  private fun getMethodCall(parent : GradleDslElement,
                            psiElement: PsiElement,
                            name : GradleNameElement,
                            methodName: String) : GradleDslMethodCall {

    val methodCall = GradleDslMethodCall(parent, psiElement, name, methodName, false)
    val arguments = (psiElement as KtCallExpression).valueArgumentList!!
    val argumentList = getExpressionList(methodCall, arguments, name, arguments.arguments, false)
    methodCall.setParsedArgumentList(argumentList)
    return methodCall
  }

  private fun getExpressionMap(parentElement : GradleDslElement,
                               mapPsiElement: PsiElement,
                               propertyName : GradleNameElement,
                               arguments : List<KtElement>) : GradleDslExpressionMap {
    val expressionMap = GradleDslExpressionMap(parentElement, mapPsiElement, propertyName, false)
    arguments.map {
      arg -> (arg as KtValueArgument).getArgumentExpression()
    }.filter {
      expression -> expression is KtBinaryExpression && expression.operationReference.getReferencedName() == "to" &&
                    expression.left != null && expression.right != null
    }.mapNotNull {
      expression -> createExpressionElement(
      expressionMap, mapPsiElement, GradleNameElement.from((expression as KtBinaryExpression).left!!), expression.right!!)
    }.forEach(expressionMap::addParsedElement)

    return expressionMap
  }

  private fun getExpressionList(parentElement : GradleDslElement,
                                listPsiElement : PsiElement,
                                propertyName : GradleNameElement,
                                valueArguments : List<KtElement>,
                                isLiteral : Boolean) : GradleDslExpressionList {
    val expressionList = GradleDslExpressionList(parentElement, listPsiElement, propertyName, isLiteral)
    valueArguments.map {
      expression -> (expression as KtValueArgument).getArgumentExpression()
    }.mapNotNull {
      argumentExpression -> createExpressionElement(
      expressionList, argumentExpression!!, GradleNameElement.empty(), argumentExpression)
    }.forEach {
      if (it is GradleDslClosure) {
        parentElement.setParsedClosureElement(it)
      }
      else {
        expressionList.addParsedExpression(it)
      }
    }
    return expressionList
  }

  private fun createExpressionElement(parent : GradleDslElement,
                                      psiElement : PsiElement,
                                      name: GradleNameElement,
                                      expression : KtExpression) : GradleDslExpression? {
    // Parse all the ValueArgument types.
    when (expression) {
      is KtValueArgumentList -> return getExpressionList(parent, expression, name, expression.arguments, true)
      is KtCallExpression -> {
        // Ex: implementation(kotlin("stdlib-jdk7")).
        val expressionName = expression.name()!!
        val arguments = expression.valueArgumentList ?: return null
        return getCallExpression(parent, expression, name, arguments, expressionName, false)
      }
      is KtParenthesizedExpression -> return createExpressionElement(parent, psiElement, name, expression.expression ?: expression)
      else -> return getExpressionElement(parent, psiElement, name, expression)
    }
  }

  private fun getExpressionElement(parentElement : GradleDslElement,
                                   psiElement : PsiElement,
                                   propertyName: GradleNameElement,
                                   propertyExpression : KtElement) : GradleDslExpression {
    return when (propertyExpression) {
      // Ex: versionName = 1.0. isQualified = false.
      is KtStringTemplateExpression, is KtConstantExpression -> GradleDslLiteral(
        parentElement, psiElement, propertyName, propertyExpression, false)
      // Ex: compileSdkVersion(SDK_VERSION).
      is KtNameReferenceExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: KotlinCompilerVersion.VERSION.
      is KtDotQualifiedExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: Delete::class.
      is KtClassLiteralExpression -> GradleDslLiteral(
        parentElement, psiElement, propertyName, propertyExpression.receiverExpression as  PsiElement, true)
      else -> {
        // The expression is not supported.
        parentElement.notification(NotificationTypeReference.INCOMPLETE_PARSING).addUnknownElement(propertyExpression)
        return GradleDslUnknownElement(parentElement, propertyExpression, propertyName)
      }
    }
  }

  private fun getClosureBlock(
    parentElement: GradleDslElement, closableBlock : KtBlockExpression, propertyName: GradleNameElement) : GradleDslClosure {
    val closureElement = GradleDslClosure(parentElement, closableBlock, propertyName)
    closableBlock.accept(this, closureElement)
    return closureElement
  }

}