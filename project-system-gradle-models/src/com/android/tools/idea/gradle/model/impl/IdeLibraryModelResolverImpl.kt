/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeDependencyCore
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeUnresolvedLibrary
import com.android.tools.idea.gradle.model.ResolverType
import org.jetbrains.annotations.VisibleForTesting
import java.io.Serializable
import kotlin.collections.asSequence

class IdeLibraryModelResolverImpl @VisibleForTesting constructor(
  private val globalLibraryTable: IdeResolvedLibraryTableImpl?,
  private val kmpLibraryTable: KotlinMultiplatformIdeLibraryTable?,
) : IdeLibraryModelResolver {

  override fun resolve(unresolved: IdeDependencyCore): Sequence<IdeLibrary> =
    if (unresolved.target.resolverType == ResolverType.KMP_ANDROID) {
      kmpLibraryTable!!.libraries[unresolved.target.libraryIndex]
    } else {
      globalLibraryTable!!.libraries[unresolved.target.libraryIndex]
    }.asSequence()

  companion object {
    @JvmStatic
    fun fromLibraryTables(
      globalLibraryTable: IdeResolvedLibraryTableImpl?,
      kmpLibraryTable: KotlinMultiplatformIdeLibraryTable?,
    ) = IdeLibraryModelResolverImpl(globalLibraryTable, kmpLibraryTable)
  }
}

interface IdeUnresolvedLibraryTable {
  val libraries: List<IdeUnresolvedLibrary>
}

interface IdeResolvedLibraryTable {
  val libraries: List<List<IdeLibrary>>
}

sealed interface KotlinMultiplatformIdeLibraryTable: IdeResolvedLibraryTable

data class IdeUnresolvedLibraryTableImpl(
  override val libraries: List<IdeUnresolvedLibrary>
) : IdeUnresolvedLibraryTable, Serializable

data class IdeResolvedLibraryTableImpl(
  override val libraries: List<List<IdeLibrary>>
) : IdeResolvedLibraryTable, KotlinMultiplatformIdeLibraryTable, Serializable
