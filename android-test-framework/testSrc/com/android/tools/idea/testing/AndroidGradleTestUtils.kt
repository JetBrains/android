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
package com.android.tools.idea.testing

import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.ViewBindingOptions
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub
import com.android.ide.common.gradle.model.stubs.AndroidGradlePluginProjectFlagsStub
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub
import com.android.ide.common.gradle.model.stubs.ApiVersionStub
import com.android.ide.common.gradle.model.stubs.BuildTypeContainerStub
import com.android.ide.common.gradle.model.stubs.BuildTypeStub
import com.android.ide.common.gradle.model.stubs.DependenciesStub
import com.android.ide.common.gradle.model.stubs.DependencyGraphsStub
import com.android.ide.common.gradle.model.stubs.InstantRunStub
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub
import com.android.ide.common.gradle.model.stubs.LintOptionsStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub
import com.android.ide.common.gradle.model.stubs.SourceProviderStub
import com.android.ide.common.gradle.model.stubs.VariantStub
import com.android.ide.common.gradle.model.stubs.VectorDrawablesOptionsStub
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.idea.IdeaSyncPopulateProjectTask
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.truth.TruthJUnit
import com.intellij.externalSystem.JavaProjectData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalTask
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import java.io.File

typealias AndroidProjectBuilder = (projectName: String, basePath: File) -> AndroidProject

/**
 * An interface providing access to [AndroidProject] sub-model builders are used to build [AndroidProject] and its other sub-models.
 */
interface AndroidProjectStubBuilder {
  val projectName: String
  val basePath: File
  val buildPath: File
  val minSdk: Int
  val targetSdk: Int
  val mainSourceProvider: SourceProvider
  val androidTestSourceProviderContainer: SourceProviderContainer?
  val unitTestSourceProviderContainer: SourceProviderContainer?
  val debugSourceProvider: SourceProvider?
  val releaseSourceProvider: SourceProvider?
  val defaultConfig: ProductFlavorContainer
  val debugBuildType: BuildTypeContainer?
  val releaseBuildType: BuildTypeContainer?
  val viewBindingOptions: ViewBindingOptions
  val mainArtifact: AndroidArtifact
  val androidProject: AndroidProject
}

/**
 * A helper method for building [AndroidProject] stubs.
 *
 * This method creates a model of a simple project which can be slightly customized by providing alternative implementations of
 * sub-model builders.
 */
fun createAndroidProjectBuilder(
  minSdk: AndroidProjectStubBuilder.() -> Int = { 16 },
  targetSdk: AndroidProjectStubBuilder.() -> Int = { 22 },
  defaultConfig: AndroidProjectStubBuilder.() -> ProductFlavorContainerStub = { buildDefaultConfigStub() },
  mainSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub = { buildMainSourceProviderStub() },
  androidTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub? = { buildAndroidTestSourceProviderContainerStub() },
  unitTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub? = { buildUnitTestSourceProviderContainerStub() },
  debugSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub? = { buildDebugSourceProviderStub() },
  releaseSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub? = { buildReleaseSourceProviderStub() },
  debugBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub? = { buildDebugBuildTypeStub() },
  releaseBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub? = { buildReleaseBuildTypeStub() },
  viewBindingOptions: AndroidProjectStubBuilder.() -> ViewBindingOptionsStub = { buildViewBindingOptions() },
  mainArtifactStub: AndroidProjectStubBuilder.() -> AndroidArtifactStub = { buildMainArtifactStub() },
  androidProject: AndroidProjectStubBuilder.() -> AndroidProject = { buildAndroidProjectStub() }
): AndroidProjectBuilder {
  return { projectName, basePath ->
    val builder = object : AndroidProjectStubBuilder {
      override val projectName: String = projectName
      override val basePath: File = basePath
      override val buildPath: File get() = basePath.resolve("build")
      override val minSdk: Int get() = minSdk()
      override val targetSdk: Int get() = targetSdk()
      override val mainSourceProvider: SourceProvider get() = mainSourceProvider()
      override val androidTestSourceProviderContainer: SourceProviderContainer? get() = androidTestSourceProvider()
      override val unitTestSourceProviderContainer: SourceProviderContainer? get() = unitTestSourceProvider()
      override val debugSourceProvider: SourceProvider? get() = debugSourceProvider()
      override val releaseSourceProvider: SourceProvider? get() = releaseSourceProvider()
      override val defaultConfig: ProductFlavorContainer = defaultConfig()
      override val debugBuildType: BuildTypeContainer? = debugBuildType()
      override val releaseBuildType: BuildTypeContainer? = releaseBuildType()
      override val viewBindingOptions: ViewBindingOptions = viewBindingOptions()
      override val mainArtifact: AndroidArtifact = mainArtifactStub()
      override val androidProject: AndroidProject = androidProject()
    }
    builder.androidProject
  }
}

fun createAndroidProjectBuilderForDefaultTestProjectStructure(): AndroidProjectBuilder =
    createAndroidProjectBuilder(
      minSdk = { AndroidVersion.MIN_RECOMMENDED_API },
      targetSdk = { AndroidVersion.VersionCodes.O_MR1 },
      mainSourceProvider = {
        SourceProviderStub(
          ARTIFACT_NAME_MAIN,
          File(basePath, "AndroidManifest.xml"),
          listOf(File(basePath, "src")),
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList(),
          listOf(File(basePath, "res")),
          emptyList(),
          emptyList(),
          emptyList())
      },
      androidTestSourceProvider = { null },
      unitTestSourceProvider = { null },
      releaseSourceProvider = { null }
    )

fun AndroidProjectStubBuilder.buildMainSourceProviderStub() =
  SourceProviderStub(ARTIFACT_NAME_MAIN, basePath.resolve("src/main"), "AndroidManifest.xml")

fun AndroidProjectStubBuilder.buildAndroidTestSourceProviderContainerStub() =
  SourceProviderContainerStub(
    ARTIFACT_NAME_ANDROID_TEST,
    SourceProviderStub(ARTIFACT_NAME_ANDROID_TEST, basePath.resolve("src/androidTest"), "AndroidManifest.xml"))

fun AndroidProjectStubBuilder.buildUnitTestSourceProviderContainerStub() =
  SourceProviderContainerStub(
    ARTIFACT_NAME_UNIT_TEST,
    SourceProviderStub(ARTIFACT_NAME_UNIT_TEST, basePath.resolve("src/test"), "AndroidManifest.xml"))

fun AndroidProjectStubBuilder.buildDebugSourceProviderStub() =
  SourceProviderStub("debug", basePath.resolve("src/debug"), "AndroidManifest.xml")

fun AndroidProjectStubBuilder.buildReleaseSourceProviderStub() =
  SourceProviderStub("release", basePath.resolve("src/release"), "AndroidManifest.xml")

fun AndroidProjectStubBuilder.buildDefaultConfigStub() = ProductFlavorContainerStub(
  ProductFlavorStub(
    mapOf(),
    listOf(),
    VectorDrawablesOptionsStub(),
    null,
    null,
    12,
    "2.0",
    ApiVersionStub("$minSdk", null, minSdk),
    ApiVersionStub("$targetSdk", null, targetSdk),
    null,
    null,
    null,
    null,
    null,
    null,
    "android.test.InstrumentationTestRunner",
    null,
    null,
    null,
    null
  ),
  mainSourceProvider,
  listOfNotNull(androidTestSourceProviderContainer, unitTestSourceProviderContainer)
)

fun AndroidProjectStubBuilder.buildDebugBuildTypeStub() = debugSourceProvider?.let { debugSourceProvider ->
  BuildTypeContainerStub(
    BuildTypeStub(
      debugSourceProvider.name, mapOf(), mapOf(), mapOf(), listOf(), listOf(), listOf(), mapOf(), null, null, null, null, null,
      true, true, true, 1, false, true),
    debugSourceProvider,
    listOf())
}

fun AndroidProjectStubBuilder.buildReleaseBuildTypeStub() = releaseSourceProvider?.let { releaseSourceProvider ->
  BuildTypeContainerStub(
    BuildTypeStub(
      releaseSourceProvider.name, mapOf(), mapOf(), mapOf(), listOf(), listOf(), listOf(), mapOf(), null, null, null, null,
      null, false, false, false, 1, true, true),
    releaseSourceProvider,
    listOf())
}

fun AndroidProjectStubBuilder.buildViewBindingOptions() = ViewBindingOptionsStub()

fun AndroidProjectStubBuilder.buildMainArtifactStub() = AndroidArtifactStub(
    ARTIFACT_NAME_MAIN,
    "compile",
    "assemble",
    buildPath.resolve("classes"),
    setOf(),
    buildPath.resolve("res"),
    DependenciesStub(listOf(), listOf(), listOf(), listOf(), listOf()),
    DependenciesStub(listOf(), listOf(), listOf(), listOf(), listOf()),
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf(),
    setOf(),
    null,
    null,
    listOf(),
    "applicationId",
    "sourceGenTaskName",
    mapOf(),
    mapOf(),
    InstantRunStub(),
    "defaultConfig",
    null,
    null,
    listOf(),
    null,
    null,
    null,
    null,
    false
  )

fun AndroidProjectStubBuilder.buildAndroidProjectStub(): AndroidProjectStub {
  val debugBuildType = this.debugBuildType
  val releaseBuildType = this.releaseBuildType
  val defaultVariant = debugBuildType ?: releaseBuildType
  val defaultVariantName = defaultVariant?.sourceProvider?.name ?: "main"
  return AndroidProjectStub(
    LatestKnownPluginVersionProvider.INSTANCE.get(),
    projectName,
    null,
    defaultConfig,
    listOfNotNull(debugBuildType, releaseBuildType),
    listOf(),
    "buildToolsVersion",
    listOf(),
    listOf(
      VariantStub(
        defaultVariantName,
        defaultVariantName,
        mainArtifact,
        listOf(),
        listOf(),
        defaultVariantName,
        listOf(),
        defaultConfig.productFlavor,
        listOf(),
        false
      )),
    listOfNotNull(debugBuildType?.sourceProvider?.name, releaseBuildType?.sourceProvider?.name),
    defaultVariantName,
    listOf(),
    getLatestAndroidPlatform(),
    listOf(),
    listOf(),
    listOf(),
    LintOptionsStub(),
    setOf(),
    JavaCompileOptionsStub(),
    AaptOptionsStub(),
    viewBindingOptions,
    buildPath,
    null,
    1,
    true,
    2,
    true,
    AndroidGradlePluginProjectFlagsStub()
  )
}


/**
 * Sets up [project] as a one module project configured in the same way sync would conigure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  basePath: File,
  stubBuilder: AndroidProjectBuilder
) {

  val moduleManager = ModuleManager.getInstance(project)
  TruthJUnit.assume().that(moduleManager.modules.size).isEqualTo(0)

  val gradlePlugins = listOf(
    "com.android.java.model.builder.JavaLibraryPlugin", "org.gradle.buildinit.plugins.BuildInitPlugin",
    "org.gradle.buildinit.plugins.WrapperPlugin", "org.gradle.api.plugins.HelpTasksPlugin",
    "com.android.build.gradle.api.AndroidBasePlugin", "org.gradle.language.base.plugins.LifecycleBasePlugin",
    "org.gradle.api.plugins.BasePlugin", "org.gradle.api.plugins.ReportingBasePlugin",
    "org.gradle.api.plugins.JavaBasePlugin", "com.android.build.gradle.AppPlugin",
    "org.gradle.plugins.ide.idea.IdeaPlugin"
  )
  val task = IdeaSyncPopulateProjectTask(project)
  val buildPath = basePath.resolve("build")
  val projectName = project.name
  val androidProjectStub = stubBuilder(projectName, basePath)
  val projectDataNode = DataNode<ProjectData>(
    ProjectKeys.PROJECT,
    ProjectData(
      GRADLE_SYSTEM_ID,
      projectName,
      basePath.path,
      basePath.path),
    null)

  projectDataNode.addChild(
    DataNode<JavaProjectData>(
      JavaProjectData.KEY,
      JavaProjectData(GRADLE_SYSTEM_ID, buildPath.path),
      projectDataNode
    )
  )

  projectDataNode.addChild(
    DataNode<ExternalProject>(
      ExternalProjectDataCache.KEY,
      object : ExternalProject {
        override fun getExternalSystemId(): String = GRADLE_SYSTEM_ID.id
        override fun getId(): String = projectName
        override fun getName(): String = projectName
        override fun getQName(): String = projectName
        override fun getDescription(): String? = null
        override fun getGroup(): String = ""
        override fun getVersion(): String = "unspecified"
        override fun getChildProjects(): Map<String, ExternalProject> = mapOf()
        override fun getProjectDir(): File = basePath
        override fun getBuildDir(): File = buildPath
        override fun getBuildFile(): File? = null
        override fun getTasks(): Map<String, ExternalTask> = mapOf()
        override fun getSourceSets(): Map<String, ExternalSourceSet> = mapOf()
        override fun getArtifacts(): List<File> = listOf()
        override fun getArtifactsByConfiguration(): Map<String, MutableSet<File>> = mapOf()
      },
      projectDataNode
    )
  )

  val moduleDataNode = DataNode<ModuleData>(
    ProjectKeys.MODULE,
    ModuleData(
      projectName,
      GRADLE_SYSTEM_ID,
      JavaModuleType.getModuleType().id,
      projectName,
      basePath.path,
      basePath.path
    ),
    projectDataNode
  )

  moduleDataNode.addChild(
    DataNode<GradleModuleModel>(
      AndroidProjectKeys.GRADLE_MODULE_MODEL,
      GradleModuleModel(
        projectName,
        listOf(),
        ":",
        basePath,
        gradlePlugins,
        null,
        null,
        null,
        false
      ),
      projectDataNode
    )
  )

  moduleDataNode.addChild(
    DataNode<AndroidModuleModel>(
      AndroidProjectKeys.ANDROID_MODEL,
      AndroidModuleModel.create(
        projectName,
        basePath,
        IdeAndroidProjectImpl.create(
          androidProjectStub,
          IdeDependenciesFactory(),
          null,
          null),
        "debug"
      ),
      projectDataNode
    )
  )
  projectDataNode.addChild(moduleDataNode)
  IdeSdks.removeJdksOn(project)
  runWriteAction {
    task.populateProject(
      projectDataNode,
      ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project),
      PostSyncProjectSetup.Request(),
      null
    )
  }
}
