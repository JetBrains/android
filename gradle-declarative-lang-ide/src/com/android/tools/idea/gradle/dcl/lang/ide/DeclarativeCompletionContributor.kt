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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.BLOCK
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.BOOLEAN
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.ENUM
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY_BLOCK
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY_VALUE
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.INTEGER
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.LONG
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.PROPERTY
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.STRING
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.INTEGER_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LONG_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_INTEGER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_LONG
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBare
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlockGroup
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifierOwner
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeSimpleFactory
import com.android.tools.idea.gradle.dcl.lang.sync.BuildDeclarativeSchemas
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.Entry
import com.android.tools.idea.gradle.dcl.lang.sync.PlainFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleDataType
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentInFile
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.nextLeaf
import com.intellij.psi.util.prevLeafs
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import kotlin.math.max

private val declarativeFlag = object : PatternCondition<PsiElement>(null) {
  override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean =
    DeclarativeIdeSupport.isEnabled()
}

private val afterSimpleFactory = object : PatternCondition<PsiElement>(null) {
  override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean {
    var sawDot = false
    for (leaf in element.prevLeafs) {
      if (psiElement().whitespaceCommentOrError().accepts(leaf))
        continue
      if (leaf.text == ".") {
        sawDot = true
      }
      else if (sawDot && leaf.text == ")" && leaf.parent is DeclarativeSimpleFactory) {
        return true
      }
      else {
        return false
      }
    }
    return false
  }
}

private val DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .andOr(
      psiElement().withParent(DeclarativeIdentifier::class.java),
      psiElement().withParent(DeclarativeBlockGroup::class.java),
      psiElement().withParent(DeclarativeFile::class.java),
    ).andNot(psiElement().withText("{"))
    .andNot(psiElement().afterLeafSkipping(
      psiElement().whitespace(),
      psiElement().withText("=")
    ))
    .andNot(psiElement().afterLeafSkipping(
      psiElement().whitespace(),
      psiElement().withText(".")
    ))

private val DECLARATIVE_ASSIGN_VALUE_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .withParents(DeclarativeIdentifier::class.java, DeclarativeBare::class.java, DeclarativeAssignment::class.java)
    .afterLeafSkipping(
      psiElement().whitespace(),
      psiElement().withText("=")
    )

private val AFTER_PROPERTY_DOT_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .withParent(DeclarativeFile::class.java)
    .afterLeafSkipping(
      psiElement().whitespace(),
      psiElement().withText(".")
    )
    .afterLeafSkipping(
      psiElement().andOr(psiElement().whitespace(), psiElement().withText(".")),
      psiElement().withText("rootProject")
    )

private val AFTER_FUNCTION_DOT_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .with(afterSimpleFactory)
    .withParent(DeclarativeFile::class.java)

private val AFTER_NUMBER_LITERAL: ElementPattern<PsiElement> =
  psiElement().afterLeafSkipping(
    psiElement().withText(""),
    psiElement().withElementType(PsiJavaPatterns.elementType().oneOf(INTEGER_LITERAL, LONG_LITERAL, UNSIGNED_INTEGER, UNSIGNED_LONG))
  )

private val AFTER_ASSIGNMENT_IDENTIFIER: ElementPattern<PsiElement> =
  psiElement().beforeLeafSkipping(
    psiElement().whitespace(),
    psiElement().withText("=")
  ).afterLeafSkipping(
    psiElement().whitespace(),
    psiElement().withParent(DeclarativeIdentifier::class.java)
  )

private val AFTER_BLOCK_IDENTIFIER: ElementPattern<PsiElement> =
  psiElement().beforeLeafSkipping(
    psiElement().whitespace(),
    psiElement().withText("{")
  ).afterLeafSkipping(
    psiElement().whitespace(),
    psiElement().withParent(DeclarativeIdentifier::class.java)
  )

private data class Suggestion(val name: String, val type: ElementType)

class DeclarativeCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN, createCompletionProvider())
    extend(CompletionType.BASIC, DECLARATIVE_ASSIGN_VALUE_SYNTAX_PATTERN, createAssignValueCompletionProvider())
    extend(CompletionType.BASIC, AFTER_PROPERTY_DOT_SYNTAX_PATTERN, createRootProjectCompletionProvider())
    extend(CompletionType.BASIC, AFTER_FUNCTION_DOT_SYNTAX_PATTERN, createPluginCompletionProvider())
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file is DeclarativeFile) {
      val offset = context.startOffset
      val psiFile = context.file
      val token = psiFile.findElementAt(max(0, offset-1))
      if (token != null && (AFTER_ASSIGNMENT_IDENTIFIER.accepts(token) || AFTER_BLOCK_IDENTIFIER.accepts(token))) {
        context.dummyIdentifier = "" // do not insert dummy identifier before = in assignment or {
      }
      else
        context.dummyIdentifier = CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
    }
  }

  private fun PsiElement.beforeElement(text: String) =
    this.skipWhitespaces()?.text == text

  override fun fillCompletionVariants(parameters: CompletionParameters, _result: CompletionResultSet) {
    val position = parameters.position
    if (AFTER_NUMBER_LITERAL.accepts(position)) {
      _result.stopHere()
      return
    }
    super.fillCompletionVariants(parameters, _result)
  }

  private fun createCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return

        val parent = parameters.position.parent
        val adjustedParent = if (parent is DeclarativeIdentifier && parent.parent is DeclarativeBlock) parent.parent else parent
        result.addAllElements(getSuggestionList(adjustedParent, schema).map { (entry, suggestion) ->
          LookupElementBuilder.create(suggestion.name)
            .withTypeText(suggestion.type.str, null, true)
            .withInsertHandler(smartInsert(suggestion, adjustedParent, entry, schema))
        })
      }
    }
  }

  private fun createAssignValueCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return

        val identifier = parameters.position.findParentOfType<DeclarativeAssignment>()?.identifier ?: return
        var suggestions = getEnumList(identifier, schema)
        if(suggestions.isEmpty()){
          suggestions = getRootFunctions(identifier, schema).map { Suggestion(it.name, FACTORY) }
        }
        result.addAllElements(suggestions.map {
          LookupElementBuilder.create(it.name)
            .withTypeText(it.type.str, null, true)
            .withInsertHandler(insert(it.type))
        })
      }
    }
  }

  private fun createRootProjectCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        result.addElement(
          LookupElementBuilder.create("name")
            .withTypeText(STRING.str, null, true)
            .withInsertHandler(insert(STRING))
        )
      }
    }
  }

  private fun createPluginCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return
        findPreviousSimpleFunction(parameters.position)?.let { parent ->
          result.addAllElements(
            getSuggestionList(parent, schema, true).filter { it.first is SchemaFunction }
              .map {
                val suggestion = it.second
                LookupElementBuilder.create(suggestion.name)
                  .withTypeText(suggestion.type.name, null, true)
                  .withInsertHandler(insert(suggestion.type))
              })
        }
      }
    }
  }

  private fun findPreviousSimpleFunction(position: PsiElement): DeclarativeSimpleFactory? {
    for (leaf in position.prevLeafs) {
      if (psiElement().whitespaceCommentOrError().accepts(leaf) || leaf.text == ".")
        continue
      else if (leaf.text == ")" && leaf.parent is DeclarativeSimpleFactory) {
        return leaf.parent as DeclarativeSimpleFactory
      }
      else return null
    }
    return null
  }

  private fun PsiElement.findParentNamedBlock() = findParentInFile(false) { it is DeclarativeBlock || it is DeclarativeFile }

  private fun insert(type: ElementType):InsertHandler<LookupElement?> = InsertHandler { context: InsertionContext, _: LookupElement ->
    val editor = context.editor
    val document = editor.document
    val file = editor.virtualFile.toPsiFile(context.project)
    val offset = editor.caretModel.offset
    val element = file?.findElementAt(offset)
    context.commitDocument()
    when (type) {
      STRING -> {
        if (element?.skipWhitespaces()?.nextLeaf(true)?.text == "=") return@InsertHandler
        document.insertString(context.tailOffset, " = \"\"")
        editor.caretModel.moveToOffset(context.tailOffset - 1)
      }

      BLOCK -> {
        if (element?.beforeElement("{") == true) return@InsertHandler
        val text = document.text
        val lineStartOffset = text.substring(0, context.tailOffset).indexOfLast { it == '\n' } + 1
        val whiteSpace = " ".repeat(context.startOffset - lineStartOffset)

        document.insertString(context.tailOffset, " {\n$whiteSpace  \n$whiteSpace}")
        editor.caretModel.moveToOffset(context.tailOffset - whiteSpace.length - 2)
      }

      FACTORY -> {
        if (element?.beforeElement("(") == true) return@InsertHandler
        document.insertString(context.tailOffset, "()")
        editor.caretModel.moveToOffset(context.tailOffset - 1)
      }

      FACTORY_BLOCK -> {
        if (element?.beforeElement("(") == true ) return@InsertHandler
        document.insertString(context.tailOffset, "(){ }")
        editor.caretModel.moveToOffset(context.tailOffset - 4)
      }

      INTEGER, LONG, BOOLEAN, ENUM -> {
        if (element?.beforeElement("=") == true ) return@InsertHandler
        document.insertString(context.tailOffset, " = ")
        editor.caretModel.moveToOffset(context.tailOffset)
      }

      else -> editor.caretModel.moveToOffset(context.tailOffset)
    }
  }

  private fun smartInsert(suggestion: Suggestion, parent: PsiElement, entry: Entry?, schemas: BuildDeclarativeSchemas): InsertHandler<LookupElement?> = InsertHandler { context: InsertionContext, item: LookupElement ->
    val editor = context.editor
    val document = editor.document
    val file = editor.virtualFile.toPsiFile(context.project)
    val offset = editor.caretModel.offset
    val element = file?.findElementAt(offset)
    context.commitDocument()
    when (suggestion.type) {
      // it's only one value here - url = uri("|")
      FACTORY_VALUE -> {
        if (element?.skipWhitespaces()?.nextLeaf(true)?.text == "=") return@InsertHandler
        val rootFunctions = getRootFunctions(parent, schemas)
          .filter { (it.semantic as? PlainFunction)?.returnValue == (entry as? DataProperty)?.valueType }

        if (rootFunctions.size == 1) {
          val function = rootFunctions.first()
          if (function.parameters.size == 1 && function.parameters.first().type == SimpleTypeRef(SimpleDataType.STRING)) {
            document.insertString(context.tailOffset, " = ${function.name}(\"\")")
            editor.caretModel.moveToOffset(context.tailOffset - 2)
          }
        }
      }
      // rootProject completion
      PROPERTY -> {
        val nextLeafText = element?.skipWhitespaces()?.nextLeaf(true)?.text
        if (nextLeafText == "=" || nextLeafText == ".") return@InsertHandler
        val path = getPath(parent, false) + suggestion.name
        val nextSuggestion = getSuggestionEntries(path, parent.containingFile.name, schemas)
        if (nextSuggestion.size == 1) {
          val nextEntry = nextSuggestion.first()
          (nextEntry as? DataProperty)?.let {
            if (it.valueType == SimpleTypeRef(SimpleDataType.STRING)) {
              document.insertString(context.tailOffset, ".${it.name} = \"\"")
              editor.caretModel.moveToOffset(context.tailOffset - 1)
            }
            else {
              document.insertString(context.tailOffset, ".${it.name} = ")
            }

          }
        }
        else {
          document.insertString(context.tailOffset, ".")
        }
      }

      else -> insert(suggestion.type).handleInsert(context, item)
    }
  }

  private fun PsiElement.skipWhitespaces(): PsiElement? {
    var nextLeaf:PsiElement? = this
    while (nextLeaf != null && nextLeaf is PsiWhiteSpace) {
      nextLeaf = PsiTreeUtil.nextLeaf(nextLeaf, true)
    }
    return nextLeaf
  }

  private fun Entry.toSuggestionPair(rootFunction: List<PlainFunction>) = this to Suggestion(simpleName, getType(this, rootFunction))

  private fun getEnumList(identifier: DeclarativeIdentifier, schemas: BuildDeclarativeSchemas): List<Suggestion> {
    val suggestions = getSuggestionEntries(identifier, schemas)
    val rootFunctions = getRootPlainFunctions(identifier, schemas)
    val enum = suggestions.find { it.simpleName == identifier.name && getType(it, rootFunctions) == ENUM }
    return getEnumConstants(enum).map { Suggestion(it, ElementType.ENUM_CONSTANT) }
  }

  private fun getSuggestionEntries(parent: PsiElement, schemas: BuildDeclarativeSchemas, includeCurrent: Boolean = false): List<Entry> {
    val path = getPath(parent, includeCurrent)
    val fileName = parent.containingFile.name
    return getSuggestionEntries(path, fileName, schemas)
  }

  private fun getSuggestionEntries(path: List<String>, fileName: String, schemas: BuildDeclarativeSchemas): List<Entry> {
    // TODO fix case for settings root - need to get InternalSettings
    if (path.isEmpty()) return schemas.getTopLevelEntries(fileName)
    var index = 0
    var receivers: List<Entry> = schemas.getTopLevelEntriesByName(path[index], fileName)
    while (index < path.size - 1) {
      index += 1
      receivers = receivers.flatMap { it.getNextLevel(path[index]) }
    }
    receivers = receivers.flatMap { it.getNextLevel() }
    return receivers.distinct()
  }

  private fun getSuggestionList(parent: PsiElement, schemas: BuildDeclarativeSchemas, includeCurrent: Boolean = false): List<Pair<Entry,Suggestion>> =
    getSuggestionEntries(parent, schemas, includeCurrent).map { it.toSuggestionPair(getRootPlainFunctions(parent, schemas)) }.distinct()

  // create path - list of identifiers from root element to parent
  private fun getPath(parent: PsiElement, includeCurrent: Boolean): List<String> {
    if (parent is DeclarativeFile) return listOf()
    val result = mutableListOf<String>()
    var current = if (includeCurrent)
      (parent as? DeclarativeIdentifierOwner) ?: parent.findParentNamedBlock()
    else parent.findParentNamedBlock()
    // to go bubble up through all elements with name
    while (current != null && current.parent != null && current is DeclarativeIdentifierOwner) {
      current.identifier.name?.let { result.add(it) }
      current = current.parent.parent
    }
    return result.reversed()
  }
}

class EnableAutoPopupInDeclarativeCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    return if (DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN.accepts(contextElement) ||
               DECLARATIVE_ASSIGN_VALUE_SYNTAX_PATTERN.accepts(contextElement)) ThreeState.NO
    else ThreeState.UNSURE
  }
}