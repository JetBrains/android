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
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.siblings
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlElementTypes.COMMA
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlTable
import java.lang.UnsupportedOperationException

class TomlDslWriter(private val context: BuildModelContext): GradleDslWriter, TomlDslNameConverter {
  override fun getContext(): BuildModelContext = context

  override fun moveDslElement(element: GradleDslElement): PsiElement? = null
  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement? = null
  override fun applyDslMethodCall(methodCall: GradleDslMethodCall): Unit = Unit
  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement? = createDslElement(expressionList)
  override fun applyDslExpressionList(expressionList: GradleDslExpressionList): Unit = maybeUpdateName(expressionList)
  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap): Unit =  maybeUpdateName(expressionMap)
  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement): Unit = maybeUpdateName(element)

  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement? = createDslElement(expressionMap)

  override fun createDslElement(element: GradleDslElement): PsiElement? {
    element.psiElement?.let { return it }
    val parentPsiElement = ensureParentPsi(element) ?: return null
    val project = parentPsiElement.project
    val factory = TomlPsiFactory(project)
    val comma = factory.createInlineTable("a = \"b\", c = \"d\"").children[2]

    val externalNameInfo = maybeTrimForParent(element, this)

    val psi = when (element.parent) {
      is GradleDslFile -> when (element) {
        is GradleDslExpressionMap -> factory.createTable(externalNameInfo.externalNameParts[0])
        else -> factory.createKeyValue(externalNameInfo.externalNameParts[0], "\"placeholder\"")
      }
      is GradleDslExpressionList -> when (element) {
        is GradleDslExpressionList -> factory.createArray("")
        is GradleDslExpressionMap -> factory.createInlineTable(" ")
        else -> factory.createLiteral("\"placeholder\"")
      }
      else -> when (element) {
        is GradleDslExpressionMap -> factory.createKeyValue(externalNameInfo.externalNameParts[0], "{ }")
        is GradleDslExpressionList -> factory.createKeyValue(externalNameInfo.externalNameParts[0], "[]")
        else -> factory.createKeyValue(externalNameInfo.externalNameParts[0], "\"placeholder\"")
      }
    }

    val anchor = getAnchorPsi(parentPsiElement, element.anchor)

    val addedElement = parentPsiElement.addAfter(psi, anchor)

    if (anchor != null) {
      when (parentPsiElement) {
        is TomlTable, is TomlFile -> addedElement.addAfter(factory.createNewline(), null)
        is TomlInlineTable -> when {
          parentPsiElement.entries.size == 1 -> Unit
          anchor is LeafPsiElement && anchor.elementType == TomlElementTypes.L_CURLY -> parentPsiElement.addAfter(comma, addedElement)
          else -> parentPsiElement.addBefore(comma, addedElement)
        }
        is TomlArray -> when {
          parentPsiElement.elements.size == 1 -> Unit
          anchor is LeafPsiElement && anchor.elementType == TomlElementTypes.L_BRACKET -> parentPsiElement.addAfter(comma, addedElement)
          else -> parentPsiElement.addBefore(comma, addedElement)
        }
      }
    }

    when (addedElement) {
      is TomlKeyValue -> element.psiElement = addedElement.value
      else -> element.psiElement = addedElement
    }

    return element.psiElement
  }

  override fun deleteDslElement(element: GradleDslElement) {
    val psiElement = element.psiElement ?: return
    val parent = element.parent ?: return
    val parentPsi = ensureParentPsi(element)
    when (parent) {
      is GradleDslFile -> psiElement.findParentOfType<TomlKeyValue>()?.delete()
      is GradleDslExpressionMap -> when (parentPsi) {
        is TomlTable -> psiElement.findParentOfType<TomlKeyValue>()?.delete()
        is TomlInlineTable -> deletePsiParentOfTypeFromDslParent<GradleDslExpressionMap, TomlKeyValue>(element, psiElement, parent)
      }
      is GradleDslExpressionList -> when (parentPsi) {
        is TomlArray -> deletePsiParentOfTypeFromDslParent<GradleDslExpressionList, TomlLiteral>(element, psiElement, parent)
      }
    }
  }

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


  override fun deleteDslLiteral(literal: GradleDslLiteral) {
    deleteDslElement(literal)
  }

  private fun ensureParentPsi(element: GradleDslElement) = element.parent?.create()

  private fun getAnchorPsi(parent: PsiElement, anchorDsl: GradleDslElement?): PsiElement? {
    var anchor = anchorDsl?.let{ findLastPsiElementIn(it) }
    if (anchor == null && (parent is TomlInlineTable || parent is TomlArray)) return parent.firstChild
    if (anchor == null && parent is TomlTable) return parent.header
    while (anchor != null && anchor.parent != parent) {
      anchor = anchor.parent
    }
    return anchor ?: parent
  }

  private inline fun <T : GradlePropertiesDslElement, reified P : TomlElement> deletePsiParentOfTypeFromDslParent(
    element: GradleDslElement,
    psiElement: PsiElement,
    parent: T
  ) {
    val parentElements = parent.originalElements
    val position = parentElements.indexOf(element).also { if(it < 0) return }
    val size = parentElements.size
    val tomlLiteral = psiElement.findParentOfType<P>(strict = false)
    when {
      size == 0 -> return // should not happen
      size == 1 -> tomlLiteral?.delete()
      position == size - 1 -> tomlLiteral.deleteToComma(forward = false)
      else -> tomlLiteral.deleteToComma(forward = true)
    }
  }

  private fun TomlElement?.deleteToComma(forward: Boolean = true) {
    this?.run {
      var seenComma = false
      siblings(forward = forward, withSelf = true)
        .takeWhile { sib -> !seenComma.also { if (sib is LeafPsiElement && sib.elementType == COMMA) seenComma = true } }
        .toList()
        .forEach { if (it !is PsiWhiteSpace) it.delete() }
    }
  }
}