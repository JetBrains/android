/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql.room

import com.android.tools.idea.lang.androidSql.NotRenamableElement
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.lang.androidSql.refactoring.AndroidSqlFindUsagesProvider
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumn
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlDefinition
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * `referencesSearch` that checks the word index for the right words (in case a table/column name is not the same as class name).
 *
 * Also it does case insensitive search.
 */
class RoomReferenceSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
  private fun getSchema(element: PsiElement) = ReadAction.compute<RoomSchema?, Nothing> {
    if (element.containingFile != null) {
      val module = ModuleUtil.findModuleForPsiElement(element) ?: return@compute null
      RoomSchemaManager.getInstance(module).getSchema(element.containingFile)
    } else {
      null
    }
  }

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    var element = queryParameters.elementToSearch
    if (element is NotRenamableElement) {
      element = element.delegate
    }

    val (words, referenceTarget) = runReadAction {

      if (!RoomDependencyChecker.getInstance(
          element.project).isRoomPresent()) return@runReadAction null

      // Return early if possible: this method is called by various inspections on all kinds of PSI elements, in most cases we don't have to
      // do anything which means we don't block a FJ thread by building a Room schema.
      val definesSqlSchema = when (element) {
        is PsiElementForFakeColumn -> true
        is PsiClass -> element.definesSqlTable()
        is PsiField, is KtProperty -> {
          val psiClass: PsiClass? = if (element is PsiField) {
            element.containingClass
          }
          else {
            (element as KtProperty).containingClass()?.toLightClass()
          }
          // A subclass can be annotated with `@Entity`, making fields into SQL column definitions.
          !(psiClass == null || psiClass.hasModifier(JvmModifier.FINAL) && !psiClass.definesSqlTable())
        }
        else -> false
      }

      if (!definesSqlSchema) {
        return@runReadAction null
      }

      val schema = getSchema(element)
      if (schema == null) {
        //Case: module that element belongs to doesn't have schema, but subclass in another module can be annotated with `@Entity`.
        //Therefore we want to make case insensitive search for that element
        if (element is PsiNamedElement && element.name != null) {
          Pair(setOf(element.name!!), element)
        }
        else {
          null
        }
      }
      else {
        getNameDefinition(schema, element)?.let(this::chooseWordsAndElement)
      }
    } ?: return

    // Note: QueryExecutorBase is a strange API: the naive way to implement it is to somehow find the references and feed them to the
    // consumer. The "proper" way is to get the optimizer and schedule an index search for references to a given element that include a
    // given text. Scheduled searches get merged and run later. We use the latter API, but:
    //   - We search for the right word (as calculate above).
    //   - We use `scopeDeterminedByUser`, not `effectiveScope`. Effective scope for a private field contains only the file in which it's
    //     defined, which is not how Room works.
    //   - We look for references to the right element: if a table/column name is overridden using annotations, the PSI references may not
    //     point to the class/field itself, but we still want to show these references in "find usages".
    for (word in words) {
      queryParameters.optimizer.searchWord(word, queryParameters.scopeDeterminedByUser, false, referenceTarget)
    }
  }

  private fun PsiClass.definesSqlTable(): Boolean {
    return hasAnnotation(RoomAnnotations.ENTITY.oldName()) ||
           hasAnnotation(RoomAnnotations.ENTITY.newName()) ||
           hasAnnotation(RoomAnnotations.DATABASE_VIEW.oldName()) ||
           hasAnnotation(RoomAnnotations.DATABASE_VIEW.newName())
  }

  /**
   * Returns set of words and element for given [AndroidSqlDefinition] to check during search usage process
   *
   * Some columns can be used by [AndroidSqlColumn.alternativeNames] and in search we need to find usages of all of them
   */
  private fun chooseWordsAndElement(definition: AndroidSqlDefinition): Pair<List<String>, PsiElement>? {
    val names = ArrayList<String>()
    if (definition.name != null) names.add(definition.name!!)
    if (definition is AndroidSqlColumn) names.addAll(definition.alternativeNames)

    if (names.isEmpty()) return null

    val words = names.map { name ->
      when {
        AndroidSqlLexer.needsQuoting(name) -> {
          // We need to figure out how a reference to this element looks like in the IdIndex.
          // We find the first "word" in the quoted name and look for it in the index,
          // as any reference for this table will include this word in its text.
          val processor = CommonProcessors.FindFirstProcessor<WordOccurrence>()
          AndroidSqlFindUsagesProvider().wordsScanner.processWords(
            AndroidSqlLexer.getValidName(name), processor)
          processor.foundValue?.let { it.baseText.substring(it.start, it.end) } ?: name
        }
        else -> name
      }
    }

    return Pair(words, definition.resolveTo)
  }

  private fun getNameDefinition(schema: RoomSchema, element: PsiElement): AndroidSqlDefinition? {
    return when (element) {
      is PsiClass -> {
        schema.findTable(element)
      }
      is PsiField -> {
        schema
          .findTable(element.containingClass ?: return null)
          ?.columns
          ?.find { it.definingElement == element }
      }
      is PsiElementForFakeColumn -> {
        schema
          .findTable(element.tablePsiElement)
          ?.columns
          ?.find { PsiManager.getInstance(element.project).areElementsEquivalent(it.definingElement, element) }
      }
      is KtProperty -> {
        val lightField = element.toLightElements().firstIsInstanceOrNull<PsiField>()
        schema
          .findTable(element.containingClass()?.toLightClass() ?: return null)
          ?.columns
          ?.find { it.definingElement == lightField }
      }
      else -> null
    }
  }
}