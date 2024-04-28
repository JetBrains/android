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
package com.android.tools.idea.gradle.dsl.toml

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.siblings
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlPsiFactory

abstract class TomlDslWriter(private val context: BuildModelContext) : GradleDslWriter {
  override fun getContext(): BuildModelContext = context
  override fun moveDslElement(element: GradleDslElement): PsiElement? = null
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

  protected fun ensureParentPsi(element: GradleDslElement) = element.parent?.create()

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

  protected inline fun <T : GradlePropertiesDslElement, reified P : TomlElement> deletePsiParentOfTypeFromDslParent(
    element: GradleDslElement,
    psiElement: PsiElement,
    parent: T
  ) {
    val parentElements = parent.originalElements
    val position = parentElements.indexOf(element).also { if (it < 0) return }
    val size = parentElements.size
    val tomlLiteral = psiElement.findParentOfType<P>(strict = false)
    when {
      size == 0 -> return // should not happen
      size == 1 -> tomlLiteral?.delete()
      position == size - 1 -> tomlLiteral.deleteToComma(forward = false)
      else -> tomlLiteral.deleteToComma(forward = true)
    }
  }

  protected fun TomlElement?.deleteToComma(forward: Boolean = true) {
    this?.run {
      var seenComma = false
      siblings(forward = forward, withSelf = true)
        .takeWhile { sib -> !seenComma.also { if (sib is LeafPsiElement && sib.elementType == TomlElementTypes.COMMA) seenComma = true } }
        .toList()
        .forEach { if (it !is PsiWhiteSpace) it.delete() }
    }
  }

  protected fun TomlPsiFactory.createDot() = createKey("a.b").children[1]
  protected fun TomlPsiFactory.createComma() = createInlineTable("a = \"b\", c = \"d\"").children[2]

}