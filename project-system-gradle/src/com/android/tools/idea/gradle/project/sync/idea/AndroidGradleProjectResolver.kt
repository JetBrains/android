/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel
import com.android.repository.Revision
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.model.IdeDebugInfo
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl.Companion.fromLibraryTables
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTable
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTable
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.GradleAndroidModelDataImpl.Companion.create
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.V2NdkModel
import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.AndroidSyncExceptionType
import com.android.tools.idea.gradle.project.sync.IdeAndroidModels
import com.android.tools.idea.gradle.project.sync.IdeAndroidNativeVariantsModels
import com.android.tools.idea.gradle.project.sync.IdeAndroidSyncError
import com.android.tools.idea.gradle.project.sync.IdeAndroidSyncIssuesAndExceptions
import com.android.tools.idea.gradle.project.sync.IdeSyncExecutionReport
import com.android.tools.idea.gradle.project.sync.SdkSync
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs
import com.android.tools.idea.gradle.project.sync.errors.COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.getIdeModuleSourceSet
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.getModuleName
import com.android.tools.idea.gradle.project.sync.idea.VariantProjectDataNodes.Companion.collectCurrentAndPreviouslyCachedVariants
import com.android.tools.idea.gradle.project.sync.idea.data.model.KotlinMultiplatformAndroidSourceSetType
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectJdkUpdateData
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporter
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.gradle.project.sync.stackTraceAsMultiLineMessage
import com.android.tools.idea.gradle.project.sync.toException
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.android.tools.idea.gradle.util.AndroidGradleSettings
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.findModule
import com.android.tools.idea.sdk.IdeSdks
import com.android.utils.appendCapitalized
import com.android.utils.findGradleSettingsFile
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.externalSystem.JavaModuleData
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectSdkData
import com.intellij.openapi.externalSystem.model.project.TestData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.Pair
import com.intellij.pom.java.LanguageLevel
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.ExceptionUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.android.configure.patchFromMppModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptModelBuilderService
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptSourceSetModel
import org.jetbrains.plugins.gradle.model.DefaultExternalProject
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import java.io.File
import java.io.IOException
import java.util.function.Function
import java.util.zip.ZipException

private val LOG = Logger.getInstance(AndroidGradleProjectResolver::class.java)

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
@Suppress("AvoidByLazy")
class AndroidGradleProjectResolver @NonInjectable @VisibleForTesting internal constructor(private val myCommandLineArgs: CommandLineArgs) :
  AbstractProjectResolverExtension(), AndroidGradleProjectResolverMarker {
  private var project: Project? = null
  private val myModuleDataByGradlePath: MutableMap<GradleProjectPath, DataNode<out ModuleData>> = mutableMapOf()
  private val myGradlePathByModuleId: MutableMap<String?, GradleProjectPath> = mutableMapOf()
  private var myResolvedLibraryTable: IdeResolvedLibraryTable? = null

  constructor() : this(CommandLineArgs())

  override fun setProjectResolverContext(projectResolverContext: ProjectResolverContext) {
    project = projectResolverContext.externalSystemTaskId.findProject()
    // Setting this flag on the `projectResolverContext` tells the Kotlin IDE plugin that we are requesting `KotlinGradleModel` for all
    // modules. This is to be able to provide additional arguments to the model builder and avoid unnecessary processing of currently the
    // inactive build variants.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY, true)
    // Similarly for KAPT.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY, true)
    myResolvedLibraryTable = null
    super.setProjectResolverContext(projectResolverContext)
  }

  override fun populateProjectExtraModels(gradleProject: IdeaProject, projectDataNode: DataNode<ProjectData>) {
    val project = project
    if (project != null) {
      removeExternalSourceSetsAndReportWarnings(project, gradleProject)
      attachVariantsSavedFromPreviousSyncs(project, projectDataNode)
      alignProjectJdkWithGradleSyncJdk(project, projectDataNode)
    }
    val buildMap = resolverCtx.models.getModel(IdeCompositeBuildMap::class.java)
    if (buildMap != null) {
      projectDataNode.createChild(AndroidProjectKeys.IDE_COMPOSITE_BUILD_MAP, buildMap)
    }

    val syncError = resolverCtx.models.getModel(IdeAndroidSyncError::class.java)
    if (syncError != null) {
      throw syncError.toException()
    }
    if (studioProjectSyncDebugModeEnabled()) {
      printDebugInfo()
    }

    // This is used in the special mode sync to fetch additional native variants.
    for (gradleModule in gradleProject.modules) {
      val nativeVariants = resolverCtx.getExtraProject(gradleModule, IdeAndroidNativeVariantsModels::class.java)
      if (nativeVariants != null) {
        projectDataNode.createChild(
          AndroidProjectKeys.NATIVE_VARIANTS,
          IdeAndroidNativeVariantsModelsWrapper(
            gradleModule.projectIdentifier.projectPath,
            nativeVariants
          )
        )
      }
    }
    val syncExecutionReport = resolverCtx.models.getModel(IdeSyncExecutionReport::class.java)
    if (syncExecutionReport != null) {
      projectDataNode.createChild(AndroidProjectKeys.SYNC_EXECUTION_REPORT, syncExecutionReport)
    }
    if (isAndroidGradleProject) {
      projectDataNode.createChild(AndroidProjectKeys.PROJECT_CLEANUP_MODEL, ProjectCleanupModel.getInstance())
    }
    super.populateProjectExtraModels(gradleProject, projectDataNode)

    if (IdeInfo.getInstance().isAndroidStudio) {
      // Remove platform ProjectSdkDataService data node overwritten by our ProjectJdkUpdateService
      ExternalSystemApiUtil.find(projectDataNode, ProjectSdkData.KEY)?.clear(true)
    }
  }

  override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
    val ideAndroidSyncIssuesAndExceptions = resolverCtx.getExtraProject(gradleModule, IdeAndroidSyncIssuesAndExceptions::class.java)
    if (!isAndroidGradleProject) {
      return nextResolver.createModule(gradleModule, projectDataNode)?.also {
        ideAndroidSyncIssuesAndExceptions?.process(it)
      }
    }
    val androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java)
    val moduleDataNode = nextResolver.createModule(gradleModule, projectDataNode) ?: return null
    createAndAttachModelsToDataNode(moduleDataNode, gradleModule, androidModels)
    ideAndroidSyncIssuesAndExceptions?.process(moduleDataNode)
    patchLanguageLevels(moduleDataNode, gradleModule, androidModels?.androidProject)
    registerModuleData(gradleModule, moduleDataNode)
    return moduleDataNode
  }

  private fun registerModuleData(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>
  ) {
    val projectIdentifier = gradleModule.gradleProject.projectIdentifier
    val sourceSetNodes = ExternalSystemApiUtil.findAll(moduleDataNode, GradleSourceSetData.KEY)
    if (!sourceSetNodes.isEmpty()) {
      // ":" and similar holder projects do not have any source sets and should not be a target of module dependencies.
      sourceSetNodes.forEach { node: DataNode<GradleSourceSetData> ->
        val sourceSet = node.data.getIdeModuleSourceSet()
        if (sourceSet.canBeConsumed) {
          val gradleProjectPath: GradleProjectPath = GradleSourceSetProjectPath(
            PathUtil.toSystemIndependentName(projectIdentifier.buildIdentifier.rootDir.path),
            projectIdentifier.projectPath,
            sourceSet
          )
          myModuleDataByGradlePath[gradleProjectPath] = node
          myGradlePathByModuleId[node.data.id] = gradleProjectPath
        }
      }
    }
  }

  private fun printDebugInfo() {
    val debugInfo = resolverCtx.models.getModel(IdeDebugInfo::class.java)
    debugInfo?.projectImportModelProviderClasspath?.entries?.forEach { (key, value) ->
      // Integration test searches for this string pattern in idea log file.
      LOG.debug("ModelProvider $key Classpath: $value")
    }
  }

  private fun patchLanguageLevels(
    moduleDataNode: DataNode<ModuleData>,
    gradleModule: IdeaModule,
    androidProject: IdeAndroidProject?
  ) {
    val javaModuleData = ExternalSystemApiUtil.find(moduleDataNode, JavaModuleData.KEY) ?: return
    val moduleData = javaModuleData.data
    if (androidProject != null) {
      val languageLevel = LanguageLevel.parse(androidProject.javaCompileOptions.sourceCompatibility)
      moduleData.languageLevel = languageLevel
      moduleData.targetBytecodeVersion = androidProject.javaCompileOptions.targetCompatibility
    } else {
      // Workaround BaseGradleProjectResolverExtension since the IdeaJavaLanguageSettings doesn't contain any information.
      // For this we set the language level based on the "main" source set of the module.
      // TODO: Remove once we have switched to module per source set. The base resolver should handle that correctly.
      val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)
      if (externalProject != null) {
        // main should always exist, if it doesn't other things will fail before this.
        val externalSourceSet = externalProject.sourceSets["main"]
        if (externalSourceSet != null) {
          val languageLevel = LanguageLevel.parse(externalSourceSet.sourceCompatibility)
          moduleData.languageLevel = languageLevel
          moduleData.targetBytecodeVersion = externalSourceSet.targetCompatibility
        }
      }
    }
  }

  override fun populateModuleCompileOutputSettings(
    gradleModule: IdeaModule,
    ideModule: DataNode<ModuleData>
  ) {
    super.populateModuleCompileOutputSettings(gradleModule, ideModule)
    ideModule.setupCompilerOutputPaths(isDelegatedBuildUsed = resolverCtx.isDelegatedBuild)
  }

  override fun getToolingExtensionsClasses(): Set<Class<*>> {
    return setOf(KaptModelBuilderService::class.java, Unit::class.java)
  }

  /**
   * Creates and attaches the following models to the moduleNode depending on the type of module:
   *
   *  * GradleAndroidModel
   *  * NdkModuleModel
   *  * GradleModuleModel
   *  * JavaModuleModel
   *
   *
   * @param moduleNode      the module node to attach the models to
   * @param gradleModule    the module in question
   * @param androidModels   the android project models obtained from this module (null is none found)
   */
  private fun createAndAttachModelsToDataNode(
    moduleNode: DataNode<ModuleData>,
    gradleModule: IdeaModule,
    androidModels: IdeAndroidModels?
  ) {
    val moduleName = moduleNode.data.internalName
    val rootModulePath = FilePaths.stringToFile(moduleNode.data.linkedExternalProjectPath)
    val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)
    val kaptGradleModel =
      if (androidModels != null) androidModels.kaptGradleModel else resolverCtx.getExtraProject(gradleModule, KaptGradleModel::class.java)
    val mppModel = resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)
    val gradlePluginModel = resolverCtx.getExtraProject(gradleModule, GradlePluginModel::class.java)
    val buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, GradleBuildScriptClasspathModel::class.java)
    var androidModel: GradleAndroidModelData? = null
    var ndkModuleModel: NdkModuleModel? = null
    var gradleModel: GradleModuleModel? = null
    if (androidModels != null) {
      androidModel = createGradleAndroidModel(moduleName, rootModulePath, androidModels, mppModel)
      val ndkModuleName = moduleName + "." + getModuleName(androidModel.mainArtifactCore.name)
      ndkModuleModel = maybeCreateNdkModuleModel(ndkModuleName, rootModulePath!!, androidModels)
    }
    val gradleSettingsFile = findGradleSettingsFile(rootModulePath!!)
    val hasArtifactsOrNoRootSettingsFile = !(gradleSettingsFile.isFile && !hasArtifacts(externalProject))
    if (hasArtifactsOrNoRootSettingsFile || androidModel != null) {
      gradleModel = createGradleModuleModel(
        moduleName,
        gradleModule,
        androidModels?.androidProject?.agpVersion,
        buildScriptClasspathModel,
        gradlePluginModel
      )
    }
    if (gradleModel != null) {
      moduleNode.createChild(AndroidProjectKeys.GRADLE_MODULE_MODEL, gradleModel)
    }
    if (androidModel != null) {
      moduleNode.createChild(AndroidProjectKeys.ANDROID_MODEL, androidModel)
    }
    if (ndkModuleModel != null) {
      moduleNode.createChild(AndroidProjectKeys.NDK_MODEL, ndkModuleModel)
    }

    // We also need to patch java modules as we disabled the kapt resolver.
    // Setup Kapt this functionality should be done by KaptProjectResovlerExtension if possible.
    // If we have module per sourceSet turned on we need to fill in the GradleSourceSetData for each of the artifacts.
    if (androidModel != null) {
      val variant = androidModel.selectedVariantCore
      val prodModule = createAndSetupGradleSourceSetDataNode(
        moduleNode, gradleModule, variant.mainArtifact.name,
        null
      )
      val unitTest: IdeBaseArtifactCore? = variant.hostTestArtifacts.find { it.name == IdeArtifactName.UNIT_TEST }
      if (unitTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, unitTest.name, prodModule)
      }
      val androidTest: IdeBaseArtifactCore? = variant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
      if (androidTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, androidTest.name, prodModule)
      }
      val testFixtures: IdeBaseArtifactCore? = variant.testFixturesArtifact
      if (testFixtures != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, testFixtures.name, prodModule)
      }
      val screenshotTest: IdeBaseArtifactCore? = variant.hostTestArtifacts.find { it.name == IdeArtifactName.SCREENSHOT_TEST }
      if (screenshotTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, screenshotTest.name, prodModule)
      }

      // Setup testData nodes for testing sources used by Gradle test runners.
      createAndSetupTestDataNode(moduleNode, androidModel)

      addGeneratedClassesToLibraryDependencies(variant, moduleNode)
    }
    patchMissingKaptInformationOntoModelAndDataNode(androidModel, moduleNode, kaptGradleModel)

    // Populate extra things
    populateAdditionalClassifierArtifactsModel(gradleModule)
  }

  @Suppress("NewApi")
  private fun createAndSetupTestDataNode(
    moduleDataNode: DataNode<ModuleData>,
    gradleAndroidModel: GradleAndroidModelData
  ) {
    // Get the unit test task for the current module.
    val testTaskName = getTasksFromAndroidModuleData(gradleAndroidModel)
    val moduleData = moduleDataNode.data
    val gradlePath = moduleData.gradleIdentityPath

    val sourceFolders: MutableSet<String> = HashSet()
    for (sourceProvider in gradleAndroidModel.getTestSourceProviders(IdeArtifactName.UNIT_TEST)) {
      for (sourceFolder in getAllSourceFolders(sourceProvider)) {
        sourceFolders.add(sourceFolder.path)
      }
    }
    val taskNamePrefix = if (gradlePath == ":") gradlePath else "$gradlePath:"
    val testData = TestData(GradleConstants.SYSTEM_ID, testTaskName, taskNamePrefix + testTaskName, sourceFolders)
    moduleDataNode.createChild(ProjectKeys.TEST, testData)
  }

  private fun addGeneratedClassesToLibraryDependencies(
    variant: IdeVariantCore,
    moduleNode: DataNode<ModuleData>)
  {
    variant.mainArtifact.generatedClassPaths.forEach {
      addToNewOrExistingLibraryData(
        moduleNode.findSourceSetDataForArtifact(variant.mainArtifact),
        it.key,
        setOf(it.value),
        false)
    }
  }

  private fun createAndSetupGradleSourceSetDataNode(
    parentDataNode: DataNode<ModuleData>,
    gradleModule: IdeaModule,
    artifactName: IdeArtifactName,
    productionModule: GradleSourceSetData?
  ): GradleSourceSetData {
    val moduleId = computeModuleIdForArtifact(resolverCtx, gradleModule, artifactName)
    val readableArtifactName = getModuleName(artifactName)
    val moduleExternalName = gradleModule.name + ":" + readableArtifactName
    val moduleInternalName = parentDataNode.data.internalName + "." + readableArtifactName
    val sourceSetData = GradleSourceSetData(
      moduleId, moduleExternalName, moduleInternalName, parentDataNode.data.moduleFileDirectoryPath,
      parentDataNode.data.linkedExternalProjectPath
    )
    if (productionModule != null) {
      sourceSetData.productionModuleId = productionModule.internalName
    }
    parentDataNode.createChild(GradleSourceSetData.KEY, sourceSetData)
    return sourceSetData
  }

  private fun populateAdditionalClassifierArtifactsModel(gradleModule: IdeaModule) {
    val project = project
    val artifacts = resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel::class.java)
    if (artifacts != null && project != null) {
      LibraryFilePaths.getInstance(project).populate(artifacts)
    }
  }

  override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val gradleAndroidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL)
    // Only process android modules.
    if (gradleAndroidModelNode == null) {
      super.populateModuleContentRoots(gradleModule, ideModule)
      return
    }
    nextResolver.populateModuleContentRoots(gradleModule, ideModule)
    ideModule.setupAndroidContentEntriesPerSourceSet(gradleAndroidModelNode.data)
  }

  override fun populateModuleDependencies(
    gradleModule: IdeaModule,
    ideModule: DataNode<ModuleData>,
    ideProject: DataNode<ProjectData>
  ) {
    val androidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL)
    // Don't process non-android modules here.
    if (androidModelNode == null) {
      super.populateModuleDependencies(gradleModule, ideModule, ideProject)
      return
    }
    if (myResolvedLibraryTable == null) {
      val ideLibraryTable = resolverCtx.models.getModel(
        IdeUnresolvedLibraryTableImpl::class.java
      )
        ?: throw IllegalStateException("IdeLibraryTableImpl is unavailable in resolverCtx when GradleAndroidModel's are present")
      myResolvedLibraryTable = buildResolvedLibraryTable(ideProject, ideLibraryTable)
      ideProject.createChild(
        AndroidProjectKeys.IDE_LIBRARY_TABLE,
        myResolvedLibraryTable!!
      )
    }
    val libraryResolver = fromLibraryTables(myResolvedLibraryTable!!, null)

    // Call all the other resolvers to ensure that any dependencies that they need to provide are added.
    nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject)
    val additionalArtifacts = resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel::class.java)
    // TODO: Log error messages from additionalArtifacts.
    val additionalArtifactsMap =
      additionalArtifacts?.artifacts?.associateBy { String.format("%s:%s:%s", it.id.groupId, it.id.artifactId, it.id.version) }.orEmpty()

    val project = project
    val libraryFilePaths = project?.let(LibraryFilePaths::getInstance)
    val artifactLookup = Function { library: IdeArtifactLibrary ->
      // Attempt to find the source/doc/samples jars within the library if we haven't injected the additional artifacts model builder
      if (additionalArtifacts == null) {
        return@Function AdditionalArtifactsPaths(listOfNotNull(library.srcJar, library.samplesJar), library.docJar)
      }

      // Otherwise fall back to using the model from the injected model builder.
      val artifactId = library.component?.toIdentifier() ?: library.name
      // First check to see if we just obtained any paths from Gradle. Since we don't request all the paths this can be null
      // or contain an incomplete set of entries. In order to complete this set we need to obtain the reminder from LibraryFilePaths cache.
      val artifacts = additionalArtifactsMap[artifactId]
      if (artifacts != null) {
        return@Function AdditionalArtifactsPaths(artifacts.sources, artifacts.javadoc)
      }

      // Then check to see whether we already have the library cached.
      if (libraryFilePaths != null) {
        val cachedPaths = libraryFilePaths.getCachedPathsForArtifact(artifactId)
        if (cachedPaths != null) {
          return@Function AdditionalArtifactsPaths(cachedPaths.sources, cachedPaths.javaDoc)
        }
      }
      null
    }

    ideModule.setupAndroidDependenciesForMpss(
      { gradleProjectPath: GradleSourceSetProjectPath ->
        val node = myModuleDataByGradlePath[gradleProjectPath] ?: return@setupAndroidDependenciesForMpss null
        node.data
      },
      artifactLookup::apply,
      androidModelNode.data.selectedVariant(libraryResolver)
    )
  }

  private fun buildResolvedLibraryTable(
    ideProject: DataNode<ProjectData>,
    ideLibraryTable: IdeUnresolvedLibraryTable
  ): IdeResolvedLibraryTable {
    val artifactModuleIdMap = buildArtifactsModuleIdMap()
    val kotlinMultiplatformAndroidSourceSetData = ExternalSystemApiUtil.find(
      ideProject,
      AndroidProjectKeys.KOTLIN_MULTIPLATFORM_ANDROID_SOURCE_SETS_TABLE
    )?.data
    return ResolvedLibraryTableBuilder(
      { key: Any? -> myGradlePathByModuleId[key] },
      { key: Any? -> myModuleDataByGradlePath[key] },
      { artifact: File -> resolveArtifact(artifactModuleIdMap, artifact) },
      {
        kotlinMultiplatformAndroidSourceSetData?.sourceSetsByGradleProjectPath?.get(
          it.path
        )?.get(KotlinMultiplatformAndroidSourceSetType.MAIN)
      }
    ).buildResolvedLibraryTable(ideLibraryTable)
  }

  private fun buildArtifactsModuleIdMap(): Map<String, Set<String>> {
    val artifactsMap = resolverCtx.artifactsMap
    return artifactsMap.keys.associateWith { path ->
      artifactsMap.getModuleMapping(path)?.moduleIds?.toSet().orEmpty()
    }
  }

  private fun resolveArtifact(artifactToModuleIdMap: Map<String, Set<String>>, artifact: File) =
    artifactToModuleIdMap[ExternalSystemApiUtil.toCanonicalPath(artifact.path)]
      ?.mapNotNull { artifactToModuleId -> myGradlePathByModuleId[artifactToModuleId] as? GradleSourceSetProjectPath }

  @Suppress("UnstableApiUsage")
  override fun resolveFinished(projectDataNode: DataNode<ProjectData>) {
    disableOrphanModuleNotifications()
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject.
  private val isAndroidGradleProject: Boolean by lazy {
    resolverCtx.hasModulesWithModel(IdeAndroidModels::class.java)
  }

  /**
   * Find IdeaModule representations of every Gradle project included in the main build and calls
   * [.removeExternalSourceSetsAndReportWarnings] on each of them.
   *
   * @param project         the project
   * @param mainGradleBuild the IdeaProject representing the mainGradleBuild
   */
  private fun removeExternalSourceSetsAndReportWarnings(project: Project, mainGradleBuild: IdeaProject) {
    // We also need to process composite builds
    val models = resolverCtx.models
    val compositeProjects: Collection<IdeaProject?> =
      models.includedBuilds.map { build -> models.getModel(build, IdeaProject::class.java) }
    val gradleProjects =
      compositeProjects.flatMap { gradleBuild: IdeaProject? -> gradleBuild!!.modules } +
      mainGradleBuild.modules

    gradleProjects.forEach { gradleProject: IdeaModule -> removeExternalSourceSetsAndReportWarnings(project, gradleProject) }
  }

  /**
   * This method strips all Gradle source sets from JetBrains' ExternalProject model associated with the given gradleProject.
   * It also emits a warning informing the user that these source sets were detected and ignored. Android modules do not use
   * standard Gradle source sets and as such they can be safely ignored by the IDE.
   *
   *
   * This method will ignore any non-Android modules as these source sets are required for these modules.
   *
   * @param gradleProject the module to process
   */
  private fun removeExternalSourceSetsAndReportWarnings(project: Project, gradleProject: IdeaModule) {
    resolverCtx.getExtraProject(gradleProject, IdeAndroidModels::class.java)
      ?: // Not an android module
      return
    val externalProject = resolverCtx.getExtraProject(gradleProject, ExternalProject::class.java)
    if (externalProject == null || externalProject.sourceSets.isEmpty()) {
      // No create source sets exist
      return
    }

    // Obtain the existing source set names to add the error message
    val sourceSetNames = java.lang.String.join(", ", externalProject.sourceSets.keys)

    // Remove the source sets so the platform doesn't create extra modules
    val defaultExternalProject = externalProject as DefaultExternalProject
    defaultExternalProject.sourceSets = emptyMap()
    val notification = Notification(
      "Detected Gradle source sets",
      "Non-Android source sets detected in '" + externalProject.getQName() + "'",
      "Gradle source sets ignored: $sourceSetNames.",
      NotificationType.WARNING
    )
    Notifications.Bus.notify(notification, project)
  }

  /**
   * This method is not used. Its functionality is only present when not using a
   * [ProjectImportModelProvider]. See: [.getModelProvider]
   */
  override fun getExtraProjectModelClasses(): Set<Class<*>> {
    throw UnsupportedOperationException("getExtraProjectModelClasses() is not used when getModelProvider() is overridden.")
  }

  override fun getModelProvider(): ProjectImportModelProvider? {
    return resolverCtx.configureAndGetExtraModelProvider()
  }

  override fun preImportCheck() {
    // Don't run pre-import checks for the buildSrc project.
    if (resolverCtx.buildSrcGroup != null) {
      return
    }
    SimulatedSyncErrors.simulateRegisteredSyncError()
    val projectPath = resolverCtx.projectPath
    SdkSync.getInstance().syncAndroidSdks(projectPath)
    val project = project

    displayInternalWarningIfForcedUpgradesAreDisabled()
    project?.getService(AssistantInvoker::class.java)?.expireProjectUpgradeNotifications(project)
    if (IdeInfo.getInstance().isAndroidStudio && !ApplicationManager.getApplication().isHeadlessEnvironment) {
      // Don't execute in IDEA in order to avoid conflicting behavior with IDEA's proxy support in gradle project.
      // (https://youtrack.jetbrains.com/issue/IDEA-245273, see BaseResolverExtension#getExtraJvmArgs)
      // To be discussed with the AOSP team to find a way to unify configuration across IDEA and AndroidStudio.
      cleanUpHttpProxySettings()
    }
  }

  override fun getExtraJvmArgs(): List<Pair<String, String>> {
    if (ExternalSystemApiUtil.isInProcessMode(GradleProjectSystemUtil.GRADLE_SYSTEM_ID)) {
      val args: MutableList<Pair<String, String>> = ArrayList()
      if (!IdeInfo.getInstance().isAndroidStudio) {
        val localProperties = localProperties
        if (localProperties.androidSdkPath == null) {
          val androidHomePath = IdeSdks.getInstance().androidSdkPath
          // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
          if (androidHomePath != null) {
            args.add(Pair.create(AndroidGradleSettings.ANDROID_HOME_JVM_ARG, androidHomePath.path))
          }
        }
      }
      return args
    }
    return emptyList()
  }

  private val localProperties: LocalProperties
    get() {
      val projectDir = FilePaths.stringToFile(resolverCtx.projectPath)
      return try {
        LocalProperties(projectDir!!)
      } catch (e: IOException) {
        val msg = String.format("Unable to read local.properties file in project '%1\$s'", projectDir!!.path)
        throw ExternalSystemException(msg, e)
      }
    }

  override fun getExtraCommandLineArgs(): List<String> {
    val project = project
    return myCommandLineArgs[project]
  }

  override fun getUserFriendlyError(
    buildEnvironment: BuildEnvironment?,
    error: Throwable,
    projectPath: String,
    buildFilePath: String?
  ): ExternalSystemException {
    val msg = error.message
    if (msg != null) {
      val rootCause = ExceptionUtil.getRootCause(error)
      if (rootCause is ZipException) {
        if (msg.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)) {
          return ExternalSystemException(msg)
        }
      }
      else if (rootCause is AndroidSyncException) {
        SyncFailureUsageReporter.getInstance().collectFailure(projectPath, rootCause.toGradleSyncFailure())
        val ideSyncIssues = rootCause.syncIssues
        if (ideSyncIssues?.isNotEmpty() == true) {
          val ideaProject = resolverCtx.models.getModel(IdeaProject::class.java)
          val ideaModule = ideaProject?.modules?.firstOrNull {
            it.matchesPath(rootCause.buildPath, rootCause.modulePath)
          }
          ideaModule?.let {
            val maybeModule = project?.findModule(it.getHolderProjectPath())
            maybeModule?.let { module ->
              SyncIssuesReporter.getInstance().report(
                mapOf(module to ideSyncIssues),
                ExternalSystemApiUtil.getExternalRootProjectPath(module))
              return ExternalSystemException(rootCause.message)
            }
          }
        }
        return ExternalSystemException(rootCause.message)
      }
    }
    return super.getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath)
  }

  private fun AndroidSyncException.toGradleSyncFailure(): GradleSyncFailure = when (type) {
    AndroidSyncExceptionType.AGP_VERSION_TOO_OLD -> GradleSyncFailure.OLD_ANDROID_PLUGIN
    AndroidSyncExceptionType.AGP_VERSION_TOO_NEW -> GradleSyncFailure.ANDROID_PLUGIN_TOO_NEW
    AndroidSyncExceptionType.AGP_VERSION_INCOMPATIBLE -> GradleSyncFailure.ANDROID_PLUGIN_VERSION_INCOMPATIBLE
    AndroidSyncExceptionType.AGP_VERSIONS_MISMATCH -> GradleSyncFailure.MULTIPLE_ANDROID_PLUGIN_VERSIONS
    AndroidSyncExceptionType.NO_VALID_NATIVE_ABI_FOUND -> GradleSyncFailure.ANDROID_SYNC_NO_VALID_NATIVE_ABI_FOUND
    AndroidSyncExceptionType.NO_VARIANTS_FOUND -> GradleSyncFailure.ANDROID_SYNC_NO_VARIANTS_FOUND
  }

  private fun displayInternalWarningIfForcedUpgradesAreDisabled() {
    if (StudioFlags.DISABLE_FORCED_UPGRADES.get()) {
      val project = project
      project?.getService(AssistantInvoker::class.java)?.displayForceUpdatesDisabledMessage(project)
    }
  }

  private fun cleanUpHttpProxySettings() {
    val project = project
    if (project != null) {
      HttpProxySettingsCleanUp.cleanUp(project)
    }
  }

  override fun enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
    val classPath = parameters.classPath
    classPath.add(PathUtil.getJarPathForClass(javaClass))
    classPath.add(PathUtil.getJarPathForClass(Revision::class.java))
    classPath.add(PathUtil.getJarPathForClass(AndroidGradleSettings::class.java))
  }

  companion object {
    /**
     * Stores a collection of variants of the data node tree for previously synced build variants.
     *
     *
     * NOTE: This key/data is not directly processed by any data importers.
     */
    val CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS = Key.create(
      VariantProjectDataNodes::class.java, 1 /* not used */
    )

    private const val BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME = "Build sync orphan modules"
    private val IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY =
      com.intellij.openapi.util.Key.create<Boolean>("IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY")
    private val IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY =
      com.intellij.openapi.util.Key.create<Boolean>("IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY")

    private fun createGradleModuleModel(
      moduleName: String,
      gradleModule: IdeaModule,
      modelVersionString: String?,
      buildScriptClasspathModel: GradleBuildScriptClasspathModel?,
      gradlePluginModel: GradlePluginModel?
    ): GradleModuleModel {
      val buildScriptPath = try {
        gradleModule.gradleProject.buildScript.sourceFile
      } catch (e: UnsupportedOperationException) {
        null
      }
      return GradleModuleModel(
        moduleName,
        gradleModule.gradleProject,
        buildScriptPath,
        buildScriptClasspathModel?.gradleVersion,
        modelVersionString,
        gradlePluginModel?.hasSafeArgsJava() ?: false,
        gradlePluginModel?.hasSafeArgsKotlin() ?: false
      )
    }

    private fun maybeCreateNdkModuleModel(
      moduleName: String,
      rootModulePath: File,
      ideModels: IdeAndroidModels
    ): NdkModuleModel? {
      // Prefer V2 NativeModule if available
      val selectedAbiName = ideModels.selectedAbiName ?: return null
      // If there are models we have a selected ABI name.
      if (ideModels.v2NativeModule != null) {
        return NdkModuleModel(
          moduleName,
          rootModulePath,
          ideModels.selectedVariantName,
          selectedAbiName,
          V2NdkModel(ideModels.androidProject.agpVersion, ideModels.v2NativeModule!!)
        )
      }
      // V2 model not available, fallback to V1 model.
      if (ideModels.v1NativeProject != null) {
        val ideNativeVariantAbis: MutableList<IdeNativeVariantAbi> = ArrayList()
        if (ideModels.v1NativeVariantAbi != null) {
          ideNativeVariantAbis.add(ideModels.v1NativeVariantAbi!!)
        }
        return NdkModuleModel(
          moduleName,
          rootModulePath,
          ideModels.selectedVariantName,
          selectedAbiName,
          ideModels.v1NativeProject!!,
          ideNativeVariantAbis
        )
      }
      return null
    }

    private fun createGradleAndroidModel(
      moduleName: String,
      rootModulePath: File?,
      ideModels: IdeAndroidModels,
      mppModel: KotlinMPPGradleModel?
    ): GradleAndroidModelData {
      return create(
        moduleName,
        rootModulePath!!,
        ideModels.androidProject,
        ideModels.fetchedVariants.map {
          if (mppModel != null && it.name == ideModels.selectedVariantName) it.patchFromMppModel(ideModels.androidProject, mppModel)
          else it
        },
        ideModels.selectedVariantName
      )
    }

    /**
     * Get test tasks for a given android model.
     *
     * @return the test task for the module. This does not include the full task path, but only the task name.
     * The full task path will be configured later at the execution level in the Gradle producers.
     */
    private fun getTasksFromAndroidModuleData(gradleAndroidModel: GradleAndroidModelData): String {
      val variant = gradleAndroidModel.selectedVariantName
      return "test".appendCapitalized(variant, "unitTest")
    }

    private fun computeModuleIdForArtifact(
      resolverCtx: ProjectResolverContext,
      gradleModule: IdeaModule,
      artifactName: IdeArtifactName
    ): String {
      return GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule) + ":" + getModuleName(artifactName)
    }

    /**
     * Adds the Kapt generated source directories to Android models generated source folders and sets up the kapt generated class library
     * for both Android and non-android modules.
     *
     *
     * This should probably not be done here. If we need this information in the Android model then this should
     * be the responsibility of the Android Gradle plugin. If we don't then this should be handled by the
     * KaptProjectResolverExtension, however as of now this class only works when module per source set is
     * enabled.
     */
    fun patchMissingKaptInformationOntoModelAndDataNode(
      androidModel: GradleAndroidModelData?,
      moduleDataNode: DataNode<ModuleData>,
      kaptGradleModel: KaptGradleModel?
    ) {
      if (androidModel == null || kaptGradleModel == null || !kaptGradleModel.isEnabled) {
        return
      }
      kaptGradleModel.sourceSets.forEach { sourceSet: KaptSourceSetModel ->
        val result = findVariantAndDataNode(sourceSet, androidModel, moduleDataNode)
          ?: // No artifact was found for the current source set
          return@forEach
        val variant = result.first
        if (variant == androidModel.selectedVariantCore) {
          val classesDirFile = sourceSet.generatedClassesDirFile
          addToNewOrExistingLibraryData(result.second, "kaptGeneratedClasses", setOf(classesDirFile), sourceSet.isTest, true)
        }
      }
    }

    private fun addToNewOrExistingLibraryData(
      moduleDataNode: DataNode<GradleSourceSetData>,
      name: String,
      files: Set<File?>,
      isTest: Boolean,
      isExported: Boolean = false,
    ) {
      // Code adapted from KaptProjectResolverExtension
      val newLibrary = LibraryData(GradleProjectSystemUtil.GRADLE_SYSTEM_ID, name)
      val existingData = moduleDataNode.children.asSequence()
        .map { obj: DataNode<*> -> obj.data }
        .filter { data: Any? -> data is LibraryDependencyData && newLibrary.externalName == data.externalName }
        .map { data: Any -> (data as LibraryDependencyData).target }
        .firstOrNull()
      if (existingData != null) {
        files.forEach { file: File? -> existingData.addPath(LibraryPathType.BINARY, file!!.absolutePath) }
      } else {
        files.forEach { file: File? -> newLibrary.addPath(LibraryPathType.BINARY, file!!.absolutePath) }
        val libraryDependencyData = LibraryDependencyData(moduleDataNode.data, newLibrary, LibraryLevel.MODULE)
        libraryDependencyData.scope = if (isTest) DependencyScope.TEST else DependencyScope.COMPILE
        libraryDependencyData.isExported = isExported
        moduleDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
      }
    }

    private fun findVariantAndDataNode(
      sourceSetModel: KaptSourceSetModel,
      androidModel: GradleAndroidModelData,
      moduleNode: DataNode<ModuleData>
    ): Pair<IdeVariantCore, DataNode<GradleSourceSetData>>? {
      val sourceSetName = sourceSetModel.sourceSetName
      if (!sourceSetModel.isTest) {
        val variant = androidModel.findVariantCoreByName(sourceSetName)
        return if (variant == null) null else Pair.create(variant, moduleNode.findSourceSetDataForArtifact(variant.mainArtifact))
      }

      // Check if it's android test source set.
      val androidTestSuffix = "AndroidTest"
      if (sourceSetName.endsWith(androidTestSuffix)) {
        val variantName = sourceSetName.substring(0, sourceSetName.length - androidTestSuffix.length)
        val variant = androidModel.findVariantCoreByName(variantName)
        val artifact: IdeBaseArtifactCore? = variant?.deviceTestArtifacts?.find { it.name == IdeArtifactName.ANDROID_TEST }
        return if (artifact == null) null else Pair.create(variant, moduleNode.findSourceSetDataForArtifact(artifact))
      }

      // Check if it's test fixtures source set.
      val testFixturesSuffix = "TestFixtures"
      if (sourceSetName.endsWith(testFixturesSuffix)) {
        val variantName = sourceSetName.substring(0, sourceSetName.length - testFixturesSuffix.length)
        val variant = androidModel.findVariantCoreByName(variantName)
        val artifact: IdeBaseArtifactCore? = variant?.testFixturesArtifact
        return if (artifact == null) null else Pair.create(variant, moduleNode.findSourceSetDataForArtifact(artifact))
      }

      // Check if it's unit test source set.
      val unitTestSuffix = "UnitTest"
      if (sourceSetName.endsWith(unitTestSuffix)) {
        val variantName = sourceSetName.substring(0, sourceSetName.length - unitTestSuffix.length)
        val variant = androidModel.findVariantCoreByName(variantName)
        val artifact: IdeBaseArtifactCore? = variant?.hostTestArtifacts?.find { it.name == IdeArtifactName.UNIT_TEST }
        return if (artifact == null) null else Pair.create(variant, moduleNode.findSourceSetDataForArtifact(artifact))
      }

      // Check if it's a screenshot test source set.
      val screenshotTest = "ScreenshotTest"
      if (sourceSetName.endsWith(screenshotTest)) {
        val variantName = sourceSetName.substring(0, sourceSetName.length - screenshotTest.length)
        val variant = androidModel.findVariantCoreByName(variantName)
        val artifact = variant?.hostTestArtifacts?.find { it.name == IdeArtifactName.SCREENSHOT_TEST }
        return if (artifact == null) null else Pair.create(variant, moduleNode.findSourceSetDataForArtifact(artifact))
      }

      return null
    }

    private fun hasArtifacts(externalProject: ExternalProject?): Boolean {
      return externalProject?.artifacts?.isNotEmpty() ?: false
    }

    /**
     * A method that resets the configuration of "Build sync orphan modules" notification group to "not display" and "not log"
     * in order to prevent a notification which allows users to restore the removed module as a non-Gradle module. Non-Gradle modules
     * are not supported by AS in Gradle projects.
     */
    private fun disableOrphanModuleNotifications() {
      if (IdeInfo.getInstance().isAndroidStudio) {
        NotificationsConfiguration
          .getNotificationsConfiguration()
          .changeSettings(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false)
      }
    }

    fun shouldDisableForceUpgrades(): Boolean {
      if (ApplicationManager.getApplication().isUnitTestMode) return true
      if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return true
      return StudioFlags.DISABLE_FORCED_UPGRADES.get()
    }

    private val VARIANTS_SAVED_FROM_PREVIOUS_SYNCS =
      com.intellij.openapi.util.Key<VariantProjectDataNodes>("variants.saved.from.previous.syncs")

    @JvmStatic
    fun saveCurrentlySyncedVariantsForReuse(project: Project) {
      val data = ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.basePath!!) ?: return
      val currentDataNodes = data.externalProjectStructure ?: return
      project.putUserData(
        VARIANTS_SAVED_FROM_PREVIOUS_SYNCS,
        collectCurrentAndPreviouslyCachedVariants(currentDataNodes)
      )
    }

    @JvmStatic
    fun clearVariantsSavedForReuse(project: Project) {
      project.putUserData(
        VARIANTS_SAVED_FROM_PREVIOUS_SYNCS,
        null
      )
    }

    @VisibleForTesting
    fun attachVariantsSavedFromPreviousSyncs(project: Project, projectDataNode: DataNode<ProjectData>) {
      val projectUserData = project.getUserData(VARIANTS_SAVED_FROM_PREVIOUS_SYNCS)
      if (projectUserData != null) {
        projectDataNode.createChild(CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, projectUserData)
      }
    }

    fun alignProjectJdkWithGradleSyncJdk(project: Project, projectDataNode: DataNode<ProjectData>) {
      JdkUtils.getMaxVersionJdkPathFromAllGradleRoots(project)?.let {
        projectDataNode.createChild(AndroidProjectKeys.PROJECT_JDK_UPDATE, ProjectJdkUpdateData(it))
      }
    }

    private fun getAllSourceFolders(provider: IdeSourceProvider): Collection<File> {
      return listOf(
        provider.javaDirectories,
        provider.kotlinDirectories,
        provider.resDirectories,
        provider.aidlDirectories,
        provider.renderscriptDirectories,
        provider.assetsDirectories,
        provider.jniLibsDirectories,
        provider.baselineProfileDirectories,
      ).flatten()
    }
  }
}

private fun IdeAndroidSyncIssuesAndExceptions.process(moduleDataNode: DataNode<ModuleData>) {
  fun Collection<Throwable>.toSyncIssues(): List<IdeSyncIssue> {
    return map {
      IdeSyncIssueImpl(
        message = it.message ?: "",
        data = null,
        multiLineMessage = it.stackTraceAsMultiLineMessage(),
        severity = IdeSyncIssue.SEVERITY_ERROR,
        type = IdeSyncIssue.TYPE_EXCEPTION
      )
    }
  }
  val allSyncIssues = (exceptions.toSyncIssues() + syncIssues)

  fun Collection<IdeSyncIssue>.createAsDataNodesAndAttach(moduleDataNode: DataNode<ModuleData>) {
    forEach { it: IdeSyncIssue -> moduleDataNode.createChild(AndroidProjectKeys.SYNC_ISSUE, it) }
  }

  allSyncIssues.createAsDataNodesAndAttach(moduleDataNode)
  exceptions.forEach { logger<AndroidGradleProjectResolver>().warn(it) }
}

private fun IdeaModule.matchesPath(buildPath: String?, modulePath: String?): Boolean {
  return projectIdentifier.buildIdentifier.rootDir.path == buildPath
         && projectIdentifier.projectPath?.equals(modulePath) == true
}

private fun IdeaModule.getHolderProjectPath(): GradleHolderProjectPath {
  return GradleHolderProjectPath(
    projectIdentifier.buildIdentifier.rootDir.path,
    projectIdentifier.projectPath)
}

