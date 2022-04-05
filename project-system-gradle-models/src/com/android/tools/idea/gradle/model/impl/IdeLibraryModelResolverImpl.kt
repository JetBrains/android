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
import com.android.tools.idea.gradle.model.IdePreResolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedModuleLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import java.io.Serializable

class IdeLibraryModelResolverImpl(private val libraryTable: (LibraryReference) -> IdeLibrary) : IdeLibraryModelResolver {
  override fun resolveAndroidLibrary(unresolved: IdeDependencyCore): IdeAndroidLibraryDependency? {
    return IdeAndroidLibraryDependencyImpl(libraryTable(unresolved.target) as? IdeAndroidLibrary ?: return null, unresolved.isProvided)
  }

  override fun resolveJavaLibrary(unresolved: IdeDependencyCore): IdeJavaLibraryDependency? {
    return IdeJavaLibraryDependencyImpl(libraryTable(unresolved.target) as? IdeJavaLibrary ?: return null, unresolved.isProvided)
  }

  override fun resolveModule(unresolved: IdeDependencyCore): IdeModuleDependency? {
    val moduleLibrary = when (val library = libraryTable(unresolved.target)) {
      is IdeModuleLibrary -> library
      is IdePreResolvedModuleLibrary -> error("Module dependencies are not yet resolved")
      is IdeUnresolvedModuleLibrary -> error("Module dependencies are not yet resolved")
      else -> return null
    }
    return IdeModuleDependencyImpl(moduleLibrary)
  }
}

data class IdeLibraryTableImpl(
  val libraries: List<IdeLibrary>
) : Serializable
