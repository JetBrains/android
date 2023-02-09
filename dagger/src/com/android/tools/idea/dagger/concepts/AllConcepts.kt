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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.IndexValue

/**
 * Collection of all known [DaggerConcept]s. This is effectively the entry point for external
 * consumers of concepts, since they do not need to know about the individual concepts and instead
 * look at the set of all of them together.
 */
object AllConcepts : DaggerConcept {
  private val CONCEPTS =
    listOf(
      InjectedConstructorDaggerConcept,
      InjectedFieldDaggerConcept,
      ProvidesMethodDaggerConcept,
    )

  override val indexers =
    CONCEPTS.map(DaggerConcept::indexers).let { indexersList ->
      DaggerConceptIndexers(
        indexersList.flatMap(DaggerConceptIndexers::fieldIndexers),
        indexersList.flatMap(DaggerConceptIndexers::methodIndexers)
      )
    }

  override val indexValueReaders: List<IndexValue.Reader> =
    CONCEPTS.flatMap(DaggerConcept::indexValueReaders)

  override val daggerElementIdentifiers: DaggerElementIdentifiers =
    DaggerElementIdentifiers.of(CONCEPTS.map(DaggerConcept::daggerElementIdentifiers))
}
