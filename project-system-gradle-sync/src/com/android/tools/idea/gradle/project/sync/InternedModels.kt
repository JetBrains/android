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

import com.android.annotations.concurrency.GuardedBy
import com.android.builder.model.v2.ide.Library
import com.android.tools.idea.gradle.model.ClasspathType
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeUnresolvedLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedUnknownLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreDirect
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Small data class to store information for mapping projects to dependencies.
 */
internal data class ClasspathIdentifier(
  val buildId: BuildId,
  val projectPath: String,
  val artifact: IdeModuleSourceSet,
  val classpathType: ClasspathType,
)
data class LibraryIdentity private constructor(val key: Any) {
  companion object {
    fun fromLibrary(library: Library) = LibraryIdentity(library.androidLibraryData?.resFolder?.parentFile?.let { LibraryAndExtractedFolder(library.key, it) } ?: library.key)
    fun fromFile(file: File) = LibraryIdentity(file.path)
    fun fromIdeModel(library: IdeUnresolvedLibrary) = LibraryIdentity(library)
    private data class LibraryAndExtractedFolder(val libraryKey: String, val folder: File)
  }
}

class InternedModels(private val buildRootDirectory: File?) {
  private val lock = ReentrantLock()

  @GuardedBy("lock")
  private val strings: MutableMap<String, String> = HashMap()
  @GuardedBy("lock")
  private val libraries: MutableList<IdeUnresolvedLibrary> = mutableListOf()

  // Map from unique artifact address to level2 library instance. The library instances are
  // supposed to be shared by all artifacts. When creating IdeLevel2Dependencies, check if current library is available in this map,
  // if it's available, don't create new one, simple add reference to it. If it's not available, create new instance and save
  // to this map, so it can be reused the next time when the same library is added.
  @GuardedBy("lock")
  val librariesByKey = mutableMapOf<LibraryIdentity, LibraryReference>()

  // Library names are expected to be unique, and thus we track already allocated library names to be able to uniqualize names when
  // necessary.
  @GuardedBy("lock")
  private val allocatedLibraryNames: MutableSet<String> = HashSet()

  // Different modules (Gradle projects) may (usually do) share the same libraries. We create up to two library instances in this case.
  // One is when the library is used as a regular dependency and one when it is used as a "provided" dependency. This is going to change
  // when we add support for dependency graphs and different entities are used to represent libraries and dependencies.
  // We use mutable [Instances] objects to keep record of already instantiated and named library objects for each of the cases.
  @GuardedBy("lock")
  private val androidLibraries: MutableMap<IdeAndroidLibraryImpl, LibraryReference> = HashMap()
  @GuardedBy("lock")
  private val javaLibraries: MutableMap<IdeJavaLibraryImpl, LibraryReference> = HashMap()
  @GuardedBy("lock")
  private val moduleLibraries: MutableMap<IdeUnresolvedLibrary, LibraryReference> = HashMap()
  @GuardedBy("lock")
  private val unknownLibraries: MutableMap<IdeUnresolvedLibrary, LibraryReference> = HashMap()
  var artifactToLibraryReferenceMap: Map<File, LibraryReference>? = null ; private set

  @GuardedBy("lock")
  internal val projectReferenceToArtifactClasspathMap: MutableMap<ClasspathIdentifier, Pair<IdeDependenciesCoreDirect, Int>> = HashMap()

  fun lookup(reference: LibraryReference): IdeUnresolvedLibrary = libraries[reference.libraryIndex]

  fun intern(string: String) = lock.withLock {
    strings.getOrPut(string) { string }
  }

  /**
   * Finds an existing or creates a new library instance that match [unnamedAndroidLibrary]. When creating a new library generates a unique
   * library name based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */

  fun internAndroidLibrary(key: LibraryIdentity, factory: () -> IdeAndroidLibraryImpl): LibraryReference {
    return androidLibraries.createOrGetLibrary(key, factory) { unnamed ->
      if (unnamed.name.isNotEmpty()) error("Unnamed library expected: $unnamed")
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
  fun internJavaLibrary(key: LibraryIdentity, factory: () -> IdeJavaLibraryImpl): LibraryReference {
    return javaLibraries.createOrGetLibrary(key, factory) { unnamed ->
      if (unnamed.name.isNotEmpty()) error("Unnamed library expected: $unnamed")
      unnamed.copy(name = nameLibrary(unnamed))
    }
  }

  fun internModuleLibrary(key: LibraryIdentity, factory: () -> IdeUnresolvedLibrary): LibraryReference {
    return moduleLibraries.createOrGetLibrary(key, factory) { it } // no need to name
  }

  fun internUnknownLibrary(key: LibraryIdentity, factory: () -> IdeUnresolvedUnknownLibrary): LibraryReference {
    return unknownLibraries.createOrGetLibrary(key, factory) { it } // no need to name
  }

  /**
   * Finds an existing or creates a new library instance that match [key]. When creating a new library generates a unique library name
   * based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  private fun <T : IdeUnresolvedLibrary> MutableMap<T, LibraryReference>.createOrGetLibrary(
    key: LibraryIdentity,
    unnamedFactory: () -> T,
    namedFactory: (unnamed: T) -> T
  ): LibraryReference {
    return lock.withLock {
      // The object created by the factory below can be a fairly large one, and it's not efficient to allocate it and use as a key, due
      // to is hashCode and equals implementations.  So, here we're using two sets of keys (first, the artifact address, and then the object
      // itself), to make the factory invocations less frequent, and map lookups/insertions faster.
      //
      // We can't use just the initial key, because in principle, two distinct keys can end up matching the same IDE model, due to the
      // subtle modeling differences between Gradle and the IDE libraries.
      librariesByKey.computeIfAbsent(key) {
        val ideModel = unnamedFactory()
        computeIfAbsent(ideModel) {
          val index = libraries.size
          // Add the named library to the library table, maps contain only the unnamed libraries,
          // whereas this is the one with named libraries.
          libraries.add(namedFactory(ideModel))
          LibraryReference(index)
        }
      }
    }
  }

  fun createLibraryTable(): IdeUnresolvedLibraryTableImpl {
    return IdeUnresolvedLibraryTableImpl(libraries.toList())
  }

  /**
   * Prepares [ModelCache] for running any post-processors previously returned in [IdeModelWithPostProcessor] instances.
   */
  fun prepare() {
    artifactToLibraryReferenceMap =
      createLibraryTable()
        .libraries
        .mapIndexed { index, ideLibrary ->
          val reference = LibraryReference(index)
          reference to ideLibrary
        }
        .flatMap { (reference, ideLibrary) ->
          when (ideLibrary) {
            is IdeAndroidLibrary -> ideLibrary.runtimeJarFiles.map { it to reference }
            is IdeJavaLibrary -> listOf(ideLibrary.artifact to reference)
            else -> emptyList()
          }
        }
        .toMap()
  }

  private fun nameLibrary(unnamed: IdeArtifactLibrary) =
    allocatedLibraryNames.generateLibraryName(projectBasePath = buildRootDirectory, artifactAddress = unnamed.artifactAddress)

  internal fun addProjectReferenceToArtifactClasspath(id: ClasspathIdentifier, pair: Pair<IdeDependenciesCoreDirect, Int>) {
    lock.withLock {
      projectReferenceToArtifactClasspathMap.putIfAbsent(id, pair)
    }
  }

  internal fun getProjectReferenceToArtifactClasspath(classpathId: ClasspathIdentifier) = lock.withLock {
    projectReferenceToArtifactClasspathMap[classpathId]
  }

  internal fun getLibraryByKey(libraryIdentity: LibraryIdentity) = lock.withLock {
    librariesByKey[libraryIdentity]
  }
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
