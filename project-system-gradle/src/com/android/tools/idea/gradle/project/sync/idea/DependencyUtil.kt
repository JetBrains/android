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
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeArtifactDependency
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeDependency
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdePreResolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnresolvedModuleLibrary
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.buildId
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTable
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTable
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
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import org.gradle.tooling.model.UnsupportedMethodException
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.linkProjectLibrary
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
 * Removes name extension or qualifier or classifier from the given [libraryName]. If the given [libraryName]
 * can't be parsed as a [GradleCoordinate] this method returns the [libraryName] un-edited.
 */
private fun stripExtensionAndClassifier(libraryName: String): String {
  val parts = libraryName.split(':')
  if (parts.size < 3) return libraryName // There is not enough parts to form a group:id:version string.
  return "${parts[0]}:${parts[1]}:${parts[2]}"
}

private fun IdeArtifactLibrary.isModuleLevel(modulePath: String) = try {
  FileUtil.isAncestor(modulePath, artifactAddress, false)
}
catch (e: UnsupportedMethodException) {
  false
}

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
  private val processedModuleDependencies: MutableMap<GradleProjectPath, ModuleDependencyData>,
  private val project: Project?
) {

  private abstract inner class WorkItem<T : IdeDependency<*>> {
    abstract fun isAlreadyProcessed(): Boolean
    protected abstract fun setupTarget()
    protected abstract fun createDependencyData(scope: DependencyScope)

    fun setup(scope: DependencyScope) {
      setupTarget()
      createDependencyData(scope)
    }
  }

  private abstract inner class LibraryWorkItem<T : IdeArtifactDependency<*>>(protected val library: T) : WorkItem<T>() {
    protected val libraryName = library.target.name
    protected val libraryData: LibraryData = LibraryData(GradleConstants.SYSTEM_ID, libraryName, false)

    final override fun isAlreadyProcessed(): Boolean = processedLibraries.containsKey(libraryName)

    final override fun createDependencyData(scope: DependencyScope) {
      // Finally create the LibraryDependencyData
      val libraryDependencyData = LibraryDependencyData(moduleDataNode.data, libraryData, workOutLibraryLevel())
      libraryDependencyData.scope = scope
      libraryDependencyData.isExported = false
      processedLibraries[libraryName] = libraryDependencyData
    }

    private fun workOutLibraryLevel(): LibraryLevel {
      // Work out the level of the library, if the library path is inside the module directory we treat
      // this as a Module level library. Otherwise we treat it as a Project level one.
      return when {
        library.target.isModuleLevel(moduleDataNode.data.moduleFileDirectoryPath) -> LibraryLevel.MODULE
        !linkProjectLibrary(null, projectDataNode, libraryData) -> LibraryLevel.MODULE
        else -> LibraryLevel.PROJECT
      }
    }
  }

  private inner class JavaLibraryWorkItem(library: IdeJavaLibraryDependency) : LibraryWorkItem<IdeJavaLibraryDependency>(library) {
    override fun setupTarget() {
      ArtifactDependencySpec.create(library.target.artifactAddress)?.also {
        libraryData.setGroup(it.group)
        libraryData.artifactId = it.name
        libraryData.version = it.version
      }

      libraryData.addPath(BINARY, library.target.artifact.absolutePath)
      setupSourcesAndJavaDocsFrom(libraryData, libraryName)
    }
  }

  private inner class AndroidLibraryWorkItem(library: IdeAndroidLibraryDependency) : LibraryWorkItem<IdeAndroidLibraryDependency>(library) {
    override fun setupTarget() {
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
    }
  }

  private inner class ModuleLibraryWorkItem(
    val targetModuleGradlePath: GradleProjectPath,
    val targetData: ModuleData
  ) : WorkItem<IdeModuleDependency>() {
    override fun isAlreadyProcessed(): Boolean = processedModuleDependencies.containsKey(targetModuleGradlePath)

    override fun setupTarget() {
      // Module has been already set up.
    }

    override fun createDependencyData(scope: DependencyScope) {
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
      // TODO(b/208357458): Once we correct source set matching we need to revisit whether this should produce an error
      LOG.warnInProduction(ExternalSystemException("Cannot find module with id: $targetModuleGradlePath"))
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
      processedModuleDependencies,
      project
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

class ResolvedLibraryTableBuilder(
  private val getGradlePathBy: (moduleId: String) -> GradleProjectPath?,
  private val getModuleDataNode: (GradleProjectPath) -> DataNode<out ModuleData>?,
  private val resolveArtifact: (File) -> List<GradleSourceSetProjectPath>?
) {
  fun buildResolvedLibraryTable(
    ideLibraryTable: IdeUnresolvedLibraryTable,
  ): IdeResolvedLibraryTable {
    return ideLibraryTable.resolve(
      artifactResolver = { resolveArtifact(it) },
      moduleDependencyExpander = ::resolveAdditionalKmpSourceSets
    )
  }

  private fun resolveAdditionalKmpSourceSets(sourceSet: GradleSourceSetProjectPath): List<GradleSourceSetProjectPath> {
    return sequence {
      yield(sourceSet)
      val targetSourceSetData = getModuleDataNode(sourceSet)
        ?: let {
          logError("Resolved source set not found for: $sourceSet")
          return@sequence
        }
      val kmpDependsOn = ExternalSystemApiUtil.find(targetSourceSetData, KotlinSourceSetData.KEY)?.data?.sourceSetInfo?.dependsOn.orEmpty()
      yieldAll(kmpDependsOn.mapNotNull(getGradlePathBy))
    }
      .distinct()
      .filterIsInstance<GradleSourceSetProjectPath>()
      .toList()
  }

  private val logger = Logger.getInstance(this.javaClass)

  private fun logError(message: String) {
    logger.error(message, Throwable())
  }
}

private fun IdeUnresolvedLibraryTable.resolve(
  artifactResolver: (File) -> List<GradleSourceSetProjectPath>?,
  moduleDependencyExpander: (GradleSourceSetProjectPath) -> List<GradleSourceSetProjectPath>
): IdeResolvedLibraryTable {

  fun resolve(preResolved: IdePreResolvedModuleLibrary): List<IdeModuleLibrary> {
    val expandedSourceSets = moduleDependencyExpander(
      GradleSourceSetProjectPath(
        preResolved.buildId,
        preResolved.projectPath,
        preResolved.sourceSet
      )
    )
    return expandedSourceSets.map {
      IdeModuleLibraryImpl(
        buildId = it.buildRoot,
        projectPath = it.path,
        variant = preResolved.variant,
        lintJar = preResolved.lintJar,
        sourceSet = it.sourceSet
      )
    }
  }

  fun resolve(unresolved: IdeUnresolvedModuleLibrary): List<IdeLibrary> {
    val targets = artifactResolver(unresolved.artifact)
      ?: return listOf(
        IdeJavaLibraryImpl(
          unresolved.artifact.path,
          unresolved.artifact.path,
          unresolved.artifact
        )
      )

    val unresolvedModuleBuilds = targets.filter { it.buildRoot != unresolved.buildId }
    if (unresolvedModuleBuilds.isNotEmpty()) {
      error("Unexpected resolved modules build id ${unresolvedModuleBuilds.map { it.buildRoot }.toSet()} != ${unresolved.buildId}")
    }

    val unresolvedModulePaths = targets.filter { it.path != unresolved.projectPath }
    if (unresolvedModulePaths.isNotEmpty()) {
      error("Unexpected resolved modules project path ${unresolvedModulePaths.map { it.path }.toSet()} != ${unresolved.projectPath}")
    }

    return targets.flatMap {
      resolve(
        IdePreResolvedModuleLibraryImpl(
          buildId = unresolved.buildId,
          projectPath = unresolved.projectPath,
          variant = unresolved.variant,
          lintJar = unresolved.lintJar,
          sourceSet = it.sourceSet
        )
      )
    }
  }

  return IdeResolvedLibraryTableImpl(
    libraries.map {
      when (it) {
        is IdeJavaLibrary -> listOf(it)
        is IdeAndroidLibrary -> listOf(it)
        is IdeModuleLibrary -> error("Unexpected resolved library: $it")
        is IdeUnresolvedModuleLibrary -> resolve(it)
        is IdePreResolvedModuleLibrary -> resolve(it)
      }
    }
  )
}
