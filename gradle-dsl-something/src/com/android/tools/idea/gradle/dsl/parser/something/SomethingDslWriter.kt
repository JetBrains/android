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
import com.android.tools.idea.gradle.something.psi.SomethingAssignment
import com.android.tools.idea.gradle.something.psi.SomethingBlock
import com.android.tools.idea.gradle.something.psi.SomethingFile
import com.android.tools.idea.gradle.something.psi.SomethingPsiFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.findParentOfType

class SomethingDslWriter(private val context: BuildModelContext) : GradleDslWriter, SomethingDslNameConverter {

  override fun getContext(): BuildModelContext = context
  override fun moveDslElement(element: GradleDslElement): PsiElement? = null
  override fun createDslElement(element: GradleDslElement): PsiElement? {
    if (element.isAlreadyCreated()) element.psiElement?.let { return it }
    val parentPsiElement = element.parent?.create() ?: return null
    val project = parentPsiElement.project
    val factory = SomethingPsiFactory(project)
    val name = element.name

    val psiElement = when (element){
      is GradleDslLiteral -> factory.createAssignment(name, "\"placeholder\"")
      is GradleDslElementList, is GradleDslBlockElement -> factory.createBlock(name)
      else -> null
    }
    psiElement ?: return null

    val anchor = getAnchor(parentPsiElement, element.anchor)
    val addedElement = parentPsiElement.addAfter(psiElement, anchor)

    when (parentPsiElement) {
      is SomethingBlock -> addedElement.addAfter(factory.createNewline(), null)
    }

    element.psiElement = when (addedElement) {
      is SomethingAssignment -> addedElement.value
      else -> addedElement
    }
    return element.psiElement
  }

  private fun getAnchor(parent: PsiElement, anchorDsl: GradleDslElement?): PsiElement? {
    var anchor = anchorDsl?.let { findLastPsiElementIn(it) }
    if (anchor == null && parent is SomethingBlock) return parent.blockEntriesStart
    while (anchor != null && anchor.parent != parent) {
      anchor = anchor.parent
    }
    return anchor ?: parent
  }

  override fun deleteDslElement(element: GradleDslElement) {
    element.delete()
  }

  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement? = null
  override fun applyDslMethodCall(methodCall: GradleDslMethodCall): Unit = Unit
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

    val element = psiElement.replace(newElement)
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
  private fun GradleDslElement.isAlreadyCreated(): Boolean = psiElement?.findParentOfType<SomethingFile>(strict = false) != null


}