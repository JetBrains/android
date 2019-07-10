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
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.android.tools.idea.gradle.dsl.parser.needToCreateParent
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.isWhiteSpaceOrNls

class KotlinDslWriter : GradleDslWriter {
  override fun moveDslElement(element: GradleDslElement): PsiElement? {
    val anchorAfter = element.anchor ?: return null
    val parentPsiElement = getParentPsi(element) ?: return null

    val anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)

    // Create a dummy element to move the element to.
    val psiFactory = KtPsiFactory(parentPsiElement.project)
    val lineTerminator = psiFactory.createNewLine(1)
    val toReplace = parentPsiElement.addAfter(lineTerminator, anchor)

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

    if (element.isNewEmptyBlockElement) return null  // Avoid creation of an empty block.

    if (needToCreateParent(element)) {
      anchorAfter = null
    }

    val parentPsiElement = getParentPsi(element) ?: return null

    val project = parentPsiElement.project
    val psiFactory = KtPsiFactory(project)

    var statementText = maybeTrimForParent(element.nameElement, element.parent) // The text should be quoted if not followed by anything else,  otherwise it will create a reference expression.
    if (element.isBlockElement) {
      statementText += " {\n}"  // Can't create expression with another new line after.
    }
    else if (element.shouldUseAssignment()) {
      if (element.elementType == PropertyType.REGULAR) {
        statementText += " = \"abc\""
      }
      // TODO: add support for variables when available.
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
    var addedElement : PsiElement
    val anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)

    when (parentPsiElement) {
      is KtFile -> {
        addedElement = parentPsiElement.addAfter(statement, anchor)
        if (element.isBlockElement && !isWhiteSpaceOrNls(addedElement.prevSibling)) {
          parentPsiElement.addBefore(lineTerminator, addedElement)
        }
        parentPsiElement.addBefore(lineTerminator, addedElement)
      }
      is KtBlockExpression -> {
        addedElement = parentPsiElement.addAfter(statement, anchor)
        if (anchorAfter != null) {
          parentPsiElement.addBefore(lineTerminator, addedElement)
        }
        else {
          parentPsiElement.addAfter(lineTerminator, addedElement)
        }
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
    else if (addedElement is KtCallExpression || addedElement is KtBinaryExpression) {
      element.psiElement = addedElement
    }

    return element.psiElement
  }

  override fun deleteDslElement(element: GradleDslElement) {
    deletePsiElement(element, element.psiElement)
  }

  override fun createDslLiteral(literal: GradleDslLiteral): PsiElement? {
    val parent = literal.parent

    return when (parent) {
      is GradleDslExpressionList, is GradleDslExpressionMap, is GradleDslMethodCall -> processMethodCallElement(literal)
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

  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun applyDslMethodCall(methodCall: GradleDslMethodCall) {
    maybeUpdateName(methodCall)
  }

  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun applyDslExpressionList(expressionList: GradleDslExpressionList) {
    maybeUpdateName(expressionList)
  }

  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap) {
    maybeUpdateName(expressionMap)
  }

  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement) {
    maybeUpdateName(element)
  }
}