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
package com.android.tools.idea.gradle.completions

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.findParentOfType
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

val DECLARATIVE_BUILD_FILE = psiFile().withName(StandardPatterns.string().equalTo(SdkConstants.FN_DECLARATIVE_BUILD_GRADLE))

val INSIDE_TABLE_HEADER =
  psiElement(TomlTableHeader::class.java)
    .withLanguage(TomlLanguage)
    .inFile(DECLARATIVE_BUILD_FILE)

val INSIDE_SIMPLE_KEY = psiElement().withParent(TomlKeySegment::class.java)
  .withLanguage(TomlLanguage)
  .inFile(DECLARATIVE_BUILD_FILE)


private enum class ElementType(val str: String) {
  STRING("String"),
  INTEGER("Integer"),
  BOOLEAN("Boolean"),
  BLOCK("Block element"),
  FIRST_LEVEL_BLOCK("Block element"),
  STRING_ARRAY("String Array"),
  INTEGER_ARRAY("Integer Array"),
  BOOLEAN_ARRAY("Boolean Array"),
  GENERIC_PROPERTY("Property")
}

private data class Suggestion(val name:String, val type: ElementType)

class NamedNode(val name: String) {
  private val childrenMap = mutableMapOf<String, NamedNode>()

  val children = childrenMap.keys
  fun addChild(key: NamedNode) {
    childrenMap[key.name] = key
  }

  fun getOrPut(name:String, newKey:NamedNode):NamedNode{
    val key = childrenMap[name]
    if(key == null){
      childrenMap[name] = newKey
      return newKey
    }
    return key
  }

  fun getChild(name: String): NamedNode? = childrenMap[name]
}

class DeclarativeCompletionContributor : CompletionContributor() {
  init {
    if (StudioFlags.DECLARATIVE_PLUGIN_STUDIO_SUPPORT.get()) {
      extend(CompletionType.BASIC,
             INSIDE_TABLE_HEADER,
             object : CompletionProvider<CompletionParameters>() {
               override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                 val originalFile = parameters.originalFile as? TomlFile ?: return
                 val existingKeys = getDeclaredKeys(originalFile)
                 val segment = parameters.position.parent as? TomlKeySegment ?: return
                 val path = generateExistingPath(segment)
                 result.addAllElements(getSuggestions(path, existingKeys).map {
                   LookupElementBuilder.create(it.name)
                     .withTypeText(it.type.str, null, true)
                 })
               }
             }
      )
      extend(CompletionType.BASIC,
             INSIDE_SIMPLE_KEY,
             object : CompletionProvider<CompletionParameters>() {
               override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                 val segment = parameters.position.parent as? TomlKeySegment ?: return
                 val path = generateExistingPath(segment)
                 val originalFile = parameters.originalFile as? TomlFile ?: return
                 val existingKeys = getDeclaredKeys(originalFile)
                 result.addAllElements(getSuggestions(path, existingKeys).map {
                   val element = LookupElementBuilder.create(it.name)
                     .withTypeText(it.type.str, null, true)
                   when(it.type){
                     ElementType.GENERIC_PROPERTY -> element.withInsertHandler(insertProperty())
                     else -> element
                   }
                 })
               }
             }
      )
    }
  }

  private fun insertProperty(): InsertHandler<LookupElement?> =
    InsertHandler { context: InsertionContext, item: LookupElement ->
      val editor = context.editor
      val document = editor.document
      context.commitDocument()
      document.insertString(context.tailOffset, " = ")
      editor.caretModel.moveToOffset(context.tailOffset)
    }

  private fun getDeclaredKeys(tomlFile: TomlFile): NamedNode {
    val rootKey = NamedNode("")
    var currentKey = rootKey
    tomlFile.accept(object : TomlRecursiveVisitor() {
      override fun visitTable(element: TomlTable) {
        currentKey = rootKey
        visitKeyValueOwner(element)
        currentKey = rootKey
      }

      override fun visitTableHeader(element: TomlTableHeader) {
        element.key?.segments?.forEach {
          val newKey = currentKey.getOrPut(it.text, NamedNode(it.text))
          currentKey = newKey
        }
        visitElement(element)
      }

      override fun visitKeyValue(element: TomlKeyValue) {
        var key = currentKey
        element.key.segments.forEach {
          val newKey = key.getOrPut(it.text, NamedNode(it.text))
          key = newKey
        }
      }

    })
    return rootKey
  }

  private fun getSuggestions(path: List<String>, rootNode: NamedNode): Iterable<Suggestion> {
    val rootModel = GradleBuildFile.BuildGradlePropertiesDslElementSchema()
    var currentModel: GradlePropertiesDslElementSchema = rootModel
    var currentNode: NamedNode? = rootNode

    path.forEach { element ->
      currentNode = currentNode?.getChild(element)
      val blockElement = currentModel.getBlockElementDescription(element) ?: return listOf()
      currentModel = blockElement.schemaConstructor.construct()
    }
    val result = mutableListOf<Suggestion>()
    result += currentModel.blockElementDescriptions.map { Suggestion(it.key, ElementType.BLOCK)  }
    result += currentModel.getPropertiesInfo(GradleDslNameConverter.Kind.TOML).entrySet
      .filterNot{ currentNode?.children?.contains(it.surfaceSyntaxDescription.name) ?: false }
      .map {
        val propertyDescription = it.modelEffectDescription.property
        Suggestion(it.surfaceSyntaxDescription.name, propertyDescription.transformToSuggestionType())
      }
    return result
  }

  private fun ModelPropertyDescription.transformToSuggestionType(): ElementType {
    return when (this.type) {
      ModelPropertyType.MUTABLE_LIST, ModelPropertyType.MUTABLE_SET -> ElementType.STRING_ARRAY
      ModelPropertyType.STRING -> ElementType.STRING
      ModelPropertyType.BOOLEAN -> ElementType.BOOLEAN
      ModelPropertyType.NUMERIC -> ElementType.INTEGER
      // TODO -  need to handle map type
      else -> ElementType.GENERIC_PROPERTY
    }
  }

  private fun generateExistingPath(psiElement: TomlKeySegment): List<String> {
    val result = mutableListOf<String>()
    var key: TomlKey?
    var nextElement: PsiElement = psiElement
    if (psiElement.parent.parent !is TomlTableHeader) {
      do {
        // bubble up via inline tables to root/file
        key = nextElement.findParentOfType<TomlKey>()
        if (key != null) {
          nextElement = key
          key.appendReversedSegments(result, psiElement)
        }
      }
      while (key != null)
    }
    val parentTableHeaderKey = nextElement.findParentOfType<TomlTable>()?.header?.key
    parentTableHeaderKey?.appendReversedSegments(result, psiElement)
    return result.reversed()
  }

  private fun TomlKey.appendReversedSegments(list: MutableList<String>, startElement: PsiElement) {
    segments.reversed().forEach{ segment -> if (segment != startElement) list += segment.text }
  }

}

class EnableAutoPopupInDeclarativeBuildCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
    if (INSIDE_SIMPLE_KEY.accepts(contextElement) ||
        INSIDE_TABLE_HEADER.accepts(contextElement)) ThreeState.NO else ThreeState.UNSURE
}