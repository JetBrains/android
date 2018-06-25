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
import java.util.function.Consumer

abstract class PsDependencyCollection<out ModuleT : PsModule, DependencyT>(protected val parent: ModuleT) : PsModelCollection<DependencyT>

abstract class PsDeclaredDependencyCollection<out ModuleT, out LibraryDependencyT, out ModuleDependencyT>(parent: ModuleT)
  : PsDependencyCollection<ModuleT, PsDeclaredDependency>(parent)
  where ModuleT : PsModule,
        LibraryDependencyT : PsDeclaredDependency,
        LibraryDependencyT : PsLibraryDependency,
        LibraryDependencyT : PsDependency,
        ModuleDependencyT : PsDeclaredDependency,
        ModuleDependencyT : PsModuleDependency,
        ModuleDependencyT : PsDependency
{

  open fun initParsedDependencyCollection() {}
  abstract fun createLibraryDependency(artifactDependencyModel: ArtifactDependencyModel): LibraryDependencyT
  abstract fun createModuleDependency(moduleDependencyModel: ModuleDependencyModel): ModuleDependencyT

  private val moduleDependenciesByGradlePath = LinkedListMultimap.create<String, ModuleDependencyT>()!!
  private val libraryDependenciesBySpec = LinkedListMultimap.create<PsLibraryKey, LibraryDependencyT>()!!

  init {
    collectParsedDependencies()
  }

  fun isEmpty(): Boolean = moduleDependenciesByGradlePath.isEmpty && libraryDependenciesBySpec.isEmpty

  override fun forEach(consumer: Consumer<PsDeclaredDependency>) {
    libraryDependenciesBySpec.values().forEach(consumer)
    moduleDependenciesByGradlePath.values().forEach(consumer)
  }

  fun forEachModuleDependency(consumer: (ModuleDependencyT) -> Unit) {
    moduleDependenciesByGradlePath.values().forEach(consumer)
  }

  fun forEachLibraryDependency(consumer: (LibraryDependencyT) -> Unit) {
    libraryDependenciesBySpec.values().forEach(consumer)
  }

  fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT> =
    libraryDependenciesBySpec[PsLibraryKey(group.orEmpty(), name)].toList()

  fun findLibraryDependencies(libraryKey: PsLibraryKey): List<LibraryDependencyT> =
    libraryDependenciesBySpec[libraryKey].toList()

  fun reindex() {
    val libraryDependencies = libraryDependenciesBySpec.values().toList()
    val moduleDependencies = moduleDependenciesByGradlePath.values().toList()

    libraryDependenciesBySpec.clear()
    libraryDependencies.forEach { libraryDependenciesBySpec.put(it.spec.toLibraryKey(), it) }

    moduleDependenciesByGradlePath.clear()
    moduleDependencies.forEach { moduleDependenciesByGradlePath.put(it.gradlePath, it) }
  }

  private fun collectParsedDependencies() {
    initParsedDependencyCollection()
    collectParsedDependencies(parent.parsedDependencies)
  }

  private fun collectParsedDependencies(parsedDependencies: PsParsedDependencies) {
    parsedDependencies.forEachLibraryDependency { libraryDependency ->
      val declaredDependency = createLibraryDependency(libraryDependency)
      libraryDependenciesBySpec.put(declaredDependency.spec.toLibraryKey(), declaredDependency)
    }
    parsedDependencies.forEachModuleDependency { moduleDependency ->
      val gradlePath = moduleDependency.path().forceString()
      moduleDependenciesByGradlePath.put(gradlePath, createModuleDependency(moduleDependency)
      )
    }
  }
}

abstract class PsResolvedDependencyCollection<ContainerT, out ModuleT, LibraryDependencyT, ModuleDependencyT>(
  val container: ContainerT,
  module: ModuleT
) : PsDependencyCollection<ModuleT, PsDependency>(module)
  where ModuleT : PsModule,
        LibraryDependencyT : PsResolvedDependency,
        LibraryDependencyT : PsLibraryDependency,
        LibraryDependencyT : PsDependency,
        ModuleDependencyT : PsResolvedDependency,
        ModuleDependencyT : PsModuleDependency,
        ModuleDependencyT : PsDependency {

  abstract fun collectResolvedDependencies(container: ContainerT)

  protected val moduleDependenciesByGradlePath = LinkedListMultimap.create<String, ModuleDependencyT>()!!
  protected val libraryDependenciesBySpec = LinkedListMultimap.create<PsLibraryKey, LibraryDependencyT>()!!

  init {
    @Suppress("LeakingThis")
    collectResolvedDependencies(container)
  }

  fun isEmpty(): Boolean = moduleDependenciesByGradlePath.isEmpty && libraryDependenciesBySpec.isEmpty

  override fun forEach(consumer: Consumer<PsDependency>) {
    libraryDependenciesBySpec.values().forEach(consumer)
    moduleDependenciesByGradlePath.values().forEach(consumer)
  }

  fun forEachModuleDependency(consumer: (ModuleDependencyT) -> Unit) {
    moduleDependenciesByGradlePath.values().forEach(consumer)
  }

  fun forEachLibraryDependency(consumer: (LibraryDependencyT) -> Unit) {
    libraryDependenciesBySpec.values().forEach { consumer(it) }
  }

  fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT> =
    libraryDependenciesBySpec[PsLibraryKey(group.orEmpty(), name)].toList()

  protected fun addLibraryDependency(dependency: LibraryDependencyT) {
    libraryDependenciesBySpec.put(dependency.spec.toLibraryKey(), dependency)
  }

  protected fun addModuleDependency(gradlePath: String, dependency: ModuleDependencyT) {
    moduleDependenciesByGradlePath.put(gradlePath, dependency)
  }
}