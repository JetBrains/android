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
          val key = element.header.key
          if (key != null) {
            key.doWithContext(context) { segment, context ->
              val map = GradleDslExpressionMap(context, element, GradleNameElement.from(segment, this@TomlDslParser), true)
              context.addParsedElement(map)
              getVisitor(map, GradleNameElement.empty()).let { visitor -> element.entries.forEach { it.accept(visitor) } }
            }
          }
          else {
            super.visitTable(element)
          }
      }

      override fun visitArray(element: TomlArray) {
        val list = GradleDslExpressionList(context, element, true, name)
        context.addParsedElement(list)
        getVisitor(list, GradleNameElement.empty()).let { visitor -> element.elements.forEach { it.accept(visitor) } }
      }

      override fun visitInlineTable(element: TomlInlineTable) {
        val map = GradleDslExpressionMap(context, element, name, true)
        context.addParsedElement(map)
        getVisitor(map, GradleNameElement.empty()).let { visitor -> element.entries.forEach { it.accept(visitor) } }
      }

      override fun visitLiteral(element: TomlLiteral) {
        val literal = GradleDslLiteral(context, element, name, element, LITERAL)
        context.addParsedElement(literal)
      }

      override fun visitKeyValue(element: TomlKeyValue) {
          element.key.doWithContext(context) { segment, context ->
            val key = GradleNameElement.from(segment, this@TomlDslParser)
            getVisitor(context, key).let { element.value?.accept(it) }
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
        // TODO(b/238982591): handle the other kinds too!  (TomlLiteralKind is sealed so this should be easy(!))
        else -> literal.text
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
}

fun TomlKey.doWithContext(context: GradlePropertiesDslElement, thunk: (TomlKeySegment, GradlePropertiesDslElement) -> Unit) {
  val lastSegmentIndex = segments.size - 1
  var currentContext = context
  segments.forEachIndexed { i, segment ->
    if (i == lastSegmentIndex) return thunk(segment, currentContext)
    val description = PropertiesElementDescription(segment.name, GradleDslExpressionMap::class.java, ::GradleDslExpressionMap)
    currentContext = currentContext.ensurePropertyElement(description)
    if (currentContext.psiElement == null) currentContext.psiElement = segment
  }
}