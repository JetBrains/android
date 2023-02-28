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
import com.android.projectmodel.ARTIFACT_NAME_TEST_FIXTURES
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.testutils.TestUtils.getSdk
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTasksAndOutputInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeCompositeBuildMapImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeDependencyCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeProjectPathImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.project.importing.withAfterCreate
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.GradleAndroidModelDataImpl
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.NdkModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.V1NdkModel
import com.android.tools.idea.gradle.project.model.V2NdkModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.InternedModels
import com.android.tools.idea.gradle.project.sync.idea.AdditionalArtifactsPaths
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.ALWAYS_SKIP_SYNC
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.SKIPPED_SYNC
import com.android.tools.idea.gradle.project.sync.idea.IdeaSyncPopulateProjectTask
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.getIdeModuleSourceSet
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.idea.setupAndroidContentEntriesPerSourceSet
import com.android.tools.idea.gradle.project.sync.idea.setupAndroidDependenciesForMpss
import com.android.tools.idea.gradle.project.sync.idea.setupCompilerOutputPaths
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues.Companion.syncIssues
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.gradle.util.emulateStartupActivityForTest
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.TestProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.buildNamePrefixedGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getBuildAndRelativeGradleProjectPath
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import com.android.utils.cxx.CompileCommandsEncoder
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.internal.DummySyncViewManager
import com.intellij.externalSystem.JavaProjectData
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.model.project.ModuleSdkData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes.JAVA
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.toCanonicalPath
import com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.ensureIndexesUpToDate
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableConsumer
import com.intellij.util.containers.MultiMap
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptDependenciesLibraryRootProvider
import org.jetbrains.kotlin.idea.gradleJava.configuration.CompilerArgumentsCacheMergeManager
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheHolder
import org.jetbrains.plugins.gradle.model.DefaultGradleExtension
import org.jetbrains.plugins.gradle.model.DefaultGradleExtensions
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalTask
import org.jetbrains.plugins.gradle.model.GradleExtensions
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.service.project.data.GradleExtensionsDataService
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import org.jetbrains.plugins.gradle.util.setBuildSrcModule
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

data class AndroidProjectModels(
  val androidProject: IdeAndroidProjectImpl,
  val variants: Collection<IdeVariantCoreImpl>,
  val ndkModel: NdkModel?
)

typealias AndroidProjectBuilderCore = (
  projectName: String,
  gradlePath: String,
  rootProjectBasePath: File,
  moduleBasePath: File,
  agpVersion: String,
  internedModels: InternedModels
) -> AndroidProjectModels

sealed class ModuleModelBuilder {
  abstract val gradlePath: String
  abstract val groupId: String?
  abstract val version: String?
  abstract val gradleVersion: String?
  abstract val agpVersion: String?
}

data class AndroidModuleModelBuilder(
  override val gradlePath: String,
  override val groupId: String? = null,
  override val version: String? = null,
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
    : this(gradlePath, groupId = null, version = null, gradleVersion, agpVersion, projectBuilder.build(), selectedBuildVariant,
           selectedAbiVariant = null)

  fun withSelectedAbi(abi: String) = copy(selectedAbiVariant = abi)
  fun withSelectedBuildVariant(variant: String) = copy(selectedBuildVariant = variant)
}

data class JavaModuleModelBuilder(
  override val gradlePath: String,
  override val groupId: String? = null,
  override val version: String? = null,
  override val gradleVersion: String? = null,
  val buildable: Boolean = true,
  val isBuildSrc: Boolean = false
) : ModuleModelBuilder() {

  init {
    assert(!isBuildSrc || gradlePath.dropWhile { c -> c != ':' }.startsWith(":buildSrc")) {
      "buildSrc modules must have a Gradle path starting with \":buildSrc\""
    }
  }

  constructor (gradlePath: String, buildable: Boolean = true) : this(gradlePath, groupId = null, version = null, null, buildable)

  override val agpVersion: String? = null

  companion object {
    @JvmStatic
    val rootModuleBuilder = JavaModuleModelBuilder(":", buildable = false)
  }
}

data class AndroidModuleDependency(val moduleGradlePath: String, val variant: String?)
data class AndroidLibraryDependency(val library: IdeAndroidLibraryImpl)

/**
 * An interface providing access to [AndroidProject] sub-model builders are used to build [AndroidProject] and its other sub-models.
 */
interface AndroidProjectStubBuilder {
  val agpVersion: String
  val buildId: String
  val projectName: String
  val gradleProjectPath: String
  val rootProjectBasePath: File
  val moduleBasePath: File
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
  val androidTestDebugSourceProvider: IdeSourceProviderImpl?
  val testDebugSourceProvider: IdeSourceProviderImpl?
  val releaseSourceProvider: IdeSourceProviderImpl?
  val defaultConfig: IdeProductFlavorContainerImpl
  val debugBuildType: IdeBuildTypeContainerImpl?
  val releaseBuildType: IdeBuildTypeContainerImpl?
  val flavorDimensions: List<String>?
  val dynamicFeatures: List<String>
  val viewBindingOptions: IdeViewBindingOptionsImpl
  val dependenciesInfo: IdeDependenciesInfoImpl
  val supportsBundleTask: Boolean
  fun productFlavors(dimension: String): List<IdeProductFlavorImpl>
  fun productFlavorSourceProvider(flavor: String): IdeSourceProviderImpl
  fun productFlavorContainers(dimension: String): List<IdeProductFlavorContainerImpl>

  fun androidModuleDependencies(variant: String): List<AndroidModuleDependency>?
  fun androidLibraryDependencies(variant: String): List<AndroidLibraryDependency>?
  fun mainArtifact(variant: String): IdeAndroidArtifactCoreImpl
  fun androidTestArtifact(variant: String, applicationId: String?): IdeAndroidArtifactCoreImpl?
  fun unitTestArtifact(variant: String): IdeJavaArtifactCoreImpl?
  fun testFixturesArtifact(variant: String): IdeAndroidArtifactCoreImpl?
  val androidProject: IdeAndroidProjectImpl
  val variants: List<IdeVariantCoreImpl>
  val ndkModel: NdkModel?
  val internedModels: InternedModels
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
  val buildId: AndroidProjectStubBuilder.() -> String = {
    toSystemIndependentName(rootProjectBasePath.path)
  }, //  buildId should not be assumed to be a path.
  val projectType: AndroidProjectStubBuilder.() -> IdeAndroidProjectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
  val minSdk: AndroidProjectStubBuilder.() -> Int = { 16 },
  val targetSdk: AndroidProjectStubBuilder.() -> Int = { 22 },
  val mlModelBindingEnabled: AndroidProjectStubBuilder.() -> Boolean = { false },
  val agpProjectFlags: AndroidProjectStubBuilder.() -> IdeAndroidGradlePluginProjectFlagsImpl = { buildAgpProjectFlagsStub() },
  val defaultConfig: AndroidProjectStubBuilder.() -> IdeProductFlavorContainerImpl = { buildDefaultConfigStub() },
  val mainSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl = { buildMainSourceProviderStub() },
  val androidTestSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl? =
    { buildAndroidTestSourceProviderContainerStub() },
  val unitTestSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl? =
    { buildUnitTestSourceProviderContainerStub() },
  val testFixturesSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderContainerImpl? =
    { buildTestFixturesSourceProviderContainerStub() },
  val debugSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl? = { buildDebugSourceProviderStub() },
  val androidTestDebugSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl? = { buildAndroidTestDebugSourceProviderStub() },
  val testDebugSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl? = { buildTestDebugSourceProviderStub() },
  val releaseSourceProvider: AndroidProjectStubBuilder.() -> IdeSourceProviderImpl? = { buildReleaseSourceProviderStub() },
  val debugBuildType: AndroidProjectStubBuilder.() -> IdeBuildTypeContainerImpl? = { buildDebugBuildTypeStub() },
  val releaseBuildType: AndroidProjectStubBuilder.() -> IdeBuildTypeContainerImpl? = { buildReleaseBuildTypeStub() },
  val flavorDimensions: AndroidProjectStubBuilder.() -> List<String>? = { null },
  val dynamicFeatures: AndroidProjectStubBuilder.() -> List<String> = { emptyList() },
  val viewBindingOptions: AndroidProjectStubBuilder.() -> IdeViewBindingOptionsImpl = { buildViewBindingOptions() },
  val dependenciesInfo: AndroidProjectStubBuilder.() -> IdeDependenciesInfoImpl = { buildDependenciesInfo() },
  val supportsBundleTask: AndroidProjectStubBuilder.() -> Boolean = { true },
  val productFlavorsStub: AndroidProjectStubBuilder.(dimension: String) -> List<IdeProductFlavorImpl> = { dimension -> emptyList() },
  val productFlavorSourceProviderStub: AndroidProjectStubBuilder.(flavor: String) -> IdeSourceProviderImpl =
    { flavor -> sourceProvider(flavor) },
  val productFlavorContainersStub: AndroidProjectStubBuilder.(dimension: String) -> List<IdeProductFlavorContainerImpl> =
    { dimension -> buildProductFlavorContainersStub(dimension) },
  val mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactCoreImpl =
    { variant -> buildMainArtifactStub(variant) },
  val androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String, applicationId: String?) -> IdeAndroidArtifactCoreImpl? =
    { variant, applicationId -> buildAndroidTestArtifactStub(variant, applicationId) },
  val unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeJavaArtifactCoreImpl? =
    { variant -> buildUnitTestArtifactStub(variant) },
  val testFixturesArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactCoreImpl? =
    { variant -> null },
  val androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency> = { emptyList() },
  val androidLibraryDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidLibraryDependency> =
    { emptyList() },
  val androidProject: AndroidProjectStubBuilder.() -> IdeAndroidProjectImpl = { buildAndroidProjectStub() },
  val variants: AndroidProjectStubBuilder.() -> List<IdeVariantCoreImpl> = { buildVariantStubs() },
  val ndkModel: AndroidProjectStubBuilder.() -> NdkModel? = { null }
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

  fun withFlavorDimensions(flavorDimensions: AndroidProjectStubBuilder.() -> List<String>?) =
    copy(flavorDimensions = flavorDimensions)

  fun withDynamicFeatures(dynamicFeatures: AndroidProjectStubBuilder.() -> List<String>) =
    copy(dynamicFeatures = dynamicFeatures)

  fun withViewBindingOptions(viewBindingOptions: AndroidProjectStubBuilder.() -> IdeViewBindingOptionsImpl) =
    copy(viewBindingOptions = viewBindingOptions)

  fun withSupportsBundleTask(supportsBundleTask: AndroidProjectStubBuilder.() -> Boolean) =
    copy(supportsBundleTask = supportsBundleTask)

  fun withProductFlavors(productFlavors: AndroidProjectStubBuilder.(dimension: String) -> List<IdeProductFlavorImpl>) =
    copy(productFlavorsStub = productFlavors)

  fun withProductFlavorSourceProvider(productFlavorSourceProvider: AndroidProjectStubBuilder.(flavor: String) -> IdeSourceProviderImpl) =
    copy(productFlavorSourceProviderStub = productFlavorSourceProvider)

  fun withProductFlavorContainers(productFlavorContainers: AndroidProjectStubBuilder.(dimension: String) -> List<IdeProductFlavorContainerImpl>) =
    copy(productFlavorContainersStub = productFlavorContainers)

  fun withMainArtifactStub(mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeAndroidArtifactCoreImpl) =
    copy(mainArtifactStub = mainArtifactStub)

  fun withAndroidTestArtifactStub(androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String, applicationId: String?) -> IdeAndroidArtifactCoreImpl) =
    copy(androidTestArtifactStub = androidTestArtifactStub)

  fun withUnitTestArtifactStub(unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> IdeJavaArtifactCoreImpl) =
    copy(unitTestArtifactStub = unitTestArtifactStub)

  fun withAndroidModuleDependencyList(androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency>) =
    copy(androidModuleDependencyList = androidModuleDependencyList)

  fun withAndroidLibraryDependencyList(
    androidLibraryDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidLibraryDependency>
  ) = copy(androidLibraryDependencyList = androidLibraryDependencyList)

  fun withAndroidProject(androidProject: AndroidProjectStubBuilder.() -> IdeAndroidProjectImpl) =
    copy(androidProject = androidProject)

  fun withVariants(variants: AndroidProjectStubBuilder.() -> List<IdeVariantCoreImpl>) =
    copy(variants = variants)

  fun withNdkModel(ndkModel: AndroidProjectStubBuilder.() -> V2NdkModel?) =
    copy(ndkModel = ndkModel)


  fun build(): AndroidProjectBuilderCore =
    fun(
      projectName: String,
      gradleProjectPath: String,
      rootProjectBasePath: File,
      moduleBasePath: File,
      agpVersion: String,
      internedModels: InternedModels
    ): AndroidProjectModels {
      val builder = object : AndroidProjectStubBuilder {
        override val agpVersion: String = agpVersion
        override val buildId: String get() = buildId()
        override val projectName: String = projectName
        override val gradleProjectPath: String = gradleProjectPath
        override val rootProjectBasePath: File = rootProjectBasePath
        override val moduleBasePath: File = moduleBasePath
        override val buildPath: File get() = moduleBasePath.resolve("build")
        override val projectType: IdeAndroidProjectType get() = projectType()
        override val minSdk: Int get() = minSdk()
        override val targetSdk: Int get() = targetSdk()
        override val mlModelBindingEnabled: Boolean get() = mlModelBindingEnabled()
        override val agpProjectFlags: IdeAndroidGradlePluginProjectFlagsImpl get() = agpProjectFlags()
        override val mainSourceProvider: IdeSourceProviderImpl get() = mainSourceProvider()
        override val androidTestSourceProviderContainer: IdeSourceProviderContainerImpl? get() = androidTestSourceProvider()
        override val unitTestSourceProviderContainer: IdeSourceProviderContainerImpl? get() = unitTestSourceProvider()
        override val debugSourceProvider: IdeSourceProviderImpl? get() = debugSourceProvider()
        override val androidTestDebugSourceProvider: IdeSourceProviderImpl? get() = androidTestDebugSourceProvider()
        override val testDebugSourceProvider: IdeSourceProviderImpl? get() = testDebugSourceProvider()
        override val releaseSourceProvider: IdeSourceProviderImpl? get() = releaseSourceProvider()
        override val defaultConfig: IdeProductFlavorContainerImpl = defaultConfig()
        override val debugBuildType: IdeBuildTypeContainerImpl? = debugBuildType()
        override val releaseBuildType: IdeBuildTypeContainerImpl? = releaseBuildType()
        override val flavorDimensions: List<String>? = flavorDimensions()
        override val dynamicFeatures: List<String> = dynamicFeatures()
        override val viewBindingOptions: IdeViewBindingOptionsImpl = viewBindingOptions()
        override val dependenciesInfo: IdeDependenciesInfoImpl = dependenciesInfo()
        override val supportsBundleTask: Boolean = supportsBundleTask()
        override fun productFlavors(dimension: String): List<IdeProductFlavorImpl> = productFlavorsStub(dimension)
        override fun productFlavorSourceProvider(flavor: String): IdeSourceProviderImpl = productFlavorSourceProviderStub(flavor)
        override fun productFlavorContainers(dimension: String): List<IdeProductFlavorContainerImpl> = productFlavorContainersStub(
          dimension)

        override fun androidModuleDependencies(variant: String): List<AndroidModuleDependency> = androidModuleDependencyList(variant)
        override fun androidLibraryDependencies(variant: String): List<AndroidLibraryDependency> =
          androidLibraryDependencyList(variant)

        override fun mainArtifact(variant: String): IdeAndroidArtifactCoreImpl = mainArtifactStub(variant)
        override fun androidTestArtifact(variant: String, applicationId: String?): IdeAndroidArtifactCoreImpl? = androidTestArtifactStub(
          variant, applicationId)

        override fun unitTestArtifact(variant: String): IdeJavaArtifactCoreImpl? = unitTestArtifactStub(variant)
        override fun testFixturesArtifact(variant: String): IdeAndroidArtifactCoreImpl? = testFixturesArtifactStub(variant)
        override val variants: List<IdeVariantCoreImpl> = variants()
        override val androidProject: IdeAndroidProjectImpl = androidProject()
        override val ndkModel: NdkModel? = ndkModel()
        override val internedModels: InternedModels get() = internedModels
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
    myFolder = moduleBasePath,
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
    myShadersDirectories = emptyList(),
    myCustomSourceDirectories = emptyList(),
  )
}

fun AndroidProjectStubBuilder.buildMainSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider(ARTIFACT_NAME_MAIN, moduleBasePath.resolve("src/main"))

fun AndroidProjectStubBuilder.buildAndroidTestSourceProviderContainerStub(): IdeSourceProviderContainerImpl =
  IdeSourceProviderContainerImpl(
    artifactName = ARTIFACT_NAME_ANDROID_TEST,
    sourceProvider = sourceProvider(ARTIFACT_NAME_ANDROID_TEST, moduleBasePath.resolve("src/androidTest")))

fun AndroidProjectStubBuilder.buildTestFixturesSourceProviderContainerStub(): IdeSourceProviderContainerImpl =
  IdeSourceProviderContainerImpl(
    artifactName = ARTIFACT_NAME_TEST_FIXTURES,
    sourceProvider = sourceProvider(ARTIFACT_NAME_TEST_FIXTURES, moduleBasePath.resolve("src/testFixtures")))

fun AndroidProjectStubBuilder.buildUnitTestSourceProviderContainerStub(): IdeSourceProviderContainerImpl =
  IdeSourceProviderContainerImpl(
    artifactName = ARTIFACT_NAME_UNIT_TEST,
    sourceProvider = sourceProvider(ARTIFACT_NAME_UNIT_TEST, moduleBasePath.resolve("src/test")))

fun AndroidProjectStubBuilder.buildDebugSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider("debug", moduleBasePath.resolve("src/debug"))

fun AndroidProjectStubBuilder.buildAndroidTestDebugSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider("androidTestDebug", moduleBasePath.resolve("src/androidTestDebug"))

fun AndroidProjectStubBuilder.buildTestDebugSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider("testDebug", moduleBasePath.resolve("src/testDebug"))

fun AndroidProjectStubBuilder.buildReleaseSourceProviderStub(): IdeSourceProviderImpl =
  sourceProvider("release", moduleBasePath.resolve("src/release"))

fun AndroidProjectStubBuilder.sourceProvider(name: String): IdeSourceProviderImpl =
  sourceProvider(name, moduleBasePath.resolve("src/$name"))

private fun sourceProvider(name: String, rootDir: File): IdeSourceProviderImpl {
  return IdeSourceProviderImpl(
    myName = name,
    myFolder = rootDir,
    myManifestFile = "AndroidManifest.xml",
    myJavaDirectories = listOf("java"),
    myKotlinDirectories = listOf("kotlin"),
    myResourcesDirectories = listOf("resources"),
    myAidlDirectories = listOf("aidl"),
    myRenderscriptDirectories = listOf("rs"),
    myResDirectories = listOf("res"),
    myAssetsDirectories = listOf("assets"),
    myJniLibsDirectories = listOf("jniLibs"),
    myMlModelsDirectories = listOf(),
    myShadersDirectories = listOf("shaders"),
    myCustomSourceDirectories = listOf(/*IdeCustomSourceDirectoryImpl("custom", rootDir, "custom")*/),
  )
}

fun AndroidProjectStubBuilder.buildAgpProjectFlagsStub(): IdeAndroidGradlePluginProjectFlagsImpl =
  IdeAndroidGradlePluginProjectFlagsImpl(
    applicationRClassConstantIds = true,
    testRClassConstantIds = true,
    transitiveRClasses = true,
    usesCompose = false,
    mlModelBindingEnabled = mlModelBindingEnabled,
    unifiedTestPlatformEnabled = true,
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
      listOfNotNull(
        androidTestDebugSourceProvider?.let { IdeSourceProviderContainerImpl(ARTIFACT_NAME_ANDROID_TEST, it) },
        testDebugSourceProvider?.let { IdeSourceProviderContainerImpl(ARTIFACT_NAME_UNIT_TEST, it) }
      )
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

fun AndroidProjectStubBuilder.buildProductFlavorContainersStub(dimension: String): List<IdeProductFlavorContainerImpl> {
  return this
    .productFlavors(dimension)
    .map { flavor ->
      val sourceProvider = this.productFlavorSourceProvider(flavor.name)
      IdeProductFlavorContainerImpl(flavor, sourceProvider, extraSourceProviders = emptyList())
    }
}

fun AndroidProjectStubBuilder.buildMainArtifactStub(
  variant: String,
): IdeAndroidArtifactCoreImpl {
  val androidLibraryDependencies = this.androidLibraryDependencies(variant).orEmpty()
  val dependenciesStub = buildDependenciesStub(
    dependencies = androidLibraryDependencies.map {
      IdeDependencyCoreImpl(
        internedModels.getOrCreate(it.library)
      )
    } + toIdeModuleDependencies(androidModuleDependencies(variant).orEmpty())
  )
  val assembleTaskName = "assemble".appendCapitalized(variant)
  return IdeAndroidArtifactCoreImpl(
    name = IdeArtifactName.MAIN,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("sources"),
    assembleTaskName = assembleTaskName,
    classesFolder = listOf(buildPath.resolve("intermediates/javac/$variant/classes")),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = listOf("generate".appendCapitalized(variant).appendCapitalized("sources")),
    generatedSourceFolders = listOf(
      buildPath.resolve("generated/aidl_source_output_dir/${variant}/out"),
      buildPath.resolve("generated/ap_generated_sources/${variant}/out"),
      buildPath.resolve("generated/renderscript_source_output_dir/${variant}/out"),
      buildPath.resolve("generated/source/buildConfig/${variant}"),
    ),
    isTestArtifact = false,
    compileClasspathCore = dependenciesStub,
    runtimeClasspathCore = dependenciesStub,
    unresolvedDependencies = emptyList(),
    applicationId = "applicationId",
    signingConfigName = "defaultConfig",
    isSigned = false,
    generatedResourceFolders = listOf(
      buildPath.resolve("generated/res/rs/${variant}"),
      buildPath.resolve("generated/res/resValues/${variant}"),
    ),
    additionalRuntimeApks = listOf(),
    testOptions = null,
    abiFilters = setOf(),
    buildInformation = IdeBuildTasksAndOutputInformationImpl(
      assembleTaskName = assembleTaskName,
      assembleTaskOutputListingFile = buildPath.resolve("output/apk/$variant/output.json").path,
      bundleTaskName = "bundle".takeIf { supportsBundleTask && projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }?.appendCapitalized(
        variant),
      bundleTaskOutputListingFile = buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json").path,
      apkFromBundleTaskName = "extractApksFor".takeIf { projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }?.appendCapitalized(variant),
      apkFromBundleTaskOutputListingFile = buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json").path
    ),
    codeShrinker = null,
    modelSyncFiles = listOf(),
    privacySandboxSdkInfo = null,
  )
}

fun AndroidProjectStubBuilder.buildAndroidTestArtifactStub(
  variant: String,
  applicationId: String?,
): IdeAndroidArtifactCoreImpl {
  val dependenciesStub = buildDependenciesStub(
    dependencies = toIdeModuleDependencies(androidModuleDependencies(variant).orEmpty()) +
                   listOf(
                     IdeDependencyCoreImpl(
                       internedModels.getOrCreate(
                         IdeModuleLibraryImpl(
                           buildId = buildId,
                           projectPath = gradleProjectPath,
                           variant = variant,
                           lintJar = null,
                           sourceSet = IdeModuleWellKnownSourceSet.MAIN
                         )
                       )
                     )
                   )
  )
  val assembleTaskName = "assemble".appendCapitalized(variant).appendCapitalized("androidTest")
  return IdeAndroidArtifactCoreImpl(
    name = IdeArtifactName.ANDROID_TEST,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("androidTestSources"),
    assembleTaskName = assembleTaskName,
    classesFolder = listOf(buildPath.resolve("intermediates/javac/${variant}AndroidTest/classes")),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = listOf("ideAndroidTestSetupTask1", "ideAndroidTestSetupTask2"),
    generatedSourceFolders = listOf(
      buildPath.resolve("generated/aidl_source_output_dir/${variant}AndroidTest/out"),
      buildPath.resolve("generated/ap_generated_sources/${variant}AndroidTest/out"),
      buildPath.resolve("generated/renderscript_source_output_dir/${variant}AndroidTest/out"),
      buildPath.resolve("generated/source/buildConfig/androidTest/${variant}"),
    ),
    isTestArtifact = true,
    compileClasspathCore = dependenciesStub,
    runtimeClasspathCore = dependenciesStub,
    unresolvedDependencies = emptyList(),
    applicationId = applicationId,
    signingConfigName = "defaultConfig",
    isSigned = false,
    generatedResourceFolders = listOf(
      buildPath.resolve("generated/res/rs/androidTest/${variant}"),
      buildPath.resolve("generated/res/resValues/androidTest/${variant}"),
    ),
    additionalRuntimeApks = listOf(),
    testOptions = null,
    abiFilters = setOf(),
    buildInformation = IdeBuildTasksAndOutputInformationImpl(
      assembleTaskName = assembleTaskName,
      assembleTaskOutputListingFile = buildPath.resolve("output/apk/$variant/output.json").path,
      bundleTaskName = null,
      bundleTaskOutputListingFile = buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json").path,
      apkFromBundleTaskName = null,
      apkFromBundleTaskOutputListingFile = buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json").path
    ),
    codeShrinker = null,
    modelSyncFiles = listOf(),
    privacySandboxSdkInfo = null,
  )
}

fun AndroidProjectStubBuilder.buildUnitTestArtifactStub(
  variant: String,
  dependencies: IdeDependenciesCoreImpl = buildDependenciesStub(
    dependencies = toIdeModuleDependencies(androidModuleDependencies(variant).orEmpty()) +
                   listOf(
                     IdeDependencyCoreImpl(
                       internedModels.getOrCreate(
                         IdeModuleLibraryImpl(
                           buildId = buildId,
                           projectPath = gradleProjectPath,
                           variant = variant,
                           lintJar = null,
                           sourceSet = IdeModuleWellKnownSourceSet.MAIN
                         )
                       )
                     )
                   )
  ),
  mockablePlatformJar: File? = null
): IdeJavaArtifactCoreImpl {
  return IdeJavaArtifactCoreImpl(
    name = IdeArtifactName.UNIT_TEST,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("unitTestSources"),
    assembleTaskName = "assemble".appendCapitalized(variant).appendCapitalized("unitTest"),
    classesFolder = listOf(buildPath.resolve("intermediates/javac/${variant}UnitTest/classes")),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = listOf("ideUnitTestSetupTask1", "ideUnitTestSetupTask2"),
    generatedSourceFolders = listOf(
      buildPath.resolve("generated/ap_generated_sources/${variant}UnitTest/out"),
    ),
    isTestArtifact = true,
    compileClasspathCore = dependencies,
    runtimeClasspathCore = dependencies,
    unresolvedDependencies = emptyList(),
    mockablePlatformJar = mockablePlatformJar
  )
}

private fun AndroidProjectStubBuilder.toIdeModuleDependencies(androidModuleDependencies: List<AndroidModuleDependency>) =
  androidModuleDependencies.map {
    IdeDependencyCoreImpl(
      internedModels.getOrCreate(
        IdeModuleLibraryImpl(
          projectPath = it.moduleGradlePath,
          buildId = this.buildId,
          variant = it.variant,
          lintJar = null,
          sourceSet = IdeModuleWellKnownSourceSet.MAIN
        )
      )
    )
  }

fun AndroidProjectStubBuilder.buildTestFixturesArtifactStub(
  variant: String,
): IdeAndroidArtifactCoreImpl {
  val dependenciesStub = buildDependenciesStub(
    dependencies = listOf(
      IdeDependencyCoreImpl(
        internedModels.getOrCreate(
          IdeModuleLibraryImpl(
            buildId = buildId,
            projectPath = gradleProjectPath,
            variant = variant,
            lintJar = null,
            sourceSet = IdeModuleWellKnownSourceSet.MAIN
          )
        )
      )
    )
  )
  val assembleTaskName = "assemble".appendCapitalized(variant).appendCapitalized("testFixtures")
  return IdeAndroidArtifactCoreImpl(
    name = IdeArtifactName.TEST_FIXTURES,
    compileTaskName = "compile".appendCapitalized(variant).appendCapitalized("testFixturesSources"),
    assembleTaskName = assembleTaskName,
    classesFolder = listOf(buildPath.resolve("intermediates/javac/${variant}testFixtures/classes")),
    variantSourceProvider = null,
    multiFlavorSourceProvider = null,
    ideSetupTaskNames = listOf("ideTestFixturesSetupTask1", "ideTestFixturesSetupTask2"),
    generatedSourceFolders = emptyList(),
    isTestArtifact = false,
    compileClasspathCore = dependenciesStub,
    runtimeClasspathCore = dependenciesStub,
    unresolvedDependencies = emptyList(),
    applicationId = "applicationId",
    signingConfigName = "defaultConfig",
    isSigned = false,
    generatedResourceFolders = listOf(),
    additionalRuntimeApks = listOf(),
    testOptions = null,
    abiFilters = setOf(),
    buildInformation = IdeBuildTasksAndOutputInformationImpl(
      assembleTaskName = assembleTaskName,
      assembleTaskOutputListingFile = buildPath.resolve("output/apk/$variant/output.json").path,
      bundleTaskName = null,
      bundleTaskOutputListingFile = buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json").path,
      apkFromBundleTaskName = null,
      apkFromBundleTaskOutputListingFile = buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json").path
    ),
    codeShrinker = null,
    modelSyncFiles = listOf(),
    privacySandboxSdkInfo = null,
  )
}

fun AndroidProjectStubBuilder.buildVariantStubs(): List<IdeVariantCoreImpl> {
  val dimensions = this.flavorDimensions.orEmpty()
  fun combineVariants(dimensionIndex: Int = 0): List<List<IdeProductFlavorImpl>> {
    return when (dimensionIndex) {
      dimensions.size -> listOf(emptyList())
      else -> {
        val tails = combineVariants(dimensionIndex + 1)
        val thisDimension = this.productFlavors(dimensions[dimensionIndex])
        thisDimension.flatMap { flavor -> tails.map { tail -> listOf(flavor) + tail } }
      }
    }
  }

  val flavorSequences = combineVariants()
  return flavorSequences.flatMap { flavors ->
    listOfNotNull(debugBuildType, releaseBuildType)
      .map {
        val buildType = it.buildType
        val flavorNames = flavors.map { it.name }
        val variant = (flavorNames + buildType.name).combineAsCamelCase()
        val testApplicationId = flavors.firstNotNullOfOrNull { it.testApplicationId } ?: defaultConfig.productFlavor.testApplicationId
        IdeVariantCoreImpl(
          variant,
          variant,
          mainArtifact(variant),
          unitTestArtifact(variant),
          androidTestArtifact(variant, applicationId = testApplicationId),
          testFixturesArtifact(variant),
          buildType.name,
          flavorNames,
          minSdkVersion = flavors.firstNotNullOfOrNull { it.minSdkVersion }
                          ?: defaultConfig.productFlavor.minSdkVersion
                          ?: IdeApiVersionImpl(1, null, "1"),
          targetSdkVersion = flavors.firstNotNullOfOrNull { it.targetSdkVersion }
                             ?: defaultConfig.productFlavor.targetSdkVersion,
          maxSdkVersion = flavors.firstNotNullOfOrNull { it.maxSdkVersion }
                          ?: defaultConfig.productFlavor.maxSdkVersion,
          versionCode = flavors.firstNotNullOfOrNull { it.versionCode }
                        ?: defaultConfig.productFlavor.versionCode,
          versionNameWithSuffix = (flavors.firstNotNullOfOrNull { it.versionName } ?: defaultConfig.productFlavor.versionName) +
                                  defaultConfig.productFlavor.versionNameSuffix.orEmpty() + buildType.versionNameSuffix.orEmpty(),
          versionNameSuffix = buildType.versionNameSuffix,
          instantAppCompatible = false,
          vectorDrawablesUseSupportLibrary = flavors.firstNotNullOfOrNull { it.vectorDrawables?.useSupportLibrary }
                                             ?: defaultConfig.productFlavor.vectorDrawables?.useSupportLibrary ?: false,
          resourceConfigurations = (defaultConfig.productFlavor.resourceConfigurations + flavors.flatMap { it.resourceConfigurations })
            .distinct(),
          resValues = (defaultConfig.productFlavor.resValues.entries + flavors.flatMap { it.resValues.entries })
            .associate { it.key to it.value },
          proguardFiles = (defaultConfig.productFlavor.proguardFiles + flavors.flatMap { it.proguardFiles } + buildType.proguardFiles)
            .distinct(),
          consumerProguardFiles = (defaultConfig.productFlavor.consumerProguardFiles + flavors.flatMap { it.proguardFiles } + buildType.consumerProguardFiles)
            .distinct(),
          manifestPlaceholders = (defaultConfig.productFlavor.manifestPlaceholders.entries +
                                  flavors.flatMap { it.manifestPlaceholders.entries } +
                                  buildType.manifestPlaceholders.entries
                                 )
            .associate { it.key to it.value },
          deprecatedPreMergedTestApplicationId = testApplicationId,
          testInstrumentationRunner = flavors.firstNotNullOfOrNull { it.testInstrumentationRunner }
                                      ?: defaultConfig.productFlavor.testInstrumentationRunner,
          testInstrumentationRunnerArguments = (defaultConfig.productFlavor.testInstrumentationRunnerArguments.entries +
                                                flavors.flatMap { it.testInstrumentationRunnerArguments.entries }
                                               )
            .associate { it.key to it.value },
          testedTargetVariants = listOf(),
          deprecatedPreMergedApplicationId = (flavors.firstNotNullOfOrNull { it.applicationId }
                                              ?: defaultConfig.productFlavor.applicationId
                                             ) +
                                             defaultConfig.productFlavor.applicationIdSuffix.orEmpty() +
                                             buildType.applicationIdSuffix.orEmpty(),
          desugaredMethodsFiles = listOf(),
        )
      }
  }
}

fun AndroidProjectStubBuilder.buildAndroidProjectStub(): IdeAndroidProjectImpl {
  val debugBuildType = this.debugBuildType
  val releaseBuildType = this.releaseBuildType
  val defaultVariant = debugBuildType ?: releaseBuildType
  val defaultVariantName = defaultVariant?.sourceProvider?.name ?: "main"
  val buildTypes = listOfNotNull(debugBuildType, releaseBuildType)
  val projectType = projectType
  return IdeAndroidProjectImpl(
    agpVersion = agpVersion,
    projectPath = IdeProjectPathImpl(
      rootBuildId = File("/"),
      buildId = File("/"),
      buildName = ":",
      projectPath = gradleProjectPath
    ),
    projectType = projectType,
    defaultConfig = defaultConfig,
    buildTypes = buildTypes,
    productFlavors = this.flavorDimensions.orEmpty().flatMap { this.productFlavorContainers(it) },
    basicVariants = this.variants.map {
      IdeBasicVariantImpl(
        name = it.name,
        it.mainArtifact.applicationId,
        it.androidTestArtifact?.applicationId
      )
    },
    flavorDimensions = this.flavorDimensions.orEmpty(),
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
    isBaseSplit = true,
    dynamicFeatures = dynamicFeatures,
    baseFeature = null,
    viewBindingOptions = viewBindingOptions,
    dependenciesInfo = dependenciesInfo,
    groupId = null,
    namespace = null,
    testNamespace = null,
    agpFlags = agpProjectFlags,
    variantsBuildInformation = variants.map {
      IdeVariantBuildInformationImpl(variantName = it.name, buildInformation = it.mainArtifact.buildInformation)
    },
    lintChecksJars = listOf(),
    isKaptEnabled = false
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
            listOf(Abi.X86_64, Abi.ARM64_V8A).map { abi ->
              val sourceFlagsFile = moduleBasePath.resolve("some-build-dir/${variant.name}/${abi.name}/compile_commands.json.bin")
              FileUtil.ensureExists(sourceFlagsFile.parentFile)
              CompileCommandsEncoder(sourceFlagsFile).use {}
              IdeNativeAbiImpl(
                abi.toString(),
                sourceFlagsFile = sourceFlagsFile,
                symbolFolderIndexFile = moduleBasePath.resolve("some-build-dir/${variant.name}/${abi.name}/symbol_folder_index.txt"),
                buildFileIndexFile = moduleBasePath.resolve("some-build-dir/${variant.name}/${abi.name}/build_file_index.txt"),
                additionalProjectFilesIndexFile = moduleBasePath.resolve(
                  "some-build-dir/${variant.name}/${abi.name}/additional_project_files.txt")
              )
            }
          )
        },
      nativeBuildSystem = NativeBuildSystem.CMAKE,
      ndkVersion = "21.4.7075529",
      defaultNdkVersion = "21.4.7075529",
      externalNativeBuildFile = moduleBasePath.resolve("CMakeLists.txt")
    )
  )
}

fun AndroidProjectStubBuilder.buildDependenciesStub(
  dependencies: List<IdeDependencyCoreImpl> = listOf()
): IdeDependenciesCoreImpl = IdeDependenciesCoreImpl(dependencies)

/**
 * Sets up [project] as a one module project configured in the same way sync would conigure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  rootProjectBasePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) = setupTestProjectFromAndroidModel(project, rootProjectBasePath, setupAllVariants = false, moduleBuilders = moduleBuilders)

/**
 * Sets up [project] as a one module project configured in the same way sync would configure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  rootProjectBasePath: File,
  setupAllVariants: Boolean = false,
  vararg moduleBuilders: ModuleModelBuilder
) {
  if (moduleBuilders.none { it.gradlePath == ":" }) {
    error(
      "Each project needs to have ':' module. " +
      "Add `JavaModuleModelBuilder.rootModuleBuilder` to add a default one."
    )
  }
  if (IdeSdks.getInstance().androidSdkPath === null) {
    AndroidGradleTests.setUpSdks(project, project, getSdk().toFile())
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  GradleProjectImporter.configureNewProject(project)

  val moduleManager = ModuleManager.getInstance(project)
  if (moduleManager.modules.size <= 1) {
    runWriteAction {
      val modifiableModel = moduleManager.getModifiableModel()
      val module = if (modifiableModel.modules.isEmpty()) {
        modifiableModel.newModule(rootProjectBasePath.resolve(".idea").resolve("modules").resolve("${project.name}.iml").path, JAVA.id)
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
          ModuleData(
            ":",
            GRADLE_SYSTEM_ID,
            JAVA.id,
            project.name,
            rootProjectBasePath.resolve(".idea").resolve("modules").systemIndependentPath,
            rootProjectBasePath.systemIndependentPath
          ),
          ProjectData(GRADLE_SYSTEM_ID, project.name, project.basePath!!, rootProjectBasePath.systemIndependentPath))
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
  else {
    error("There is already more than one module in the test project.")
  }

  val gradleProjectSystem = GradleProjectSystem(project)
  ProjectSystemService.getInstance(project).replaceProjectSystemForTests(object : AndroidProjectSystem by gradleProjectSystem {
    // Many tests that invoke `compileProject` work with timestamps. To avoid flaky tests we inject a millisecond delay each time
    // build is requested.
    private val buildManager = TestProjectSystemBuildManager(ensureClockAdvancesWhileBuilding = true)
    override fun getBuildManager(): ProjectSystemBuildManager = buildManager
  })
  setupTestProjectFromAndroidModelCore(project, rootProjectBasePath, moduleBuilders, setupAllVariants, cacheExistingVariants = false)
}

/**
 * Sets up [project] as a one module project configured in the same way sync would configure it from the same model.
 */
fun updateTestProjectFromAndroidModel(
  project: Project,
  rootProjectBasePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) {
  setupTestProjectFromAndroidModelCore(project, rootProjectBasePath, moduleBuilders, setupAllVariants = false,
                                       cacheExistingVariants = false)
  GradleSyncStateHolder.getInstance(project).syncSkipped(null)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}

/**
 * Sets up [project] as a one module project configured in the same way sync would configure it from the same model.
 */
fun switchTestProjectVariantsFromAndroidModel(
  project: Project,
  rootProjectBasePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) {
  setupTestProjectFromAndroidModelCore(project, rootProjectBasePath, moduleBuilders, setupAllVariants = false, cacheExistingVariants = true)
  GradleSyncStateHolder.getInstance(project).syncSkipped(null)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}

/**
 * Note: applicable to test projects set up via [AndroidProjectRule.withAndroidModels] and similar methods.
 */
fun Project.readAndClearLastSyncRequest(): GradleSyncInvoker.Request? {
  return getUserData(SKIPPED_SYNC).also {
    putUserData(SKIPPED_SYNC, null)
  }
}

private fun setupTestProjectFromAndroidModelCore(
  project: Project,
  rootProjectBasePath: File,
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
  val buildPath = rootProjectBasePath.resolve("build")
  val projectName = project.name
  val projectDataNode = DataNode<ProjectData>(
    ProjectKeys.PROJECT,
    ProjectData(
      GRADLE_SYSTEM_ID,
      projectName,
      rootProjectBasePath.systemIndependentPath,
      rootProjectBasePath.systemIndependentPath
    ),
    null
  )

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
        override fun getPath(): String = ":"
        override fun getIdentityPath(): String = ":"
        override fun getName(): String = projectName
        override fun getQName(): String = projectName
        override fun getDescription(): String? = null
        override fun getGroup(): String = ""
        override fun getVersion(): String = "unspecified"
        override fun getChildProjects(): Map<String, ExternalProject> = mapOf()
        override fun getSourceCompatibility(): String? = null
        override fun getTargetCompatibility(): String? = null
        override fun getProjectDir(): File = rootProjectBasePath
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

  val androidModels = mutableListOf<GradleAndroidModelData>()
  val internedModels = InternedModels(null)
  val libraryResolver = IdeLibraryModelResolverImpl { sequenceOf(internedModels.resolve(it)) }
  val featureToBase = mutableMapOf<String, String>()
  moduleBuilders.forEach { moduleBuilder ->
    val gradlePath = moduleBuilder.gradlePath
    val moduleRelativeBasePath = gradlePath.substring(1).replace(':', File.separatorChar)
    val imlBasePath = rootProjectBasePath
      .resolve(".idea")
      .resolve("modules")
      .resolve(moduleRelativeBasePath)
    val moduleBasePath = rootProjectBasePath.resolve(moduleRelativeBasePath)
    FileUtils.mkdirs(moduleBasePath)
    val qualifiedModuleName = if (gradlePath == ":") projectName else projectName + gradlePath.replace(":", ".")
    val moduleDataNode = when (moduleBuilder) {
      is AndroidModuleModelBuilder -> {
        val (androidProject, variants, ndkModel) = moduleBuilder.projectBuilder(
          projectName,
          gradlePath,
          rootProjectBasePath,
          moduleBasePath,
          moduleBuilder.agpVersion ?: LatestKnownPluginVersionProvider.INSTANCE.get(),
          internedModels
        )
        featureToBase.putAll(androidProject.dynamicFeatures.map { it to gradlePath })

        fun IdeAndroidProjectImpl.populateBaseFeature(): IdeAndroidProjectImpl {
          return if (projectType != IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) this
          else copy(baseFeature = featureToBase[gradlePath] ?: error("Base feature must appear fist in a test project. ($gradlePath)"))
        }

        createAndroidModuleDataNode(
          qualifiedModuleName = qualifiedModuleName,
          gradlePath = gradlePath,
          groupId = moduleBuilder.groupId,
          version = moduleBuilder.version,
          imlBasePath = imlBasePath,
          moduleBasePath = moduleBasePath,
          gradleVersion = moduleBuilder.gradleVersion,
          agpVersion = moduleBuilder.agpVersion,
          gradlePlugins = gradlePlugins,
          androidProject = androidProject.populateBaseFeature(),
          variants = variants.let { if (!setupAllVariants) it.filter { it.name == moduleBuilder.selectedBuildVariant } else it },
          libraryResolver = libraryResolver,
          ndkModel = ndkModel,
          selectedVariantName = moduleBuilder.selectedBuildVariant,
          selectedAbiName = moduleBuilder.selectedAbiVariant
        ).also { androidModelDataNode ->
          val model = ExternalSystemApiUtil.find(androidModelDataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
          if (model != null) {
            androidModels.add(model)
          }
        }
      }

      is JavaModuleModelBuilder ->
        createJavaModuleDataNode(
          parentModuleOrProjectName = projectName,
          qualifiedModuleName = qualifiedModuleName,
          gradlePath = gradlePath,
          groupId = moduleBuilder.groupId,
          version = moduleBuilder.version,
          imlBasePath = imlBasePath,
          moduleBasePath = moduleBasePath,
          buildable = moduleBuilder.buildable,
          isBuildSrc = moduleBuilder.isBuildSrc
        )
    }
    projectDataNode.addChild(moduleDataNode)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
  projectDataNode.createChild(
    AndroidProjectKeys.IDE_LIBRARY_TABLE,
    internedModels.createResolvedLibraryTable()
  )
  projectDataNode.createChild(
    AndroidProjectKeys.IDE_COMPOSITE_BUILD_MAP,
    IdeCompositeBuildMapImpl(
      builds = listOf(IdeBuildImpl(buildName = ":", buildId = rootProjectBasePath)),
      gradleSupportsDirectTaskInvocation = true
    )
  )

  setupDataNodesForSelectedVariant(project, toSystemIndependentName(rootProjectBasePath.path), androidModels, projectDataNode,
                                   libraryResolver)
  mergeContentRoots(projectDataNode)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

  val externalProjectData = InternalExternalProjectInfo(GradleConstants.SYSTEM_ID, rootProjectBasePath.path, projectDataNode)
  (ExternalProjectsManager.getInstance(project) as ExternalProjectsManagerImpl).updateExternalProjectData(externalProjectData)

  ProjectDataManager.getInstance().importData(projectDataNode, project)
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
  AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
  PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}

private fun createAndroidModuleDataNode(
  qualifiedModuleName: String,
  gradlePath: String,
  groupId: String?,
  version: String?,
  imlBasePath: File,
  moduleBasePath: File,
  gradleVersion: String?,
  agpVersion: String?,
  gradlePlugins: List<String>,
  androidProject: IdeAndroidProjectImpl,
  variants: Collection<IdeVariantCoreImpl>,
  libraryResolver: IdeLibraryModelResolver,
  ndkModel: NdkModel?,
  selectedVariantName: String,
  selectedAbiName: String?
): DataNode<ModuleData> {

  val moduleDataNode = createGradleModuleDataNode(
    gradlePath = gradlePath,
    parentModuleOrProjectName = qualifiedModuleName,
    moduleName = qualifiedModuleName,
    groupId = groupId,
    version = version,
    imlBasePath = imlBasePath,
    moduleBasePath = moduleBasePath
  )

  moduleDataNode.addChild(
    DataNode<GradleModuleModel>(
      AndroidProjectKeys.GRADLE_MODULE_MODEL,
      GradleModuleModel(
        qualifiedModuleName,
        listOf(),
        gradlePath,
        moduleBasePath,
        gradlePlugins,
        moduleBasePath.resolve("build.gradle"),
        gradleVersion,
        agpVersion
      ),
      null
    )
  )

  val gradleAndroidModel = GradleAndroidModelDataImpl.create(
    qualifiedModuleName,
    moduleBasePath,
    androidProject,
    variants,
    selectedVariantName
  )

  moduleDataNode.addChild(
    DataNode(
      AndroidProjectKeys.ANDROID_MODEL,
      gradleAndroidModel,
      null
    )
  )

  when (ndkModel) {
    is V2NdkModel -> {
      val selectedAbiName = selectedAbiName
                            ?: ndkModel.abiByVariantAbi.keys.firstOrNull { it.variant == selectedVariantName }?.abi
                            ?: error(
                              "Cannot determine the selected ABI for module '$qualifiedModuleName' with the selected variant '$selectedVariantName'"
                            )
      moduleDataNode.addChild(
        DataNode<NdkModuleModel>(
          AndroidProjectKeys.NDK_MODEL,
          NdkModuleModel(
            qualifiedModuleName,
            moduleBasePath,
            selectedVariantName,
            selectedAbiName,
            ndkModel
          ),
          null
        )
      )
    }

    is V1NdkModel -> {
      val selectedAbiName = selectedAbiName
                            ?: ndkModel.nativeVariantAbis.firstOrNull { it.variantName == selectedVariantName }?.abi
                            ?: error(
                              "Cannot determine the selected ABI for module '$qualifiedModuleName' with the selected variant '$selectedVariantName'"
                            )
      moduleDataNode.addChild(
        DataNode<NdkModuleModel>(
          AndroidProjectKeys.NDK_MODEL,
          NdkModuleModel(
            qualifiedModuleName,
            moduleBasePath,
            selectedVariantName,
            selectedAbiName,
            ndkModel.androidProject,
            ndkModel.nativeVariantAbis
          ),
          null
        )
      )
    }
    else -> { }
  }

  fun IdeBaseArtifact.setup() {
    val sourceSetModuleName = ModuleUtil.getModuleName(this.name)
    moduleDataNode.addChild(
      DataNode<GradleSourceSetData>(
        GradleSourceSetData.KEY,
        GradleSourceSetData(
          moduleDataNode.data.id + ":" + sourceSetModuleName,
          moduleDataNode.data.externalName + ":" + sourceSetModuleName,
          moduleDataNode.data.internalName + "." + sourceSetModuleName,
          moduleDataNode.data.moduleFileDirectoryPath,
          moduleDataNode.data.linkedExternalProjectPath
        ),
        null
      )
    )
  }

  val selectedVariant = gradleAndroidModel.selectedVariant(libraryResolver)
  selectedVariant.mainArtifact.setup()
  selectedVariant.androidTestArtifact?.setup()
  selectedVariant.unitTestArtifact?.setup()
  selectedVariant.testFixturesArtifact?.setup()

  return moduleDataNode
}

private fun createJavaModuleDataNode(
  parentModuleOrProjectName: String,
  qualifiedModuleName: String,
  gradlePath: String,
  groupId: String?,
  version: String?,
  imlBasePath: File,
  moduleBasePath: File,
  buildable: Boolean,
  isBuildSrc: Boolean
): DataNode<ModuleData> {

  val moduleDataNode = createGradleModuleDataNode(
    gradlePath = gradlePath,
    parentModuleOrProjectName = parentModuleOrProjectName,
    moduleName = qualifiedModuleName,
    groupId = groupId,
    version = version,
    imlBasePath = imlBasePath,
    moduleBasePath = moduleBasePath
  ).also {
    if (isBuildSrc) it.data.setBuildSrcModule()
  }

  moduleDataNode.addDefaultJdk()

  fun setupSourceSet(sourceSetName: String, isTest: Boolean) {
    val sourceSetModuleName = sourceSetName
    val sourceSetDataDataNode = DataNode<GradleSourceSetData>(
      GradleSourceSetData.KEY,
      GradleSourceSetData(
        moduleDataNode.data.id + ":" + sourceSetModuleName,
        moduleDataNode.data.externalName + ":" + sourceSetModuleName,
        moduleDataNode.data.internalName + "." + sourceSetModuleName,
        moduleDataNode.data.moduleFileDirectoryPath,
        moduleDataNode.data.linkedExternalProjectPath
      ).also {
        it.group = groupId
        it.version = version
        if (isBuildSrc) it.setBuildSrcModule()
      },
      null
    )
    val root = moduleDataNode.data.linkedExternalProjectPath + "/src/" + sourceSetName
    sourceSetDataDataNode.addChild(
      DataNode(
        ProjectKeys.CONTENT_ROOT,
        ContentRootData(
          GRADLE_SYSTEM_ID,
          root
        ).also {
          it.storePath(if (isTest) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE, root + "/java")
          it.storePath(if (isTest) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE, root + "/resources")
        },
        null
      )
    )
    sourceSetDataDataNode.addDefaultJdk()
    if (isTest) {
      sourceSetDataDataNode.addChild(
        DataNode(
          ProjectKeys.MODULE_DEPENDENCY,
          ModuleDependencyData(
            sourceSetDataDataNode.data,
            ExternalSystemApiUtil.findAll(
              moduleDataNode,
              GradleSourceSetData.KEY
            ).find { it.data.moduleName == "main" }!!.data
          ),
          null
        )
      )
    }
    moduleDataNode.addChild(sourceSetDataDataNode)
  }

  if (buildable) {
    setupSourceSet("main", isTest = false)
    setupSourceSet("test", isTest = true)

    val gradleExtensions = DefaultGradleExtensions()
    gradleExtensions.extensions.add(DefaultGradleExtension("java", "org.gradle.api.plugins.internal.DefaultJavaPluginExtension"));
    moduleDataNode.addChild(
      DataNode<GradleExtensions>(
        GradleExtensionsDataService.KEY,
        gradleExtensions,
        null
      )
    )
  }

  if (buildable || gradlePath != ":") {
    moduleDataNode.addChild(
      DataNode<GradleModuleModel>(
        AndroidProjectKeys.GRADLE_MODULE_MODEL,
        GradleModuleModel(
          qualifiedModuleName,
          listOf(),
          gradlePath,
          moduleBasePath,
          emptyList(),
          null,
          null,
          null
        ),
        null
      )
    )
  }

  return moduleDataNode
}

private fun DataNode<out ModuleData>.addDefaultJdk() {
  addChild(
    DataNode(
      ModuleSdkData.KEY,
      ModuleSdkData(IdeSdks.getInstance().jdk!!.name),
      null
    )
  )
}

private fun createGradleModuleDataNode(
  gradlePath: String,
  parentModuleOrProjectName: String,
  moduleName: String,
  groupId: String?,
  version: String?,
  imlBasePath: File,
  moduleBasePath: File
): DataNode<ModuleData> {
  val moduleDataNode = DataNode<ModuleData>(
    ProjectKeys.MODULE,
    ModuleData(
      if (gradlePath == ":") parentModuleOrProjectName else gradlePath,
      GRADLE_SYSTEM_ID,
      JavaModuleType.getModuleType().id,
      moduleName,
      imlBasePath.systemIndependentPath,
      moduleBasePath.systemIndependentPath
    ).also {
      it.gradleIdentityPath = gradlePath
      it.group = groupId
      it.version = version
    },
    null
  )
  moduleDataNode.addChild(
    DataNode(
      ProjectKeys.CONTENT_ROOT,
      ContentRootData(GRADLE_SYSTEM_ID, moduleBasePath.systemIndependentPath)
        .also {
          it.storePath(ExternalSystemSourceType.EXCLUDED, moduleBasePath.resolve(".gradle").systemIndependentPath)
          it.storePath(ExternalSystemSourceType.EXCLUDED, moduleBasePath.resolve("build").systemIndependentPath)
        },
      null
    )
  )
  return moduleDataNode
}

/**
 * This method is a replica of GradleProjectResolver#mergeSourceSetContentRoots.
 */
private fun mergeContentRoots(projectDataNode: DataNode<ProjectData>) {
  val weightMap = mutableMapOf<String, Int>()

  val moduleNodes = projectDataNode.findAll(ProjectKeys.MODULE)
  moduleNodes.forEach { moduleNode ->
    moduleNode.node.findAll(ProjectKeys.CONTENT_ROOT).forEach { rootNode ->
      var file: File? = File(rootNode.data.rootPath)
      while (file != null) {
        weightMap[file.path] = weightMap.getOrDefault(file.path, 0) + 1
        file = file.parentFile
      }
    }

    moduleNode.node.findAll(GradleSourceSetData.KEY).forEach { sourceSetNode ->
      val set = mutableSetOf<String>()
      sourceSetNode.node.findAll(ProjectKeys.CONTENT_ROOT).forEach { rootNode ->
        var file: File? = File(rootNode.data.rootPath)
        while (file != null) {
          set.add(file!!.path)
          file = file!!.parentFile
        }
      }
      set.forEach { path ->
        weightMap[path] = weightMap.getOrDefault(path, 0) + 1
      }
    }
  }

  moduleNodes.forEach { moduleNode ->
    mergeModuleContentRoots(weightMap, moduleNode.node)
    moduleNode.node.findAll(GradleSourceSetData.KEY).forEach { sourceSetNode ->
      mergeModuleContentRoots(weightMap, sourceSetNode.node)
    }
  }
}

/**
 * This method is a replica of GradleProjectResolver#mergeModuleContentRoots.
 */
private fun mergeModuleContentRoots(weightMap: Map<String, Int>, moduleNode: DataNode<*>) {
  val buildDir = File((moduleNode.data as ModuleData).linkedExternalProjectPath).resolve("build")
  val sourceSetRoots: MultiMap<String, ContentRootData> = MultiMap.create()
  val contentRootsNodes = moduleNode.findAll(ProjectKeys.CONTENT_ROOT)
  if (contentRootsNodes.size <= 1) return

  contentRootsNodes.forEach { contentRootNode ->
    var root = File(contentRootNode.data.rootPath)
    if (FileUtil.isAncestor(buildDir, root, true)) return@forEach

    while (weightMap.containsKey(root.parent) && weightMap[root.parent]!! <= 1) {
      root = root.parentFile
    }

    var mergedContentRoot: ContentRootData? = null
    val rootPath = toCanonicalPath(root.path)
    val paths = sourceSetRoots.keySet()

    paths.forEach { path ->
      if (FileUtil.isAncestor(rootPath, path, true)) {
        val values = sourceSetRoots.remove(path)
        if (values != null) {
          sourceSetRoots.put(rootPath, values)
        }
      }
      else if (FileUtil.isAncestor(path, rootPath, false)) {
        val contentRoots = sourceSetRoots.get(path)
        contentRoots.forEach { rootData ->
          if (StringUtil.equals(rootData.rootPath, path)) {
            mergedContentRoot = rootData
            return@forEach
          }
        }
        if (mergedContentRoot == null) {
          mergedContentRoot = contentRoots.iterator().next()
        }
        return@forEach
      }
      if (sourceSetRoots.size() == 1) return@forEach
    }

    if (mergedContentRoot == null) {
      mergedContentRoot = ContentRootData(GradleConstants.SYSTEM_ID, root.path)
      sourceSetRoots.putValue(mergedContentRoot!!.rootPath, mergedContentRoot)
    }

    ExternalSystemSourceType.values().forEach { sourceType ->
      contentRootNode.data.getPaths(sourceType).forEach { sourceRoot ->
        mergedContentRoot!!.storePath(sourceType, sourceRoot.path, sourceRoot.packagePrefix)
      }
    }

    contentRootNode.node.clear(true)
  }

  sourceSetRoots.entrySet().forEach { entry ->
    val ideContentRoot = ContentRootData(GradleConstants.SYSTEM_ID, entry.key)

    entry.value.forEach { rootData ->
      ExternalSystemSourceType.values().forEach { sourceType ->
        rootData.getPaths(sourceType).forEach { root ->
          ideContentRoot.storePath(sourceType, root.path, root.packagePrefix)
        }
      }
    }

    moduleNode.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot)
  }
}

/**
 * Finds a module by the given [gradlePath].
 *
 * Note: In the case of composite build [gradlePath] can be in a form of `includedProject:module:module` for modules from included projects.
 */
fun Project.gradleModule(gradlePath: String): Module? =
  ModuleManager.getInstance(this).modules
    .firstOrNull {
      it.getBuildAndRelativeGradleProjectPath()?.buildNamePrefixedGradleProjectPath() == gradlePath
    }?.getHolderModule()

/**
 * Finds a file by the [path] relative to the corresponding Gradle project root.
 */
fun Module.fileUnderGradleRoot(path: @SystemIndependent String): VirtualFile? =
  VirtualFileManager.getInstance().findFileByUrl("${FilePaths.pathToIdeaUrl(File(AndroidProjectRootUtil.getModuleDirPath(this)!!))}/$path")

interface IntegrationTestEnvironment {

  /**
   * The base test directory to be used in tests.
   */
  fun getBaseTestPath(): @SystemDependent String
}

/**
 * See implementing classes for usage examples.
 */
interface GradleIntegrationTest : IntegrationTestEnvironment {

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
    val testDataDirectory = TestUtils.resolveWorkspacePath(toSystemDependentName(getTestDataDirectoryWorkspaceRelativePath()))
    return testDataDirectory.resolve(toSystemDependentName(testDataPath)).toFile()
  }

  fun getAgpVersionSoftwareEnvironmentDescriptor(): AgpVersionSoftwareEnvironmentDescriptor {
    return AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  }
}

/**
 * Prepares a test project created from a [testProjectPath] under the given [name] so that it can be opened with [openPreparedProject].
 */
@JvmOverloads
fun GradleIntegrationTest.prepareGradleProject(
  testProjectPath: String,
  name: String,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor = getAgpVersionSoftwareEnvironmentDescriptor(),
  ndkVersion: String? = null
): File {
  return prepareGradleProject(
    testProjectPath = testProjectPath,
    name = name,
    gradleVersion = agpVersion.gradleVersion,
    gradlePluginVersion = agpVersion.agpVersion,
    kotlinVersion = agpVersion.kotlinVersion,
    ndkVersion = ndkVersion,
    compileSdkVersion = agpVersion.compileSdk,
  )
}

fun IntegrationTestEnvironment.prepareGradleProject(
  testProjectAbsolutePath: File,
  additionalRepositories: Collection<File>,
  name: String,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  ndkVersion: String?
): File {
  return prepareGradleProject(
    testProjectAbsolutePath = testProjectAbsolutePath,
    additionalRepositories = additionalRepositories,
    name = name,
    gradleVersion = agpVersion.gradleVersion,
    gradlePluginVersion = agpVersion.agpVersion,
    kotlinVersion = agpVersion.kotlinVersion,
    ndkVersion = ndkVersion,
    compileSdkVersion = agpVersion.compileSdk,
  )
}

/**
 * Prepares a test project created from a [testProjectPath] under the given [name] so that it can be opened with [openPreparedProject].
 */
@JvmOverloads
fun GradleIntegrationTest.prepareGradleProject(
  testProjectPath: String,
  name: String,
  gradleVersion: String?,
  gradlePluginVersion: String?,
  kotlinVersion: String?,
  ndkVersion: String? = null,
  compileSdkVersion: String? = null,
): File {
  val testProjectAbsolutePath: File = resolveTestDataPath(testProjectPath)
  val additionalRepositories: Collection<File> = getAdditionalRepos()

  return prepareGradleProject(
    testProjectAbsolutePath,
    additionalRepositories,
    name,
    gradleVersion,
    gradlePluginVersion,
    kotlinVersion,
    ndkVersion,
    compileSdkVersion
  )
}

private fun IntegrationTestEnvironment.prepareGradleProject(
  testProjectAbsolutePath: File,
  additionalRepositories: Collection<File>,
  name: String,
  gradleVersion: String?,
  gradlePluginVersion: String?,
  kotlinVersion: String?,
  ndkVersion: String?,
  compileSdkVersion: String?
): File {
  val projectPath = nameToPath(name)
  if (projectPath.exists()) throw IllegalArgumentException("Additional projects cannot be opened under the test name: $name")

  AndroidGradleTests.prepareProjectForImportCore(
    testProjectAbsolutePath,
    projectPath,
    ThrowableConsumer { projectRoot ->
      AndroidGradleTests.defaultPatchPreparedProject(
        projectRoot, gradleVersion, gradlePluginVersion,
        kotlinVersion,
        ndkVersion,
        compileSdkVersion,
        *additionalRepositories.toTypedArray()
      )
    })
  if (System.getenv("SYNC_BASED_TESTS_DEBUG_OUTPUT")?.toLowerCase() == "y") {
    println("Test project ${testProjectAbsolutePath.name} prepared at '$projectPath'")
  }
  return projectPath
}

fun prepareGradleProject(projectSourceRoot: File, projectPath: File, projectPatcher: ThrowableConsumer<File, IOException>) {
  AndroidGradleTests.validateGradleProjectSource(projectSourceRoot)
  AndroidGradleTests.prepareProjectForImportCore(projectSourceRoot, projectPath, projectPatcher)
}

data class OpenPreparedProjectOptions @JvmOverloads constructor(
  val expectedSyncIssues: Set<Int> = emptySet(),
  val verifyOpened: (Project) -> Unit = { verifySyncedSuccessfully(it, expectedSyncIssues) },
  val outputHandler: (Project.(String) -> Unit)? = null,
  val syncExceptionHandler: (Project.(Exception) -> Unit)? = { e ->
    println(e.message)
    e.printStackTrace()
  },
  val subscribe: (MessageBusConnection) -> Unit = {},
  val disableKtsRelatedIndexing: Boolean = false,
)

/**
 * Opens a test project previously prepared under the given [name], verifies the state of the project with [verifyOpened] and runs
 * a test [action] and then closes and disposes the project.
 *
 * The project's `.idea` directory is not required to exist, however.
 */
@JvmOverloads
fun <T> IntegrationTestEnvironment.openPreparedProject(
  name: String,
  options: OpenPreparedProjectOptions = OpenPreparedProjectOptions(),
  action: (Project) -> T
): T {
  return openPreparedProject(nameToPath(name), options, action)
}

private fun <T> openPreparedProject(
  projectPath: File,
  options: OpenPreparedProjectOptions,
  action: (Project) -> T
): T {
  // Use per-project code style settings so we never modify the IDE defaults.
  CodeStyleSettingsManager.getInstance().USE_PER_PROJECT_SETTINGS = true;

  fun clearKotlinPluginCompilerArgumentCaches() {
    val privateCache = CompilerArgumentsCacheHolder::class.memberProperties.single { it.name == "cacheAwareWithMergeByIdentifier" }
    privateCache.isAccessible = true
    (privateCache.get(CompilerArgumentsCacheMergeManager.compilerArgumentsCacheHolder) as MutableMap<*, *>).clear()
  }

  fun body(): T {
    val disposable = Disposer.newDisposable()
    try {
      val project = runInEdtAndGet {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        // This method is used to simulate what happens when the IDE is restarted before re-opening a project in order to catch issues
        // that cannot be reproduced otherwise.
        clearKotlinPluginCompilerArgumentCaches()
        if (options.disableKtsRelatedIndexing) {
          // NOTE: We do not re-register the extensions since (1) we do not know whether we removed it and (2) there is no simple way to
          //       re-register it by its class name. It means that this test might affect tests running after this one.

          // [KotlinScriptDependenciesLibraryRootProvider] contributes a lot of classes/sources to index in order to provide Ctrl+Space
          // experience in the code editor. It takes approximately 4 minutes to complete. We unregister the contributor to make our tests
          // run faster.
          AdditionalLibraryRootsProvider.EP_NAME.point.unregisterExtension(KotlinScriptDependenciesLibraryRootProvider::class.java)
        }

        var afterCreateCalled = false

        fun afterCreate(project: Project) {
          // After create is invoked via three different execution paths:
          //   (1) when we import a new Android Gradle project that does not yet have a `.idea` directory. In this case this method is
          //       called `GradleProjectImporter.createProject`;
          //   (2) when we import an Android Gradle project with an existing `.idea` directory a request to call this method is passed to
          //       the project manager from `AndroidGradleProjectOpenProcessor.doOpenProject`;
          //   (3) when we try to open a project with a `.idea` directory such that it is not recognised as an Android Gradle project this
          //       method is called normally via `ProjectUtil.openOrImport`.
          // Note, that this happens because custom `ProjectOpenProcessor`'s currently do not receive `OpenProjectTask` options.
          if (afterCreateCalled) {
            error("Attempt to call afterCreate() twice.")
          }
          afterCreateCalled = true

          @Suppress("UnstableApiUsage")
          if (options.disableKtsRelatedIndexing) {
            ScriptingSupport.EPN.getPoint(project).unregisterExtensions({ _, _ -> false }, false)
            ScriptChangeListener.LISTENER.getPoint(project).unregisterExtensions({ _, _ -> false }, false)
          }
          project.messageBus.connect(disposable).let { options.subscribe(it) }
          val outputHandler = options.outputHandler
          val syncExceptionHandler = options.syncExceptionHandler
          if (outputHandler != null || syncExceptionHandler != null) {
            injectSyncOutputDumper(project, project, options.outputHandler ?: {}, options.syncExceptionHandler ?: {})
          }
          fixDummySyncViewManager(project, disposable)
        }

        val project = GradleProjectImporter.withAfterCreate(afterCreate = { project -> afterCreate(project) }) {
          ProjectUtil.openOrImport(
            projectPath.toPath(),
            OpenProjectTask{
              beforeOpen =
              {
                afterCreate(it)
                true
              }
              projectToClose = null
              forceOpenInNewFrame = true
            }
          )!!
        }
        // Unfortunately we do not have start-up activities run in tests so we have to trigger a refresh here.
        emulateStartupActivityForTest(project)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        project.maybeOutputDiagnostics()
        project
      }
      try {
        options.verifyOpened(project)
        return action(project)
      }
      finally {
        runInEdtAndWait {
          PlatformTestUtil.saveProject(project, true)
          ProjectManager.getInstance().closeAndDispose(project)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  return body()
}

fun IntegrationTestEnvironment.nameToPath(name: String) =
  File(toSystemDependentName(getBaseTestPath() + "/" + name))

private fun verifySyncedSuccessfully(project: Project, expectedSyncIssues: Set<Int> = emptySet()) {
  val lastSyncResult = project.getProjectSystem().getSyncManager().getLastSyncResult()
  if (!lastSyncResult.isSuccessful) {
    throw IllegalStateException(lastSyncResult.name)
  }

  // Also fail the test if SyncIssues with type errors are present.
  val errors = ModuleManager.getInstance(project)
    .modules
    .flatMap { it.syncIssues() }
    .filter { it.severity == SyncIssue.SEVERITY_ERROR }
    .filter { it.type !in expectedSyncIssues }
  if (errors.isNotEmpty()) {
    throw IllegalStateException(
      errors.joinToString(separator = "\n") {
        "${it.message}\n${it.data}\n  ${it.multiLineMessage?.joinToString("\n  ")}\n"
      }
    )
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
  BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project.gradleModule(moduleGradlePath)!!, variant)
}

fun switchAbi(project: Project, moduleGradlePath: String, abi: String) {
  BuildVariantUpdater.getInstance(project).updateSelectedAbi(project.gradleModule(moduleGradlePath)!!, abi)
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
    module.verifyModel(AndroidFacet::getInstance, GradleAndroidModel::get)
    module.verifyModel({ NdkFacet.getInstance(this) }, { ndkModuleModel })
  }
}

fun Project.requestSyncAndWait() {
  AndroidGradleTests.syncProject(this, GradleSyncInvoker.Request.testRequest())
  if (ApplicationManager.getApplication().isDispatchThread) {
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(this)
  }
  else {
    runInEdtAndWait {
      AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(this)
    }
  }
}

/**
 * Set up data nodes that are normally created by the project resolver when processing [GradleAndroidModel]s.
 */
private fun setupDataNodesForSelectedVariant(
  project: Project,
  buildId: @SystemIndependent String,
  androidModuleModels: List<GradleAndroidModelData>,
  projectDataNode: DataNode<ProjectData>,
  libraryResolver: IdeLibraryModelResolverImpl
) {
  val moduleNodes = ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE)
  val moduleIdToDataMap = createGradleProjectPathToModuleDataMap(buildId, moduleNodes)
  androidModuleModels.forEach { androidModuleModel ->
    val newVariant = androidModuleModel.selectedVariant(libraryResolver)

    val moduleNode = moduleNodes.firstOrNull { node ->
      node.data.internalName == androidModuleModel.moduleName
    } ?: return@forEach

    // Now we need to recreate these nodes using the information from the new variant.
    moduleNode.setupCompilerOutputPaths(newVariant, false)
    // Then patch in any Kapt generated sources that we need
    val libraryFilePaths = LibraryFilePaths.getInstance(project)
    moduleNode.setupAndroidDependenciesForMpss({ path: GradleSourceSetProjectPath -> moduleIdToDataMap[path] }, { id ->
      AdditionalArtifactsPaths(
        libraryFilePaths.getCachedPathsForArtifact(id)?.sources,
        libraryFilePaths.getCachedPathsForArtifact(id)?.javaDoc,
        libraryFilePaths.getCachedPathsForArtifact(id)?.sampleSource
      )
    }, newVariant, project)
    moduleNode.setupAndroidContentEntriesPerSourceSet(androidModuleModel)
  }
}

private fun createGradleProjectPathToModuleDataMap(
  buildId: @SystemIndependent String,
  moduleNodes: Collection<DataNode<ModuleData>>
): Map<GradleSourceSetProjectPath, ModuleData> {
  return moduleNodes
    .flatMap { moduleDataNode ->
      ExternalSystemApiUtil.findAll(moduleDataNode, GradleSourceSetData.KEY)
        .mapNotNull {
          it.data.getIdeModuleSourceSet()
            .let { sourceSet ->
              GradleSourceSetProjectPath(
                buildId,
                if (toSystemIndependentName(moduleDataNode.data.linkedExternalProjectPath) == buildId) ":"
                else moduleDataNode.data.id,
                sourceSet
              ) to it.data
            }
        }
    }
    .toMap()
}

@JvmOverloads
fun injectBuildOutputDumpingBuildViewManager(
  project: Project,
  disposable: Disposable,
  eventHandler: (BuildEvent) -> Unit = {}
) {
  project.replaceService(
    BuildViewManager::class.java,
    object : BuildViewManager(project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        if (event is MessageEvent) {
          println(event.result.details)
        }
        eventHandler(event)
      }
    },
    disposable
  )
}

@Suppress("UnstableApiUsage")
private fun fixDummySyncViewManager(project: Project, disposable: Disposable) {
  if (project.getService(SyncViewManager::class.java) is DummySyncViewManager) {
    val listeners = CopyOnWriteArrayList<BuildProgressListener>()
    project.replaceService(
      SyncViewManager::class.java,
      object : DummySyncViewManager(project) {

        override fun addListener(listener: BuildProgressListener, disposable: Disposable) {
          listeners.add(listener)
          Disposer.register(disposable) {
            listeners.remove(listener)
          }
        }

        override fun onEvent(buildId: Any, event: BuildEvent) {
          if (event is MessageEvent) {
            println(event.result.details)
          }
          listeners.forEach {
            it.onEvent(buildId, event)
          }
        }
      },
      disposable
    )
  }
}

fun injectSyncOutputDumper(
  project: Project,
  disposable: Disposable,
  outputHandler: Project.(String) -> Unit,
  syncExceptionHandler: Project.(Exception) -> Unit
) {
  val projectId = ExternalSystemTaskId.getProjectId(project)
  ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(
    object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        if (id.ideProjectId != projectId) return
        outputHandler(project, text)
      }

      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        if (id.ideProjectId != projectId) return
        syncExceptionHandler(project, e)
      }
    },
    disposable
  )
}

fun <T> Project.buildAndWait(eventHandler: (BuildEvent) -> Unit = {}, invoker: (GradleBuildInvoker) -> ListenableFuture<T>): T {
  val gradleBuildInvoker = GradleBuildInvoker.getInstance(this)
  val disposable = Disposer.newDisposable()
  try {
    injectBuildOutputDumpingBuildViewManager(project = this, disposable = disposable, eventHandler = eventHandler)
    val future = invoker(gradleBuildInvoker)
    try {
      return future.get(5, TimeUnit.MINUTES)
    }
    finally {
      AndroidTestBase.refreshProjectFiles()
      ApplicationManager.getApplication().invokeAndWait {
        try {
          AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(this, null)
        }
        catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
  }
  finally {
    Disposer.dispose(disposable)
  }
}

// HACK: b/143864616 and ag/14916674 Bazel hack, until missing dependencies are available in "offline-maven-repo"
fun updatePluginsResolutionManagement(origContent: String, pluginDefinitions: String): String {
  val pluginsResolutionStrategy =
    """

      resolutionStrategy {
        eachPlugin {
          if (requested.id.id == "com.google.android.libraries.mapsplatform.secrets-gradle-plugin") {
              useModule("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:${'$'}{requested.version}")
          }
          if (requested.id.id == "org.jetbrains.kotlin.android" && requested.version == "1.6.21") {
              // KGP marker for 1.6.21 is not in the offline repo; remove this once Compose tests are updated to 1.7.0+
              useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
          }
        }
      }
    """.trimMargin()

  return origContent.replace("pluginManagement {", "pluginManagement { $pluginsResolutionStrategy")
}

private fun Project.maybeOutputDiagnostics() {
  if (System.getenv("SYNC_BASED_TESTS_DEBUG_OUTPUT")?.toLowerCase() == "y") {
    println("Libraries:>")
    LibraryTablesRegistrar.getInstance()
      .getLibraryTable(this)
      .libraries
      .sortedBy { it.name }
      .forEach { println(it.name) }
  }
}
