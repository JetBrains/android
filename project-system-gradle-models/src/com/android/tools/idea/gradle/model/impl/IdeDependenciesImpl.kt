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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeDependenciesCore
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeUnknownDependency
import java.io.Serializable

data class IdeDependenciesCoreImpl(
  override val dependencies: Collection<IdeDependencyCoreImpl>
) : IdeDependenciesCore, Serializable

data class IdeDependenciesImpl(
  private val classpath: IdeDependenciesCore,
  private val resolver: IdeLibraryModelResolver
) : IdeDependencies {
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  override val androidLibraries: Collection<IdeAndroidLibraryDependency> =
    classpath.dependencies.flatMap(resolver::resolveAndroidLibrary)
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  override val javaLibraries: Collection<IdeJavaLibraryDependency> =
    classpath.dependencies.flatMap(resolver::resolveJavaLibrary)
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  override val moduleDependencies: Collection<IdeModuleDependency> =
    classpath.dependencies.flatMap(resolver::resolveModule)
  @Deprecated("does not respect classpath order", ReplaceWith("this.libraries"))
  override val unknownDependencies: Collection<IdeUnknownDependency> =
    classpath.dependencies.flatMap(resolver::resolveUnknownLibrary)
  override val libraries = classpath.dependencies.flatMap { resolver.resolve(it) }
}

fun throwingIdeDependencies(): IdeDependenciesCoreImpl {
  return IdeDependenciesCoreImpl(object : Collection<IdeDependencyCoreImpl> {
    override val size: Int get() = unexpected()
    override fun isEmpty(): Boolean = unexpected()
    override fun iterator(): Iterator<IdeDependencyCoreImpl> = unexpected()
    override fun containsAll(elements: Collection<IdeDependencyCoreImpl>): Boolean = unexpected()
    override fun contains(element: IdeDependencyCoreImpl): Boolean = unexpected()

    private fun unexpected(): Nothing {
      error("Should not be called")
    }

  })
}
