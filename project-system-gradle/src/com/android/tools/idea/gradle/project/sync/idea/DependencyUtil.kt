/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.SdkConstants.ANDROIDX_ANNOTATIONS_ARTIFACT
import com.android.SdkConstants.ANNOTATIONS_LIB_ARTIFACT
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeArtifactDependency
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeDependency
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.buildId
import com.android.tools.idea.gradle.model.projectPath
import com.android.tools.idea.gradle.model.sourceSet
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.LibraryPathType.BINARY
import com.intellij.openapi.externalSystem.model.project.LibraryPathType.DOC
import com.intellij.openapi.externalSystem.model.project.LibraryPathType.SOURCE
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

private val LOG = Logger.getInstance(AndroidDependenciesSetupContext::class.java)

typealias SourcesPath = File?
typealias JavadocPath = File?
typealias SampleSourcePath = File?
typealias ArtifactId = String

data class AdditionalArtifactsPaths(val sources: SourcesPath, val javadoc: JavadocPath, val sampleSources: SampleSourcePath)

/**
 * Computes the module ID for the given target of this [library]. We want to be able to reuse the
 * maps of module ID to [ModuleData] in the [GradleExecutionWorkspace], in order to do this we need to be able
 * to reconstruct the module ID key. It is initially computed in [GradleProjectResolverUtil.getModuleId] it's
 * format is currently as follows:
 *   1 - For projects under the main build,  the module ID will just by the Gradle path to the project.
 *       For example ":app", ":lib", ":app:nested:deepNested"
 *   2 - For other project not under the main build,  the module ID will be the name of the Gradle root
 *       project followed by the full Gradle path.
 *       For example "IncludedProject:app", "OtherBuild:lib:"
 *
 *
 */
fun computeModuleIdForLibraryTarget(
  library: IdeModuleDependency
): GradleSourceSetProjectPath {
  val libraryBuildId = toSystemIndependentName(library.buildId)
  return GradleSourceSetProjectPath(libraryBuildId, library.projectPath, library.sourceSet)
}

private class AndroidDependenciesSetupContext(
  private val moduleDataNode: DataNode<out ModuleData>,
  private val projectDataNode: DataNode<ProjectData>,
  private val gradleProjectPathToModuleData: (GradleSourceSetProjectPath) -> ModuleData?,
  private val additionalArtifactsMapper: (ArtifactId) -> AdditionalArtifactsPaths?,
  private val processedLibraries: MutableMap<String, LibraryDependencyData>,
  private val processedModuleDependencies: MutableMap<GradleProjectPath, ModuleDependencyData>
) {

  private abstract inner class WorkItem<D : IdeDependency<*>, T> {
    abstract fun isAlreadyProcessed(): Boolean
    protected abstract fun setupTargetData(): T
    protected abstract fun createDependencyData(targetData: T, scope: DependencyScope)

    fun setup(scope: DependencyScope) {
      val targetData: T = setupTargetData()
      createDependencyData(targetData, scope)
    }
  }

  private abstract inner class LibraryWorkItem<D : IdeArtifactDependency<*>>(protected val library: D) : WorkItem<D, LibraryData>() {
    protected val libraryName = library.target.name

    final override fun isAlreadyProcessed(): Boolean = processedLibraries.containsKey(libraryName)

    final override fun createDependencyData(targetData: LibraryData, scope: DependencyScope) {
      // Finally create the LibraryDependencyData
      val libraryDependencyData =
        LibraryDependencyData(moduleDataNode.data, targetData, maybeLinkLibraryAndWorkOutLibraryLevel(projectDataNode, targetData))
      libraryDependencyData.scope = scope
      libraryDependencyData.isExported = false
      processedLibraries[libraryName] = libraryDependencyData
    }
  }

  private inner class JavaLibraryWorkItem(library: IdeJavaLibraryDependency) : LibraryWorkItem<IdeJavaLibraryDependency>(library) {
    override fun setupTargetData(): LibraryData {
      val libraryData = LibraryData(GradleConstants.SYSTEM_ID, libraryName, false)
      ArtifactDependencySpec.create(library.target.artifactAddress)?.also {
        libraryData.setGroup(it.group)
        libraryData.artifactId = it.name
        libraryData.version = it.version
      }

      libraryData.addPath(BINARY, library.target.artifact.absolutePath)
      setupSourcesAndJavaDocsFrom(libraryData, libraryName)
      return libraryData
    }
  }

  private inner class AndroidLibraryWorkItem(library: IdeAndroidLibraryDependency) : LibraryWorkItem<IdeAndroidLibraryDependency>(library) {
    override fun setupTargetData(): LibraryData {
      val libraryData = LibraryData(GradleConstants.SYSTEM_ID, libraryName, false)
      val target = library.target
      target.compileJarFiles.filter { it.exists() }.forEach { compileJar ->
        libraryData.addPath(BINARY, compileJar.path)
      }
      if (target.resFolder.exists()) {
        libraryData.addPath(BINARY, target.resFolder.path)
      }
      if (target.manifest.exists()) {
        libraryData.addPath(BINARY, target.manifest.path)
      }
      setupAnnotationsFrom(libraryData, libraryName, target)
      setupSourcesAndJavaDocsFrom(libraryData, libraryName)
      return libraryData
    }
  }

  private inner class ModuleLibraryWorkItem(
    val targetModuleGradlePath: GradleProjectPath,
    val targetData: ModuleData
  ) : WorkItem<IdeModuleDependency, Unit>() {
    override fun isAlreadyProcessed(): Boolean = processedModuleDependencies.containsKey(targetModuleGradlePath)

    override fun setupTargetData() {
      // Module has been already set up.
    }

    override fun createDependencyData(target: Unit, scope: DependencyScope) {
      // Skip if the dependency is a dependency on itself, this can be produced by Gradle when the a module
      // dependency on the module in a different scope ie test code depending on the production code.
      // In IDEA this dependency is implicit.
      // TODO(rework-14): Do we need this special case, is it handled by IDEAs data service.
      // See https://issuetracker.google.com/issues/68016998.
      if (targetData == moduleDataNode.data) return
      val moduleDependencyData = ModuleDependencyData(moduleDataNode.data, targetData)
      moduleDependencyData.scope = scope
      moduleDependencyData.isExported = false
      processedModuleDependencies[targetModuleGradlePath] = moduleDependencyData
    }
  }

  private fun createModuleLibraryWorkItem(library: IdeModuleDependency): ModuleLibraryWorkItem? {
    if (library.projectPath.isEmpty()) return null
    val targetModuleGradlePath = computeModuleIdForLibraryTarget(library)
    val targetData = gradleProjectPathToModuleData(targetModuleGradlePath)
    if (targetData == null) {
      if (IdeInfo.getInstance().isAndroidStudio) {
        LOG.warnInProduction(ExternalSystemException("Cannot find module with id: $targetModuleGradlePath"))
      }
      return null;
    }
    return ModuleLibraryWorkItem(targetModuleGradlePath, targetData)
  }

  fun setupForArtifact(artifact: IdeBaseArtifact, scope: DependencyScope) {
    val dependencies = artifact.compileClasspath

    // TODO(rework-12): Sort out the order of dependencies.
    (dependencies.javaLibraries.map(::JavaLibraryWorkItem) +
     dependencies.androidLibraries.map(::AndroidLibraryWorkItem) +
     dependencies.moduleDependencies.mapNotNull(::createModuleLibraryWorkItem)
    )
      .forEach { workItem ->
        if (workItem.isAlreadyProcessed()) return@forEach
        workItem.setup(scope)
      }
  }

  private fun setupSourcesAndJavaDocsFrom(
    libraryData: LibraryData,
    libraryName: String
  ) {
    val (sources, javadocs, sampleSources) =
      additionalArtifactsMapper(stripExtensionAndClassifier(libraryName)) ?: return

    sources?.also { libraryData.addPath(SOURCE, it.absolutePath) }
    javadocs?.also { libraryData.addPath(DOC, it.absolutePath) }
    sampleSources?.also { libraryData.addPath(SOURCE, it.absolutePath) }
  }

  private fun setupAnnotationsFrom(
    libraryData: LibraryData,
    libraryName: String,
    library: IdeAndroidLibrary
  ) {
    // Add external annotations.
    // TODO: Why do we only do this for Android modules?
    // TODO: Add this to the model instead!
    (library.compileJarFiles + library.resFolder).distinct()
      .forEach { binaryPath ->
      if (binaryPath.name == FD_RES) {
        val annotationsFile = binaryPath.parentFile.resolve(FN_ANNOTATIONS_ZIP)
        if (annotationsFile.isFile) {
          libraryData.addPath(LibraryPathType.ANNOTATION, annotationsFile.absolutePath)
        }
      }
      else if ((libraryName.startsWith(ANDROIDX_ANNOTATIONS_ARTIFACT) ||
                libraryName.startsWith(ANNOTATIONS_LIB_ARTIFACT)) &&
               binaryPath.name.endsWith(DOT_JAR)) {
        val annotationsFile = binaryPath.let { it.parentFile.resolve(it.name.removeSuffix(DOT_JAR) + "-" + FN_ANNOTATIONS_ZIP)}
        if (annotationsFile.isFile) {
          libraryData.addPath(LibraryPathType.ANNOTATION, annotationsFile.absolutePath)
        }
      }
    }
  }
}

fun DataNode<ModuleData>.setupAndroidDependenciesForMpss(
  gradleProjectPathToModuleData: (GradleSourceSetProjectPath) -> ModuleData?,
  additionalArtifactsMapper: (ArtifactId) -> AdditionalArtifactsPaths?,
  variant: IdeVariant,
  project: Project?
) {
  // The DataNode tree should have a ProjectData node as a parent of the ModuleData node. We don't throw an
  // exception here as other intellij plugins can manipulate the tree, we do not want to break an import
  // completely due to a badly behaved plugin.
  @Suppress("UNCHECKED_CAST") val projectDataNode = parent as? DataNode<ProjectData>
  if (projectDataNode == null) {
    LOG.error(
      "Couldn't find project data for module ${data.moduleName}, incorrect tree structure."
    )
    return
  }

  fun populateDependenciesFromArtifact(
    gradleSourceSetData: DataNode<GradleSourceSetData>,
    ideBaseArtifact: IdeBaseArtifact,
    dependencyScope: DependencyScope
  ) {
    val processedLibraries = mutableMapOf<String, LibraryDependencyData>()
    val processedModuleDependencies = mutableMapOf<GradleProjectPath, ModuleDependencyData>()

    // Setup the dependencies for the main artifact, the main dependencies are done first since there scope is more permissive.
    // This allows us to just skip the dependency if it is already present.
    AndroidDependenciesSetupContext(
      gradleSourceSetData,
      projectDataNode,
      gradleProjectPathToModuleData,
      additionalArtifactsMapper,
      processedLibraries,
      processedModuleDependencies
    )
      .setupForArtifact(ideBaseArtifact, dependencyScope)

    var orderIndex = 0
    processedModuleDependencies.forEach { (_, moduleDependencyData) ->
      moduleDependencyData.order = orderIndex++
      gradleSourceSetData.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
    }
    processedLibraries.forEach { (_, libraryDependencyData) ->
      libraryDependencyData.order = orderIndex++
      gradleSourceSetData.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
    }
  }

  populateDependenciesFromArtifact(findSourceSetDataForArtifact(variant.mainArtifact), variant.mainArtifact,
                                   DependencyScope.COMPILE)
  variant.testFixturesArtifact?.also {
    populateDependenciesFromArtifact(findSourceSetDataForArtifact(it), it, DependencyScope.COMPILE)
  }
  variant.unitTestArtifact?.also {
    populateDependenciesFromArtifact(findSourceSetDataForArtifact(it), it, DependencyScope.TEST)
  }
  variant.androidTestArtifact?.also {
    populateDependenciesFromArtifact(findSourceSetDataForArtifact(it), it, DependencyScope.TEST)
  }
}

fun DataNode<ModuleData>.findSourceSetDataForArtifact(ideBaseArtifact: IdeBaseArtifactCore): DataNode<GradleSourceSetData> {
  return ExternalSystemApiUtil.find(this, GradleSourceSetData.KEY) {
    it.data.externalName.substringAfterLast(":") == ModuleUtil.getModuleName(ideBaseArtifact.name)
  } ?: throw ExternalSystemException("Missing GradleSourceSetData data for artifact!")
}

internal fun IdeModuleDependency.getGradleProjectPath(): GradleProjectPath =
  GradleSourceSetProjectPath(toSystemIndependentName(buildId), projectPath, sourceSet)
