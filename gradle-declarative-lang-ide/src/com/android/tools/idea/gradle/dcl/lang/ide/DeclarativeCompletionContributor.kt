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
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.EXPRESSION
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY_BLOCK
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.OBJECT_VALUE
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.INTEGER
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.LONG
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.PROPERTY
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.STRING
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.INTEGER_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LONG_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_INTEGER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_LONG
import com.android.tools.idea.gradle.dcl.lang.psi.AssignmentType.APPEND
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeArgument
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBare
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlockGroup
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifierOwner
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeQualified
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeSimpleFactory
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRefWithTypes

import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.DataTypeReference
import com.android.tools.idea.gradle.dcl.lang.sync.Entry
import com.android.tools.idea.gradle.dcl.lang.sync.Named
import com.android.tools.idea.gradle.dcl.lang.sync.PlainFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaMemberFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleDataType
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.android.tools.idea.gradle.dcl.lang.sync.AugmentationKind
import com.android.tools.idea.gradle.dcl.lang.sync.FullName
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
import com.intellij.psi.codeStyle.CodeStyleManager
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
import com.intellij.psi.util.prevLeaf
import com.intellij.psi.util.prevLeafs
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.gradle.declarative.dsl.schema.FqName
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

private val factoryArgument = object : PatternCondition<PsiElement>(null) {
  override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean {
    val grandParent = element.parent?.parent?.parent
    return grandParent is DeclarativeArgument
  }
}

private val LEFT_ELEMENT_ASSIGNMENT:ElementPattern<LeafPsiElement> = psiElement(LeafPsiElement::class.java).andOr(
  psiElement().afterLeafSkipping(
    psiElement().whitespace(),
    psiElement().withText("=")
  ),
  psiElement().afterLeafSkipping(
    psiElement().whitespace(),
    psiElement().withText("+=")
  )
)

private val DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .andOr(
      psiElement().withParent(DeclarativeIdentifier::class.java),
      psiElement().withParent(DeclarativeBlockGroup::class.java),
      psiElement().withParent(DeclarativeFile::class.java),
    ).andNot(psiElement().withText("{"))
    .andNot(LEFT_ELEMENT_ASSIGNMENT)
    .andNot(psiElement().afterLeafSkipping(
      psiElement().whitespace(),
      psiElement().withText(".")
    ))
    // rule does not work for function parameters
    .andNot(psiElement().with(factoryArgument))

private val DECLARATIVE_ASSIGN_VALUE_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .withParents(DeclarativeIdentifier::class.java, DeclarativeBare::class.java, DeclarativeAssignment::class.java)
    .and(LEFT_ELEMENT_ASSIGNMENT)

private val DECLARATIVE_FACTORY_ARGUMENT_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .with(factoryArgument)
    // not inside property
    .andNot(psiElement().withParents(DeclarativeIdentifier::class.java, DeclarativeQualified::class.java))

private val AFTER_PROPERTY_DOT_ASSIGNABLE_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .withParent(DeclarativeFile::class.java)
    .afterLeafSkipping(
      psiElement().whitespace(),
      psiElement().withText(".")
    )
    .afterLeafSkipping(
      psiElement().andOr(psiElement().whitespace(), psiElement().withText(".")),
      psiElement().withText("rootProject") // rootProject remains only root object with assignable properties
    )

private val AFTER_PROPERTY_DOT_SYNTAX_PATTERN: PsiElementPattern.Capture<LeafPsiElement> =
  psiElement(LeafPsiElement::class.java)
    .with(declarativeFlag)
    .withParents(DeclarativeIdentifier::class.java, DeclarativeQualified::class.java)

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
    extend(CompletionType.BASIC, DECLARATIVE_FACTORY_ARGUMENT_SYNTAX_PATTERN, createFactoryArgumentCompletionProvider())
    extend(CompletionType.BASIC, AFTER_PROPERTY_DOT_ASSIGNABLE_SYNTAX_PATTERN, createRootProjectCompletionProvider())
    extend(CompletionType.BASIC, AFTER_PROPERTY_DOT_SYNTAX_PATTERN, createPropertyCompletionProvider())
    extend(CompletionType.BASIC, AFTER_FUNCTION_DOT_SYNTAX_PATTERN, createPluginCompletionProvider())
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file is DeclarativeFile) {
      val offset = context.startOffset
      val psiFile = context.file
      val token = psiFile.findElementAt(max(0, offset - 1))
      if (token != null && (AFTER_ASSIGNMENT_IDENTIFIER.accepts(token) || AFTER_BLOCK_IDENTIFIER.accepts(token))) {
        context.dummyIdentifier = "" // do not insert dummy identifier before = in assignment or {
      }
      else
        context.dummyIdentifier = CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
    }
  }

  private fun PsiElement.beforeElement(text: String) =
    this.skipWhitespaces()?.text == text

  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    val position = parameters.position
    if (AFTER_NUMBER_LITERAL.accepts(position)) {
      resultSet.stopHere()
      return
    }
    super.fillCompletionVariants(parameters, resultSet)
  }

  private fun createCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return

        // if user edit existing assignment - we don't need to add += with augmentations
        val includeAugmentation = !isLeftHandAssignment(parameters.position)
        val parent = parameters.position.parent
        val adjustedParent = if (parent is DeclarativeIdentifier && parent.parent is DeclarativeBlock) parent.parent else parent
        var suggestionList = getSuggestionList(adjustedParent, schema)
        if (includeAugmentation) {
          suggestionList = updateSuggestionListWithAugmentations(suggestionList, schema, parent)
        }
        result.addAllElements(suggestionList.map { (entry, suggestion) ->
          LookupElementBuilder.create(suggestion.name)
            .withTypeText(suggestion.type.str, null, true)
            .withInsertHandler(smartInsert(suggestion, adjustedParent, entry, schema))
        })
      }
    }
  }

  private fun isLeftHandAssignment(element: PsiElement): Boolean {
    val text = element.nextLeaf(true)?.skipWhitespaces()?.text
    return text == "+=" || text == "="
  }

  private fun isAppendValue(element: PsiElement): Boolean {
    val assignment = element.findParentOfType<DeclarativeAssignment>()
    return assignment?.assignmentType == APPEND
  }

  private fun createPropertyCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return

        val element = parameters.position.parent
        val suggestions = getSuggestionList(element, schema).map{ it.second }
        addSimpleSuggestions(result, suggestions)
      }
    }
  }

  private fun createAssignValueCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return

        val element = parameters.position
        // do not show any suggestions if += is used for inappropriate property
        if (isAppendValue(element)) {
          element.findParentOfType<DeclarativeAssignment>()?.let { assignment ->
            val hasAppendAugmentation = getSuggestionList(assignment.parent, schema)
              .filter { it.second.name == assignment.identifier.name }
              .any {
                val list = schema.getAugmentedTypes(element.containingFile.name)[it.first.getFqName()]
                list?.any { it == AugmentationKind.PLUS } == true
              }
            if (!hasAppendAugmentation) return
          }
        }

        val identifier = element.findParentOfType<DeclarativeAssignment>()?.identifier ?: return
        var suggestions = getMaybeEnumList(identifier, schema) + getMaybeBooleanList(identifier, schema)
        if (suggestions.isEmpty()) {
          suggestions = getRootFunctions(identifier, schema).map { Suggestion(it.name, FACTORY) } +
                        getRootProperties(identifier, schema). map { Suggestion (it.name, PROPERTY)}

        }
        addSimpleSuggestions(result, suggestions)
      }
    }
  }

  private fun createFactoryArgumentCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val schema = DeclarativeService.getInstance(project).getDeclarativeSchema() ?: return

        val element = parameters.position
        val suggestions = getRootFunctions(element, schema).map { Suggestion(it.name, FACTORY) } +
                        getRootProperties(element, schema). map { Suggestion (it.name, PROPERTY)}
        addSimpleSuggestions(result, suggestions)
      }
    }
  }

  private fun addSimpleSuggestions(
    result: CompletionResultSet,
    suggestions: List<Suggestion>
  ) {
    result.addAllElements(suggestions.map {
      LookupElementBuilder.create(it.name)
        .withTypeText(it.type.str, null, true)
        .withInsertHandler(insertAssignmentValue(it.type))
    })
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
            getSuggestionList(parent, schema, true).filter { it.first is SchemaMemberFunction }
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

  private fun insert(type: ElementType): InsertHandler<LookupElement?> = InsertHandler { context: InsertionContext, _: LookupElement ->
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

        document.insertString(context.tailOffset, " {\n$whiteSpace\n$whiteSpace}")
        val newOffset = context.tailOffset - whiteSpace.length - 2 // -2 is to skip both chars "\n}"
        editor.caretModel.moveToOffset(newOffset)
        CodeStyleManager.getInstance(context.project).adjustLineIndent(document, newOffset)
      }

      FACTORY -> {
        if (element?.beforeElement("(") == true) return@InsertHandler
        document.insertString(context.tailOffset, "()")
        editor.caretModel.moveToOffset(context.tailOffset - 1)
      }

      FACTORY_BLOCK -> {
        if (element?.beforeElement("(") == true) return@InsertHandler
        document.insertString(context.tailOffset, "(){ }")
        editor.caretModel.moveToOffset(context.tailOffset - 4)
      }

      INTEGER, LONG, BOOLEAN, ENUM -> {
        if (element?.beforeElement("=") == true) return@InsertHandler
        document.insertString(context.tailOffset, " = ")
        editor.caretModel.moveToOffset(context.tailOffset)
      }

      else -> editor.caretModel.moveToOffset(context.tailOffset)
    }
  }

  private fun insertAssignmentValue(type: ElementType): InsertHandler<LookupElement?> = InsertHandler { context: InsertionContext, _: LookupElement ->
    context.editor.run {
      val file = virtualFile.toPsiFile(context.project)
      val element = file?.findElementAt(caretModel.offset)
      context.commitDocument()
      when (type) {
        FACTORY -> {
          if (element?.beforeElement("(") == true) return@InsertHandler
          document.insertString(context.tailOffset, "()")
          caretModel.moveToOffset(context.tailOffset - 1)
        }
        else -> caretModel.moveToOffset(context.tailOffset)
      }
    }
  }

  private fun smartInsert(suggestion: Suggestion,
                          parent: PsiElement,
                          entry: Entry?,
                          schemas: BuildDeclarativeSchemas): InsertHandler<LookupElement?> = InsertHandler { context: InsertionContext, item: LookupElement ->
    val editor = context.editor
    val document = editor.document
    val file = editor.virtualFile.toPsiFile(context.project)
    val offset = editor.caretModel.offset
    val element = file?.findElementAt(offset)
    context.commitDocument()
    when (suggestion.type) {
      // object type property
      OBJECT_VALUE -> {
        if (element?.skipWhitespaces()?.nextLeaf(true)?.text == "=") return@InsertHandler
        val rootFunctions = getRootFunctions(parent, schemas).distinct()
          .filter {
            // reason is that function type has generic type T and data property has concrete type argument (like String)
            (it.semantic as? PlainFunction)?.returnValue?.compareIgnoringGeneric((entry as? DataProperty)?.valueType) == true
          }

        if (rootFunctions.size == 1) {
          val function = rootFunctions.first()
          if (function.parameters.size == 1 && function.parameters.first().type == SimpleTypeRef(SimpleDataType.STRING)) {
            document.insertString(context.tailOffset, " = ${function.name}(\"\")")
            editor.caretModel.moveToOffset(context.tailOffset - 2)
          }
          else {
            // single function but with unknown parameter(s)
            document.insertString(context.tailOffset, " = ${function.name}()")
            editor.caretModel.moveToOffset(context.tailOffset - 1)
          }
        }
        else {
          document.insertString(context.tailOffset, " = ")
          editor.caretModel.moveToOffset(context.tailOffset)
        }
      }
      // rootProject completion
      PROPERTY -> {
        val nextLeafText = element?.skipWhitespaces()?.nextLeaf(true)?.text
        if (nextLeafText == "=" || nextLeafText == ".") return@InsertHandler
        val path = getPath(parent, false) + suggestion.name
        val nextSuggestion = getSuggestionEntries(path, parent.containingFile.name, schemas)
        if (nextSuggestion.size == 1) {
          val nextEntry = nextSuggestion.first().entry
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
      EXPRESSION -> {
        // if expression ends with function call - need to insert cursor between ()
        if (element?.prevLeaf(true)?.text == ")")
          editor.caretModel.moveToOffset(context.tailOffset - 1)
      }
      else -> insert(suggestion.type).handleInsert(context, item)
    }
  }

  private fun DataTypeReference.compareIgnoringGeneric(other: DataTypeReference?): Boolean {
    if (this.javaClass != other?.javaClass) return false
    return when (this) {
      is DataClassRefWithTypes -> fqName == (other as? DataClassRefWithTypes)?.fqName
      else -> this == other
    }
  }

  private fun PsiElement.skipWhitespaces(): PsiElement? {
    var nextLeaf: PsiElement? = this
    while (nextLeaf != null && nextLeaf is PsiWhiteSpace) {
      nextLeaf = PsiTreeUtil.nextLeaf(nextLeaf, true)
    }
    return nextLeaf
  }


  private fun EntryWithContext.toSuggestionPair(rootFunction: List<PlainFunction>) =
    this.entry to Suggestion(entry.simpleName, getType(this, rootFunction))

  private fun getMaybeEnumList(identifier: DeclarativeIdentifier, schemas: BuildDeclarativeSchemas): List<Suggestion> {
    val suggestions = getSuggestionEntries(identifier, schemas)
    val rootFunctions = getRootPlainFunctions(identifier, schemas)
    val enum = suggestions.find { it.entry.simpleName == identifier.name && getType(it, rootFunctions) == ENUM }
    return getEnumConstants(enum).map { Suggestion(it, ElementType.ENUM_CONSTANT) }
  }

  private fun getMaybeBooleanList(identifier: DeclarativeIdentifier, schemas: BuildDeclarativeSchemas): List<Suggestion> {
    val suggestions = getSuggestionEntries(identifier, schemas)
    val rootFunctions = getRootPlainFunctions(identifier, schemas)
    return if (suggestions.any { it.entry.simpleName == identifier.name && getType(it, rootFunctions) == BOOLEAN })
      listOf(Suggestion("true", BOOLEAN), Suggestion("false", BOOLEAN))
    else
      listOf()
  }

  private fun getSuggestionEntries(parent: PsiElement,
                                   schemas: BuildDeclarativeSchemas,
                                   includeCurrent: Boolean = false): List<EntryWithContext> {
    val path = getPath(parent, includeCurrent)
    val fileName = parent.containingFile.name
    return getSuggestionEntries(path, fileName, schemas)
  }

  private fun getSuggestionEntries(path: List<String>, fileName: String, schemas: BuildDeclarativeSchemas): List<EntryWithContext> {
    // TODO fix case for settings root - need to get InternalSettings
    if (path.isEmpty()) return schemas.getTopLevelEntries(fileName)
    var index = 0
    var receivers: List<EntryWithContext> = schemas.getTopLevelEntriesByName(path[index], fileName)
    while (index < path.size - 1) {
      index += 1
      receivers = receivers.flatMap { it.getNextLevel(path[index]) }
    }
    receivers = receivers.flatMap { it.getNextLevel() }
    return receivers.distinctBy { it.entry }
  }

  // update suggestions with possible += expressions
  private fun updateSuggestionListWithAugmentations(pairs: List<Pair<Entry, Suggestion>>,
                                                    schemas: BuildDeclarativeSchemas,
                                                    parent: PsiElement): List<Pair<Entry, Suggestion>> =
    pairs.flatMap { entry -> updateSuggestionPair(entry,schemas,parent) }

  private fun updateSuggestionPair(pair: Pair<Entry, Suggestion>, schemas: BuildDeclarativeSchemas,
                                   parent: PsiElement): List<Pair<Entry, Suggestion>> {
    val first = pair.first
    val fqName = first.getFqName() ?: return listOf(pair)
    val augmentedList = schemas.getAugmentedTypes(parent.containingFile.name)[fqName]
    if (augmentedList?.any { it == AugmentationKind.PLUS } == true) {
      val rootFunctions = getRootFunctions(parent, schemas).distinct()
        .filter {
          // reason is that function type has generic type T and data property has concrete type argument (like String)
          (it.semantic as? PlainFunction)?.returnValue?.compareIgnoringGeneric((first as DataProperty).valueType) == true
        }
      if (rootFunctions.size == 1) {
        val function = rootFunctions.first()
        return listOf(first to Suggestion("${pair.second.name} = ${function.name}()", EXPRESSION),
                      first to Suggestion("${pair.second.name} += ${function.name}()", EXPRESSION))

      }


    }
    return listOf(pair)
  }

  private fun Entry.getFqName(): FullName? {
    if (this is DataProperty) {
      val type = valueType
      if (type is Named) return type.fqName
    }
    return null
  }

  private fun getSuggestionList(parent: PsiElement,
                                schemas: BuildDeclarativeSchemas,
                                includeCurrent: Boolean = false): List<Pair<Entry, Suggestion>> =
    getSuggestionEntries(parent, schemas, includeCurrent).map { it.toSuggestionPair(getRootPlainFunctions(parent, schemas)) }.distinct()

  // create path - list of identifiers from root element to parent
  private fun getPath(parent: PsiElement, includeCurrent: Boolean): List<String> {
    if (parent is DeclarativeFile) return listOf()
    val result = mutableListOf<String>()
    // try handle property
    tryParsePropertyPath(parent, includeCurrent)?.let { return it }
    var current = if (includeCurrent)
      (parent as? DeclarativeIdentifierOwner) ?: parent.findParentNamedBlock()
    else parent.findParentNamedBlock()
    // to go bubble up through all elements with name
    while (current != null && current.parent != null) {
      // iterate through identifier owners but skip factory as a wrapper
      if (current is DeclarativeIdentifierOwner)
        current.identifier.name?.let { result.add(it) }
      current = current.parent
    }
    return result.reversed()
  }

  // return null if not property case
  private fun tryParsePropertyPath(parent: PsiElement, includeCurrent: Boolean): List<String>? {
    if (parent.parent is DeclarativeQualified) {
      val result = mutableListOf<String>()
      val qualified = parent.parent as DeclarativeQualified
      if (includeCurrent) qualified.identifier.name?.let { result.add(it) }
      var current = qualified.getReceiver()
      while (current != null) {
        current.identifier.name?.let { result.add(it) }
        current = current.getReceiver()
      }
      return result.reversed()
    }
    else
      return null
  }
}

class EnableAutoPopupInDeclarativeCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    return if (DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN.accepts(contextElement) ||
               DECLARATIVE_ASSIGN_VALUE_SYNTAX_PATTERN.accepts(contextElement) ||
               AFTER_PROPERTY_DOT_ASSIGNABLE_SYNTAX_PATTERN.accepts(contextElement) ||
               AFTER_PROPERTY_DOT_SYNTAX_PATTERN.accepts(contextElement) ||
               AFTER_FUNCTION_DOT_SYNTAX_PATTERN.accepts(contextElement)) ThreeState.NO
    else ThreeState.UNSURE
  }
}