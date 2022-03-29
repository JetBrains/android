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
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependencyCore
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependencyCore
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

class IdeLibraryModelResolverImpl(private val libraryTable: (LibraryReference) -> IdeLibrary) : IdeLibraryModelResolver {
  private val androidLibraries: ConcurrentHashMap<Identity<IdeAndroidLibraryDependencyCore>, IdeAndroidLibraryDependency> = ConcurrentHashMap()
  private val javaLibraries: ConcurrentHashMap<Identity<IdeJavaLibraryDependencyCore>, IdeJavaLibraryDependency> = ConcurrentHashMap()

  override fun resolveAndroidLibrary(unresolved: IdeAndroidLibraryDependencyCore): IdeAndroidLibraryDependency {
    return androidLibraries.getOrElse(Identity(unresolved)) {
      IdeAndroidLibraryDependencyImpl(libraryTable(unresolved.target) as IdeAndroidLibrary, unresolved.isProvided)
    }
  }

  override fun resolveJavaLibrary(unresolved: IdeJavaLibraryDependencyCore): IdeJavaLibraryDependency {
    return javaLibraries.getOrElse(Identity(unresolved)) {
      IdeJavaLibraryDependencyImpl(libraryTable(unresolved.target) as IdeJavaLibrary, unresolved.isProvided)
    }
  }
}

data class IdeLibraryTableImpl(
  val libraries: List<IdeLibrary>
): Serializable


private class Identity<T>(val core: T) {
  override fun equals(other: Any?): Boolean = other is Identity<*> && core === other.core
  override fun hashCode(): Int = System.identityHashCode(core)
}

