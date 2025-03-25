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
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.ResolverType
import org.jetbrains.annotations.VisibleForTesting
import java.io.Serializable

class IdeLibraryModelResolverImpl @VisibleForTesting constructor(
  private val libraryTable: (LibraryReference) -> Sequence<IdeLibrary>
) : IdeLibraryModelResolver {

  override fun resolve(unresolved: IdeDependencyCore): Sequence<IdeLibrary> {
    return libraryTable(unresolved.target)
  }

  companion object {
    @JvmStatic
    fun fromLibraryTables(
      globalLibraryTable: IdeResolvedLibraryTable?,
      kmpLibraryTable: IdeResolvedLibraryTable?,
    ): IdeLibraryModelResolver {
      return IdeLibraryModelResolverImpl {
        if (it.resolverType == ResolverType.KMP_ANDROID) {
          kmpLibraryTable!!.libraries[it.libraryIndex].asSequence()
        }
        else {
          globalLibraryTable!!.libraries[it.libraryIndex].asSequence()
        }
      }
    }
  }
}

interface IdeUnresolvedLibraryTable {
  val libraries: List<IdeUnresolvedLibrary>
}

interface IdeResolvedLibraryTable {
  val libraries: List<List<IdeLibrary>>
}

interface KotlinMultiplatformIdeLibraryTable: IdeResolvedLibraryTable

data class IdeUnresolvedLibraryTableImpl(
  override val libraries: List<IdeUnresolvedLibrary>
) : IdeUnresolvedLibraryTable, Serializable

data class IdeResolvedLibraryTableImpl(
  override val libraries: List<List<IdeLibrary>>
) : IdeResolvedLibraryTable, KotlinMultiplatformIdeLibraryTable, Serializable
