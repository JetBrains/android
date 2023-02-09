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
package com.android.tools.idea.dagger.index

import com.android.tools.idea.dagger.concepts.AllConcepts
import com.android.tools.idea.dagger.concepts.DaggerConcept
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexFieldWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexPsiWrapper
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

typealias IndexEntries = MutableMap<String, MutableSet<IndexValue>>

internal class DaggerDataIndexer
@VisibleForTesting
internal constructor(private val conceptIndexers: DaggerConceptIndexers = AllConcepts.indexers) :
  DataIndexer<String, Set<IndexValue>, FileContent> {

  companion object {
    val INSTANCE = DaggerDataIndexer()
  }

  override fun map(inputData: FileContent): Map<String, Set<IndexValue>> {
    val results: IndexEntries = mutableMapOf()
    val visitor =
      when (inputData.fileType) {
        KotlinFileType.INSTANCE ->
          KotlinVisitor(results, conceptIndexers, inputData.psiFile as KtFile)
        JavaFileType.INSTANCE ->
          JavaVisitor(results, conceptIndexers, inputData.psiFile as PsiJavaFile)
        else -> return emptyMap()
      }

    inputData.psiFile.accept(visitor)
    return results
  }

  private class KotlinVisitor(
    private val results: IndexEntries,
    private val conceptIndexers: DaggerConceptIndexers,
    ktFile: KtFile
  ) : KtTreeVisitorVoid() {
    private val wrapperFactory = DaggerIndexPsiWrapper.KotlinFactory(ktFile)

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
      conceptIndexers.doIndexing(wrapperFactory.of(constructor), results)
      // No need to continue traversing within the method.
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
      conceptIndexers.doIndexing(wrapperFactory.of(constructor), results)
      // No need to continue traversing within the method.
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
      conceptIndexers.doIndexing(wrapperFactory.of(function), results)
      // No need to continue traversing within the method.
    }

    override fun visitProperty(property: KtProperty) {
      conceptIndexers.doIndexing(wrapperFactory.of(property), results)
      // No need to continue traversing within the field definition.
    }
  }

  private class JavaVisitor(
    private val results: IndexEntries,
    private val conceptIndexers: DaggerConceptIndexers,
    psiJavaFile: PsiJavaFile
  ) : JavaRecursiveElementWalkingVisitor() {
    private val wrapperFactory = DaggerIndexPsiWrapper.JavaFactory(psiJavaFile)

    override fun visitMethod(method: PsiMethod) {
      conceptIndexers.doIndexing(wrapperFactory.of(method), results)
      // No need to continue traversing within the method.
    }

    override fun visitField(field: PsiField) {
      conceptIndexers.doIndexing(wrapperFactory.of(field), results)
      // No need to continue traversing within the field definition.
    }
  }
}

/**
 * An indexer for a single [DaggerConcept]. Operates using a [DaggerIndexPsiWrapper], so that the
 * logic is common to Kotlin and Java.
 */
fun interface DaggerConceptIndexer<T : DaggerIndexPsiWrapper> {
  fun addIndexEntries(wrapper: T, indexEntries: IndexEntries)

  fun IndexEntries.addIndexValue(key: String, value: IndexValue) {
    this.getOrPut(key) { mutableSetOf() }.add(value)
  }
}

/** Utility class containing [DaggerConceptIndexer]s associated with [DaggerConcept]s. */
class DaggerConceptIndexers(
  val fieldIndexers: List<DaggerConceptIndexer<DaggerIndexFieldWrapper>> = emptyList(),
  val methodIndexers: List<DaggerConceptIndexer<DaggerIndexMethodWrapper>> = emptyList()
) {

  fun doIndexing(wrapper: DaggerIndexFieldWrapper, indexEntries: IndexEntries) =
    fieldIndexers.forEach { it.addIndexEntries(wrapper, indexEntries) }
  fun doIndexing(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) =
    methodIndexers.forEach { it.addIndexEntries(wrapper, indexEntries) }
}
