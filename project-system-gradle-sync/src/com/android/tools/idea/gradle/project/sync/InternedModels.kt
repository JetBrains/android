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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import java.io.File

class InternedModels(private val buildRootDirectory: File?) {
  private val strings: MutableMap<String, String> = HashMap()
  private val libraries: MutableList<IdeLibrary> = mutableListOf()

  // Library names are expected to be unique, and thus we track already allocated library names to be able to uniqualize names when
  // necessary.
  private val allocatedLibraryNames: MutableSet<String> = HashSet()

  // Different modules (Gradle projects) may (usually do) share the same libraries. We create up to two library instances in this case.
  // One is when the library is used as a regular dependency and one when it is used as a "provided" dependency. This is going to change
  // when we add support for dependency graphs and different entities are used to represent libraries and dependencies.
  // We use mutable [Instances] objects to keep record of already instantiated and named library objects for each of the cases.
  private val androidLibraries: MutableMap<IdeAndroidLibraryImpl, Pair<LibraryReference, IdeAndroidLibraryImpl>> = HashMap()
  private val javaLibraries: MutableMap<IdeJavaLibraryImpl, Pair<LibraryReference, IdeJavaLibraryImpl>> = HashMap()
  private val moduleLibraries: MutableMap<IdeModuleLibraryImpl, Pair<LibraryReference, IdeModuleLibraryImpl>> = HashMap()

  fun resolve(reference: LibraryReference): IdeLibrary = libraries[reference.libraryIndex]

  fun intern(string: String): String {
    return strings.getOrPut(string) { string }
  }

  /**
   * Finds an existing or creates a new library instance that match [unnamedAndroidLibrary]. When creating a new library generates a unique
   * library name based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  fun getOrCreate(unnamedAndroidLibrary: IdeAndroidLibraryImpl): LibraryReference {
    if (unnamedAndroidLibrary.name.isNotEmpty()) error("Unnamed library expected: $unnamedAndroidLibrary")
    return androidLibraries.createOrGetLibrary(unnamedAndroidLibrary) { unnamed ->
      unnamed.copy(name = nameLibrary(unnamed))
    }
  }

  /**
   * Finds an existing or creates a new library instance that match [unnamedJavaLibrary]. When creating a new library generates a unique
   * library name based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  fun getOrCreate(unnamedJavaLibrary: IdeJavaLibraryImpl): LibraryReference {
    if (unnamedJavaLibrary.name.isNotEmpty()) error("Unnamed library expected: $unnamedJavaLibrary")
    return javaLibraries.createOrGetLibrary(unnamedJavaLibrary) { unnamed ->
      unnamed.copy(name = nameLibrary(unnamed))
    }
  }

  /**
   * Interns [moduleLibrary].
   */
  fun getOrCreate(moduleLibrary: IdeModuleLibraryImpl): LibraryReference {
    return moduleLibraries.createOrGetLibrary(moduleLibrary) { it }
  }

  /**
   * Finds an existing or creates a new library instance that match [unnamed]. When creating a new library generates a unique library name
   * based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  private fun <T : IdeLibrary> MutableMap<T, Pair<LibraryReference, T>>.createOrGetLibrary(
    unnamed: T,
    factory: (unnamed: T) -> T
  ): LibraryReference {
    return computeIfAbsent(unnamed) {
      val library = factory(unnamed)
      val index = libraries.size
      libraries.add(library)
      LibraryReference(index) to library
    }.first
  }

  fun createLibraryTable(): IdeLibraryTableImpl {
    return IdeLibraryTableImpl(libraries.toList())
  }

  private fun nameLibrary(unnamed: IdeArtifactLibrary) =
    allocatedLibraryNames.generateLibraryName(projectBasePath = buildRootDirectory, artifactAddress = unnamed.artifactAddress)
}

private fun MutableSet<String>.generateLibraryName(projectBasePath: File?, artifactAddress: String): String {
  val baseLibraryName = convertToLibraryName(artifactAddress, projectBasePath)
  var candidateLibraryName = baseLibraryName
  var suffix = 0
  while (!this.add(candidateLibraryName)) {
    suffix++
    candidateLibraryName = "$baseLibraryName ($suffix)"
  }
  return candidateLibraryName
}
