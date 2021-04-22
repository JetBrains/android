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
import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.builder.model.level2.Library
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.io.FilePaths
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
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.filesEqual
import com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import org.gradle.tooling.model.UnsupportedMethodException
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.linkProjectLibrary
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.File.separatorChar

private val LOG = Logger.getInstance(AndroidDependenciesSetupContext::class.java)

typealias SourcesPath = File?
typealias JavadocPath = File?
typealias SampleSourcePath = File?
typealias ArtifactId = String
typealias ArtifactPath = File

data class AdditionalArtifactsPaths(val sources: SourcesPath, val javadoc: JavadocPath, val sampleSources: SampleSourcePath)

/**
 * Sets up the [LibraryDependencyData] and [ModuleDependencyData] on the receiving [ModuleData] node.
 *
 * This uses the information provided in the given [variant] if no variant is given then the selected
 * variant from the [AndroidModuleModel] is used. This method assumes that this module has an attached
 * [AndroidModuleModel] data node (given by the key [AndroidProjectKeys.ANDROID_MODEL]).
 *
 * [additionalArtifactsMapper] is used to obtain the respective sources and Javadocs which are attached to the
 * libraries. TODO: Replace with something that makes the call sites nicer and shouldn't rely on the project object.
 *
 * The [idToModuleData] map must be provided and must correctly map module ids created in the same form
 * as [GradleProjectResolverUtil.getModuleId] to the [ModuleData]. This is used to set up
 * [ModuleDependencyData].
 */
@JvmOverloads
fun DataNode<ModuleData>.setupAndroidDependenciesForModule(
  idToModuleData: (String) -> ModuleData?,
  additionalArtifactsMapper: (ArtifactId, ArtifactPath) -> AdditionalArtifactsPaths,
  variant: IdeVariant? = null
) {
  val androidModel = ExternalSystemApiUtil.find(this, AndroidProjectKeys.ANDROID_MODEL)?.data ?: return // TODO: Error here
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

  // We need the composite information to compute the module IDs we compute here to only traverse the data
  // node tree once.
  val compositeData = ExternalSystemApiUtil.find(projectDataNode, CompositeBuildData.KEY)?.data

  // These maps keep track of all the dependencies that we have already seen. This allows us to skip over processing
  // dependencies multiple times with more specific scopes.
  val processedLibraries = mutableMapOf<String, LibraryDependencyData>()
  val processedModuleDependencies = mutableMapOf<String, ModuleDependencyData>()

  val selectedVariant = variant ?: androidModel.selectedVariant

  // First set up any extra sdk libraries as these should really be in the SDK.
  getExtraSdkLibraries(projectDataNode, this, androidModel.androidProject.bootClasspath).forEach { sdkLibraryDependency ->
    processedLibraries[sdkLibraryDependency.target.externalName] = sdkLibraryDependency
  }

  val dependenciesSetupContext = AndroidDependenciesSetupContext(
    this,
    androidModel.features.shouldExportDependencies(),
    projectDataNode,
    compositeData,
    idToModuleData,
    additionalArtifactsMapper,
    processedLibraries,
    processedModuleDependencies
  )

  // Setup the dependencies for the main artifact, the main dependencies are done first since there scope is more permissive.
  // This allows us to just skip the dependency if it is already present.
  dependenciesSetupContext.setupForArtifact(selectedVariant.mainArtifact, DependencyScope.COMPILE)
  val endCompileIndex = processedLibraries.size

  // Setup the dependencies of the test artifact.
  listOfNotNull(selectedVariant.unitTestArtifact, selectedVariant.androidTestArtifact).forEach { testArtifact ->
      dependenciesSetupContext.setupForArtifact(testArtifact, DependencyScope.TEST)
  }

  // Determine an order for the dependencies, for now we put the modules first and the libraries after.
  // The order of the libraries and modules is the same order as we obtain them from AGP, with the
  // dependencies from the main artifact coming first (java libs then android) and the test artifacts
  // coming after (java libs then android).
  // TODO(rework-12): What is the correct order
  var orderIndex = 0

  val processedLibrarySize = processedLibraries.size
  var tempOrderIndex = 0
  processedLibraries.forEach { (_, libraryDependencyData) ->
    // We want the Test scope artifacts to appear on the classpath before the compile type artifacts. This is to prevent ensure that
    // if the same dependency (with a different version) is present as both a test and compile dependency then we use the Test version
    // when running tests. This should become irrelevant once we switch to running unit tests through Gradle.
    libraryDependencyData.order =  orderIndex + Math.floorMod((tempOrderIndex++ - endCompileIndex), processedLibrarySize)
    createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
  }
  orderIndex += tempOrderIndex

  // Due to the way intellij collects classpaths for test (using all transitive deps) we are putting all module dependencies last so that
  // their dependencies will be last on the classpath and not overwrite actual dependencies of the module being tested.
  // This should be removed once we have a way to correct the order of the classpath, or we start running tests via Gradle.
  processedModuleDependencies.forEach { (_, moduleDependencyData) ->
    moduleDependencyData.order = orderIndex++
    createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
  }
}

// TODO: Should this be moved and shared with the plugin?
const val LOCAL_LIBRARY_PREFIX = "__local_aars__"

/**
 * Attempts to shorten the library name by making paths relative and makes paths system independent.
 * Name shortening is required because the maximum allowed file name length is 256 characters and .jar files located in deep
 * directories in CI environments may exceed this limit.
 */
private fun adjustLocalLibraryName(artifactFile: File, projectBasePath: String) : @SystemIndependent String {
  val maybeRelative = artifactFile.relativeToOrSelf(File(toSystemDependentName(projectBasePath)))
  if (!filesEqual(maybeRelative, artifactFile)) {
    return toSystemIndependentName(File(".${File.separator}${maybeRelative}").path)
  }

  return toSystemIndependentName(artifactFile.path)
}

/**
 * Converts the artifact address into a name that will be used by the IDE to represent the library.
 */
private fun convertToLibraryName(library: IdeLibrary, projectBasePath: String): String {
  if (library.artifactAddress.startsWith("$LOCAL_LIBRARY_PREFIX:"))  {
    return adjustLocalLibraryName(library.artifact, projectBasePath)
  }

  return convertMavenCoordinateStringToIdeLibraryName(library.artifactAddress)
}

/**
 * Converts the name of a maven form dependency from the format that is returned from the Android Gradle plugin [Library]
 * to the name that will be used to setup the library in the IDE. The Android Gradle plugin uses maven co-ordinates to
 * represent the library.
 *
 * In order to share the libraries between Android and non-Android modules we want to convert the artifact
 * co-ordinate string that will match the ones that would be set up in the IDE for non-android modules.
 *
 * Current this method removes any @jar from the end of the coordinate since IDEA defaults to this and doesn't display
 * it.
 */
private fun convertMavenCoordinateStringToIdeLibraryName(mavenCoordinate: String) : String {
  return mavenCoordinate.removeSuffix("@jar")
}

/**
 * Removes name extension or qualifier or classifier from the given [libraryName]. If the given [libraryName]
 * can't be parsed as a [GradleCoordinate] this method returns the [libraryName] un-edited.
 */
private fun stripExtensionAndClassifier(libraryName: String) : String {
  val parts = libraryName.split(':')
  if (parts.size < 3) return libraryName // There is not enough parts to form a group:id:version string.
  return "${parts[0]}:${parts[1]}:${parts[2]}"
}

private fun IdeLibrary.isModuleLevel(modulePath: String) = try {
  FileUtil.isAncestor(modulePath, artifactAddress, false)
} catch (e: UnsupportedMethodException) {
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
private fun computeModuleIdForLibraryTarget(
  library: IdeModuleLibrary,
  projectData: ProjectData?,
  compositeData: CompositeBuildData?
) : String {
  // If we don't have a ProjectData or CompositeData we assume that the target module is contained within the
  // main Gradle build.
  if (projectData == null) {
    return library.projectPath
  }
  val libraryBuildId = library.buildId?.let { toSystemIndependentName(it) }
  if (libraryBuildId == projectData.linkedExternalProjectPath ||
      compositeData == null) {
    return GradleProjectResolverUtil.getModuleId(library.projectPath, projectData.externalName)
  }

  // Since the dependency doesn't have the same root path as the module's project it must be pointing to a
  // module in an included build. We now need to find the name of the root Gradle build that the module
  // belongs to in order to construct the module ID.
  val projectName = compositeData.compositeParticipants.firstOrNull {
    it.rootPath == libraryBuildId
  }?.rootProjectName ?: return GradleProjectResolverUtil.getModuleId(library.projectPath, projectData.externalName)

  return if (library.projectPath == ":") projectName else projectName + library.projectPath
}

private class AndroidDependenciesSetupContext(
  private val moduleDataNode: DataNode<out ModuleData>,
  private val shouldExportDependencies: Boolean,
  private val projectDataNode: DataNode<ProjectData>,
  private val compositeData: CompositeBuildData?,
  private val idToModuleData: (String) -> ModuleData?,
  private val additionalArtifactsMapper: (ArtifactId, ArtifactPath) -> AdditionalArtifactsPaths?,
  private val processedLibraries: MutableMap<String, LibraryDependencyData>,
  private val processedModuleDependencies: MutableMap<String, ModuleDependencyData>
) {

  private abstract inner class WorkItem<T : IdeLibrary> {
    abstract fun isAlreadyProcessed(): Boolean
    protected abstract fun setupTarget()
    protected abstract fun createDependencyData(scope: DependencyScope)

    fun setup(scope: DependencyScope) {
      setupTarget()
      createDependencyData(scope)
    }
  }

  private abstract inner class LibraryWorkItem<T : IdeLibrary>(protected val library: T) : WorkItem<T>() {
    protected val libraryName = convertToLibraryName(library, projectDataNode.data.linkedExternalProjectPath)
    protected val libraryData: LibraryData = LibraryData(GradleConstants.SYSTEM_ID, libraryName, false)

    final override fun isAlreadyProcessed(): Boolean = processedLibraries.containsKey(libraryName)

    final override fun createDependencyData(scope: DependencyScope) {
      // Finally create the LibraryDependencyData
      val libraryDependencyData = LibraryDependencyData(moduleDataNode.data, libraryData, workOutLibraryLevel())
      libraryDependencyData.scope = scope
      libraryDependencyData.isExported = shouldExportDependencies
      processedLibraries[libraryName] = libraryDependencyData
    }

    private fun workOutLibraryLevel(): LibraryLevel {
      // Work out the level of the library, if the library path is inside the module directory we treat
      // this as a Module level library. Otherwise we treat it as a Project level one.
      return when {
        library.isModuleLevel(moduleDataNode.data.moduleFileDirectoryPath) -> LibraryLevel.MODULE
        !linkProjectLibrary(null, projectDataNode, libraryData) -> LibraryLevel.MODULE
        else -> LibraryLevel.PROJECT
      }
    }
  }

  private inner class JavaLibraryWorkItem(library: IdeJavaLibrary) : LibraryWorkItem<IdeJavaLibrary>(library) {
    override fun setupTarget() {
      libraryData.addPath(BINARY, library.artifact.absolutePath)
      setupSourcesAndJavaDocsFrom(libraryData, libraryName, library)
    }
  }

  private inner class AndroidLibraryWorkItem(library: IdeAndroidLibrary) : LibraryWorkItem<IdeAndroidLibrary>(library) {
    override fun setupTarget() {
      library.compileJarFiles.forEach { compileJar ->
        libraryData.addPath(BINARY, compileJar)
      }
      libraryData.addPath(BINARY, library.resFolder)
      // TODO: Should this be binary? Do we need the platform to allow custom types here?
      libraryData.addPath(BINARY, library.manifest)
      setupAnnotationsFrom(libraryData, libraryName, library)
      setupSourcesAndJavaDocsFrom(libraryData, libraryName, library)
    }
  }

  private inner class ModuleLibraryWorkItem(
    val targetModuleId: String,
    val targetData: ModuleData
  ) : WorkItem<IdeModuleLibrary>() {
    override fun isAlreadyProcessed(): Boolean = processedModuleDependencies.containsKey(targetModuleId)

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
      moduleDependencyData.isExported = shouldExportDependencies
      processedModuleDependencies[targetModuleId] = moduleDependencyData
    }
  }

  private fun createModuleLibraryWorkItem(library: IdeModuleLibrary): ModuleLibraryWorkItem? {
    if (library.projectPath.isEmpty()) return null
    val targetModuleId = computeModuleIdForLibraryTarget(library, projectDataNode.data, compositeData)
    // If we aren't using module per source set then we short cut here as the current implementation takes a long time
    if (!StudioFlags.USE_MODULE_PER_SOURCE_SET.get()) {
      val targetData = idToModuleData(targetModuleId) ?: return null
      return ModuleLibraryWorkItem(targetModuleId, targetData)
    }

    // TODO: This is really slow, we need to modify the platform so that the GradleExecutionWorkspace makes the data node accessible
    val targetDataNode = ExternalSystemApiUtil.find(projectDataNode, ProjectKeys.MODULE) { moduleDataNode ->
      moduleDataNode.data.id == targetModuleId
    } ?: return null

    val sourceSets = ExternalSystemApiUtil.findAll(targetDataNode, GradleSourceSetData.KEY)
    // TODO: Get the correct source set to depend on from Gradle
    val sourceSet = sourceSets.firstOrNull {
      it.data.moduleName == "main"
    } ?: sourceSets.firstOrNull {
      !it.data.moduleName.contains("test")
    }

    return if (sourceSet != null) {
      ModuleLibraryWorkItem(sourceSet.data.id, sourceSet.data)
    } else {
      ModuleLibraryWorkItem(targetModuleId, targetDataNode.data)
    }
  }

  fun setupForArtifact(artifact: IdeBaseArtifact, scope: DependencyScope) {
    val dependencies = artifact.level2Dependencies

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
    libraryName: String,
    library: IdeLibrary
  ) {
    val (sources, javadocs, sampleSources) =
      additionalArtifactsMapper(stripExtensionAndClassifier(libraryName), library.artifact) ?: return

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
    (library.compileJarFiles + library.resFolder).distinct().mapNotNull {
      FilePaths.toSystemDependentPath(it)?.path
    }.forEach { binaryPath ->
      if (binaryPath.endsWith(separatorChar + FD_RES)) {
        val annotationsFile = File(binaryPath.removeSuffix(FD_RES) + FN_ANNOTATIONS_ZIP)
        if (annotationsFile.isFile) {
          libraryData.addPath(LibraryPathType.ANNOTATION, annotationsFile.absolutePath)
        }
      }
      else if ((libraryName.startsWith(ANDROIDX_ANNOTATIONS_ARTIFACT) ||
                libraryName.startsWith(ANNOTATIONS_LIB_ARTIFACT)) &&
               binaryPath.endsWith(DOT_JAR)) {
        val annotationsFile = File(binaryPath.removeSuffix(DOT_JAR) + "-" + FN_ANNOTATIONS_ZIP)
        if (annotationsFile.isFile) {
          libraryData.addPath(LibraryPathType.ANNOTATION, annotationsFile.absolutePath)
        }
      }
    }
  }
}

/**
 * Sets the 'useLibrary' libraries or SDK add-ons as library dependencies.
 *
 * These libraries are set at the project level, which makes it impossible to add them to a IDE SDK definition because the IDE SDK is
 * global to the whole IDE. To work around this limitation, we set these libraries as module dependencies instead.
 *
 * TODO: The priority of these is wrong, they should be part of the SDK.
 *
 */
private fun getExtraSdkLibraries(
  projectDataNode: DataNode<ProjectData>,
  moduleDataNode: DataNode<ModuleData>,
  bootClasspath: Collection<String>
) : List<LibraryDependencyData> {
  return bootClasspath.filter { path ->
    File(path).name != FN_FRAMEWORK_LIBRARY
  }.map { path ->
    val filePath = File(path)
    val name = if (filePath.isFile) getNameWithoutExtension(filePath) else sanitizeFileName(path)

    val libraryData = LibraryData(GradleConstants.SYSTEM_ID, name, false)
    libraryData.addPath(BINARY, path)

    // Attempt to find JavaDocs and Sources for the SDK additional lib
    // TODO: Do we actually need this, where are these sources/javadocs located.
    val sources = LibraryFilePaths.findArtifactFilePathInRepository(filePath, "-sources.jar", true)
    if (sources != null) {
      libraryData.addPath(SOURCE, sources.absolutePath)
    }
    val javaDocs = LibraryFilePaths.findArtifactFilePathInRepository(filePath, "-javadoc.jar", true)
    if (javaDocs != null) {
      libraryData.addPath(DOC, javaDocs.absolutePath)
    }

    val libraryLevel = if (linkProjectLibrary(null, projectDataNode, libraryData)) LibraryLevel.PROJECT else LibraryLevel.MODULE

    LibraryDependencyData(moduleDataNode.data, libraryData, libraryLevel).apply {
      scope = DependencyScope.COMPILE
      isExported = false
    }
  }
}

//****************************************************************************************************************************
/* Below are methods related to the processing of dependencies for Android modules when module per source set is being used */
//****************************************************************************************************************************

fun DataNode<ModuleData>.setupAndroidDependenciesForMpss(
  idToModuleData: (String) -> ModuleData?,
  additionalArtifactsMapper: (ArtifactId, ArtifactPath) -> AdditionalArtifactsPaths,
  androidModel: AndroidModuleModel,
  variant: IdeVariant
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

  // We need the composite information to compute the module IDs we compute here to only traverse the data
  // node tree once.
  val compositeData = ExternalSystemApiUtil.find(projectDataNode, CompositeBuildData.KEY)?.data

  fun populateDependenciesFromArtifact(
    gradleSourceSetData: DataNode<GradleSourceSetData>,
    ideBaseArtifact: IdeBaseArtifact,
    dependencyScope: DependencyScope
  ) {
    val processedLibraries = mutableMapOf<String, LibraryDependencyData>()
    val processedModuleDependencies = mutableMapOf<String, ModuleDependencyData>()

    // Setup the dependencies for the main artifact, the main dependencies are done first since there scope is more permissive.
    // This allows us to just skip the dependency if it is already present.
    AndroidDependenciesSetupContext(
      gradleSourceSetData,
      androidModel.features.shouldExportDependencies(),
      projectDataNode,
      compositeData,
      idToModuleData,
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
      libraryDependencyData.order =  orderIndex++
      gradleSourceSetData.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
    }
  }

  populateDependenciesFromArtifact(findSourceSetDataForArtifact(variant.mainArtifact), variant.mainArtifact,
                                   DependencyScope.COMPILE)
  variant.unitTestArtifact?.also {
    populateDependenciesFromArtifact(findSourceSetDataForArtifact(it), it, DependencyScope.TEST)
  }
  variant.androidTestArtifact?.also {
    populateDependenciesFromArtifact(findSourceSetDataForArtifact(it), it, DependencyScope.TEST)
  }
}

fun DataNode<ModuleData>.findSourceSetDataForArtifact(ideBaseArtifact: IdeBaseArtifact) : DataNode<GradleSourceSetData> {
  return ExternalSystemApiUtil.find(this, GradleSourceSetData.KEY) {
    it.data.externalName.substringAfterLast(":") == ModuleUtil.getModuleName(ideBaseArtifact)
  } ?: throw ExternalSystemException("Missing GradleSourceSetData data for artifact!")
}

