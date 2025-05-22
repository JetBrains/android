/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile.GradleBundleRefLiteral
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile.GradleDslVersionLiteral
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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.REFERENCE

class TomlDslParser(
  private val psiFile: TomlFile,
  private val dslFile: GradleDslFile
) : GradleDslParser, TomlDslNameConverter {
  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean = false

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    mutableListOf()


  override fun parse() {
    fun getVisitor(context: GradlePropertiesDslElement, name: GradleNameElement): TomlRecursiveVisitor = object : TomlRecursiveVisitor() {
      override fun visitTable(element: TomlTable) {
        val key = element.header.key
        if (key != null) {
          key.doWithContext(context) { segment, context ->
            val map = context.getOrCreateMap(segment, element)
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
        val literal = GradleDslLiteral(context, element, name, element, GradleDslLiteral.LiteralType.LITERAL)
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
    postProcessing()
  }

  private fun GradlePropertiesDslElement.getOrCreateMap(segment: TomlKeySegment, psiElement: PsiElement): GradleDslExpressionMap {
    val description = PropertiesElementDescription(segment.name, GradleDslExpressionMap::class.java, ::GradleDslExpressionMap)
    return getPropertyElement(description) ?: GradleDslExpressionMap(this, psiElement,
                                                                     GradleNameElement.from(segment, this@TomlDslParser),
                                                                     true).also { addParsedElement(it) }
  }

  fun TomlKey.doWithContext(context: GradlePropertiesDslElement, thunk: (TomlKeySegment, GradlePropertiesDslElement) -> Unit) {
    val lastSegmentIndex = segments.size - 1
    var currentContext = context
    segments.forEachIndexed { i, segment ->
      if (i == lastSegmentIndex) return thunk(segment, currentContext)
      val map = currentContext.getOrCreateMap(segment, segment)
      currentContext = map
      if (currentContext.psiElement == null) currentContext.psiElement = segment
    }
  }

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? {
    return when (literal) {
      is TomlLiteral -> when (val kind = literal.kind) {
        is TomlLiteralKind.String -> kind.value
        is TomlLiteralKind.Boolean -> literal.text == "true" // need this for rejectAll attribute
        else -> literal.text
      }
      else -> literal.text
    }
  }

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> =
    (dslFile as? GradleVersionCatalogFile)?.getInjection(context, psiElement) ?: mutableListOf()

  override fun getPropertiesElement(
    nameParts: MutableList<String>,
    parentElement: GradlePropertiesDslElement,
    nameElement: GradleNameElement?
  ): GradlePropertiesDslElement? {
    return null
  }

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? {
    return when (literal) {
      is String -> TomlPsiFactory(context.dslFile.project, true).createLiteral("\"$literal\"")
      is Int, is Boolean, is BigDecimal -> TomlPsiFactory(context.dslFile.project, true).createLiteral(literal.toString())
      // sometimes elements refer to other elements with just string - example: productFlavor.initWith = "dependent"
      is ReferenceTo -> TomlPsiFactory(context.dslFile.project, true).createLiteral("\"${literal.referredElement.name}\"")
      else -> null
    }
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) = Unit
  override fun getContext(): BuildModelContext = context

  private fun postProcessing() {
    // versions and libraries (plugins) can be in any order so need to establish references once everything is parsed
    replaceVersionRefsWithInjections()
    replaceLibraryRefsInBundlesWithInjections()
  }

  private fun replaceVersionRefsWithInjections() {
    val libraries: GradleDslExpressionMap? = dslFile.getPropertyElement("libraries", GradleDslExpressionMap::class.java)
    val plugins: GradleDslExpressionMap? = dslFile.getPropertyElement("plugins", GradleDslExpressionMap::class.java)
    val versions: GradleDslExpressionMap = dslFile.getPropertyElement("versions", GradleDslExpressionMap::class.java) ?: return

    val versionRefReplacer: (GradleDslExpressionMap) -> Unit = { library ->
      val versionProperty: GradleDslElement? = library.getPropertyElement("version")
      if (versionProperty is GradleDslExpressionMap) {
        val refProperty: GradleDslElement? = versionProperty.getPropertyElement("ref")
        if (refProperty is GradleDslLiteral) {
          val targetName = refProperty.getValue(String::class.java)
          if (targetName != null) {
            versions.getPropertyElement(targetName)?.let { targetProperty ->
              refProperty.psiElement?.let { psi ->
                val reference = GradleDslVersionLiteral(library, psi, versionProperty.getNameElement(), psi, REFERENCE)
                val injection = GradleReferenceInjection(reference, targetProperty, psi, targetName)
                targetProperty.registerDependent(injection)
                reference.addDependency(injection)
                library.substituteElement(versionProperty, reference)
              }
            }
          }
        }
      }
      else if (versionProperty is GradleDslLiteral) {
        val literal: GradleDslLiteral =
          GradleDslVersionLiteral(library, versionProperty.psiElement!!, versionProperty.nameElement, versionProperty.psiElement!!, LITERAL)
        library.substituteElement(versionProperty, literal)
      }
    }

    libraries?.getPropertyElements(GradleDslExpressionMap::class.java)?.forEach(versionRefReplacer)
    plugins?.getPropertyElements(GradleDslExpressionMap::class.java)?.forEach(versionRefReplacer)
  }

  private fun replaceLibraryRefsInBundlesWithInjections() {
    val libraries: GradleDslExpressionMap = dslFile.getPropertyElement("libraries", GradleDslExpressionMap::class.java) ?: return
    val bundles: GradleDslExpressionMap = dslFile.getPropertyElement("bundles", GradleDslExpressionMap::class.java) ?: return

    bundles.getPropertyElements(GradlePropertiesDslElement::class.java).forEach { bundle ->
      val elements: List<GradleDslElement> = bundle.getCurrentElements()
      elements.forEach { element: GradleDslElement? ->
        if (element is GradleDslLiteral) {
          val targetName = element.getValue(String::class.java)
          if (targetName != null) {
            libraries.getPropertyElement(targetName)?.let { targetProperty ->
              element.psiElement?.let { psi ->
                val reference = GradleBundleRefLiteral(bundle, psi, targetProperty.nameElement, psi, REFERENCE)
                val injection = GradleReferenceInjection(reference, targetProperty, psi, targetName)
                targetProperty.registerDependent(injection)
                reference.addDependency(injection)
                bundle.substituteElement(element, reference)
              }
            }
          }
        }
      }
    }
  }

}