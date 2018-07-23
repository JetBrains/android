/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.google.common.collect.LinkedListMultimap

interface PsDependencyCollection<out ModuleT, out LibraryDependencyT, out ModuleDependencyT>
  where ModuleT : PsModule,
        LibraryDependencyT : PsLibraryDependency,
        ModuleDependencyT : PsModuleDependency
{
  val parent: ModuleT
  val libraries: List<LibraryDependencyT>
  val modules: List<ModuleDependencyT>

  fun isEmpty(): Boolean

  fun findModuleDependencies(gradlePath: String): List<ModuleDependencyT>
  fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT>
  fun findLibraryDependencies(libraryKey: PsLibraryKey): List<LibraryDependencyT>

  fun forEachModuleDependency(consumer: (ModuleDependencyT) -> Unit) = modules.forEach(consumer)
  fun forEachLibraryDependency(consumer: (LibraryDependencyT) -> Unit) = libraries.forEach(consumer)
}

abstract class PsDependencyCollectionBase<out ModuleT, LibraryDependencyT, ModuleDependencyT>(
  override val parent: ModuleT
) : PsDependencyCollection<ModuleT, LibraryDependencyT, ModuleDependencyT>
  where ModuleT : PsModule,
        LibraryDependencyT : PsLibraryDependency,
        ModuleDependencyT : PsModuleDependency {
  private val libraryDependenciesBySpec = LinkedListMultimap.create<PsLibraryKey, LibraryDependencyT>()!!
  private val moduleDependenciesByGradlePath = LinkedListMultimap.create<String, ModuleDependencyT>()!!

  override fun isEmpty(): Boolean = moduleDependenciesByGradlePath.isEmpty && libraryDependenciesBySpec.isEmpty
  override val libraries: List<LibraryDependencyT> get() = libraryDependenciesBySpec.values()
  override val modules: List<ModuleDependencyT> get() = moduleDependenciesByGradlePath.values()

  override fun findModuleDependencies(gradlePath: String): List<ModuleDependencyT> =
    moduleDependenciesByGradlePath[gradlePath].toList()

  override fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT> =
    libraryDependenciesBySpec[PsLibraryKey(group.orEmpty(), name)].toList()

  override fun findLibraryDependencies(libraryKey: PsLibraryKey): List<LibraryDependencyT> =
    libraryDependenciesBySpec[libraryKey].toList()

  protected fun addLibraryDependency(dependency: LibraryDependencyT) {
    libraryDependenciesBySpec.put(dependency.spec.toLibraryKey(), dependency)
  }

  protected fun addModuleDependency(dependency: ModuleDependencyT) {
    moduleDependenciesByGradlePath.put(dependency.gradlePath, dependency)
  }

  fun reindex() {
    val libraryDependencies = libraryDependenciesBySpec.values().toList()
    val moduleDependencies = moduleDependenciesByGradlePath.values().toList()

    libraryDependenciesBySpec.clear()
    libraryDependencies.forEach { libraryDependenciesBySpec.put(it.spec.toLibraryKey(), it) }

    moduleDependenciesByGradlePath.clear()
    moduleDependencies.forEach { moduleDependenciesByGradlePath.put(it.gradlePath, it) }
  }
}

abstract class PsDeclaredDependencyCollection<out ModuleT, LibraryDependencyT, ModuleDependencyT>(parent: ModuleT)
  : PsDependencyCollectionBase<ModuleT, LibraryDependencyT, ModuleDependencyT>(parent)
  where ModuleT : PsModule,
        LibraryDependencyT : PsDeclaredDependency,
        LibraryDependencyT : PsLibraryDependency,
        ModuleDependencyT : PsDeclaredDependency,
        ModuleDependencyT : PsModuleDependency
{
  open fun initParsedDependencyCollection() {}
  abstract fun createLibraryDependency(artifactDependencyModel: ArtifactDependencyModel): LibraryDependencyT
  abstract fun createModuleDependency(moduleDependencyModel: ModuleDependencyModel): ModuleDependencyT

  init {
    collectParsedDependencies()
  }

  private fun collectParsedDependencies() {
    initParsedDependencyCollection()
    collectParsedDependencies(parent.parsedDependencies)
  }

  private fun collectParsedDependencies(parsedDependencies: PsParsedDependencies) {
    parsedDependencies.forEachLibraryDependency { libraryDependency ->
      addLibraryDependency(createLibraryDependency(libraryDependency))
    }
    parsedDependencies.forEachModuleDependency { moduleDependency ->
      addModuleDependency(createModuleDependency(moduleDependency))
    }
  }
}

abstract class PsResolvedDependencyCollection<ContainerT, out ModuleT, LibraryDependencyT, ModuleDependencyT>(
  val container: ContainerT,
  module: ModuleT
) : PsDependencyCollectionBase<ModuleT, LibraryDependencyT, ModuleDependencyT>(module)
  where ModuleT : PsModule,
        LibraryDependencyT : PsResolvedDependency,
        LibraryDependencyT : PsLibraryDependency,
        ModuleDependencyT : PsResolvedDependency,
        ModuleDependencyT : PsModuleDependency {

  abstract fun collectResolvedDependencies(container: ContainerT)

  init {
    @Suppress("LeakingThis")
    collectResolvedDependencies(container)
  }
}
