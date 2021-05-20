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

import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.testutils.TestUtils.getSdk
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTasksAndOutputInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.V2NdkModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.getInstance
import com.android.tools.idea.gradle.project.sync.idea.AdditionalArtifactsPaths
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.ALWAYS_SKIP_SYNC
import com.android.tools.idea.gradle.project.sync.idea.IdeaSyncPopulateProjectTask
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.idea.setupAndroidContentEntries
import com.android.tools.idea.gradle.project.sync.idea.setupAndroidDependenciesForModule
import com.android.tools.idea.gradle.project.sync.idea.setupCompilerOutputPaths
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
import com.android.utils.cxx.CompileCommandsEncoder
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
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTests
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

data class AndroidProjectModels(
  val androidProject: IdeAndroidProjectImpl,
  val variants: Collection<IdeVariantImpl>,
  val ndkModel: V2NdkModel?
)

typealias AndroidProjectBuilderCore = (projectName: String, basePath: File, agpVersion: String) -> AndroidProjectModels

sealed class ModuleModelBuilder {
  abstract val gradlePath: String
  abstract val gradleVersion: String?
  abstract val agpVersion: String?
}

data class AndroidModuleModelBuilder(
  override val gradlePath: String,
  override val gradleVersion: String? = null,
  override val agpVersion: String? = null,
  val projectBuilder: AndroidProjectBuilderCore,
  val selectedBuildVariant: String,
  val selectedAbiVariant: String? = null
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
    : this(gradlePath, gradleVersion, agpVersion, projectBuilder.build(), selectedBuildVariant, selectedAbiVariant = null)

  fun withSelectedAbi(abi: String) = copy(selectedAbiVariant = abi)
  fun withSelectedBuildVariant(variant: String) = copy(selectedBuildVariant = variant)
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
  val projectType: IdeAndroidProjectType
  val minSdk: Int
  val targetSdk: Int
  val mlModelBindingEnabled: Boolean
  val agpProjectFlags: IdeAndroidGradlePluginProjectFlagsImpl
  val mainSourceProvider: IdeSourceProviderImpl
  val androidTestSourceProviderContainer: IdeSourceProviderContainerImpl?
  val unitTestSourceProviderContainer: IdeSourceProviderContainerImpl?
  val debugSourceProvider: IdeSourceProviderImpl?
  val releaseSourceProvider: IdeSourceProviderImpl?
  val defaultConfig: IdeProductFlavorContainerImpl
  val debugBuildType: IdeBuildTypeContainerImpl?
  val releaseBuildType: IdeBuildTypeContainerImpl?
  val dynamicFeatures: List<String>
  val viewBindingOptions: IdeViewBindingOptionsImpl
  val dependenciesInfo: IdeDependenciesInfoImpl
  val supportsBundleTask: Boolean
  fun androidModuleDependencies(variant: String): List<AndroidModuleDependency>?
  fun androidLibraryDependencies(variant: String): List<IdeAndroidLibraryImpl>?
  fun mainArtifact(variant: String): IdeAndroidArtifactImpl
  fun androidTestArtifact(variant: String): IdeAndroidArtifactImpl
  fun unitTestArtifact(variant: String): IdeJavaArtifactImpl
  val androidProject: IdeAndroidProjectImpl
  val variants: List<IdeVariantImpl>
  val ndkModel: V2NdkModel?
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
  val projectType: AndroidProjectStubBuilder.() -> IdeAndroidProjectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
  val minSdk: AndroidProjectStubBuilder.() -> Int = { 16 },
  val targetSdk: AndroidProjectStubBuilder.() -> Int = { 22 },
  val mlModelBindingEnabled: AndroidProjectStubBuilder.() -> Boolean = { false },
  val agpProjectFlags: AndroidProjectStubBuilder.() -> IdeAndroidGradlePluginProjectFlagsImpl = { buildAgpProjectFlagsStub() },
  val defaultConfig: AndroidProjectStubBuilder.() -> IdeProductFlavorContainerImpl = { buildDefaultConfigStub() },
  val mainSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl = { buildMainSourceProviderStub() },
  val androidTestSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl? = { buildAndroidTestSourceProviderContainerStub() },
  val unitTestSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl? = { buildUnitTestSourceProviderContainerStub() },
  val debugSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl? = { buildDebugSourceProviderStub() },
  val releaseSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl? = { buildReleaseSourceProviderStub() },
  val debugBuildType: AndroidProjectStubBuilder.() -> IdeBuildTypeContainerImpl? = { buildDebugBuildTypeStub() },
  val releaseBuildType: AndroidProjectStubBuilder.() -> IdeBuildTypeContainerImpl? = { buildReleaseBuildTypeStub() },
  val dynamicFeatures: AndroidProjectStubBuilder.() -> List<String> = { emptyList() },
  val viewBindingOptions: AndroidProjectStubBuilder.() -> IdeViewBindingOptionsImpl = { buildViewBindingOptions() },
  val dependenciesInfo: AndroidProjectStubBuilder.() -> IdeDependenciesInfoImpl = { buildDependenciesInfo() },
  val supportsBundleTask: AndroidProjectStubBuilder.() -> Boolean = { true },
  val mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactImpl = { variant -> buildMainArtifactStub(variant) },
  val androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactImpl =
    { variant -> buildAndroidTestArtifactStub(variant) },
  val unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeJavaArtifactImpl =
    { variant -> buildUnitTestArtifactStub(variant) },
  val androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency> = { emptyList() },
  val androidLibraryDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<IdeAndroidLibraryImpl> = { emptyList() },
  val androidProject: AndroidProjectStubBuilder.() -> IdeAndroidProjectImpl = { buildAndroidProjectStub() },
  val variants: AndroidProjectStubBuilder.() -> List<IdeVariantImpl> = { buildVariantStubs() },
  val ndkModel: AndroidProjectStubBuilder.() -> V2NdkModel? = { null }
) {

  fun withBuildId(buildId: AndroidProjectStubBuilder.() -> String) =
    copy(buildId = buildId)

  fun withProjectType(projectType: AndroidProjectStubBuilder.() -> IdeAndroidProjectType) =
    copy(projectType = projectType)

  fun withMinSdk(minSdk: AndroidProjectStubBuilder.() -> Int) =
    copy(minSdk = minSdk)

  fun withTargetSdk(targetSdk: AndroidProjectStubBuilder.() -> Int) =
    copy(targetSdk = targetSdk)

  fun withMlModelBindingEnabled(mlModelBindingEnabled: AndroidProjectStubBuilder.() -> Boolean) =
    copy(mlModelBindingEnabled = mlModelBindingEnabled)

  fun withAgpProjectFlags(agpProjectFlags: AndroidProjectStubBuilder.() -> IdeAndroidGradlePluginProjectFlagsImpl) =
    copy(agpProjectFlags = agpProjectFlags)

  fun withDefaultConfig(defaultConfig: AndroidProjectStubBuilder.() -> IdeProductFlavorContainerImpl) =
    copy(defaultConfig = defaultConfig)

  fun withMainSourceProvider(mainSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl) =
    copy(mainSourceProvider = mainSourceProvider)

  fun withAndroidTestSourceProvider(androidTestSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl?) =
    copy(androidTestSourceProvider = androidTestSourceProvider)

  fun withUnitTestSourceProvider(unitTestSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl?) =
    copy(unitTestSourceProvider = unitTestSourceProvider)

  fun withDebugSourceProvider(debugSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl?) =
    copy(debugSourceProvider = debugSourceProvider)

  fun withReleaseSourceProvider(releaseSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl?) =
    copy(releaseSourceProvider = releaseSourceProvider)

  fun withDebugBuildType(debugBuildType: AndroidProjectStubBuilder.() -> IdeBuildTypeContainerImpl?) =
    copy(debugBuildType = debugBuildType)

  fun withReleaseBuildType(releaseBuildType: AndroidProjectStubBuilder.() -> IdeBuildTypeContainerImpl?) =
    copy(releaseBuildType = releaseBuildType)

  fun withDynamicFeatures(dynamicFeatures: AndroidProjectStubBuilder.() -> List<String>) =
    copy(dynamicFeatures = dynamicFeatures)

  fun withViewBindingOptions(viewBindingOptions: AndroidProjectStubBuilder.() -> IdeViewBindingOptionsImpl) =
    copy(viewBindingOptions = viewBindingOptions)

  fun withSupportsBundleTask(supportsBundleTask: AndroidProjectStubBuilder.() -> Boolean) =
    copy(supportsBundleTask = supportsBundleTask)

  fun withMainArtifactStub(mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactImpl) =
    copy(mainArtifactStub = mainArtifactStub)

  fun withAndroidTestArtifactStub(androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactImpl) =
    copy(androidTestArtifactStub = androidTestArtifactStub)

  fun withUnitTestArtifactStub(unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeJavaArtifactImpl) =
    copy(unitTestArtifactStub = unitTestArtifactStub)

  fun withAndroidModuleDependencyList(androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency>) =
    copy(androidModuleDependencyList = androidModuleDependencyList)

  fun withAndroidLibraryDependencyList(androidLibraryDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<IdeAndroidLibraryImpl>) =
    copy(androidLibraryDependencyList = androidLibraryDependencyList)

  fun withAndroidProject(androidProject: AndroidProjectStubBuilder.() -> IdeAndroidProjectImpl) =
    copy(androidProject = androidProject)

  fun withVariants(variants: AndroidProjectStubBuilder.() -> List<IdeVariantImpl>) =
    copy(variants = variants)

  fun withNdkModel(ndkModel: AndroidProjectStubBuilder.() -> V2NdkModel?) =
    copy(ndkModel = ndkModel)


  fun build(): AndroidProjectBuilderCore = fun(projectName: String, basePath: File, agpVersion: String) : AndroidProjectModels {
    val builder = object : AndroidProjectStubBuilder {
      override val agpVersion: String = agpVersion
      override val buildId: String = buildId()
      override val projectName: String = projectName
      override val basePath: File = basePath
      override val buildPath: File get() = basePath.resolve("build")
      override val projectType: IdeAndroidProjectType get() = projectType()
      override val minSdk: Int get() = minSdk()
      override val targetSdk: Int get() = targetSdk()
      override val mlModelBindingEnabled: Boolean get() = mlModelBindingEnabled()
      override val agpProjectFlags: IdeAndroidGradlePluginProjectFlagsImpl get() = agpProjectFlags()
      override val mainSourceProvider: IdeSourceProviderImpl get() = mainSourceProvider()
      override val androidTestSourceProviderContainer: IdeSourceProviderContainerImpl? get() = androidTestSourceProvider()
      override val unitTestSourceProviderContainer: IdeSourceProviderContainerImpl? get() = unitTestSourceProvider()
      override val debugSourceProvider: IdeSourceProviderImpl? get() = debugSourceProvider()
      override val releaseSourceProvider: IdeSourceProviderImpl? get() = releaseSourceProvider()
      override val defaultConfig: IdeProductFlavorContainerImpl = defaultConfig()
      override val debugBuildType: IdeBuildTypeContainerImpl? = debugBuildType()
      override val releaseBuildType: IdeBuildTypeContainerImpl? = releaseBuildType()
      override val dynamicFeatures: List<String> = dynamicFeatures()
      override val viewBindingOptions: IdeViewBindingOptionsImpl = viewBindingOptions()
      override val dependenciesInfo: IdeDependenciesInfoImpl = dependenciesInfo()
      override val supportsBundleTask: Boolean = supportsBundleTask()
      override fun androidModuleDependencies(variant: String): List<AndroidModuleDependency> = androidModuleDependencyList(variant)
      override fun androidLibraryDependencies(variant: String): List<IdeAndroidLibraryImpl> = androidLibraryDependencyList(variant)
      override fun mainArtifact(variant: String): IdeAndroidArtifactImpl = mainArtifactStub(variant)
      override fun androidTestArtifact(variant: String): IdeAndroidArtifactImpl = androidTestArtifactStub(variant)
      override fun unitTestArtifact(variant: String): IdeJavaArtifactImpl = unitTestArtifactStub(variant)
      override val variants: List<IdeVariantImpl> = variants()
      override val androidProject: IdeAndroidProjectImpl = androidProject()
      override val ndkModel: V2NdkModel? = ndkModel()
    }
    return AndroidProjectModels(
      androidProject = builder.androidProject,
      variants = builder.variants,
      ndkModel = builder.ndkModel
    )
  }
}

@JvmOverloads
fun createAndroidProjectBuilderForDefaultTestProjectStructure(
  projectType: IdeAndroidProjectType = IdeAndroidProjectType.PROJECT_TYPE_APP
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

fun AndroidProjectStubBuilder.createMainSourceProviderForDefaultTestProjectStructure(): IdeSourceProviderImpl {
  return IdeSourceProviderImpl(
    myName = ARTIFACT_NAME_MAIN,
    myFolder = basePath,
    myManifestFile = "AndroidManifest.xml",
    myJavaDirectories = listOf("src"),
    myKotlinDirectories = listOf("srcKotlin"),
    myResourcesDirectories = emptyList(),
    myAidlDirectories = emptyList(),
    myRenderscriptDirectories = emptyList(),
    myResDirectories = listOf("res"),
    myAssetsDirectories = emptyList(),
    myJniLibsDirectories = emptyList(),
    myMlModelsDirectories = emptyList(),
    myShadersDirectories = emptyList()
  )
}

fun AndroidProjectStubBuilder.buildMainSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider(ARTIFACT_NAME_MAIN, basePath.resolve("src/main"))

fun AndroidProjectStubBuilder.buildAndroidTestSourceProviderContainerStub(): IdeSourceProviderContainerImpl =
  IdeSourceProviderContainerImpl(
    artifactName = ARTIFACT_NAME_ANDROID_TEST,
    sourceProvider = sourceProvider(ARTIFACT_NAME_ANDROID_TEST, basePath.resolve("src/androidTest")))

fun AndroidProjectStubBuilder.buildUnitTestSourceProviderContainerStub(): IdeSourceProviderContainerImpl =
  IdeSourceProviderContainerImpl(
    artifactName = ARTIFACT_NAME_UNIT_TEST,
    sourceProvider = sourceProvider(ARTIFACT_NAME_UNIT_TEST, basePath.resolve("src/test")))

fun AndroidProjectStubBuilder.buildDebugSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider("debug", basePath.resolve("src/debug"))

fun AndroidProjectStubBuilder.buildReleaseSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider("release", basePath.resolve("src/release"))

private fun sourceProvider(name: String, rootDir: File): IdeSourceProviderImpl = IdeSourceProviderImpl(
  myName = name,
  myFolder = rootDir,
  myManifestFile = "AndroidManifest.xml",
  myJavaDirectories = listOf("java"),
  myKotlinDirectories = listOf("kotlin"),
  myResourcesDirectories = listOf("resources"),
  myAidlDirectories = listOf("aidl"),
  myRenderscriptDirectories = listOf("renderscript"),
  myResDirectories = listOf("res"),
  myAssetsDirectories = listOf("assets"),
  myJniLibsDirectories = listOf("jniLibs"),
  myMlModelsDirectories = listOf("ml"),
  myShadersDirectories = listOf("shaders")
)

fun AndroidProjectStubBuilder.buildAgpProjectFlagsStub(): IdeAndroidGradlePluginProjectFlagsImpl =
  IdeAndroidGradlePluginProjectFlagsImpl(
    applicationRClassConstantIds = true,
    testRClassConstantIds = true,
    transitiveRClasses = true,
    usesCompose = false,
    mlModelBindingEnabled = mlModelBindingEnabled
  )

fun AndroidProjectStubBuilder.buildDefaultConfigStub() = IdeProductFlavorContainerImpl(
  productFlavor = IdeProductFlavorImpl(
    testInstrumentationRunnerArguments = mapOf(),
    resourceConfigurations = listOf(),
    vectorDrawables = IdeVectorDrawablesOptionsImpl(useSupportLibrary = true),
    dimension = null,
    applicationId = null,
    versionCode = 12,
    versionName = "2.0",
    minSdkVersion = IdeApiVersionImpl(minSdk, null, "$minSdk"),
    targetSdkVersion = IdeApiVersionImpl(targetSdk, null, "$targetSdk"),
    maxSdkVersion = null,
    testApplicationId = null,
    testInstrumentationRunner = "android.test.InstrumentationTestRunner",
    testHandleProfiling = null,
    testFunctionalTest = null,
    applicationIdSuffix = null,
    consumerProguardFiles = emptyList(),
    manifestPlaceholders = emptyMap(),
    multiDexEnabled = null,
    name = "default",
    proguardFiles = emptyList(),
    resValues = emptyMap(),
    versionNameSuffix = null
  ),
  sourceProvider = mainSourceProvider,
  extraSourceProviders = listOfNotNull(androidTestSourceProviderContainer, unitTestSourceProviderContainer)
)

fun AndroidProjectStubBuilder.buildDebugBuildTypeStub(): IdeBuildTypeContainerImpl? =
  debugSourceProvider?.let { debugSourceProvider ->
    IdeBuildTypeContainerImpl(
      IdeBuildTypeImpl(
        name = debugSourceProvider.name,
        resValues = mapOf(),
        proguardFiles = listOf(),
        consumerProguardFiles = listOf(),
        manifestPlaceholders = mapOf(),
        applicationIdSuffix = null,
        versionNameSuffix = null,
        multiDexEnabled = null,
        isDebuggable = true,
        isJniDebuggable = true,
        isRenderscriptDebuggable = true,
        renderscriptOptimLevel = 1,
        isMinifyEnabled = false,
        isZipAlignEnabled = true
      ),
      debugSourceProvider,
      listOf()
    )
  }

fun AndroidProjectStubBuilder.buildReleaseBuildTypeStub(): IdeBuildTypeContainerImpl? =
  releaseSourceProvider?.let { releaseSourceProvider ->
    IdeBuildTypeContainerImpl(
      buildType = IdeBuildTypeImpl(
        name = releaseSourceProvider.name,
        resValues = mapOf(),
        proguardFiles = listOf(),
        consumerProguardFiles = listOf(),
        manifestPlaceholders = mapOf(),
        applicationIdSuffix = null,
        versionNameSuffix = null,
        multiDexEnabled = null,
        isDebuggable = false,
        isJniDebuggable = false,
        isRenderscriptDebuggable = false,
        renderscriptOptimLevel = 1,
        isMinifyEnabled = true,
        isZipAlignEnabled = true
      ),
      sourceProvider = releaseSourceProvider,
      extraSourceProviders = listOf())
  }

fun AndroidProjectStubBuilder.buildViewBindingOptions(): IdeViewBindingOptionsImpl = IdeViewBindingOptionsImpl(enabled = false)
fun AndroidProjectStubBuilder.buildDependenciesInfo(): IdeDependenciesInfoImpl =
  IdeDependenciesInfoImpl(includeInApk = true, includeInBundle = true)

fun AndroidProjectStubBuilder.buildMainArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf()
): IdeAndroidArtifactImpl {
  val androidModuleDependencies = this.androidModuleDependencies(variant).orEmpty()
  val androidLibraryDependencies = this.androidLibraryDependencies(variant).orEmpty()
  val dependenciesStub = buildDependenciesStub(
    libraries = androidLibraryDependencies,
    projects = androidModuleDependencies.map {
      IdeModuleLibraryImpl(
        projectPath = it.moduleGradlePath,
        buildId = this.buildId,
        variant = it.variant
      )
    }
  )
  val assembleTaskName = "assemble".appendCapitalized(variant)
  return IdeAndroidArtifactImpl(
    name = IdeArtifactName.MAIN,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("sources"),
    assembleTaskName = assembleTaskName,
    classesFolder = buildPath.resolve("intermediates/javac/$variant/classes"),
    additionalClassesFolders = classFolders,
    javaResourcesFolder = buildPath.resolve("intermediates/java_res/$variant/out"),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = setOf("ideSetupTask1", "ideSetupTask2"),
    mutableGeneratedSourceFolders = mutableListOf(),
    isTestArtifact = false,
    level2Dependencies = dependenciesStub,
    applicationId = "applicationId",
    signingConfigName = "defaultConfig",
    outputs = listOf<IdeAndroidArtifactOutputImpl>(),
    isSigned = false,
    generatedResourceFolders = listOf(),
    additionalRuntimeApks = listOf(),
    testOptions = null,
    abiFilters = setOf(),
    buildInformation = IdeBuildTasksAndOutputInformationImpl(
      assembleTaskName = assembleTaskName,
      assembleTaskOutputListingFile = buildPath.resolve("output/apk/$variant/output.json").path,
      bundleTaskName = "bundle".takeIf { supportsBundleTask && projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }?.appendCapitalized(variant),
      bundleTaskOutputListingFile = buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json").path,
      apkFromBundleTaskName = "extractApksFor".takeIf { projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }?.appendCapitalized(variant),
      apkFromBundleTaskOutputListingFile = buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json").path
    ),
    codeShrinker = null,
  )
}

fun AndroidProjectStubBuilder.buildAndroidTestArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf()
): IdeAndroidArtifactImpl {
  val dependenciesStub = buildDependenciesStub()
  val assembleTaskName = "assemble".appendCapitalized(variant).appendCapitalized("androidTest")
  return IdeAndroidArtifactImpl(
    name = IdeArtifactName.ANDROID_TEST,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("androidTestSources"),
    assembleTaskName = assembleTaskName,
    classesFolder = buildPath.resolve("intermediates/javac/${variant}AndroidTest/classes"),
    additionalClassesFolders = classFolders,
    javaResourcesFolder = buildPath.resolve("intermediates/java_res/${variant}AndroidTest/out"),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = setOf("ideAndroidTestSetupTask1", "ideAndroidTestSetupTask2"),
    mutableGeneratedSourceFolders = mutableListOf(),
    isTestArtifact = false,
    level2Dependencies = dependenciesStub,
    applicationId = "applicationId",
    signingConfigName = "defaultConfig",
    outputs = listOf<IdeAndroidArtifactOutputImpl>(),
    isSigned = false,
    generatedResourceFolders = listOf(),
    additionalRuntimeApks = listOf(),
    testOptions = null,
    abiFilters = setOf(),
    buildInformation = IdeBuildTasksAndOutputInformationImpl(
      assembleTaskName = assembleTaskName,
      assembleTaskOutputListingFile = buildPath.resolve("output/apk/$variant/output.json").path,
      bundleTaskName = "bundle".takeIf { supportsBundleTask && projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }?.appendCapitalized(variant)?.appendCapitalized("androidTest"),
      bundleTaskOutputListingFile = buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json").path,
      apkFromBundleTaskName = "extractApksFor".takeIf { projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }?.appendCapitalized(variant)?.appendCapitalized("androidTest"),
      apkFromBundleTaskOutputListingFile = buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json").path
    ),
    codeShrinker = null,
  )
}

fun AndroidProjectStubBuilder.buildUnitTestArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf(),
  dependencies: IdeDependenciesImpl = buildDependenciesStub(),
  mockablePlatformJar: File? = null
): IdeJavaArtifactImpl {
  return IdeJavaArtifactImpl(
    name = IdeArtifactName.UNIT_TEST,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("unitTestSources"),
    assembleTaskName = "assemble".appendCapitalized(variant).appendCapitalized("unitTest"),
    classesFolder = buildPath.resolve("intermediates/javac/${variant}UnitTest/classes"),
    additionalClassesFolders = classFolders,
    javaResourcesFolder = buildPath.resolve("intermediates/java_res/${variant}UnitTest/out"),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = setOf("ideUnitTestSetupTask1", "ideUnitTestSetupTask2"),
    mutableGeneratedSourceFolders = mutableListOf(),
    isTestArtifact = true,
    level2Dependencies = dependencies,
    mockablePlatformJar = mockablePlatformJar
  )
}

fun AndroidProjectStubBuilder.buildVariantStubs(): List<IdeVariantImpl> {
  return listOfNotNull(debugBuildType, releaseBuildType)
    .map {
      val buildType = it.buildType
      val variant = buildType.name
      IdeVariantImpl(
        variant,
        variant,
        mainArtifact(variant),
        unitTestArtifact(variant),
        androidTestArtifact(variant),
        variant,
        listOf(),
        minSdkVersion = defaultConfig.productFlavor.minSdkVersion,
        targetSdkVersion = defaultConfig.productFlavor.targetSdkVersion,
        maxSdkVersion = defaultConfig.productFlavor.maxSdkVersion,
        versionCode = defaultConfig.productFlavor.versionCode,
        instantAppCompatible = false,
        versionNameSuffix = buildType.versionNameSuffix,
        versionNameWithSuffix = defaultConfig.productFlavor.versionName + defaultConfig.productFlavor.versionNameSuffix.orEmpty() + buildType.versionNameSuffix.orEmpty(),
        vectorDrawablesUseSupportLibrary = defaultConfig.productFlavor.vectorDrawables?.useSupportLibrary ?: false,
        resourceConfigurations = defaultConfig.productFlavor.resourceConfigurations,
        resValues = defaultConfig.productFlavor.resValues,
        proguardFiles = defaultConfig.productFlavor.proguardFiles + buildType.proguardFiles,
        consumerProguardFiles = defaultConfig.productFlavor.consumerProguardFiles + buildType.consumerProguardFiles,
        manifestPlaceholders = defaultConfig.productFlavor.manifestPlaceholders + buildType.manifestPlaceholders,
        testApplicationId = defaultConfig.productFlavor.testApplicationId,
        testInstrumentationRunner = defaultConfig.productFlavor.testInstrumentationRunner,
        testInstrumentationRunnerArguments = defaultConfig.productFlavor.testInstrumentationRunnerArguments,
        testedTargetVariants = listOf(),
        deprecatedPreMergedApplicationId = defaultConfig.productFlavor.applicationId + defaultConfig.productFlavor.applicationIdSuffix.orEmpty() + buildType.applicationIdSuffix.orEmpty(),
      )
    }
}

fun AndroidProjectStubBuilder.buildAndroidProjectStub(): IdeAndroidProjectImpl {
  val debugBuildType = this.debugBuildType
  val releaseBuildType = this.releaseBuildType
  val defaultVariant = debugBuildType ?: releaseBuildType
  val defaultVariantName = defaultVariant?.sourceProvider?.name ?: "main"
  val buildTypes = listOfNotNull(debugBuildType, releaseBuildType)
  return IdeAndroidProjectImpl(
    modelVersion = agpVersion,
    name = projectName,
    projectType = projectType,
    defaultConfig = defaultConfig,
    buildTypes = buildTypes,
    productFlavors = listOf(),
    variantNames = this.variants.map { it.name },
    flavorDimensions = listOf(),
    compileTarget = getLatestAndroidPlatform(),
    bootClasspath = listOf(),
    signingConfigs = listOf(),
    aaptOptions = IdeAaptOptionsImpl(IdeAaptOptions.Namespacing.DISABLED),
    lintOptions = IdeLintOptionsImpl(),
    javaCompileOptions = IdeJavaCompileOptionsImpl(
      encoding = "encoding",
      sourceCompatibility = "sourceCompatibility",
      targetCompatibility = "targetCompatibility",
      isCoreLibraryDesugaringEnabled = false
    ),
    buildFolder = buildPath,
    resourcePrefix = null,
    buildToolsVersion = "buildToolsVersion",
    ndkVersion = "ndkVersion",
    isBaseSplit = true,
    dynamicFeatures = dynamicFeatures,
    viewBindingOptions = viewBindingOptions,
    dependenciesInfo = dependenciesInfo,
    groupId = null,
    agpFlags = agpProjectFlags,
    variantsBuildInformation = variants.map {
      IdeVariantBuildInformationImpl(variantName = it.name, buildInformation = it.mainArtifact.buildInformation)
    },
    lintRuleJars = listOf()
  )
}

fun AndroidProjectStubBuilder.buildNdkModelStub(): V2NdkModel {
  return V2NdkModel(
    agpVersion = agpVersion,
    nativeModule = IdeNativeModuleImpl(
      name = projectName,
      variants = variants
        .map { variant ->
          IdeNativeVariantImpl(
            variant.name,
            listOf(Abi.X86_64, Abi.ARM64_V8A).map {abi ->
              val sourceFlagsFile = basePath.resolve("some-build-dir/${variant.name}/${abi.name}/compile_commands.json.bin")
              FileUtil.ensureExists(sourceFlagsFile.parentFile)
              CompileCommandsEncoder(sourceFlagsFile).use {}
              IdeNativeAbiImpl(
                abi.toString(),
                sourceFlagsFile = sourceFlagsFile,
                symbolFolderIndexFile = basePath.resolve("some-build-dir/${variant.name}/${abi.name}/symbol_folder_index.txt"),
                buildFileIndexFile = basePath.resolve("some-build-dir/${variant.name}/${abi.name}/build_file_index.txt"),
                additionalProjectFilesIndexFile = basePath.resolve("some-build-dir/${variant.name}/${abi.name}/additional_project_files.txt")
              )
            }
          )
        },
      nativeBuildSystem = NativeBuildSystem.CMAKE,
      ndkVersion = "21.4.7075529",
      defaultNdkVersion = "21.4.7075529",
      externalNativeBuildFile = basePath.resolve("CMakeLists.txt")
    )
  )
}

fun AndroidProjectStubBuilder.buildDependenciesStub(
  libraries: List<IdeAndroidLibraryImpl> = listOf(),
  javaLibraries: List<IdeJavaLibraryImpl> = listOf(),
  projects: List<IdeModuleLibraryImpl> = listOf(),
  runtimeOnlyClasses: List<File> = listOf()
): IdeDependenciesImpl = IdeDependenciesImpl(libraries, javaLibraries, projects, runtimeOnlyClasses)

/**
 * Sets up [project] as a one module project configured in the same way sync would conigure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  basePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) = setupTestProjectFromAndroidModel(project, basePath, setupAllVariants = false, moduleBuilders = moduleBuilders)

/**
 * Sets up [project] as a one module project configured in the same way sync would configure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  basePath: File,
  setupAllVariants: Boolean = false,
  vararg moduleBuilders: ModuleModelBuilder
) {
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
  setupTestProjectFromAndroidModelCore(project, basePath, moduleBuilders, setupAllVariants, cacheExistingVariants = false)
}

/**
 * Sets up [project] as a one module project configured in the same way sync would configure it from the same model.
 */
fun updateTestProjectFromAndroidModel(
  project: Project,
  basePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) {
  setupTestProjectFromAndroidModelCore(project, basePath, moduleBuilders, setupAllVariants = false, cacheExistingVariants = false)
  getInstance(project).syncSkipped(null)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}

/**
 * Sets up [project] as a one module project configured in the same way sync would configure it from the same model.
 */
fun switchTestProjectVariantsFromAndroidModel(
  project: Project,
  basePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) {
  setupTestProjectFromAndroidModelCore(project, basePath, moduleBuilders, setupAllVariants = false, cacheExistingVariants = true)
  getInstance(project).syncSkipped(null)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}

private fun setupTestProjectFromAndroidModelCore(
  project: Project,
  basePath: File,
  moduleBuilders: Array<out ModuleModelBuilder>,
  setupAllVariants: Boolean,
  cacheExistingVariants: Boolean,
) {
  // Always skip SYNC in light sync tests.
  project.putUserData(ALWAYS_SKIP_SYNC, true)
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

  if (cacheExistingVariants) {
    AndroidGradleProjectResolver.saveCurrentlySyncedVariantsForReuse(project)
    AndroidGradleProjectResolver.attachVariantsSavedFromPreviousSyncs(project, projectDataNode)
    AndroidGradleProjectResolver.clearVariantsSavedForReuse(project)
  }

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
        val (androidProject, variants, ndkModel) = moduleBuilder.projectBuilder(
          moduleName,
          moduleBasePath,
          moduleBuilder.agpVersion ?: LatestKnownPluginVersionProvider.INSTANCE.get()
        )
        createAndroidModuleDataNode(
          moduleName,
          gradlePath,
          moduleBasePath,
          moduleBuilder.gradleVersion,
          moduleBuilder.agpVersion,
          gradlePlugins,
          androidProject,
          variants.let { if (!setupAllVariants) it.filter { it.name == moduleBuilder.selectedBuildVariant } else it },
          ndkModel,
          moduleBuilder.selectedBuildVariant,
          moduleBuilder.selectedAbiVariant
        ).also { androidModelDataNode ->
          val model = ExternalSystemApiUtil.find(androidModelDataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
          if (model != null) {
            androidModels.add(model)
          }
        }
      }
      is JavaModuleModelBuilder ->
        createJavaModuleDataNode(
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
  moduleName: String,
  gradlePath: String,
  moduleBasePath: File,
  gradleVersion: String?,
  agpVersion: String?,
  gradlePlugins: List<String>,
  androidProject: IdeAndroidProjectImpl,
  variants: Collection<IdeVariantImpl>,
  ndkModel: V2NdkModel?,
  selectedVariantName: String,
  selectedAbiName: String?
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
        moduleBasePath.resolve("build.gradle"),
        gradleVersion,
        agpVersion,
        false
      ),
      null
    )
  )

  moduleDataNode.addChild(
    DataNode<AndroidModuleModel>(
      AndroidProjectKeys.ANDROID_MODEL,
      AndroidModuleModel.create(
        moduleName,
        moduleBasePath,
        androidProject,
        variants,
        selectedVariantName
      ),
      null
    )
  )

  if (ndkModel != null) {
    val selectedAbiName = selectedAbiName
                          ?: ndkModel.abiByVariantAbi.keys.firstOrNull { it.variant == selectedVariantName }?.abi
                          ?: error(
                            "Cannot determine the selected ABI for module '$moduleName' with the selected variant '$selectedVariantName'")
    moduleDataNode.addChild(
      DataNode<NdkModuleModel>(
        AndroidProjectKeys.NDK_MODEL,
        NdkModuleModel(
          moduleName,
          moduleBasePath,
          selectedVariantName,
          selectedAbiName,
          ndkModel
        ),
        null
      )
    )
  }

  return moduleDataNode
}

private fun createJavaModuleDataNode(
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
  @JvmDefault
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
  action: (Project) -> T
): T {

  fun body(): T {
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

  var result: Result<T> = Result.failure(IllegalStateException())
  doNotEnableExternalStorageByDefaultInTests {
    result = Result.success(body())
  }
  return result.getOrThrow()
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

fun switchAbi(project: Project, moduleGradlePath: String, abi: String) {
  BuildVariantUpdater.getInstance(project).updateSelectedAbi(project, project.gradleModule(moduleGradlePath)!!.name, abi)
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

fun Project.requestSyncAndWait() {
  AndroidGradleTests.syncProject(this, GradleSyncInvoker.Request.testRequest())
}

/**
 * Set up data nodes that are normally created by the project resolver when processing [AndroidModuleModel]s.
 */
private fun setupDataNodesForSelectedVariant(
  project: Project,
  androidModuleModels: List<AndroidModuleModel>,
  projectDataNode: DataNode<ProjectData>
) {
  val moduleNodes = ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)
  val moduleIdToDataMap = createModuleIdToModuleDataMap(moduleNodes)
  androidModuleModels.forEach { androidModuleModel ->
    val newVariant = androidModuleModel.selectedVariant

    val moduleNode = moduleNodes.firstOrNull { node ->
      node.data.internalName == androidModuleModel.moduleName
    } ?: return@forEach

    // Now we need to recreate these nodes using the information from the new variant.
    moduleNode.setupCompilerOutputPaths(newVariant)
    // Then patch in any Kapt generated sources that we need
    val libraryFilePaths = LibraryFilePaths.getInstance(project)
    moduleNode.setupAndroidDependenciesForModule({ id: String -> moduleIdToDataMap[id] }, { id, path ->
      AdditionalArtifactsPaths(
        libraryFilePaths.findSourceJarPath(id, path),
        libraryFilePaths.findJavadocJarPath(id, path),
        libraryFilePaths.findSampleSourcesJarPath(id, path)
      )
    }, newVariant)
    moduleNode.setupAndroidContentEntries(newVariant)
  }
}

private fun createModuleIdToModuleDataMap(moduleNodes: Collection<DataNode<ModuleData>>): Map<String, ModuleData> {
  return moduleNodes.map { moduleDataNode -> moduleDataNode.data }.associateBy { moduleData ->
    moduleData.id
  }
}

