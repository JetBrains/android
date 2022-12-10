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

import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeDependencyCore
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdePreResolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnknownDependency
import com.android.tools.idea.gradle.model.IdeUnknownLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedModuleLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import org.jetbrains.annotations.VisibleForTesting
import java.io.Serializable

class IdeLibraryModelResolverImpl @VisibleForTesting constructor(
  private val libraryTable: (LibraryReference) -> Sequence<IdeLibrary>
) : IdeLibraryModelResolver {
  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  override fun resolveAndroidLibrary(unresolved: IdeDependencyCore): Sequence<IdeAndroidLibraryDependency> {
    return libraryTable(unresolved.target)
      .filterIsInstance<IdeAndroidLibrary>()
      .map { IdeAndroidLibraryDependencyImpl(it) }
  }

  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  override fun resolveJavaLibrary(unresolved: IdeDependencyCore): Sequence<IdeJavaLibraryDependency> {
    return libraryTable(unresolved.target)
      .filterIsInstance<IdeJavaLibrary>()
      .map { IdeJavaLibraryDependencyImpl(it) }
  }

  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  override fun resolveModule(unresolved: IdeDependencyCore): Sequence<IdeModuleDependency> {
    return libraryTable(unresolved.target)
      .mapNotNull {
        when (it) {
          is IdeModuleLibrary -> it
          is IdePreResolvedModuleLibrary -> error("Module dependencies are not yet resolved")
          is IdeUnresolvedModuleLibrary -> error("Module dependencies are not yet resolved")
          else -> null
        }
      }
      .map(::IdeModuleDependencyImpl)
  }

  @Deprecated("IdeDependency and subclasses will be removed", ReplaceWith("this.resolve(unresolved)"))
  override fun resolveUnknownLibrary(unresolved: IdeDependencyCore): Sequence<IdeUnknownDependency> {
    return libraryTable(unresolved.target)
      .filterIsInstance<IdeUnknownLibrary>()
      .map { IdeUnknownDependencyImpl(it) }
  }

  override fun resolve(unresolved: IdeDependencyCore): Sequence<IdeLibrary> {
    return libraryTable(unresolved.target)
  }

  companion object {
    @JvmStatic
    fun fromLibraryTable(resolvedLibraryTable: IdeResolvedLibraryTable): IdeLibraryModelResolver {
      return IdeLibraryModelResolverImpl { resolvedLibraryTable.libraries[it.libraryIndex].asSequence() }
    }
  }
}

interface IdeUnresolvedLibraryTable {
  val libraries: List<IdeLibrary>
}

interface IdeResolvedLibraryTable {
  val libraries: List<List<IdeLibrary>>
}

data class IdeUnresolvedLibraryTableImpl(
  override val libraries: List<IdeLibrary>
) : IdeUnresolvedLibraryTable, Serializable

data class IdeResolvedLibraryTableImpl(
  override val libraries: List<List<IdeLibrary>>
) : IdeResolvedLibraryTable, Serializable
