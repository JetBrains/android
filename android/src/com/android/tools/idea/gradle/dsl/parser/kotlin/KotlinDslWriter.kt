/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.android.tools.idea.gradle.dsl.parser.needToCreateParent
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.isWhiteSpaceOrNls

class KotlinDslWriter : GradleDslWriter {
  override fun moveDslElement(element: GradleDslElement): PsiElement? {
    val anchorAfter = element.anchor ?: return null
    val parentPsiElement = getParentPsi(element) ?: return null

    val anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)

    // Create a dummy element to move the element to.
    val psiFactory = KtPsiFactory(parentPsiElement.project)
    val lineTerminator = psiFactory.createNewLine(1)
    // If the element has no anchor, add it to the beginning of the block.
    val toReplace = if (parentPsiElement is KtBlockExpression && anchorAfter == null) {
      parentPsiElement.addBefore(lineTerminator, anchor)
    }
    else {
      parentPsiElement.addAfter(lineTerminator, anchor)
    }

    // Find the element we need to replace.
    var e = element.psiElement ?: return null
    while (!(e.parent is KtFile || (e.parent is KtCallExpression && (e.parent as KtCallExpression).isBlockElement()))) {
      if (e.parent == null) {
        e = element.psiElement as PsiElement
        break
      }
      e = e.parent
    }

    // Copy the old PsiElement tree.
    val treeCopy = e.copy()

    // Replace what needs to be replaced.
    val newTree = toReplace.replace(treeCopy)

    // Delete original tree.
    e.delete()

    // Set the new PsiElement.
    element.psiElement = newTree

    return element.psiElement
  }

  override fun createDslElement(element: GradleDslElement): PsiElement? {
    var anchorAfter = element.anchor
    val psiElement = element.psiElement
    if (psiElement != null) return psiElement
    var isRealList = false // This is to keep track if we're creating a real list (listOf()).

    if (element.isNewEmptyBlockElement) return null  // Avoid creation of an empty block.

    if (needToCreateParent(element)) {
      anchorAfter = null
    }

    val parentPsiElement = getParentPsi(element) ?: return null

    val project = parentPsiElement.project
    val psiFactory = KtPsiFactory(project)

    // The text should be quoted if not followed by anything else,  otherwise it will create a reference expression.
    var statementText = maybeTrimForParent(element.nameElement, element.parent)
    if (element.isBlockElement) {
      statementText += " {\n}"  // Can't create expression with another new line after.
    }
    else if (element.shouldUseAssignment()) {
      if (element.elementType == PropertyType.REGULAR) {
        statementText += " = \"abc\""
      }
      // TODO: add support for variables when available.
    }
    else if (element is GradleDslExpressionList) {
      val parentDsl = element.parent
      if (parentDsl is GradleDslMethodCall && element.elementType == PropertyType.DERIVED) {
        // This is when we have not a proper list element (listOf()) but rather a methodCall arguments. In such case we need to skip
        // creating the list and use the KtValueArgumentList of the parent.
        return (parentDsl.psiElement as? KtCallExpression)?.valueArgumentList  // TODO add more tests to verify the code consistency.
      }
      else {
        // This is the case where we are handling a list element
        statementText += "listOf()"
        isRealList = true
      }
    }
    else if (element is GradleDslExpressionMap) {
      statementText += "mapOf()"
    }
    else {
      statementText += "()"
    }

    val statement = psiFactory.createExpression(statementText) // Doesn't work for variables (see createExpression implementation in factory.
    when (statement) {
      is KtBinaryExpression -> {
        statement.right?.delete()
      }
      is KtCallExpression -> {
        if (element.isBlockElement) {
          // Add new line to separate blocks statements.
          statement.addAfter(psiFactory.createNewLine(), statement.lastChild)
        }
      }
      // TODO add support for variables when available.
    }

    val lineTerminator = psiFactory.createNewLine()
    val addedElement : PsiElement
    val anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)

    when (parentPsiElement) {
      is KtFile -> {
        // If the anchor is null, we would add the new element to the beginning of the file which is correct, unless the file starts
        // with a comment : in such case we need to add the element right after the comment and not before.
        val fileBlock = parentPsiElement.script?.blockExpression
        val firstRealChild = fileBlock?.firstChild
        if (fileBlock != null && anchor == null && firstRealChild?.node?.elementType == BLOCK_COMMENT) {
          addedElement = fileBlock.addAfter(statement, firstRealChild)
          // If we're adding a {} block element,  we need to add an empty line before it.
          if (element.isBlockElement && !isWhiteSpaceOrNls(addedElement.prevSibling)) {
            fileBlock.addBefore(lineTerminator, addedElement)
          }
          fileBlock.addBefore(lineTerminator, addedElement)
        }
        else {
          addedElement = parentPsiElement.addAfter(statement, anchor)
          if (element.isBlockElement && !isWhiteSpaceOrNls(addedElement.prevSibling)) {
            parentPsiElement.addBefore(lineTerminator, addedElement)
          }
          parentPsiElement.addBefore(lineTerminator, addedElement)

        }
      }
      is KtBlockExpression -> {
        addedElement = parentPsiElement.addBefore(statement, anchor)
        if (anchorAfter != null) {
          parentPsiElement.addBefore(lineTerminator, addedElement)
        }
        else {
          parentPsiElement.addAfter(lineTerminator, addedElement)
        }
      }
      is KtValueArgumentList -> {
        val argumentValue = psiFactory.createArgument(statement)
        addedElement = parentPsiElement.addArgumentAfter(argumentValue, anchor as? KtValueArgument)
      }
      else -> {
        addedElement = parentPsiElement.addAfter(statement, anchor)
        parentPsiElement.addBefore(lineTerminator, addedElement)
      }
    }

    if (element.isBlockElement) {
      val blockExpression = getKtBlockExpression(addedElement)
      if (blockExpression != null) {
        element.psiElement = blockExpression
      }
    }
    else if (addedElement is KtBinaryExpression) {
      element.psiElement = addedElement
    }
    else if (addedElement is KtCallExpression) {
      if (element is GradleDslExpressionList && !isRealList) {
        element.psiElement = addedElement.valueArgumentList
      }
      else {
        element.psiElement = addedElement
      }
    }
    else if (addedElement is KtValueArgument) {
      element.psiElement = addedElement.getArgumentExpression()
    }

    return element.psiElement
  }

  override fun deleteDslElement(element: GradleDslElement) {
    deletePsiElement(element, element.psiElement)
  }

  override fun createDslLiteral(literal: GradleDslLiteral): PsiElement? {
    return when (literal.parent) {
      is GradleDslExpressionList -> createListElement(literal)
      is GradleDslExpressionMap -> createMapElement(literal)
      else -> createDslElement(literal)
    }
  }

  override fun applyDslLiteral(literal: GradleDslLiteral) {
    val psiElement = literal.psiElement ?: return
    maybeUpdateName(literal)

    val newLiteral = literal.unsavedValue ?: return
    val psiExpression = literal.expression
    if (psiExpression != null) {
      val replace = psiElement.replace(newLiteral)
      // Make sure we replaced with the right psi element for the GradleDslLiteral.
      if (replace is KtStringTemplateExpression || replace is KtConstantExpression
          || replace is KtNameReferenceExpression || replace is KtDotQualifiedExpression) {
        literal.setExpression(replace)
      }
    }
    else {
      // This element has just been created and will either be "propertyName =" or "propertyName()".
      val added = psiElement.addAfter(newLiteral, psiElement.lastChild)
      literal.setExpression(added)
    }

    literal.reset()
    literal.commit()
  }

  override fun deleteDslLiteral(literal: GradleDslLiteral) {
    deletePsiElement(literal, literal.expression)
  }

  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement? {
    val psiElement = methodCall.psiElement
    if (psiElement != null && psiElement.isValid) {
      return psiElement
    }

    val methodParent = methodCall.parent ?: return null

    var anchorAfter = methodCall.anchor

    //If the parent doesn't have a psiElement, the anchor will be used to create it. In such case, we need to empty the anchor.
    if (needToCreateParent(methodCall)) {
      anchorAfter = null
    }

    val parentPsiElement = methodParent.create() ?: return null
    val anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)
    val psiFactory = KtPsiFactory(parentPsiElement.project)

    val statementText =
      if (methodCall.fullName.isNotEmpty() && methodCall.fullName != methodCall.methodName) {
        // Ex: implementation(fileTree()).
        maybeTrimForParent(methodCall.getNameElement(), methodCall.getParent()) + "(" + maybeTrimForParent(
          GradleNameElement.fake(methodCall.getMethodName()), methodCall.getParent()) + "())"
      }
    else {
        // Ex : proguardFile() where the name is the same as the methodName, so we need to make sure we create one method only.
        maybeTrimForParent(
          GradleNameElement.fake(methodCall.getMethodName()), methodCall.getParent()) + "()"
      }
    val expression =
      psiFactory.createExpression(statementText) as? KtCallExpression ?: throw IllegalArgumentException(
        "Can't create expression from \"$statementText\"")  // Maybe we can change the behaviour to just return null in such case.

    val addedElement :PsiElement
    if (parentPsiElement is KtBlockExpression && anchorAfter == null) {
      addedElement = parentPsiElement.addBefore(expression, anchor)
      // We need to add empty lines if we're adding expressions to a block because IDEA doesn't handle formatting
      // in kotlin the same way as GROOVY.
      if (anchor != null && !hasNewLineBetween(addedElement, anchor)) {
        val lineTerminator = psiFactory.createNewLine()
        parentPsiElement.addAfter(lineTerminator, addedElement)
      }
    }
    else if (parentPsiElement is KtValueArgumentList) {
      val valueArgument = psiFactory.createArgument(expression)
      val addedArgument = parentPsiElement.addArgumentAfter(valueArgument, anchor as? KtValueArgument)
      addedElement = addedArgument.getArgumentExpression() ?: throw Exception("ValueArgument was not created properly.")
    }
    else {
      addedElement = parentPsiElement.addAfter(expression, anchor)
      // We need to add empty lines if we're adding expressions to a file because IDEA doesn't handle formatting
      // in kotlin the same way as GROOVY.
      if (anchor != null && !hasNewLineBetween(anchor, addedElement)) {
        val lineTerminator = psiFactory.createNewLine()
        parentPsiElement.addBefore(lineTerminator, addedElement)
      }
    }

    // Adjust the PsiElement for methodCall.
    val argumentList = (addedElement as KtCallExpression).valueArgumentList?.arguments ?: return null
    if (argumentList.size == 1 && argumentList[0].getArgumentExpression() is KtCallExpression) {
      methodCall.psiElement = argumentList[0].getArgumentExpression()
      methodCall.argumentsElement.psiElement = (argumentList[0].getArgumentExpression() as KtCallExpression).valueArgumentList
      return methodCall.psiElement
    }
    else if (argumentList.isEmpty()) {
      methodCall.psiElement = addedElement
      methodCall.argumentsElement.psiElement = addedElement.valueArgumentList

      val unsavedClosure = methodCall.unsavedClosure
      if (unsavedClosure != null) {
        createAndAddClosure(unsavedClosure, methodCall)
      }
      return methodCall.psiElement
    }

    return null
  }

  override fun applyDslMethodCall(methodCall: GradleDslMethodCall) {
    maybeUpdateName(methodCall)
    methodCall.argumentsElement.applyChanges()
    val unsavedClosure = methodCall.unsavedClosure
    if (unsavedClosure != null) {
      createAndAddClosure(unsavedClosure, methodCall)
    }
  }

  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement? {
    // GradleDslExpressionList represents list objects as well as method arguments.
    var psiElement = expressionList.psiElement
    if (psiElement != null) {
      return psiElement
    }
    else {
      if (expressionList.parent is GradleDslExpressionMap) {
        // The list is an entry in a map, and we need to create a binaryExpression for it.
        return createBinaryExpression(expressionList)
      }
      psiElement = createDslElement(expressionList) ?: return null
    }

    if (psiElement is KtCallExpression) return psiElement

    if (psiElement is KtBinaryExpression) {
      val emptyList = KtPsiFactory(psiElement.project).createExpression("listOf()")
      val added = psiElement.addAfter(emptyList, psiElement.lastChild)
      expressionList.psiElement = added
      return expressionList.psiElement
    }
    else if (psiElement is KtValueArgumentList) { // When the dsl list resolves to a callExpression arguments.
      if (expressionList.expressions.size == 1 && psiElement.arguments.size == 1 && !expressionList.isAppendToArgumentListWithOneElement) {
        // Sometimes we don't want to allow adding to a list that has one argument (ex : proguardFile("xyz")).
        expressionList.psiElement = null
        psiElement = createDslElement(expressionList)
      }
      return psiElement
    }

    // TODO : add support for variables when available.
    return null
  }

  override fun applyDslExpressionList(expressionList: GradleDslExpressionList) {
    maybeUpdateName(expressionList)
  }

  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement? {
    if (expressionMap.psiElement != null) return expressionMap.psiElement

    expressionMap.psiElement = createDslElement(expressionMap) ?: return null
    return expressionMap.psiElement
  }

  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap) {
    maybeUpdateName(expressionMap)
  }

  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement) {
    maybeUpdateName(element)
  }
}