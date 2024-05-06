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
import com.android.builder.model.v2.ide.LibraryType
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
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

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
    @VisibleForTesting
    fun fromLibrary(library: Library): LibraryIdentity = LibraryIdentity(run {
      checkNotNull(library.libraryInfo) {
        "Use IDE model as key for project dependencies"
      }
      library.toAddressAndArtifactPath()
    })

    internal fun fromFile(file: File) = LibraryIdentity(file.path.toString())

    @VisibleForTesting
    fun fromIdeModel(library: IdeUnresolvedLibrary) = LibraryIdentity(library)

    private fun Library.toAddressAndArtifactPath() =
      "${libraryInfo!!.group}:${libraryInfo!!.name}:${libraryInfo!!.version}${artifact?.absolutePath ?: ""}"
  }

  /**
   * In some cases (explained in the methods below), we prefer one library identity over another to ensure deterministic behavior
   * across multiple syncs. This class tracks which identity belongs to which library to be able to later compare them and sort
   * them according to a pre-defined order.
   */
  class LibraryIdentityProvider {
    private val identityToLibrary = mutableMapOf<LibraryIdentity, Library>()

    private val comparator = compareBy<LibraryIdentity, List<String>>(sortedListComparator) {
      identityToLibrary[it]?.libraryInfo?.capabilities.orEmpty().sorted()
    }.thenBy(sortedMapEntries) {
      identityToLibrary[it]?.libraryInfo?.attributes?.entries.orEmpty().sortedBy { it.key }
    }

    /**
     * For Android libraries, when the build script classpath is different for each project/build, we might
     * end up running the same transform for the same artifact multiple times, and the only signal for that
     * we have on this side is the AAR extraction folder. Here when multiple of those are collapsed together
     * this makes sure we always pick a consistent one across multiple syncs.
     *
     * Function returns identity alongside whether the library is updated to make sure the resulting IDE model
     * can be updated too.
     */
    internal fun associate(library: Library) : Pair<LibraryIdentity, Boolean> =
      if (library.type == LibraryType.ANDROID_LIBRARY) {
        associateWithV2AndroidLibrary(library)
      } else {
        fromLibrary(library).also {
          identityToLibrary[it] = library
        } to false // no need to update non-android libraries
      }

    private fun associateWithV2AndroidLibrary(library: Library): Pair<LibraryIdentity, Boolean> {
      var updated = false
      val identity = fromLibrary(library)
      identityToLibrary.computeIfPresent(identity) { _, oldLibrary ->
        val oldFolder = oldLibrary.toExtractedFolder()
        val newFolder = library.toExtractedFolder()
        if (oldFolder != null && newFolder != null && newFolder < oldFolder) {
          updated = true
          library
        }
        else {
          oldLibrary
        }
      } ?: identityToLibrary.put(identity, library)
      return identity to updated
    }

    private fun Library.toExtractedFolder() = androidLibraryData?.resFolder?.parentFile?.path?.toString()

    /**
     * In some cases, artifacts with different set of gradle attributes can end up matching the same artifact,
     * so they end up having  the same identity, and we can use any of them. However, we still want to make sure
     * we pick one consistently across multiple syncs. This function is used  to sort the library table entries
     * in a deterministic order by comparing their attribute and capability sets.
     */
    fun compare(identity1: LibraryIdentity, identity2: LibraryIdentity): Int = comparator.compare(identity1, identity2)

    companion object {
      private val sortedListComparator = Comparator<List<String>> { list1, list2 ->
        for (i in 0 until min(list1.size, list2.size)) {
          // Comparing the length first makes sure test fixtures come after a main library, purely for "cosmetic" reasons.
          compareBy<String> { it.length }
            .thenBy { it }
            .compare(list1[i], list2[i]).let {
              if (it != 0) return@Comparator it
            }
        }
        return@Comparator list1.size - list2.size
      }

      private val sortedMapEntries = Comparator<List<Map.Entry<String, String>>> { entries1, entries2 ->
        for (i in 0 until min(entries1.size, entries2.size)) {
          compareBy<Map.Entry<String, String>> { it.key }
            .thenBy { it.value }
            .compare(entries1[i], entries2[i]).let {
              if (it != 0) return@Comparator it
            }
        }
        return@Comparator entries1.size - entries2.size
      }
    }
  }
}

class InternedModels(private val buildRootDirectory: File?) {
  private val lock = ReentrantLock()
  private val libraryIdentityProvider = LibraryIdentity.LibraryIdentityProvider()

  @GuardedBy("lock")
  private val strings: MutableMap<String, String> = HashMap()
  @GuardedBy("lock")
  private val libraries: MutableList<IdeUnresolvedLibrary> = mutableListOf()

  // Map from a [LibraryIdentity] to an indexed reference in the library table.
  @GuardedBy("lock")
  private val keyToLibraryReference = mutableMapOf<LibraryIdentity, LibraryReference>()


  var artifactToLibraryReferenceMap: Map<File, LibraryReference>? = null ; private set

  @GuardedBy("lock")
  internal val projectReferenceToArtifactClasspathMap: MutableMap<ClasspathIdentifier, Pair<IdeDependenciesCoreDirect, Int>> = HashMap()

  fun lookup(reference: LibraryReference): IdeUnresolvedLibrary = libraries[reference.libraryIndex]

  fun intern(string: String) = lock.withLock {
    strings.getOrPut(string) { string }
  }

  fun internAndroidLibrary(key: IdeUnresolvedLibrary, factory: () -> IdeAndroidLibraryImpl) =
    createOrGetLibrary(LibraryIdentity.fromIdeModel(key), factory)

  fun internAndroidLibraryV2(v2Library: Library, factory: () -> IdeAndroidLibraryImpl): LibraryReference = lock.withLock {
      val (identity, isLibraryHigherPriority) = libraryIdentityProvider.associate(v2Library)
      return keyToLibraryReference.computeIfPresent(identity) { _ , existingReference ->
        if (isLibraryHigherPriority) {
          libraries[existingReference.libraryIndex] = factory()
        }
        existingReference
      } ?: createOrGetLibrary(identity, factory)
    }

  fun internJavaLibrary(key: LibraryIdentity, factory: () -> IdeJavaLibraryImpl) =
    createOrGetLibrary(key, factory)

  fun internJavaLibraryV2(library: Library, factory: () -> IdeJavaLibraryImpl) = lock.withLock {
    val (identity, _) = libraryIdentityProvider.associate(library)
    createOrGetLibrary(identity, factory)
  }

  fun internModuleLibrary(key: LibraryIdentity, factory: () -> IdeUnresolvedLibrary) = createOrGetLibrary(key, factory)
  fun internUnknownLibraryV2(library: Library, factory: () -> IdeUnresolvedUnknownLibrary) = lock.withLock {
    val (identity, _) = libraryIdentityProvider.associate(library)
    createOrGetLibrary(identity, factory)
  }

  /**
   * Finds an existing or creates a new library instance that match [key]. When creating a new library generates a unique library name
   * based on its artifact address.
   */
  private fun createOrGetLibrary(
    key: LibraryIdentity,
    modelFactory: () -> IdeUnresolvedLibrary
  ): LibraryReference {
    return lock.withLock {
      keyToLibraryReference.computeIfAbsent(key) {
        LibraryReference(libraries.size).also {
          libraries.add(modelFactory())
        }
      }
    }
  }


    // Note: Naming mechanism is going to change in the future when dependencies and libraries are separated. We will try to assign more
    // meaningful names to libraries representing different artifact variants under the same Gradle coordinates.
    private fun nameLibraries() {
      data class IdeModelAndReferenceAndIdentity (
        val ideModel: IdeArtifactLibrary,
        val reference: LibraryReference,
        val identity: LibraryIdentity,
      ) : Comparable<IdeModelAndReferenceAndIdentity> {
        override fun compareTo(other: IdeModelAndReferenceAndIdentity) =
          libraryIdentityProvider.compare(this.identity, other.identity)
      }

      keyToLibraryReference.entries.distinctBy { it.value }
        .mapNotNull {(key, reference) ->
          (lookup(reference) as? IdeArtifactLibrary)?.let { IdeModelAndReferenceAndIdentity(it, reference, key) }
        }.groupBy {
          convertToLibraryName(it.ideModel.artifactAddress, buildRootDirectory)
        }.forEach { (libraryName, unnamedLibrariesWithSameName) ->
          var counter = 0
          unnamedLibrariesWithSameName
            .sorted()
            .forEach { ( library, reference) ->
              val name = if (counter > 0) "$libraryName ($counter)" else libraryName
              val namedLibrary = when (library) {
                is IdeAndroidLibraryImpl -> library.copy(name = name)
                is IdeJavaLibraryImpl -> library.copy(name = name)
                else -> null
              }
              if (namedLibrary != null) {
                libraries[reference.libraryIndex] = namedLibrary
                counter++
              }
            }
        }
    }

  // This will be initialized at the end, at which point we also name the libraries if there are any duplicates
  fun createLibraryTable():  IdeUnresolvedLibraryTableImpl {
    nameLibraries()
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

  internal fun addProjectReferenceToArtifactClasspath(id: ClasspathIdentifier, pair: Pair<IdeDependenciesCoreDirect, Int>) {
    lock.withLock {
      projectReferenceToArtifactClasspathMap.putIfAbsent(id, pair)
    }
  }

  internal fun getProjectReferenceToArtifactClasspath(classpathId: ClasspathIdentifier) = lock.withLock {
    projectReferenceToArtifactClasspathMap[classpathId]
  }

  @VisibleForTesting
  fun getLibraryByKey(libraryIdentity: LibraryIdentity) = lock.withLock {
    keyToLibraryReference[libraryIdentity]
  }
}