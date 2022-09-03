/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.java

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependencyCollection
import com.android.tools.idea.gradle.structure.model.matchJarDeclaredDependenciesIn
import com.android.tools.idea.gradle.structure.model.relativeFile
import com.intellij.openapi.externalSystem.model.project.dependencies.ArtifactDependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.FileCollectionDependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.ReferenceNode
import com.intellij.openapi.externalSystem.model.project.dependencies.UnknownDependencyNode
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.io.File

interface PsJavaDependencyCollection<out LibraryDependencyT, out JarDependencyT, out ModuleDependencyT>
  : PsDependencyCollection<PsJavaModule, LibraryDependencyT, JarDependencyT, ModuleDependencyT>
  where LibraryDependencyT : PsJavaDependency,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsJavaDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsJavaDependency,
        ModuleDependencyT : PsModuleDependency {
  override val items: List<PsJavaDependency> get() = modules + libraries + jars
}

class PsDeclaredJavaDependencyCollection(parent: PsJavaModule)
  : PsDeclaredDependencyCollection<PsJavaModule, PsDeclaredLibraryJavaDependency,
  PsDeclaredJarJavaDependency, PsDeclaredModuleJavaDependency>(parent),
    PsJavaDependencyCollection<PsDeclaredLibraryJavaDependency, PsDeclaredJarJavaDependency, PsDeclaredModuleJavaDependency> {

  override fun createOrUpdateLibraryDependency(
    existing: PsDeclaredLibraryJavaDependency?,
    artifactDependencyModel: ArtifactDependencyModel
  ): PsDeclaredLibraryJavaDependency =
    (existing ?: PsDeclaredLibraryJavaDependency(parent)).apply {init(artifactDependencyModel)}

  override fun createOrUpdateJarFileDependency(
    existing: PsDeclaredJarJavaDependency?,
    fileDependencyModel: FileDependencyModel
  ): PsDeclaredJarJavaDependency =
    (existing ?: PsDeclaredJarJavaDependency(parent)).apply {init(fileDependencyModel)}

  override fun createOrUpdateJarFileTreeDependency(
    existing: PsDeclaredJarJavaDependency?,
    fileTreeDependencyModel: FileTreeDependencyModel
  ): PsDeclaredJarJavaDependency =
    (existing ?: PsDeclaredJarJavaDependency(parent)).apply {init(fileTreeDependencyModel)}

  override fun createOrUpdateModuleDependency(
    existing: PsDeclaredModuleJavaDependency?,
    moduleDependencyModel: ModuleDependencyModel
  ): PsDeclaredModuleJavaDependency =
    (existing ?: PsDeclaredModuleJavaDependency(parent)).apply {init(moduleDependencyModel)}
}

class PsResolvedJavaDependencyCollection(module: PsJavaModule)
  : PsResolvedDependencyCollection<PsJavaModule, PsJavaModule, PsResolvedLibraryJavaDependency,
  PsResolvedJarJavaDependency, PsResolvedModuleJavaDependency>(
  container = module,
  module = module
),
    PsJavaDependencyCollection<PsResolvedLibraryJavaDependency, PsResolvedJarJavaDependency, PsResolvedModuleJavaDependency> {

  override fun collectResolvedDependencies(container: PsJavaModule) {
    val gradleDependencyGraph = parent.resolvedModelDependencies

    fun processFile(file: File) {
      val artifactCanonicalFile = file.canonicalFile ?: return
      val matchingDeclaredDependencies =
        matchJarDeclaredDependenciesIn(parent.dependencies, artifactCanonicalFile)
      val path = parent.relativeFile(artifactCanonicalFile)
      val jarDependency = PsResolvedJarJavaDependency(parent, this, path.path.orEmpty(), matchingDeclaredDependencies)
      addJarDependency(jarDependency)
    }

    val dependencyNodeMap: Long2ObjectMap<DependencyNode> = Long2ObjectOpenHashMap()
    val componentDependencies = gradleDependencyGraph?.componentsDependencies?.firstOrNull { it.componentName == "main" } ?: return
    // The code below creates lazily expanding nodes. In order to resolve an arbitrary ReferenceNode we must perform
    // a complete traversal here.
    populateDependencyNodeMap(componentDependencies.compileDependenciesGraph, dependencyNodeMap)

    componentDependencies.compileDependenciesGraph.dependencies.forEach { maybeReference ->
      @Suppress("MoveVariableDeclarationIntoWhen")
      val dependency = if (maybeReference is ReferenceNode) dependencyNodeMap[maybeReference.id] else maybeReference
      when(dependency) {
        is ArtifactDependencyNode -> {
          addLibraryDependency(processLibraryNode(dependency, dependencyNodeMap))
        }
        is FileCollectionDependencyNode -> {
          // The format for dependency.path is the same as org.gradle.api.file.FileCollection#getAsPath
          // That is a list of file paths seperated by the path separator
          dependency.path.split(File.pathSeparator).forEach {
            processFile(File(it))
          }
        }
        is ProjectDependencyNode -> {
          val module = parent.parent.findModuleByGradlePath(dependency.projectPath)
          if (module != null) {
            // All Java dependencies are currently compile scope, this value is currently not used.
            addModule(module, "COMPILE")
          }
        }
        is UnknownDependencyNode -> Unit
      }
    }
  }


  /**
   * Traverses the complete tree of dependencies starting at [dependency] and populates all none [ReferenceNode]s
   * within the [dependencyNodeMap].
   */
  private fun populateDependencyNodeMap(dependency: DependencyNode, dependencyNodeMap: Long2ObjectMap<DependencyNode>) {
    val queue = ArrayDeque<DependencyNode>()
    queue.add(dependency)
    while (queue.isNotEmpty()) {
      val item = queue.removeFirst()
      if (item !is ReferenceNode) {
        dependencyNodeMap[item.id] = item
        queue.addAll(item.dependencies)
      }
    }
  }

  private fun processLibraryNode(library: ArtifactDependencyNode, dependencyNodeMap: Long2ObjectMap<DependencyNode>): PsResolvedLibraryJavaDependency {
    val parsedDependencies = parent.dependencies
    val group = library.group
    val name = library.module
    val version = library.version
    val coordinates = GradleCoordinate(group, name, version)
    val matchingDeclaredDependencies = parsedDependencies
      .findLibraryDependencies(coordinates.groupId, coordinates.artifactId)
    val resolvedDependencies = library.dependencies
      .map { if (it is ReferenceNode) dependencyNodeMap[it.id] else it }
      .filterIsInstance<ArtifactDependencyNode>()
    // TODO(b/110774403): Support Java module dependency scopes.
    return PsResolvedLibraryJavaDependency(parent, library, matchingDeclaredDependencies) {
      resolvedDependencies.map { processLibraryNode(it, dependencyNodeMap) }.toSet()
    }
  }

  private fun addModule(module: PsModule, scope: String) {
    val gradlePath = module.gradlePath
    val matchingParsedDependencies =
      parent
        .dependencies
        .findModuleDependencies(gradlePath)
    addModuleDependency(PsResolvedModuleJavaDependency(parent, gradlePath, scope, module, matchingParsedDependencies))
  }
}