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
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.google.common.collect.LinkedListMultimap
import java.io.File

interface PsDependencyCollection<out ModuleT, out LibraryDependencyT, out JarDependencyT, out ModuleDependencyT>
  where ModuleT : PsModule,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsModuleDependency
{
  val parent: ModuleT
  val libraries: List<LibraryDependencyT>
  val jars: List<JarDependencyT>
  val modules: List<ModuleDependencyT>
  val items: List<PsBaseDependency> get() = modules + libraries + jars

  fun isEmpty(): Boolean

  fun findModuleDependencies(gradlePath: String): List<ModuleDependencyT>
  fun findLibraryDependencies(compactNotation: String): List<LibraryDependencyT>
  fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT>
  fun findLibraryDependencies(libraryKey: PsLibraryKey): List<LibraryDependencyT>
  fun findJarDependencies(filePath: String): List<JarDependencyT>

  fun forEachModuleDependency(consumer: (ModuleDependencyT) -> Unit) = modules.forEach(consumer)
  fun forEachLibraryDependency(consumer: (LibraryDependencyT) -> Unit) = libraries.forEach(consumer)
  fun forEachJarDependency(consumer: (JarDependencyT) -> Unit) = jars.forEach(consumer)
}

abstract class PsDependencyCollectionBase<out ModuleT, LibraryDependencyT, JarDependencyT, ModuleDependencyT>(
  override val parent: ModuleT
) : PsDependencyCollection<ModuleT, LibraryDependencyT, JarDependencyT, ModuleDependencyT>
  where ModuleT : PsModule,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsModuleDependency {
  private val libraryDependenciesBySpec = LinkedListMultimap.create<PsLibraryKey, LibraryDependencyT>()!!
  private val jarDependenciesByPath = LinkedListMultimap.create<String, JarDependencyT>()!!
  private val moduleDependenciesByGradlePath = LinkedListMultimap.create<String, ModuleDependencyT>()!!

  override fun isEmpty(): Boolean =
    moduleDependenciesByGradlePath.isEmpty && libraryDependenciesBySpec.isEmpty && jarDependenciesByPath.isEmpty
  override val libraries: List<LibraryDependencyT> get() = libraryDependenciesBySpec.values()
  override val jars: List<JarDependencyT> get() = jarDependenciesByPath.values()
  override val modules: List<ModuleDependencyT> get() = moduleDependenciesByGradlePath.values()

  override fun findModuleDependencies(gradlePath: String): List<ModuleDependencyT> =
    moduleDependenciesByGradlePath[gradlePath].toList()

  override fun findJarDependencies(filePath: String): List<JarDependencyT> =
    jarDependenciesByPath[filePath].toList()

  // FIXME(xof): untangle the confusion between PsArtifactDependencySpec (which can have versions) and PsLibraryKey (which can't)
  override fun findLibraryDependencies(compactNotation: String): List<LibraryDependencyT> {
    val spec = PsArtifactDependencySpec.create(compactNotation) ?: return listOf()
    return libraryDependenciesBySpec[PsLibraryKey(spec.group.orEmpty(), spec.name)]
      .filter { it.spec == spec }
      .toList()
  }

  override fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT> =
    libraryDependenciesBySpec[PsLibraryKey(group.orEmpty(), name)].toList()

  override fun findLibraryDependencies(libraryKey: PsLibraryKey): List<LibraryDependencyT> =
    libraryDependenciesBySpec[libraryKey].toList()

  protected fun addLibraryDependency(dependency: LibraryDependencyT) {
    libraryDependenciesBySpec.put(dependency.spec.toLibraryKey(), dependency)
  }

  protected fun addJarDependency(dependency: JarDependencyT) {
    jarDependenciesByPath.put(dependency.filePath, dependency)
  }

  protected fun addModuleDependency(dependency: ModuleDependencyT) {
    moduleDependenciesByGradlePath.put(dependency.gradlePath, dependency)
  }

  fun reindex() {
    val libraryDependencies = libraryDependenciesBySpec.values().toList()
    val jarDependencies = jarDependenciesByPath.values().toList()
    val moduleDependencies = moduleDependenciesByGradlePath.values().toList()

    libraryDependenciesBySpec.clear()
    libraryDependencies.forEach { libraryDependenciesBySpec.put(it.spec.toLibraryKey(), it) }

    jarDependenciesByPath.clear()
    jarDependencies.forEach { jarDependenciesByPath.put(it.filePath, it) }

    moduleDependenciesByGradlePath.clear()
    moduleDependencies.forEach { moduleDependenciesByGradlePath.put(it.gradlePath, it) }
  }
}

abstract class PsDeclaredDependencyCollection<out ModuleT, LibraryDependencyT, JarDependencyT, ModuleDependencyT>(parent: ModuleT)
  : PsDependencyCollectionBase<ModuleT, LibraryDependencyT, JarDependencyT, ModuleDependencyT>(parent)
  where ModuleT : PsModule,
        LibraryDependencyT : PsDeclaredDependency,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsDeclaredDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsDeclaredDependency,
        ModuleDependencyT : PsModuleDependency
{
  open fun initParsedDependencyCollection() {}
  abstract fun createLibraryDependency(artifactDependencyModel: ArtifactDependencyModel): LibraryDependencyT
  abstract fun createJarFileDependency(fileDependencyModel: FileDependencyModel): JarDependencyT
  abstract fun createJarFileTreeDependency(fileTreeDependencyModel: FileTreeDependencyModel): JarDependencyT
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
    parsedDependencies.forEachFileDependency { fileDependency ->
      addJarDependency(createJarFileDependency(fileDependency))
    }
    parsedDependencies.forEachFileTreeDependency { fileTreeDependency ->
      addJarDependency(createJarFileTreeDependency(fileTreeDependency))
    }
    parsedDependencies.forEachModuleDependency { moduleDependency ->
      addModuleDependency(createModuleDependency(moduleDependency))
    }
  }
}

abstract class PsResolvedDependencyCollection<ContainerT, out ModuleT, LibraryDependencyT, JarDependencyT, ModuleDependencyT>(
  val container: ContainerT,
  module: ModuleT
) : PsDependencyCollectionBase<ModuleT, LibraryDependencyT, JarDependencyT, ModuleDependencyT>(module)
  where ModuleT : PsModule,
        LibraryDependencyT : PsResolvedDependency,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsResolvedDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsResolvedDependency,
        ModuleDependencyT : PsModuleDependency {

  abstract fun collectResolvedDependencies(container: ContainerT)

  init {
    @Suppress("LeakingThis")
    collectResolvedDependencies(container)
  }
}

fun <T : PsDeclaredJarDependency> PsResolvedDependencyCollection<*, *, *, *, *>.matchJarDeclaredDependenciesIn(
  parsedDependencies: PsDeclaredDependencyCollection<*, *, T, *>,
  artifactCanonicalFile: File
): List<T> = parsedDependencies
  .jars
  .filter { probe ->
    val probleFile = File(probe.filePath)
    val resolvedProbe = parent.resolveFile(probleFile)
    val caninicalResolvedProbe = resolvedProbe.canonicalFile
    caninicalResolvedProbe?.let { artifactCanonicalFile.startsWith(it) } == true
  }