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
import com.android.tools.idea.gradle.model.ClasspathType
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeUnresolvedLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedUnknownLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreDirect
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import java.io.File
import java.util.Objects
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
sealed class LibraryIdentity private constructor(val identity: Any) {
  class IdeLibraryModel(library: IdeLibrary) : LibraryIdentity(library)

  class IdeModuleModel(library: IdeUnresolvedLibrary) : LibraryIdentity(library)

  override fun equals(other: Any?): Boolean {
    return Objects.equals(identity, (other as? LibraryIdentity)?.identity)
  }

  override fun hashCode(): Int {
    return Objects.hashCode(identity)
  }
}

class InternedModels(private val buildRootDirectory: File?) {
  private val lock = ReentrantLock()

  @GuardedBy("lock")
  private val strings: MutableMap<String, String> = HashMap()
  @GuardedBy("lock")
  private val libraries: MutableList<IdeUnresolvedLibrary> = mutableListOf()

  // Library names are expected to be unique, and thus we track already allocated library names to be able to uniqualize names when
  // necessary.
  @GuardedBy("lock")
  private val allocatedLibraryNames: MutableSet<String> = HashSet()

  // Different modules (Gradle projects) may (usually do) share the same libraries. We create up to two library instances in this case.
  // One is when the library is used as a regular dependency and one when it is used as a "provided" dependency. This is going to change
  // when we add support for dependency graphs and different entities are used to represent libraries and dependencies.
  // We use mutable [Instances] objects to keep record of already instantiated and named library objects for each of the cases.
  @GuardedBy("lock")
  private val androidLibraries: MutableMap<LibraryIdentity, Pair<LibraryReference, IdeAndroidLibraryImpl>> = HashMap()
  @GuardedBy("lock")
  private val javaLibraries: MutableMap<LibraryIdentity, Pair<LibraryReference, IdeJavaLibraryImpl>> = HashMap()
  @GuardedBy("lock")
  private val moduleLibraries: MutableMap<LibraryIdentity, Pair<LibraryReference, IdeUnresolvedLibrary>> = HashMap()
  @GuardedBy("lock")
  private val unknownLibraries: MutableMap<LibraryIdentity, Pair<LibraryReference, IdeUnresolvedUnknownLibrary>> = HashMap()
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
    //if (unnamedAndroidLibrary.name.isNotEmpty()) error("Unnamed library expected: $unnamedAndroidLibrary")
    return androidLibraries.createOrGetLibrary(key) {
      val unnamed = factory()
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
    //if (unnamedJavaLibrary.name.isNotEmpty()) error("Unnamed library expected: $unnamedJavaLibrary")
    return javaLibraries.createOrGetLibrary(key) {
      val unnamed = factory()
      unnamed.copy(name = nameLibrary(unnamed))
    }
  }

  fun internModuleLibrary(key: LibraryIdentity, factory: () -> IdeUnresolvedLibrary): LibraryReference {
    return moduleLibraries.createOrGetLibrary(key, factory)
  }

  fun internUnknownLibrary(key: LibraryIdentity, factory: () -> IdeUnresolvedUnknownLibrary): LibraryReference {
    return unknownLibraries.createOrGetLibrary(key, factory)
  }

  /**
   * Finds an existing or creates a new library instance that match [key]. When creating a new library generates a unique library name
   * based on its artifact address.
   *
   * Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
   * meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
   */
  private fun <T : IdeUnresolvedLibrary> MutableMap<LibraryIdentity, Pair<LibraryReference, T>>.createOrGetLibrary(
    key: LibraryIdentity,
    factory: () -> T
  ): LibraryReference {
    return lock.withLock {
      computeIfAbsent(key) {
        val library = factory()
        val index = libraries.size
        libraries.add(library)
        LibraryReference(index) to library
      }.first
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
