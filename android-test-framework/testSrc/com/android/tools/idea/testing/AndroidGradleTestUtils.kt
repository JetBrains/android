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

import com.android.AndroidProjectTypes
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.android.builder.model.AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.Dependencies
import com.android.builder.model.DependenciesInfo
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaLibrary
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.SyncIssue
import com.android.builder.model.ViewBindingOptions
import com.android.ide.common.gradle.model.impl.ModelCache
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub
import com.android.ide.common.gradle.model.stubs.AndroidGradlePluginProjectFlagsStub
import com.android.ide.common.gradle.model.stubs.AndroidLibraryStub
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub
import com.android.ide.common.gradle.model.stubs.ApiVersionStub
import com.android.ide.common.gradle.model.stubs.BuildTypeContainerStub
import com.android.ide.common.gradle.model.stubs.BuildTypeStub
import com.android.ide.common.gradle.model.stubs.DependenciesInfoStub
import com.android.ide.common.gradle.model.stubs.DependenciesStub
import com.android.ide.common.gradle.model.stubs.DependencyGraphsStub
import com.android.ide.common.gradle.model.stubs.InstantRunStub
import com.android.ide.common.gradle.model.stubs.JavaArtifactStub
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub
import com.android.ide.common.gradle.model.stubs.LintOptionsStub
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub
import com.android.ide.common.gradle.model.stubs.SourceProviderStub
import com.android.ide.common.gradle.model.stubs.VariantBuildInformationStub
import com.android.ide.common.gradle.model.stubs.VariantStub
import com.android.ide.common.gradle.model.stubs.VectorDrawablesOptionsStub
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.ide.common.repository.GradleVersion
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.testutils.TestUtils.getSdk
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.idea.IdeaSyncPopulateProjectTask
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.idea.setupDataNodesForSelectedVariant
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues.Companion.syncIssues
import com.android.tools.idea.gradle.util.GradleProjects
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.gradle.util.emulateStartupActivityForTest
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.truth.Truth.assertThat
import com.intellij.externalSystem.JavaProjectData
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes.JAVA
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.ensureIndexesUpToDate
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableConsumer
import com.intellij.util.text.nullize
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalTask
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import java.io.File
import java.io.IOException
import java.util.function.Consumer

typealias AndroidProjectBuilderCore = (projectName: String, basePath: File, agpVersion: String) -> AndroidProject

sealed class ModuleModelBuilder {
  abstract val gradlePath: String
  abstract val gradleVersion: String?
  abstract val agpVersion: String?
}

data class AndroidModuleModelBuilder(
  override val gradlePath: String,
  override val gradleVersion: String? = null,
  override val agpVersion: String? = null,
  val selectedBuildVariant: String,
  val projectBuilder: AndroidProjectBuilderCore
) : ModuleModelBuilder() {
  constructor (gradlePath: String, selectedBuildVariant: String, projectBuilder: AndroidProjectBuilder)
    : this(gradlePath, null, null, selectedBuildVariant, projectBuilder)

  constructor (
    gradlePath: String,
    gradleVersion: String? = null,
    agpVersion: String? = null,
    selectedBuildVariant: String,
    projectBuilder: AndroidProjectBuilder
  )
    : this(gradlePath, gradleVersion, agpVersion, selectedBuildVariant, projectBuilder.build())
}

data class JavaModuleModelBuilder(
  override val gradlePath: String,
  override val gradleVersion: String? = null,
  val buildable: Boolean = true
) : ModuleModelBuilder() {

  constructor (gradlePath: String, buildable: Boolean = true) : this(gradlePath, null, buildable)

  override val agpVersion: String? = null

  companion object {
    @JvmStatic
    val rootModuleBuilder = JavaModuleModelBuilder(":", buildable = false)
  }
}

data class AndroidModuleDependency(val moduleGradlePath: String, val variant: String?)

/**
 * An interface providing access to [AndroidProject] sub-model builders are used to build [AndroidProject] and its other sub-models.
 */
interface AndroidProjectStubBuilder {
  val agpVersion: String
  val buildId: String
  val projectName: String
  val basePath: File
  val buildPath: File
  val projectType: Int
  val minSdk: Int
  val targetSdk: Int
  val mlModelBindingEnabled: Boolean
  val agpProjectFlags: AndroidGradlePluginProjectFlags
  val mainSourceProvider: SourceProvider
  val androidTestSourceProviderContainer: SourceProviderContainer?
  val unitTestSourceProviderContainer: SourceProviderContainer?
  val debugSourceProvider: SourceProvider?
  val releaseSourceProvider: SourceProvider?
  val defaultConfig: ProductFlavorContainer
  val debugBuildType: BuildTypeContainer?
  val releaseBuildType: BuildTypeContainer?
  val dynamicFeatures: List<String>
  val viewBindingOptions: ViewBindingOptions
  val dependenciesInfo: DependenciesInfo
  val supportsBundleTask: Boolean
  fun androidModuleDependencies(variant: String): List<AndroidModuleDependency>?
  fun mainArtifact(variant: String): AndroidArtifact
  fun androidTestArtifact(variant: String): AndroidArtifact
  fun unitTestArtifact(variant: String): JavaArtifact
  val androidProject: AndroidProject
}

/**
 * A helper class for building [AndroidProject] stubs.
 *
 * This method creates a model of a simple project which can be slightly customized by providing alternative implementations of
 * sub-model builders.
 *
 * If a totally different is needed implement [AndroidProjectBuilderCore] directly.
 */
data class AndroidProjectBuilder(
  val buildId: AndroidProjectStubBuilder.() -> String = { "/tmp/buildId" }, //  buildId should not be assumed to be a path.
  val projectType: AndroidProjectStubBuilder.() -> Int = { AndroidProjectTypes.PROJECT_TYPE_APP },
  val minSdk: AndroidProjectStubBuilder.() -> Int = { 16 },
  val targetSdk: AndroidProjectStubBuilder.() -> Int = { 22 },
  val mlModelBindingEnabled: AndroidProjectStubBuilder.() -> Boolean = { false },
  val agpProjectFlags: AndroidProjectStubBuilder.() -> AndroidGradlePluginProjectFlags = { buildAgpProjectFlagsStub() },
  val defaultConfig: AndroidProjectStubBuilder.() -> ProductFlavorContainerStub = { buildDefaultConfigStub() },
  val mainSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub = { buildMainSourceProviderStub() },
  val androidTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub? = { buildAndroidTestSourceProviderContainerStub() },
  val unitTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub? = { buildUnitTestSourceProviderContainerStub() },
  val debugSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub? = { buildDebugSourceProviderStub() },
  val releaseSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub? = { buildReleaseSourceProviderStub() },
  val debugBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub? = { buildDebugBuildTypeStub() },
  val releaseBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub? = { buildReleaseBuildTypeStub() },
  val dynamicFeatures: AndroidProjectStubBuilder.() -> List<String> = { emptyList() },
  val viewBindingOptions: AndroidProjectStubBuilder.() -> ViewBindingOptionsStub = { buildViewBindingOptions() },
  val dependenciesInfo: AndroidProjectStubBuilder.() -> DependenciesInfoStub = { buildDependenciesInfo() },
  val supportsBundleTask: AndroidProjectStubBuilder.() -> Boolean = { true },
  val mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> AndroidArtifactStub = { variant -> buildMainArtifactStub(variant) },
  val androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> AndroidArtifactStub =
    { variant -> buildAndroidTestArtifactStub(variant) },
  val unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> JavaArtifactStub =
    { variant -> buildUnitTestArtifactStub(variant) },
  val androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency> = { emptyList() },
  val androidProject: AndroidProjectStubBuilder.() -> AndroidProject = { buildAndroidProjectStub() }
) {

  fun withBuildId(buildId: AndroidProjectStubBuilder.() -> String) =
    copy(buildId = buildId)

  fun withProjectType(projectType: AndroidProjectStubBuilder.() -> Int) =
    copy(projectType = projectType)

  fun withMinSdk(minSdk: AndroidProjectStubBuilder.() -> Int) =
    copy(minSdk = minSdk)

  fun withTargetSdk(targetSdk: AndroidProjectStubBuilder.() -> Int) =
    copy(targetSdk = targetSdk)

  fun withMlModelBindingEnabled(mlModelBindingEnabled: AndroidProjectStubBuilder.() -> Boolean) =
    copy(mlModelBindingEnabled = mlModelBindingEnabled)

  fun withAgpProjectFlags(agpProjectFlags: AndroidProjectStubBuilder.() -> AndroidGradlePluginProjectFlags) =
    copy(agpProjectFlags = agpProjectFlags)

  fun withDefaultConfig(defaultConfig: AndroidProjectStubBuilder.() -> ProductFlavorContainerStub) =
    copy(defaultConfig = defaultConfig)

  fun withMainSourceProvider(mainSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub) =
    copy(mainSourceProvider = mainSourceProvider)

  fun withAndroidTestSourceProvider(androidTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub?) =
    copy(androidTestSourceProvider = androidTestSourceProvider)

  fun withUnitTestSourceProvider(unitTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub?) =
    copy(unitTestSourceProvider = unitTestSourceProvider)

  fun withDebugSourceProvider(debugSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub?) =
    copy(debugSourceProvider = debugSourceProvider)

  fun withReleaseSourceProvider(releaseSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub?) =
    copy(releaseSourceProvider = releaseSourceProvider)

  fun withDebugBuildType(debugBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub?) =
    copy(debugBuildType = debugBuildType)

  fun withReleaseBuildType(releaseBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub?) =
    copy(releaseBuildType = releaseBuildType)

  fun withDynamicFeatures(dynamicFeatures: AndroidProjectStubBuilder.() -> List<String>) =
    copy(dynamicFeatures = dynamicFeatures)

  fun withViewBindingOptions(viewBindingOptions: AndroidProjectStubBuilder.() -> ViewBindingOptionsStub) =
    copy(viewBindingOptions = viewBindingOptions)

  fun withSupportsBundleTask(supportsBundleTask: AndroidProjectStubBuilder.() -> Boolean) =
    copy(supportsBundleTask = supportsBundleTask)

  fun withMainArtifactStub(mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> AndroidArtifactStub) =
    copy(mainArtifactStub = mainArtifactStub)

  fun withAndroidTestArtifactStub(androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> AndroidArtifactStub) =
    copy(androidTestArtifactStub = androidTestArtifactStub)

  fun withUnitTestArtifactStub(unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> JavaArtifactStub) =
    copy(unitTestArtifactStub = unitTestArtifactStub)

  fun withAndroidModuleDependencyList(androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency>) =
    copy(androidModuleDependencyList = androidModuleDependencyList)

  fun withAndroidProject(androidProject: AndroidProjectStubBuilder.() -> AndroidProject) =
    copy(androidProject = androidProject)


  fun build(): AndroidProjectBuilderCore = { projectName, basePath, agpVersion ->
    val builder = object : AndroidProjectStubBuilder {
      override val agpVersion: String = agpVersion
      override val buildId: String = buildId()
      override val projectName: String = projectName
      override val basePath: File = basePath
      override val buildPath: File get() = basePath.resolve("build")
      override val projectType: Int get() = projectType()
      override val minSdk: Int get() = minSdk()
      override val targetSdk: Int get() = targetSdk()
      override val mlModelBindingEnabled: Boolean get() = mlModelBindingEnabled()
      override val agpProjectFlags: AndroidGradlePluginProjectFlags get() = agpProjectFlags()
      override val mainSourceProvider: SourceProvider get() = mainSourceProvider()
      override val androidTestSourceProviderContainer: SourceProviderContainer? get() = androidTestSourceProvider()
      override val unitTestSourceProviderContainer: SourceProviderContainer? get() = unitTestSourceProvider()
      override val debugSourceProvider: SourceProvider? get() = debugSourceProvider()
      override val releaseSourceProvider: SourceProvider? get() = releaseSourceProvider()
      override val defaultConfig: ProductFlavorContainer = defaultConfig()
      override val debugBuildType: BuildTypeContainer? = debugBuildType()
      override val releaseBuildType: BuildTypeContainer? = releaseBuildType()
      override val dynamicFeatures: List<String> = dynamicFeatures()
      override val viewBindingOptions: ViewBindingOptions = viewBindingOptions()
      override val dependenciesInfo: DependenciesInfo = dependenciesInfo()
      override val supportsBundleTask: Boolean = supportsBundleTask()
      override fun androidModuleDependencies(variant: String): List<AndroidModuleDependency> = androidModuleDependencyList(variant)
      override fun mainArtifact(variant: String): AndroidArtifact = mainArtifactStub(variant)
      override fun androidTestArtifact(variant: String): AndroidArtifact = androidTestArtifactStub(variant)
      override fun unitTestArtifact(variant: String): JavaArtifact = unitTestArtifactStub(variant)
      override val androidProject: AndroidProject = androidProject()
    }
    builder.androidProject
  }
}

@JvmOverloads
fun createAndroidProjectBuilderForDefaultTestProjectStructure(
  projectType: Int = AndroidProjectTypes.PROJECT_TYPE_APP
): AndroidProjectBuilder =
  AndroidProjectBuilder(
    projectType = { projectType },
    minSdk = { AndroidVersion.MIN_RECOMMENDED_API },
    targetSdk = { AndroidVersion.VersionCodes.O_MR1 },
    mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
    androidTestSourceProvider = { null },
    unitTestSourceProvider = { null },
    releaseSourceProvider = { null }
  )
fun AndroidProjectStubBuilder.createMainSourceProviderForDefaultTestProjectStructure(): SourceProviderStub {
  return SourceProviderStub(
    ARTIFACT_NAME_MAIN,
    File(basePath, "AndroidManifest.xml"),
    listOf(File(basePath, "src")),
    emptyList(),
    emptyList(),
    emptyList(),
    listOf(File(basePath, "res")),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList())
}

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

fun AndroidProjectStubBuilder.buildAgpProjectFlagsStub() =
  AndroidGradlePluginProjectFlagsStub(mapOf(ML_MODEL_BINDING to mlModelBindingEnabled))

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
fun AndroidProjectStubBuilder.buildDependenciesInfo() = DependenciesInfoStub()

fun AndroidProjectStubBuilder.buildMainArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf()
): AndroidArtifactStub {
  val androidModuleDependencies = this.androidModuleDependencies(variant).orEmpty()
  val dependenciesStub = buildDependenciesStub(
    libraries = androidModuleDependencies.map {
      AndroidLibraryStub(
        MavenCoordinatesStub("artifacts", it.moduleGradlePath, "unspecificed", "jar"),
        this.buildId,
        it.moduleGradlePath,
        "artifacts:${it.moduleGradlePath}:unspecified@jar",
        false,
        false,
        File("stub_bundle.jar"),
        File("stub_folder"),
        emptyList(),
        emptyList(),
        File("stub_AndroidManifest.xml"),
        File("stub_jarFile.jar"),
        File("stub_compileJarFile.jar"),
        File("stub_resFolder"),
        File("stub_resStaticLibrary"),
        File("stub_assetsFolder"),
        it.variant,
        emptyList(),
        File("stub_proguard.txt"),
        File("stub_lintJar.jar"),
        File("stub_publicResources"),
        File("stub_symbolFile.txt"),
        File("stub_annotations.zip")
      )
    }
  )
  return AndroidArtifactStub(
    ARTIFACT_NAME_MAIN,
    "compile".appendCapitalized(variant).appendCapitalized("sources"),
    "assemble".appendCapitalized(variant),
    buildPath.resolve("output/apk/$variant/output.json"),
    buildPath.resolve("intermediates/javac/$variant/classes"),
    classFolders,
    buildPath.resolve("intermediates/java_res/$variant/out"),
    dependenciesStub,
    dependenciesStub,
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf("ideSetupTask1", "ideSetupTask2"),
    setOf(),
    null,
    null,
    listOf<AndroidArtifactOutput>(),
    "applicationId",
    "generate".appendCapitalized(variant).appendCapitalized("sources"),
    mapOf(),
    mapOf(),
    InstantRunStub(),
    "defaultConfig",
    null,
    listOf(),
    null,
    null,
    "bundle".takeIf { supportsBundleTask && projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant),
    buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json"),
    "extractApksFor".takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant),
    buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json"),
    null,
    false
  )
}

fun AndroidProjectStubBuilder.buildAndroidTestArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf()
): AndroidArtifactStub {
  val dependenciesStub = buildDependenciesStub()
  return AndroidArtifactStub(
    ARTIFACT_NAME_ANDROID_TEST,
    "compile".appendCapitalized(variant).appendCapitalized("androidTestSources"),
    "assemble".appendCapitalized(variant).appendCapitalized("androidTest"),
    buildPath.resolve("output/apk/$variant/output.json"),
    buildPath.resolve("intermediates/javac/${variant}AndroidTest/classes"),
    classFolders,
    buildPath.resolve("intermediates/java_res/${variant}AndroidTest/out"),
    dependenciesStub,
    dependenciesStub,
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf("ideAndroidTestSetupTask1", "ideAndroidTestSetupTask2"),
    setOf(),
    null,
    null,
    listOf(),
    "applicationId",
    "generate".appendCapitalized(variant).appendCapitalized("androidTestSources"),
    mapOf(),
    mapOf(),
    InstantRunStub(),
    "defaultConfig",
    null,
    listOf(),
    null,
    null,
    "bundle"
      .takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant)?.appendCapitalized("androidTest"),
    buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json"),
    "extractApksFor"
      .takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant)?.appendCapitalized("androidTest"),
    buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json"),
    null,
    false
  )
}

fun AndroidProjectStubBuilder.buildUnitTestArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf(),
  dependencies: Dependencies = buildDependenciesStub(),
  mockablePlatformJar: File? = null
): JavaArtifactStub {
  return JavaArtifactStub(
    ARTIFACT_NAME_UNIT_TEST,
    "compile".appendCapitalized(variant).appendCapitalized("unitTestSources"),
    "assemble".appendCapitalized(variant).appendCapitalized("unitTest"),
    buildPath.resolve("intermediates/javac/${variant}UnitTest/classes"),
    classFolders,
    buildPath.resolve("intermediates/java_res/${variant}UnitTest/out"),
    dependencies,
    dependencies,
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf("ideUnitTestSetupTask1", "ideUnitTestSetupTask2"),
    setOf(),
    null,
    null,
    mockablePlatformJar
  )
}

fun AndroidProjectStubBuilder.buildAndroidProjectStub(): AndroidProjectStub {
  val debugBuildType = this.debugBuildType
  val releaseBuildType = this.releaseBuildType
  val defaultVariant = debugBuildType ?: releaseBuildType
  val defaultVariantName = defaultVariant?.sourceProvider?.name ?: "main"
  return AndroidProjectStub(
    agpVersion,
    projectName,
    null,
    defaultConfig,
    listOfNotNull(debugBuildType, releaseBuildType),
    listOf(),
    "buildToolsVersion",
    "ndkVersion",
    listOf(),
    listOf("debug", "release")
      .map { variant ->
        VariantStub(
          variant,
          variant,
          mainArtifact(variant),
          listOf(androidTestArtifact(variant)),
          listOf(unitTestArtifact(variant)),
          variant,
          listOf(),
          defaultConfig.productFlavor,
          listOf(),
          false,
          listOf()
        )
      },
    listOfNotNull(debugBuildType?.sourceProvider?.name, releaseBuildType?.sourceProvider?.name),
    defaultVariantName,
    listOf(),
    getLatestAndroidPlatform(),
    listOf(),
    listOf(),
    LintOptionsStub(),
    listOf(),
    setOf(),
    JavaCompileOptionsStub(),
    AaptOptionsStub(),
    dynamicFeatures,
    viewBindingOptions,
    dependenciesInfo,
    buildPath,
    null,
    1,
    true,
    projectType,
    true,
    agpProjectFlags,
    listOf("debug", "release")
      .map { variantName ->
        VariantBuildInformationStub(
          variantName,
          "assemble".appendCapitalized(variantName),
          buildPath.resolve("output/apk/$variantName/output.json").absolutePath,
          "bundle".takeIf { supportsBundleTask && projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variantName),
          buildPath.resolve("intermediates/bundle_ide_model/$variantName/output.json").absolutePath,
          "extractApksFor".takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variantName),
          buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variantName/output.json").absolutePath
        )
      }
  )
}

fun AndroidProjectStubBuilder.buildDependenciesStub(
  libraries: List<AndroidLibrary> = listOf(),
  javaLibraries: List<JavaLibrary> = listOf(),
  projects: List<String> = listOf(),
  javaModules: List<Dependencies.ProjectIdentifier> = listOf(),
  runtimeOnlyClasses: List<File> = listOf()
): DependenciesStub = DependenciesStub(libraries, javaLibraries, projects, javaModules, runtimeOnlyClasses)

/**
 * Sets up [project] as a one module project configured in the same way sync would conigure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  basePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) {
  val modelCache = ModelCache.create()
  if (IdeSdks.getInstance().androidSdkPath === null) {
    AndroidGradleTests.setUpSdks(project, project, getSdk().toFile())
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  val moduleManager = ModuleManager.getInstance(project)
  if (moduleManager.modules.size <= 1) {
    runWriteAction {
      val modifiableModel = moduleManager.modifiableModel
      val module = if (modifiableModel.modules.isEmpty()) {
        modifiableModel.newModule(basePath.resolve("${project.name}.iml").path, JAVA.id)
      }
      else {
        moduleManager.modules[0]
      }
      if (module.name != project.name) {
        modifiableModel.renameModule(module, project.name)
      }
      modifiableModel.commit()
      ExternalSystemModulePropertyManager
        .getInstance(module)
        .setExternalOptions(
          GRADLE_SYSTEM_ID,
          ModuleData(":", GRADLE_SYSTEM_ID, JAVA.id, project.name, basePath.systemIndependentPath, basePath.systemIndependentPath),
          ProjectData(GRADLE_SYSTEM_ID, project.name, project.basePath!!, basePath.systemIndependentPath))
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
  else {
    error("There is already more than one module in the test project.")
  }

  ProjectSystemService.getInstance(project).replaceProjectSystemForTests(GradleProjectSystem(project))
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

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
  val projectDataNode = DataNode<ProjectData>(
    ProjectKeys.PROJECT,
    ProjectData(
      GRADLE_SYSTEM_ID,
      projectName,
      basePath.systemIndependentPath,
      basePath.systemIndependentPath),
    null)

  projectDataNode.addChild(
    DataNode<JavaProjectData>(
      JavaProjectData.KEY,
      JavaProjectData(GRADLE_SYSTEM_ID, buildPath.systemIndependentPath, LanguageLevel.JDK_1_6, null),
      null
    )
  )
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

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
        override fun getSourceCompatibility(): String? = null
        override fun getTargetCompatibility(): String? = null
        override fun getProjectDir(): File = basePath
        override fun getBuildDir(): File = buildPath
        override fun getBuildFile(): File? = null
        override fun getTasks(): Map<String, ExternalTask> = mapOf()
        override fun getSourceSets(): Map<String, ExternalSourceSet> = mapOf()
        override fun getArtifacts(): List<File> = listOf()
        override fun getArtifactsByConfiguration(): Map<String, MutableSet<File>> = mapOf()
      },
      null
    )
  )
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

  val androidModels = mutableListOf<AndroidModuleModel>()
  moduleBuilders.forEach { moduleBuilder ->
    val gradlePath = moduleBuilder.gradlePath
    val moduleName = gradlePath.substringAfterLast(':').nullize() ?: projectName;
    val moduleBasePath = basePath.resolve(gradlePath.substring(1).replace(':', File.separatorChar))
    FileUtils.mkdirs(moduleBasePath)
    val moduleDataNode = when (moduleBuilder) {
      is AndroidModuleModelBuilder -> {
        createAndroidModuleDataNode(
          modelCache,
          moduleName,
          gradlePath,
          moduleBasePath,
          moduleBuilder.gradleVersion,
          moduleBuilder.agpVersion,
          gradlePlugins,
          moduleBuilder.projectBuilder(
            moduleName,
            moduleBasePath,
            moduleBuilder.agpVersion ?: LatestKnownPluginVersionProvider.INSTANCE.get()
          ),
          moduleBuilder.selectedBuildVariant
        ).also { androidModelDataNode ->
          val model = ExternalSystemApiUtil.find(androidModelDataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
          if (model != null) {
            androidModels.add(model)
          }
        }
      }
      is JavaModuleModelBuilder ->
        createJavaModuleDataNode(
          modelCache,
          moduleName,
          gradlePath,
          moduleBasePath,
          moduleBuilder.buildable
        )
    }
    projectDataNode.addChild(moduleDataNode)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  setupDataNodesForSelectedVariant(project, androidModels, projectDataNode)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

  ProjectDataManager.getInstance().importData(projectDataNode, project, true)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

  // Effectively getTestRootDisposable(), which is not the project itself but its earlyDisposable.
  IdeSdks.removeJdksOn((project as? ProjectEx)?.earlyDisposable ?: project)
  runWriteAction {
    task.populateProject(
      projectDataNode,
      null
    )
    if (GradleSyncState.getInstance(project).lastSyncFailed()) error("Test project setup failed.")
  }
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  ensureIndexesUpToDate(project)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}

private fun createAndroidModuleDataNode(
  modelCache: ModelCache,
  moduleName: String,
  gradlePath: String,
  moduleBasePath: File,
  gradleVersion: String?,
  agpVersion: String?,
  gradlePlugins: List<String>,
  androidProjectStub: AndroidProject,
  selectedVariantName: String
): DataNode<ModuleData> {

  val moduleDataNode = createGradleModuleDataNode(gradlePath, moduleName, moduleBasePath)

  moduleDataNode.addChild(
    DataNode<GradleModuleModel>(
      AndroidProjectKeys.GRADLE_MODULE_MODEL,
      GradleModuleModel(
        moduleName,
        listOf(),
        gradlePath,
        moduleBasePath,
        gradlePlugins,
        null,
        gradleVersion,
        agpVersion,
        false
      ),
      null
    )
  )

  val modelVersion = GradleVersion.tryParseAndroidGradlePluginVersion(androidProjectStub.modelVersion)
  val androidProject = modelCache.androidProjectFrom(androidProjectStub)
  moduleDataNode.addChild(
    DataNode<AndroidModuleModel>(
      AndroidProjectKeys.ANDROID_MODEL,
      AndroidModuleModel.create(
        moduleName,
        moduleBasePath,
        androidProject,
        androidProjectStub.variants.map { modelCache.variantFrom(androidProject, it, modelVersion) },
        selectedVariantName
      ),
      null
    )
  )

  return moduleDataNode
}

private fun createJavaModuleDataNode(
  modelCache: ModelCache,
  moduleName: String,
  gradlePath: String,
  moduleBasePath: File,
  buildable: Boolean
): DataNode<ModuleData> {

  val moduleDataNode = createGradleModuleDataNode(gradlePath, moduleName, moduleBasePath)

  if (buildable || gradlePath != ":") {
    moduleDataNode.addChild(
      DataNode<GradleModuleModel>(
        AndroidProjectKeys.GRADLE_MODULE_MODEL,
        GradleModuleModel(
          moduleName,
          listOf(),
          gradlePath,
          moduleBasePath,
          emptyList(),
          null,
          null,
          null,
          false
        ),
        null
      )
    )
  }

  moduleDataNode.addChild(
    DataNode<JavaModuleModel>(
      AndroidProjectKeys.JAVA_MODULE_MODEL,
      JavaModuleModel.create(
        moduleName,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyMap(),
        null,
        null,
        null,
        buildable
      ),
      null
    )
  )

  return moduleDataNode
}

private fun createGradleModuleDataNode(
  gradlePath: String,
  moduleName: String,
  moduleBasePath: File
): DataNode<ModuleData> {
  val moduleDataNode = DataNode<ModuleData>(
    ProjectKeys.MODULE,
    ModuleData(
      if (gradlePath == ":") moduleName else gradlePath,
      GRADLE_SYSTEM_ID,
      JavaModuleType.getModuleType().id,
      moduleName,
      moduleBasePath.systemIndependentPath,
      moduleBasePath.systemIndependentPath
    ),
    null
  )
  return moduleDataNode
}

/**
 * Finds a module by the given [gradlePath].
 *
 * Note: In the case of composite build [gradlePath] can be in a form of `includedProject:module:module` for modules from included projects.
 */
fun Project.gradleModule(gradlePath: String): Module? =
  ModuleManager.getInstance(this).modules.singleOrNull { GradleProjects.getGradleModulePath(it) == gradlePath }

/**
 * Finds a file by the [path] relative to the corresponding Gradle project root.
 */
fun Module.fileUnderGradleRoot(path: @SystemIndependent String): VirtualFile? =
  VirtualFileManager.getInstance().findFileByUrl("${FilePaths.pathToIdeaUrl(File(AndroidProjectRootUtil.getModuleDirPath(this)!!))}/$path")

/**
 * See implementing classes for usage examples.
 */
interface GradleIntegrationTest {
  /**
   * Assumed to be matched by [UsefulTestCase.getName].
   */
  fun getName(): String

  /**
   * The base test directory to be used in tests.
   */
  fun getBaseTestPath(): @SystemDependent String


  /**
   * The path to a test data directory relative to the workspace or `null` to use the legacy resolution.
   */
  fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String

  /**
   * The collection of additional repositories to be added to the Gradle project.
   */
  fun getAdditionalRepos(): Collection<File>

  /**
   * The base testData directory to be used in tests.
   */
  fun resolveTestDataPath(testDataPath: @SystemIndependent String): File {
    val testDataDirectory = getWorkspaceRoot().resolve(toSystemDependentName(getTestDataDirectoryWorkspaceRelativePath()))
    return testDataDirectory.resolve(toSystemDependentName(testDataPath)).toFile()
  }
}

/**
 * Prepares a test project created from a [testProjectPath] under the given [name] so that it can be opened with [openPreparedProject].
 */
@JvmOverloads
fun GradleIntegrationTest.prepareGradleProject(
  testProjectPath: String,
  name: String,
  gradleVersion: String? = null,
  gradlePluginVersion: String? = null,
  kotlinVersion: String? = null
): File {
  if (name == this.getName()) throw IllegalArgumentException("Additional projects cannot be opened under the test name: $name")
  val srcPath = resolveTestDataPath(testProjectPath)
  val projectPath = nameToPath(name)

  AndroidGradleTests.prepareProjectForImportCore(
    srcPath, projectPath,
    ThrowableConsumer<File, IOException> { projectRoot ->
      AndroidGradleTests.defaultPatchPreparedProject(projectRoot, gradleVersion, gradlePluginVersion,
                                                     kotlinVersion,
                                                     *getAdditionalRepos().toTypedArray())
    })
  return projectPath
}

fun prepareGradleProject(projectSourceRoot: File, projectPath: File, projectPatcher: ThrowableConsumer<File, IOException>) {
  AndroidGradleTests.validateGradleProjectSource(projectSourceRoot)
  AndroidGradleTests.prepareProjectForImportCore(projectSourceRoot, projectPath, projectPatcher)
}

/**
 * Opens a test project previously prepared under the given [name], runs a test [action] and then closes and disposes the project.
 *
 * The project's `.idea` directory is not required to exist, however.
 */
fun <T> GradleIntegrationTest.openPreparedProject(name: String, action: (Project) -> T): T {
  return openPreparedProject(
    nameToPath(name),
    verifyOpened = ::verifySyncedSuccessfully,
    action = action
  )
}

/**
 * Opens a test project previously prepared under the given [name], verifies the state of the project with [verifyOpened] and runs
 * a test [action] and then closes and disposes the project.
 *
 * The project's `.idea` directory is not required to exist, however.
 */
fun <T> GradleIntegrationTest.openPreparedProject(
  name: String,
  verifyOpened: (Project) -> Unit,
  action: (Project) -> T
): T {
  return openPreparedProject(nameToPath(name), verifyOpened, action)
}

private fun <T> openPreparedProject(
  projectPath: File,
  verifyOpened: (Project) -> Unit,
  action: (Project) -> T): T {
  val project = runInEdtAndGet {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    val project = ProjectUtil.openOrImport(projectPath.absolutePath, null, true)!!
    // Unfortunately we do not have start-up activities run in tests so we have to trigger a refresh here.
    emulateStartupActivityForTest(project)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    project
  }
  try {
    verifyOpened(project)
    return action(project)
  }
  finally {
    runInEdtAndWait {
      PlatformTestUtil.saveProject(project, true)
      ProjectUtil.closeAndDispose(project)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }
}

private fun GradleIntegrationTest.nameToPath(name: String) =
  File(toSystemDependentName(getBaseTestPath() + "/" + name))

private fun verifySyncedSuccessfully(project: Project) {
  val lastSyncResult = project.getProjectSystem().getSyncManager().getLastSyncResult()
  if (!lastSyncResult.isSuccessful) {
    throw IllegalStateException(lastSyncResult.name)
  }

  // Also fail the test if SyncIssues with type errors are present.
  val errors = ModuleManager.getInstance(project)
    .modules
    .flatMap { it.syncIssues() }
    .filter { it.severity == SyncIssue.SEVERITY_ERROR }
  if (errors.isNotEmpty()) {
    throw IllegalStateException(errors.joinToString(separator = "\n") { it.message })
  }
}

fun JavaCodeInsightTestFixture.makeAutoIndexingOnCopy(): JavaCodeInsightTestFixture {
  return object : JavaCodeInsightTestFixture by this@makeAutoIndexingOnCopy {
    override fun copyFileToProject(sourceFilePath: String): VirtualFile {
      return copyFileToProject(sourceFilePath, sourceFilePath)
    }

    override fun copyFileToProject(sourceFilePath: String, targetPath: String): VirtualFile {
      val testDataPath = testDataPath
      val sourceFile = File(testDataPath, toSystemDependentName(sourceFilePath))
      val targetFile: File = File(tempDirPath).resolve(toSystemDependentName(targetPath))
      assert(sourceFile.exists())
      FileUtil.createParentDirs(targetFile)
      FileUtil.copy(sourceFile, targetFile)
      VfsUtil.markDirtyAndRefresh(false, false, false, targetFile)
      ensureIndexesUpToDate(project)
      return VfsUtil.findFileByIoFile(targetFile, true) ?: error("Failed to copy $sourceFile to $targetFile")
    }

    override fun copyDirectoryToProject(sourceFilePath: String, targetPath: String): VirtualFile {
      error("Not implemented")
    }
  }
}


fun verifySyncSkipped(project: Project, disposable: Disposable) {
  assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED)
  project.verifyModelsAttached()
  var completed = false
  project.runWhenSmartAndSynced(disposable, callback = Consumer {
    completed = true
  })
  assertThat(completed).isTrue()
}

fun switchVariant(project: Project, moduleGradlePath: String, variant: String) {
  BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, project.gradleModule(moduleGradlePath)!!.name, variant)
}


inline fun <reified F, reified M> Module.verifyModel(getFacet: Module.() -> F?, getModel: F.() -> M) {
  val facet = getFacet()
  if (facet != null) {
    val model = facet.getModel()
    assertThat(model).named("${M::class.simpleName} for ${F::class.simpleName} in ${name} module").isNotNull()
  }
}

private fun Project.verifyModelsAttached() {
  ModuleManager.getInstance(this).modules.forEach { module ->
    module.verifyModel(GradleFacet::getInstance, GradleFacet::getGradleModuleModel)
    if (GradleFacet.getInstance(module) != null) {
      // Java facets are not created for modules without GradleFacet even if there is a JavaModuleModel.
      module.verifyModel(JavaFacet::getInstance, JavaFacet::getJavaModuleModel)
    }
    module.verifyModel(AndroidFacet::getInstance, AndroidModuleModel::get)
    module.verifyModel({ NdkFacet.getInstance(this) }, { ndkModuleModel })
  }
}
