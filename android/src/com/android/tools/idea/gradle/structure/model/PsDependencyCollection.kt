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
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.google.common.collect.LinkedListMultimap
import java.io.File
import java.lang.IllegalStateException

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

  open fun onDependencyAdded(dependency: PsBaseDependency) = Unit
  open fun onCleared() = Unit

  override fun findLibraryDependencies(group: String?, name: String): List<LibraryDependencyT> =
    libraryDependenciesBySpec[PsLibraryKey(group.orEmpty(), name)].toList()

  override fun findLibraryDependencies(libraryKey: PsLibraryKey): List<LibraryDependencyT> =
    libraryDependenciesBySpec[libraryKey].toList()

  protected fun addLibraryDependency(dependency: LibraryDependencyT) {
    libraryDependenciesBySpec.put(dependency.spec.toLibraryKey(), dependency)
    onDependencyAdded(dependency)
  }

  protected fun addJarDependency(dependency: JarDependencyT) {
    jarDependenciesByPath.put(dependency.filePath, dependency)
    onDependencyAdded(dependency)
  }

  protected fun addModuleDependency(dependency: ModuleDependencyT) {
    moduleDependenciesByGradlePath.put(dependency.gradlePath, dependency)
    onDependencyAdded(dependency)
  }

  protected fun clear() {
    libraryDependenciesBySpec.clear()
    moduleDependenciesByGradlePath.clear()
    jarDependenciesByPath.clear()
    onCleared()
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

fun ArtifactDependencyModel.toKey(): String =
  PsArtifactDependencySpec.create(group().toString(), name().forceString(), version().toString()).toString()

fun FileDependencyModel.toKey(): String = file().asString().orEmpty()

fun FileTreeDependencyModel.toKey(): String = dir().asString().orEmpty()

fun ModuleDependencyModel.toKey(): String = path().asString().orEmpty()

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
  private val parsedModelToDependency = mutableMapOf<DependencyModel, PsBaseDependency>()

  abstract fun createOrUpdateLibraryDependency(
    existing: LibraryDependencyT?,
    artifactDependencyModel: ArtifactDependencyModel
  ): LibraryDependencyT

  abstract fun createOrUpdateJarFileDependency(existing: JarDependencyT?, fileDependencyModel: FileDependencyModel): JarDependencyT

  abstract fun createOrUpdateJarFileTreeDependency(
    existing: JarDependencyT?,
    fileTreeDependencyModel: FileTreeDependencyModel
  ): JarDependencyT

  abstract fun createOrUpdateModuleDependency(existing: ModuleDependencyT?, moduleDependencyModel: ModuleDependencyModel): ModuleDependencyT

  init {
    refresh()
  }

  fun refresh() {
    refreshParsedDependencies(parent.parsedDependencies)
  }

  @Suppress("UNCHECKED_CAST")
  private fun refreshParsedDependencies(parsedDependencies: PsParsedDependencies) {
    val existingItems = parsedModelToDependency.values.map { it as PsDeclaredDependency }.groupBy { it.toKey() to it.configurationName }
    val newNextIndexes = mutableMapOf<Pair<String, String>, Int>()
    clear()
    parsedDependencies.forEachLibraryDependency { dependency ->
      val key = dependency.toKey() to dependency.configurationName()
      val index = newNextIndexes.getOrDefault(key, 0)
      newNextIndexes[key] = index + 1
      val existing = existingItems[key]?.getOrNull(index) as? LibraryDependencyT
      addLibraryDependency(createOrUpdateLibraryDependency(existing, dependency))
    }
    parsedDependencies.forEachFileDependency { dependency ->
      val key = dependency.toKey() to dependency.configurationName()
      val index = newNextIndexes.getOrDefault(key, 0)
      newNextIndexes[key] = index + 1
      val existing = existingItems[key]?.getOrNull(index) as? JarDependencyT
      addJarDependency(createOrUpdateJarFileDependency(existing, dependency))
    }
    parsedDependencies.forEachFileTreeDependency { dependency ->
      val key = dependency.toKey() to dependency.configurationName()
      val index = newNextIndexes.getOrDefault(key, 0)
      newNextIndexes[key] = index + 1
      val existing = existingItems[key]?.getOrNull(index) as? JarDependencyT
      addJarDependency(createOrUpdateJarFileTreeDependency(existing, dependency))
    }
    parsedDependencies.forEachModuleDependency { dependency ->
      val key = dependency.toKey() to dependency.configurationName()
      val index = newNextIndexes.getOrDefault(key, 0)
      newNextIndexes[key] = index + 1
      val existing = existingItems[key]?.getOrNull(index) as? ModuleDependencyT
      addModuleDependency(createOrUpdateModuleDependency(existing, dependency))
    }
  }

  override fun onDependencyAdded(dependency: PsBaseDependency) {
    val parsedModel = (dependency as PsDeclaredDependency).parsedModel
    if (parsedModelToDependency.put(parsedModel, dependency) != null) {
      throw IllegalStateException("Duplicate dependency for model: $parsedModel")
    }
  }

  override fun onCleared() {
    parsedModelToDependency.clear()
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
    caninicalResolvedProbe.let { artifactCanonicalFile.startsWith(it) }
  }
