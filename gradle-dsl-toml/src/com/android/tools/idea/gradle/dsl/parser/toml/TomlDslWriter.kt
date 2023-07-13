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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.siblings
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlElementTypes.COMMA
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlTable

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
    if (element.isAlreadyCreated()) element.psiElement?.let { return it }

    if (element.isNewEmptyBlockElement) {
      return null // Avoid creation of an empty block statement.
    }

    if (!canReuseParent(element)) element.parent?.psiElement = null

    val parentPsiElement = ensureParentPsi(element) ?: return null
    val project = parentPsiElement.project
    val factory = TomlPsiFactory(project)

    if (element.parent is GradleDslElementList) {
      // Inside the list we can have GradleDslLiteral, infix notation element, GradleDslMethodCall with reference
      return createArrayTable(element, factory)
    }

    if (element.parent?.isBlockElement == true && element.isBlockElement) {
      // we have special handler for block elements
      return createBlockInBlock(element, factory)
    }

    val name = getNameTrimmedForParent(element)

    val psi = when (element.parent) {
                is GradleDslFile -> when (element) {
                  is GradleDslExpressionMap, is GradleDslBlockElement -> factory.createTable(name)
                  is GradleDslElementList, is GradleDslExpressionList -> factory.createArrayTable(name)
                  else -> factory.createKeyValue(name, "\"placeholder\"")
                }

                is GradleDslElementList, is GradleDslExpressionList -> when (element) {
                  is GradleDslExpressionList -> factory.createArray("")
                  is GradleDslExpressionMap -> factory.createInlineTable(" ")
                  is GradleDslSimpleExpression -> factory.createLiteral("\"${element.value}\"")
                  else -> factory.createLiteral("\"placeholder\"")
                }

                is GradleDslInfixExpression ->
                  factory.createKeyValue(name, "\"placeholder\"")

                else -> when (element) {
                  is GradleDslExpressionMap -> factory.createKeyValue(name, "{ }")
                  is GradleDslExpressionList -> factory.createKeyValue(name, "[]")
                  else -> factory.createKeyValue(name, "\"placeholder\"")
                }
              }

    val anchor = getAnchorPsi(parentPsiElement, element.anchor)

    val addedElement = parentPsiElement.addAfter(psi, anchor)

    val comma = factory.createComma()
    if (anchor != null) {
      when (parentPsiElement) {
        // this actually adds new line before addedElement
        is TomlTable, is TomlFile, is TomlArrayTable -> addedElement.addAfter(factory.createNewline(), null)
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

  private fun getNameTrimmedForParent(element:GradleDslElement):String{
    val externalNameInfo = maybeTrimForParent(element, this)
    return externalNameInfo.externalNameParts.getOrElse(0) { "" }
  }

  /**
   * Mark element.parent.psiElement as null if we cannot reuse existing parent psi
   * For example, if we need to add element but parent psi is a key segment
   */
  private fun canReuseParent(element: GradleDslElement):Boolean{
    val parentPsi = element.parent?.psiElement
    return parentPsi !is TomlKeySegment // we need to create parent psi element
  }

  private fun GradleDslElement.isAlreadyCreated(): Boolean = psiElement?.findParentOfType<TomlFile>(strict = false) != null

  private fun TomlPsiFactory.createDot() = createKey("a.b").children[1]
  private fun TomlPsiFactory.createComma() = createInlineTable("a = \"b\", c = \"d\"").children[2]
  private fun TomlPsiFactory.createArrayTable(name: String) = createTableHeader("[$name]").parent as TomlArrayTable

  private fun createArrayTable(element: GradleDslElement, factory: TomlPsiFactory): PsiElement? {
    val parent = element.parent ?: return null
    val newTable = factory.createArrayTable(parent.getSegmentedName())
    val file = parent.psiElement?.findParentOfType<TomlFile>() ?: return null
    val arrayTableAnchor = getAnchorPsi(file, parent.anchor)

    val addedArrayTable = file.addAfter(newTable, arrayTableAnchor)
    addedArrayTable.addAfter(factory.createNewline(), null)

    if (element is GradleDslLiteral) {
      val psi = factory.createKeyValue(element.name, "\"placeholder\"")
      val anchor = getAnchorPsi(addedArrayTable, element.anchor)
      val addedElement: TomlKeyValue = addedArrayTable.addAfter(psi, anchor) as TomlKeyValue
      addedElement.addAfter(factory.createNewline(), null)
      element.psiElement = addedElement.value
    }
    else {
      element.psiElement = addedArrayTable
    }

    return element.psiElement
  }

  private fun createBlockInBlock(element: GradleDslElement, factory: TomlPsiFactory): PsiElement?{
    val parent = element.parent ?: return null
    val table = ensureParentPsi(element) as? TomlTable ?: return null

    if(parent.hasOnlyBlockElements() && table.entries.isEmpty()) {
      // need to reuse parent block as it's empty and there is no non block elements
      val headerKey = table.header.key ?: return null
      val segments = headerKey.segments

      val name = getNameTrimmedForParent(element)

      val psi = factory.createKeySegment(name)
      val addedElement = headerKey.addAfter(psi, segments.last())
      headerKey.addBefore(factory.createDot(), addedElement)

      parent.psiElement = segments.last()
      element.psiElement = table
      return table
    }
    else {
      // need to create new table
      val file = table.parent
      val newTable = factory.createTable(element.getSegmentedName())
      val anchor = getAnchorPsi(file, null)

      val addedElement = file.addAfter(newTable, anchor)
      addedElement.addAfter(factory.createNewline(), null)
      element.psiElement = addedElement
      return addedElement
    }
  }

  private fun GradleDslElement.hasOnlyBlockElements() = children.all { it.isBlockElement }

  /**
   * Returns null if current block has only blocks or empty.
   * Returns reversed dotted path of blocked elements from current to parents like "android.buildTypes"
   */
  private fun GradleDslElement.getSegmentedName(): String {
    val result = mutableListOf<String>()
    var currentElement: GradleDslElement? = this
    while (currentElement != null && currentElement.isBlockElement) {
      result.add(currentElement.name)
      currentElement = currentElement.parent
    }
    result.reverse()
    return result.joinToString(".")
  }

  override fun deleteDslElement(element: GradleDslElement) {
    val psiElement = element.psiElement ?: return
    psiElement.findParentOfType<TomlFile>() ?: return

    val parent = element.parent ?: return
    val parentPsi = ensureParentPsi(element)
    when (parent) {
      is GradleDslFile -> psiElement.findParentOfType<TomlKeyValue>()?.delete()
      is GradleDslExpressionMap -> when (parentPsi) {
        is TomlTable, is TomlArrayTable -> psiElement.findParentOfType<TomlKeyValue>()?.delete()
        is TomlInlineTable -> deletePsiParentOfTypeFromDslParent<GradleDslExpressionMap, TomlKeyValue>(element, psiElement, parent)
      }
      is GradleDslExpressionList -> when (parentPsi) {
        is TomlArray -> deletePsiParentOfTypeFromDslParent<GradleDslExpressionList, TomlLiteral>(element, psiElement, parent)
      }
      is GradlePropertiesDslElement -> psiElement.findParentOfType<TomlKeyValue>()?.delete()
    }
    deleteEmptyParent(parentPsi)
  }

  private fun deleteEmptyParent(parentPsi: PsiElement?) {
    when (parentPsi) {
      is TomlArrayTable -> if (parentPsi.entries.isEmpty()) parentPsi.delete()
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
      // if we did not find parent psi - we have split element case
      if(anchor is TomlFile) return parent.lastChild
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