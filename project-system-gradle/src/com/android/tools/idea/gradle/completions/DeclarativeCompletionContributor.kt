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
import com.android.tools.idea.gradle.completions.ElementType.ARRAY_TABLE
import com.android.tools.idea.gradle.completions.ElementType.BLOCK
import com.android.tools.idea.gradle.completions.ElementType.BOOLEAN
import com.android.tools.idea.gradle.completions.ElementType.GENERIC_PROPERTY
import com.android.tools.idea.gradle.completions.ElementType.INTEGER
import com.android.tools.idea.gradle.completions.ElementType.STRING
import com.android.tools.idea.gradle.completions.ElementType.STRING_ARRAY
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.DECLARATIVE_TOML
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.android.tools.idea.gradle.util.generateExistingPath
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

val DECLARATIVE_BUILD_FILE = psiFile().withName(StandardPatterns.string().equalTo(SdkConstants.FN_DECLARATIVE_BUILD_GRADLE))

val INSIDE_TABLE_HEADER =
  psiElement().withSuperParent(3, TomlTableHeader::class.java)
    .inFile(DECLARATIVE_BUILD_FILE)

val INSIDE_SIMPLE_KEY =
  psiElement().withSuperParent(3, TomlKeyValue::class.java)
    .inFile(DECLARATIVE_BUILD_FILE)

private enum class ElementType(val str: String) {
  STRING("String"),
  INTEGER("Integer"),
  BOOLEAN("Boolean"),
  BLOCK("Block element"),
  ARRAY_TABLE("Array Table"),
  FIRST_LEVEL_BLOCK("Block element"),
  STRING_ARRAY("String Array"),
  INTEGER_ARRAY("Integer Array"),
  BOOLEAN_ARRAY("Boolean Array"),
  GENERIC_PROPERTY("Property")
}

private data class Suggestion(val name: String, val type: ElementType)

private class NamedNode(val name: String) {
  private val childrenMap = mutableMapOf<String, NamedNode>()

  val children = childrenMap.keys
  fun addChild(key: NamedNode) {
    childrenMap[key.name] = key
  }

  fun getOrPut(name: String, newKey: NamedNode): NamedNode {
    val key = childrenMap[name]
    if (key == null) {
      childrenMap[name] = newKey
      return newKey
    }
    return key
  }

  fun getChild(name: String): NamedNode? = childrenMap[name]
}

class DeclarativeCompletionContributor : CompletionContributor() {
  init {
    if (Registry.`is`("android.gradle.declarative.plugin.studio.support")) {
      extend(CompletionType.BASIC,
             INSIDE_TABLE_HEADER,
             object : CompletionProvider<CompletionParameters>() {
               override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                 val originalFile = parameters.originalFile as? TomlFile ?: return
                 val existingKeys = getDeclaredKeys(originalFile)
                 val segment = parameters.position.parent as? TomlKeySegment ?: return
                 val path = generateExistingPath(segment, false)
                 result.addAllElements(getSuggestions(path, existingKeys).map {
                   val element = LookupElementBuilder.create(it.name)
                     .withTypeText(it.type.str, null, true)
                   when (it.type) {
                     GENERIC_PROPERTY, STRING, INTEGER, BOOLEAN, STRING_ARRAY -> element.withInsertHandler(extractFromTable(it.type))
                     ARRAY_TABLE -> element.withInsertHandler(insertArrayTable())
                     else -> element
                   }
                 })
               }
             }
      )
      extend(CompletionType.BASIC,
             INSIDE_SIMPLE_KEY,
             object : CompletionProvider<CompletionParameters>() {
               override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                 val segment = parameters.position.parent as? TomlKeySegment ?: return
                 val path = generateExistingPath(segment, false)
                 val originalFile = parameters.originalFile as? TomlFile ?: return
                 val existingKeys = getDeclaredKeys(originalFile)
                 result.addAllElements(getSuggestions(path, existingKeys).map {
                   val element = LookupElementBuilder.create(it.name)
                     .withTypeText(it.type.str, null, true)
                   when (it.type) {
                     GENERIC_PROPERTY, STRING, BOOLEAN, INTEGER, STRING_ARRAY -> element.withInsertHandler(insertProperty(it.type))
                     ARRAY_TABLE -> element.withInsertHandler(insertArrayTable())
                     else -> element
                   }
                 })
               }
             }
      )
    }
  }

  private fun extractFromTable(type: ElementType): InsertHandler<LookupElement?> =
    InsertHandler { context: InsertionContext, item: LookupElement ->
      val editor = context.editor
      val document = editor.document
      context.commitDocument()
      val inserted = item.lookupString

      // delete inserted string including dot
      val startInserted = context.tailOffset - inserted.length
      document.deleteString(startInserted - 1, context.tailOffset)

      // insert lookupString after new line
      var newOffset = document.text.indexOf("\n", startInserted - 1)
      if (newOffset == -1) {
        newOffset = document.text.length
      }
      document.insertString(newOffset, "\n" + inserted)

      val offsetAfterInsertion = newOffset + inserted.length + 1
      editor.caretModel.moveToOffset(offsetAfterInsertion)
      context.tailOffset = offsetAfterInsertion

      insertProperty(type).handleInsert(context, item)
    }

  /**
   * Insert handler adds double square brackets if needed.
   * Handler detects single brackets and append second to the existing ones.
   * Analysis happens withing single line of where editing is done.
   */
  private fun insertArrayTable(): InsertHandler<LookupElement?> =
    InsertHandler { context: InsertionContext, _: LookupElement ->
      val editor = context.editor
      val document = editor.document
      context.commitDocument()

      val text = document.text
      // Here we extract line of where suggestion happened.
      // Boundaries are \n symbol or start/end of the file
      val lineStart = text.substring(0, context.tailOffset).indexOf("\n") + 1
      // Line end is \n symbol or comment symbol
      val lineEnd = "[\\n#]".toRegex().find(text)?.range?.start ?: text.length
      val currLine = text.substring(lineStart, lineEnd)

      val lineStartNoWhitespaces = currLine.indexOfFirst { !it.isWhitespace() }

      if (!currLine.startsWith("[[", lineStartNoWhitespaces)) {
        if (currLine.startsWith("[", lineStartNoWhitespaces)) {
          document.insertString(lineStart + lineStartNoWhitespaces, "[")
        }
        else {
          document.insertString(lineStart + lineStartNoWhitespaces, "[[")
        }
      }

      if (!currLine.contains("]]")) {
        // if inserted string is at the very end of the file - in this case tailOffset is bigger than doc size
        if (context.tailOffset < document.text.length &&
            document.text[context.tailOffset] == ']') {
          document.insertString(context.tailOffset, "]")
        }
        else {
          // or adding ]] at the end of inserted string (tailOffset updates itself after we, maybe, inserted [[)
          document.insertString(context.tailOffset, "]]")
        }
      }

      val newOffset = document.text.indexOf("\n", context.tailOffset).takeIf { it > -1 } ?: document.text.length
      //inserting new line symbol with caret right before the end of current line
      document.insertString(newOffset, "\n")
      editor.caretModel.moveToOffset(newOffset + 1)
    }

  private fun insertProperty(type: ElementType): InsertHandler<LookupElement?> =
    InsertHandler { context: InsertionContext, _: LookupElement ->
      val editor = context.editor
      val document = editor.document
      context.commitDocument()
      when(type){
        STRING -> {
          document.insertString(context.tailOffset, " = \"\"")
          editor.caretModel.moveToOffset(context.tailOffset - 1)
        }
        STRING_ARRAY -> {
          document.insertString(context.tailOffset, " = [\"\"]")
          editor.caretModel.moveToOffset(context.tailOffset - 2)
        }
        else -> {
          document.insertString(context.tailOffset, " = ")
          editor.caretModel.moveToOffset(context.tailOffset)
        }
      }
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
    var currentModel: GradlePropertiesDslElementSchema = GradleBuildFile.BuildGradlePropertiesDslElementSchema()
    var currentNode: NamedNode? = rootNode

    path.forEach { element ->
      currentNode = currentNode?.getChild(element)
      val blockElement = currentModel.getBlockElementDescription(DECLARATIVE_TOML, element) ?: return listOf()
      currentModel = blockElement.schemaConstructor.construct()
    }
    val result = mutableListOf<Suggestion>()
    result += currentModel.getBlockElementDescriptions(DECLARATIVE_TOML).map {
      Suggestion(it.key,
                 if(isArrayBlock(it.value)) ARRAY_TABLE else BLOCK
      )
    }
    result += currentModel.getPropertiesInfo(DECLARATIVE_TOML).entrySet
      .filterNot { currentNode?.children?.contains(it.surfaceSyntaxDescription.name) ?: false }
      .map {
        val propertyDescription = it.modelEffectDescription.property
        Suggestion(it.surfaceSyntaxDescription.name, propertyDescription.transformToSuggestionType())
      }
    return result
  }

  private fun isArrayBlock(description: PropertiesElementDescription<*>):Boolean =
    GradleDslElementList::class.java.isAssignableFrom(description.clazz)

  private fun ModelPropertyDescription.transformToSuggestionType(): ElementType {
    return when (this.type) {
      ModelPropertyType.MUTABLE_LIST, ModelPropertyType.MUTABLE_SET -> STRING_ARRAY
      ModelPropertyType.STRING -> STRING
      ModelPropertyType.BOOLEAN -> BOOLEAN
      ModelPropertyType.NUMERIC -> INTEGER
      // TODO -  need to handle map type
      else -> GENERIC_PROPERTY
    }
  }

}

class EnableAutoPopupInDeclarativeBuildCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = when {
    INSIDE_SIMPLE_KEY.accepts(contextElement) || INSIDE_TABLE_HEADER.accepts(contextElement) -> ThreeState.NO
    else -> ThreeState.UNSURE
  }
}