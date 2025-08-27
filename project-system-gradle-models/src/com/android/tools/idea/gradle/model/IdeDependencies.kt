/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeDependencyCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl

/** Historically, the interfaces and implementations were separated, but we don't need that distinction anymore as the interfaces are not
 * exposed anymore. Providing the data class here goes against the pattern in the rest of the package but it's the easiest and cleanest
 * solution to be able to provide and use this cleanly without changing much in the code while refactoring.
 *
 * Ideally in the future we want everything to be data classes here and remove the interfaces, but doing that incrementally is also fine.
 */
data class IdeDependencies(
  internal val classpath: IdeDependenciesCoreImpl,
  /** Utility method to provide easy access to a resolver without having to re-create one from the library table. */
  val resolver: IdeLibraryModelResolverImpl
) {
  @Transient
  private var librariesField: List<IdeLibrary>? = null

  /** Returns the libraries of all types, both direct and transitive */
  val libraries: List<IdeLibrary> get() = synchronized(this) {
    librariesField ?: classpath.dependencies.flatMap { resolver.resolve(it) }.also {
      librariesField = it
    }
  }

  /**
   * Returns the list of all dependencies, both direct and transitive as [IdeDependencyCore]s.
   * These contain an unresolved library reference [IdeDependencyCore.target] which should be resolved with a [IdeLibraryModelResolver].
   * They also contain a list of indexes of their dependencies, these are indices back into this list of dependencies.
   */
  val unresolvedDependencies: List<IdeDependencyCoreImpl> = classpath.dependencies
}

/** Method to resolve transitive dependencies from [IdeDependencyCore.dependencies] back to their [IdeDependencyCore] */
val IdeDependencies.lookup: (Int) -> IdeDependencyCore
  get() = { classpath.lookup(it) }
