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

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.android.tools.idea.gradle.declarative.psi.DeclarativeArgumentsList
import com.android.tools.idea.gradle.declarative.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlockGroup
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFactory
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFile
import com.android.tools.idea.gradle.declarative.psi.DeclarativePsiFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.findParentOfType

class DeclarativeDslWriter(private val context: BuildModelContext) : GradleDslWriter, DeclarativeDslNameConverter {

  override fun getContext(): BuildModelContext = context
  override fun moveDslElement(element: GradleDslElement): PsiElement? = null
  override fun createDslElement(element: GradleDslElement): PsiElement? {
    if (element.isAlreadyCreated()) element.psiElement?.let { return it }
    if (element.isNewEmptyBlockElement()) {
      return null // Avoid creation of an empty block statement.
    }

    val psiElementOfParent = element.parent?.create() ?: return null
    val parentPsiElement = when (psiElementOfParent) {
      is DeclarativeBlock -> psiElementOfParent.blockGroup
      else -> psiElementOfParent
    }

    val project = parentPsiElement.project
    val factory = DeclarativePsiFactory(project)
    val name = getNameTrimmedForParent(element)

    val psiElement = when (element) {
      is GradleDslLiteral ->
        if (parentPsiElement is DeclarativeArgumentsList)
          factory.createLiteral(element.value)
        else if (element.externalSyntax == ASSIGNMENT)
          factory.createAssignment(name, "\"placeholder\"")
        else
          factory.createOneParameterFactory(name, "\"placeholder\"")

      is GradleDslElementList, is GradleDslBlockElement -> factory.createBlock(name)
      is GradleDslMethodCall -> {
        if (element.isDoubleFunction()) {
          val internal = factory.createFactory(element.methodName)
          factory.createOneParameterFactory(name, internal.text)
        }
        else factory.createFactory(name)
      }

      else -> null
    }
    psiElement ?: return null

    val anchor = getAnchor(parentPsiElement, element.anchor)
    val addedElement = parentPsiElement.addAfter(psiElement, anchor)

    // after processing
    val comma = factory.createComma()
    when (parentPsiElement) {
      is DeclarativeBlockGroup -> addedElement.addAfter(factory.createNewline(), null)
      is DeclarativeArgumentsList ->
        if (parentPsiElement.arguments.size > 1)
          parentPsiElement.addBefore(comma, addedElement)
        else Unit
        // TODO add logic for inserting attribute in the first place
    }

    element.psiElement = when (addedElement) {
      is DeclarativeAssignment -> addedElement.value
      else -> addedElement
    }
    return element.psiElement
  }

  private fun getNameTrimmedForParent(element: GradleDslElement): String {
    val defaultName = element.name // use this when other mechanisms fail
    val externalNameInfo = maybeTrimForParent(element, this)
    return externalNameInfo.externalNameParts.getOrElse(0){
      // fallback to external naming mechanism for blocks
      val parent = element.parent
      if (parent is GradlePropertiesDslElement) {
        val name = element.nameElement.fullNameParts().lastOrNull() ?: defaultName
        externalNameForPropertiesParent(name, parent)
      }
      else defaultName
    }
  }

  // this is DSL function like `implementation project(":my")` where `implementation` is name and `project` is function name
  private fun GradleDslMethodCall.isDoubleFunction(): Boolean =
    methodName != name && methodName.isNotEmpty()

  private fun getAnchor(parent: PsiElement, anchorDsl: GradleDslElement?): PsiElement? {
    var anchor = anchorDsl?.let { findLastPsiElementIn(it) }
    if (anchor == null && parent is DeclarativeBlockGroup) return parent.blockEntriesStart
    if (anchor == null && parent is DeclarativeArgumentsList) return parent.firstChild
    while (anchor != null && anchor.parent != parent) {
      anchor = anchor.parent
    }
    return anchor ?: parent
  }

  override fun deleteDslElement(element: GradleDslElement) {
    element.psiElement?.delete()
  }

  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement {
    val call = createDslElement(methodCall) as DeclarativeFactory
    if (methodCall.isDoubleFunction())
      call.argumentsList?.arguments?.forEach {
        (it as? DeclarativeFactory)?.argumentsList?.let { arg -> methodCall.argumentsElement.psiElement = arg }
      }
    else
      methodCall.argumentsElement.psiElement = call.argumentsList
    methodCall.arguments.forEach { it.create() }
    return call
  }
  override fun applyDslMethodCall(methodCall: GradleDslMethodCall) {
    maybeUpdateName(methodCall)
    methodCall.argumentsElement.applyChanges()
  }

  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement? = createDslElement(expressionList)
  override fun applyDslExpressionList(expressionList: GradleDslExpressionList): Unit = maybeUpdateName(expressionList)

  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap): Unit = maybeUpdateName(expressionMap)
  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement): Unit = maybeUpdateName(element)

  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement? = createDslElement(expressionMap)

  override fun createDslLiteral(literal: GradleDslLiteral) = createDslElement(literal)

  override fun applyDslLiteral(literal: GradleDslLiteral) {
    val psiElement = literal.psiElement ?: return
    maybeUpdateName(literal)
    val newElement = literal.unsavedValue ?: return
    val element =
      when(psiElement){
        is DeclarativeAssignment -> psiElement.value?.firstChild?.replace(newElement) ?: return
        is DeclarativeFactory -> psiElement.argumentsList?.arguments?.firstOrNull()?.replace(newElement) ?: return
        else -> psiElement.replace(newElement)
      }
    literal.setExpression(element)
    literal.reset()
    literal.commit()
  }

  override fun deleteDslLiteral(literal: GradleDslLiteral) {
    deleteDslElement(literal)
  }

  private fun maybeUpdateName(element: GradleDslElement) {
    val nameElement = element.nameElement
    val localName = nameElement.localName
    if (localName.isNullOrEmpty() || nameElement.originalName == localName) return

    val oldName = nameElement.namedPsiElement ?: return

    val newName = GradleNameElement.unescape(localName)

    // only rename elements that already have name
    if (oldName is PsiNamedElement) {
      oldName.setName(newName)
      element.nameElement.commitNameChange(oldName, this, element.parent)
    }
  }
  private fun GradleDslElement.isAlreadyCreated(): Boolean = psiElement?.findParentOfType<DeclarativeFile>(strict = false) != null

}