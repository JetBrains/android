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
package com.android.tools.idea.dagger.index.concepts

import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexFieldWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexPsiWrapper

/**
 * Represents a concept in the Dagger framework that will be indexed and analyzed at runtime. Some examples include "injected constructor",
 * "provides method", and "component".
 */
interface DaggerConcept {
  /** Any indexers required for this concept. */
  val indexers: DaggerConceptIndexers

  companion object {
    internal val ALL_CONCEPTS = listOf(
      InjectedConstructorDaggerConcept,
      InjectedFieldDaggerConcept,
      ProvidesMethodDaggerConcept)
  }
}

/** Utility class containing [DaggerConceptIndexer]s associated with [DaggerConcept]s. */
class DaggerConceptIndexers(
  val fieldIndexers: List<DaggerConceptIndexer<DaggerIndexFieldWrapper>> = emptyList(),
  val methodIndexers: List<DaggerConceptIndexer<DaggerIndexMethodWrapper>> = emptyList()) {

  companion object {
    val ALL_INDEXERS = DaggerConcept.ALL_CONCEPTS.map { it.indexers }.let { indexers ->
      DaggerConceptIndexers(
        indexers.map { it.fieldIndexers }.flatten(),
        indexers.map { it.methodIndexers }.flatten())
    }
  }
}

/** An indexer for a single [DaggerConcept]. Operates using a [DaggerIndexPsiWrapper], so that the logic is common to Kotlin and Java. */
interface DaggerConceptIndexer<T : DaggerIndexPsiWrapper> {
  fun addIndexEntries(wrapper: T, indexEntries: MutableMap<String, MutableSet<IndexValue>>)

  fun MutableMap<String, MutableSet<IndexValue>>.addIndexValue(key: String, value: IndexValue) {
    this.getOrPut(key) { mutableSetOf() }.add(value)
  }
}
