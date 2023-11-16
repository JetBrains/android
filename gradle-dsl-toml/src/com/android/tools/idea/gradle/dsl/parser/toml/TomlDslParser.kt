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
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import java.math.BigDecimal

class TomlDslParser(
  val psiFile: TomlFile,
  private val context: BuildModelContext,
  private val dslFile: GradleDslFile
) : GradleDslParser, TomlDslNameConverter {
  override fun getContext(): BuildModelContext = context

  override fun parse() {
    fun getVisitor(context: GradlePropertiesDslElement, name: GradleNameElement): TomlRecursiveVisitor = object : TomlRecursiveVisitor() {
      override fun visitTable(element: TomlTable) {
        element.header.key?.doWithContext(context) { segment, context ->
          if (segment.name == null) return@doWithContext
          val nameElement = GradleNameElement.from(segment, this@TomlDslParser)
          val description = context.getMapDescription(segment.name!!)
          val map = context.getPropertyElement(description) ?: context.createChildDslElement(nameElement, element) {
            GradleDslExpressionMap(context, element, nameElement, true)
          }
          map.psiElement = element // element must point to the last table psi
          getVisitor(map, GradleNameElement.empty()).let { visitor -> element.entries.forEach { it.accept(visitor) } }
        }
      }

      override fun visitArray(element: TomlArray) {
        val list = context.createChildDslElement(name, element) { GradleDslExpressionList(context, element, true, name) }
        list.externalSyntax = ExternalNameSyntax.ASSIGNMENT
        getVisitor(list, GradleNameElement.empty()).let { visitor -> element.elements.forEach { it.accept(visitor) } }
      }

      override fun visitInlineTable(element: TomlInlineTable) {
        val map = context.createChildDslElement(name, element) { GradleDslExpressionMap(context, element, name, true) }
        getVisitor(map, GradleNameElement.empty()).let { visitor -> element.entries.forEach { it.accept(visitor) } }
      }

      override fun visitLiteral(element: TomlLiteral) {
        val literal = GradleDslLiteral(context, element, name, element, LITERAL)
        literal.externalSyntax = ExternalNameSyntax.ASSIGNMENT
        context.addParsedElement(literal)
      }

      override fun visitKeyValue(element: TomlKeyValue) {
        element.key.doWithContext(context) { segment, context ->
          val key = GradleNameElement.from(segment, this@TomlDslParser)
          getVisitor(context, key).let { element.value?.accept(it) }
        }
      }

      override fun visitArrayTable(element: TomlArrayTable) {
        element.header.key?.doWithContext(context) { segment, context ->
          val name = segment.name ?: return@doWithContext
          val nameElement = GradleNameElement.from(segment, this@TomlDslParser)
          val description = context.getChildPropertiesElementDescription(name)
                            ?: PropertiesElementDescription(name,
                                                            GradleDslExpressionList::class.java,
                                                            ::GradleDslExpressionList)

          val list: GradlePropertiesDslElement = context.getPropertyElement(description)
                                                 ?: context.createChildDslElement(nameElement, element) {
                                                   GradleDslExpressionList(context, GradleNameElement.create(name))
                                                 }
          val map = GradleDslExpressionMap(context, element, GradleNameElement.empty(), true)

          list.addParsedElement(map)
          context.addParsedElement(list)
          getVisitor(map, GradleNameElement.empty()).let { visitor -> element.entries.forEach { it.accept(visitor) } }
        }
      }
    }
    psiFile.accept(getVisitor(dslFile, GradleNameElement.empty()))
  }

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? {
    return when (literal) {
      is String -> TomlPsiFactory(context.dslFile.project, true).createLiteral("\"$literal\"")
      is Int, is Boolean, is BigDecimal -> TomlPsiFactory(context.dslFile.project, true).createLiteral(literal.toString())
      else -> null
    }
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) {

  }

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? {
    return when (literal) {
      is TomlLiteral -> when (val kind = literal.kind) {
        is TomlLiteralKind.String -> kind.value
        is TomlLiteralKind.Boolean -> literal.text == "true"
        is TomlLiteralKind.Number -> literal.text.replace("_", "").let { text ->
          when {
            text.startsWith("0x") -> text.substring(2).toLong(radix = 16)
            text.startsWith("0o") -> text.substring(2).toLong(radix = 8)
            text.startsWith("0b") -> text.substring(2).toLong(radix = 2)
            text == "inf" || text == "+inf" -> Double.POSITIVE_INFINITY
            text == "-inf" -> Double.NEGATIVE_INFINITY
            text == "nan" || text == "+nan" -> Double.NaN
            text == "-nan" -> Double.NaN // no distinct NaN value available
            text.contains("[eE.]".toRegex()) -> text.toDouble()
            else -> text.replace("_", "").let { it.toIntOrNull(10) ?: it.toLong(10) }
          }
        }
        is TomlLiteralKind.DateTime -> literal.text // No Gradle Dsl representation of Toml DateTime
        null -> literal.text
      }
      else -> literal.text
    }
  }

  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean {
    return false
  }

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> {
    return mutableListOf()
  }

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    (dslFile as? GradleVersionCatalogFile)?.getInjection(context, psiElement) ?: mutableListOf()

  override fun getPropertiesElement(nameParts: MutableList<String>,
                                    parentElement: GradlePropertiesDslElement,
                                    nameElement: GradleNameElement?): GradlePropertiesDslElement? {
    return null
  }

  fun TomlKey.doWithContext(context: GradlePropertiesDslElement, thunk: (TomlKeySegment, GradlePropertiesDslElement) -> Unit) {
    val lastSegmentIndex = segments.size - 1
    var currentContext = context
    segments.forEachIndexed { i, segment ->
      if (i == lastSegmentIndex) return thunk(segment, currentContext)
      if (segment.name == null) return
      val description = currentContext.getMapDescription(segment.name!!)

      val nameElement = GradleNameElement.from(segment, this@TomlDslParser)
      currentContext = currentContext.getPropertyElement(description) ?: currentContext.createChildDslElement(nameElement, segment) {
        GradleDslExpressionMap(currentContext, segment, nameElement, true)
      }
    }
  }
}


fun GradlePropertiesDslElement.createChildDslElement(elementName: GradleNameElement,
                                                     psiElement: PsiElement,
                                                     defaultConstructor: () -> GradlePropertiesDslElement): GradlePropertiesDslElement {
  val result = when (val description = getChildPropertiesElementDescription(elementName.name())) {
    null -> defaultConstructor.invoke()
    else -> {
      val element = description.constructor.construct(this, elementName)
      element.psiElement = psiElement
      element
    }
  }
  result.externalSyntax = ExternalNameSyntax.ASSIGNMENT
  addParsedElement(result)
  return result
}

fun GradlePropertiesDslElement.getMapDescription(name: String): PropertiesElementDescription<*> {
  var description = getChildPropertiesElementDescription(name) ?: PropertiesElementDescription(name,
                                                                                               GradleDslExpressionMap::class.java,
                                                                                               ::GradleDslExpressionMap)
  if (description.name == null) {
    // null means all names are acceptable, but we need to search for our particular name in order to update if it's there
    description = description.copyWithName(name)
  }
  return description
}
