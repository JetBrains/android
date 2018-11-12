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
import com.android.tools.idea.gradle.model.java.JarLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependencyCollection
import com.android.tools.idea.gradle.structure.model.matchJarDeclaredDependenciesIn
import com.android.tools.idea.gradle.structure.model.relativeFile

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

  override fun createLibraryDependency(artifactDependencyModel: ArtifactDependencyModel): PsDeclaredLibraryJavaDependency =
    PsDeclaredLibraryJavaDependency(parent, artifactDependencyModel)

  override fun createJarFileDependency(fileDependencyModel: FileDependencyModel): PsDeclaredJarJavaDependency =
    PsDeclaredJarJavaDependency(parent, fileDependencyModel)

  override fun createJarFileTreeDependency(fileTreeDependencyModel: FileTreeDependencyModel): PsDeclaredJarJavaDependency =
    PsDeclaredJarJavaDependency(parent, fileTreeDependencyModel)

  override fun createModuleDependency(moduleDependencyModel: ModuleDependencyModel): PsDeclaredModuleJavaDependency =
    PsDeclaredModuleJavaDependency(parent, moduleDependencyModel)
}

class PsResolvedJavaDependencyCollection(module: PsJavaModule)
  : PsResolvedDependencyCollection<PsJavaModule, PsJavaModule, PsResolvedLibraryJavaDependency,
  PsResolvedJarJavaDependency, PsResolvedModuleJavaDependency>(
  container = module,
  module = module
),
    PsJavaDependencyCollection<PsResolvedLibraryJavaDependency, PsResolvedJarJavaDependency, PsResolvedModuleJavaDependency> {
  override fun collectResolvedDependencies(container: PsJavaModule) {
    val gradleModel = parent.resolvedModel
    gradleModel
      ?.jarLibraryDependencies
      ?.filter { it.scope.equals("COMPILE", ignoreCase = true) || it.scope.equals("PROVIDED", ignoreCase = true) }
      ?.forEach { addLibrary(it) }
    gradleModel
      ?.javaModuleDependencies
      ?.forEach { moduleDependency ->
      parent.parent.findModuleByGradlePath(moduleDependency.moduleId.replaceBefore(':', "").substring(1))
        ?.let { module -> addModule(module, moduleDependency.scope ?: "") }
    }
  }

  private fun addLibrary(library: JarLibraryDependency) {
    val parsedDependencies = parent.dependencies
    val group = library.moduleVersion?.group ?: ""
    val name = library.moduleVersion?.name
    val version = library.moduleVersion?.version
    val coordinates = if (name != null && version != null) GradleCoordinate(group, name, version) else null
    if (coordinates != null) {
      val matchingDeclaredDependencies = parsedDependencies
        .findLibraryDependencies(coordinates.groupId, coordinates.artifactId)
        // TODO(b/110774403): Support Java module dependency scopes.
        .filter { library.moduleVersion != null }
      addLibraryDependency(PsResolvedLibraryJavaDependency(parent, library, matchingDeclaredDependencies).also {
        library.binaryPath?.let { file ->
          it.setDependenciesFromPomFile(parent.parent.pomDependencyCache.getPomDependencies(file))
        }
      })
    } else {
      val artifactCanonicalFile = library.binaryPath?.canonicalFile ?: return
      val matchingDeclaredDependencies =
        matchJarDeclaredDependenciesIn(parsedDependencies, artifactCanonicalFile)
      val path = parent.relativeFile(artifactCanonicalFile)
      val jarDependency = PsResolvedJarJavaDependency(parent, this, path.path.orEmpty(), matchingDeclaredDependencies)
      addJarDependency(jarDependency)
    }
  }


  private fun addModule(module: PsModule, scope: String) {
    val gradlePath = module.gradlePath!!
    val matchingParsedDependencies =
      parent
        .dependencies
        .findModuleDependencies(gradlePath)
    addModuleDependency(PsResolvedModuleJavaDependency(parent, gradlePath, scope, module, matchingParsedDependencies))
  }
}