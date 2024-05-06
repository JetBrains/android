/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.kotlin.android.models

import com.android.builder.model.proto.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.proto.ide.AndroidVersion
import com.android.builder.model.proto.ide.Library
import com.android.builder.model.proto.ide.SigningConfig
import com.android.builder.model.proto.ide.TestInfo
import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Version
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidSourceSetKey
import com.android.kotlin.multiplatform.models.AndroidCompilation
import com.android.kotlin.multiplatform.models.AndroidTarget
import com.android.kotlin.multiplatform.models.SourceProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_ANDROID_TEST
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_UNIT_TEST
import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.ResolverType
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTasksAndOutputInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreDirect
import com.android.tools.idea.gradle.model.impl.IdeDependencyCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeExtraSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl.Companion.wellKnownOrCreate
import com.android.tools.idea.gradle.model.impl.IdeProjectPathImpl
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeSigningConfigImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeTestOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.model.GradleAndroidModelDataImpl
import com.android.tools.idea.gradle.project.model.ourAndroidSyncVersion
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependencyCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.documentationClasspath
import org.jetbrains.kotlin.gradle.idea.tcs.extras.sourcesClasspath
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.WeakInterner
import java.io.File
import java.nio.charset.Charset

/**
 * Used to convert models coming from the build side in the kotlin model extras to the IDE models representation.
 */
class KotlinModelConverter {
  companion object {
    const val kotlinMultiplatformAndroidVariantName = "androidMain"

    internal fun KotlinSourceSet.getJavaSourceDirectories() = sourceDirs.map { File(it.parentFile, "java") }
  }

  private val interner = WeakInterner(lock = null) // No need for a lock since the resolution happens sequentially.

  private val seenDependencies = mutableMapOf<IdeaKotlinDependencyCoordinates, LibraryReference>()
  private val libraries = mutableListOf<IdeLibrary>()

  private val useAdditionalArtifactsFromLibraries by lazy {
    GradleExperimentalSettings.getInstance().USE_MULTI_VARIANT_EXTRA_ARTIFACTS &&
    StudioFlags.GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT.get()
  }

  private fun String.deduplicate() = interner.getOrPut(this)
  private fun File.deduplicateFile(): File = File(path.deduplicate())
  private fun com.android.builder.model.proto.ide.File.convertAndDeduplicate() = File(absolutePath.deduplicate())
  private fun Collection<com.android.builder.model.proto.ide.File>.convertAndDeduplicate() = map { it.convertAndDeduplicate() }

  private fun AndroidVersion.convert(): IdeApiVersionImpl {
    val apiString = codename ?: apiLevel.toString()
    return IdeApiVersionImpl(
      apiLevel = apiLevel,
      codename = codename.deduplicate(),
      apiString = apiString.deduplicate()
    )
  }

  private fun TestInfo.convert(): IdeTestOptionsImpl {
    val executionOption: IdeTestOptions.Execution? =
      when (execution) {
        null -> null
        TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR ->
          IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
        TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR ->
          IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR
        TestInfo.Execution.HOST ->
          IdeTestOptions.Execution.HOST
        TestInfo.Execution.UNRECOGNIZED -> IdeTestOptions.Execution.HOST
      }
    return IdeTestOptionsImpl(
      animationsDisabled = animationsDisabled,
      execution = executionOption,
      instrumentedTestTaskName = instrumentedTestTaskName.deduplicate()
    )
  }

  private fun SourceProvider.convert(
    sourceSet: KotlinSourceSet,
    withJava: Boolean
  ): IdeSourceProviderImpl {
    val folder = File(manifestFile.absolutePath).parentFile
    fun File.makeRelativeAndDeduplicate(): String = (if (folder != null) relativeToOrSelf(folder) else this).path.deduplicate()
    fun String.makeRelativeAndDeduplicate(): String = File(this).makeRelativeAndDeduplicate()
    fun Collection<File>.makeRelativeAndDeduplicate(): Collection<String> = map { it.makeRelativeAndDeduplicate() }
    return IdeSourceProviderImpl(
      myName = sourceSet.name,
      myFolder = folder,
      myManifestFile = manifestFile.absolutePath.makeRelativeAndDeduplicate(),
      myKotlinDirectories = sourceSet.sourceDirs.makeRelativeAndDeduplicate(),
      myResourcesDirectories = sourceSet.resourceDirs.makeRelativeAndDeduplicate(),
      myJavaDirectories = if (withJava) {
        sourceSet.getJavaSourceDirectories().makeRelativeAndDeduplicate()
      } else emptyList(),
      myAidlDirectories = emptyList(),
      myRenderscriptDirectories = emptyList(),
      myResDirectories = emptyList(),
      myAssetsDirectories = emptyList(),
      myJniLibsDirectories = emptyList(),
      myShadersDirectories = emptyList(),
      myMlModelsDirectories = emptyList(),
      myBaselineProfileDirectories = emptyList(),
      myCustomSourceDirectories = emptyList()
    )
  }

  private fun AndroidGradlePluginProjectFlags.convert() = IdeAndroidGradlePluginProjectFlagsImpl(
    applicationRClassConstantIds = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS
    }.value,
    testRClassConstantIds = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS
    }.value,
    transitiveRClasses = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS
    }.value,
    usesCompose = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE
    }.value,
    mlModelBindingEnabled = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING
    }.value,
    unifiedTestPlatformEnabled = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM
    }.value,
    useAndroidX = booleanFlagValuesList.first {
      it.flag == AndroidGradlePluginProjectFlags.BooleanFlag.USE_ANDROID_X
    }.value,
  )

  private fun SigningConfig.convert() = IdeSigningConfigImpl(
    name = name.deduplicate(),
    storeFile = storeFile?.convertAndDeduplicate(),
    storePassword = storePassword?.deduplicate(),
    keyAlias = keyAlias?.deduplicate()
  )

  private fun computeForCoordinatesIfAbsent(
    coordinates: IdeaKotlinDependencyCoordinates?,
    action: () -> LibraryReference
  ): LibraryReference {
    return if (coordinates == null) {
      action()
    } else {
      seenDependencies.computeIfAbsent(coordinates) {
        action()
      }
    }
  }

  private fun recordLibraryDependency(library: IdeLibrary): LibraryReference {
    val index = libraries.size
    libraries.add(library)
    return LibraryReference(index, ResolverType.KMP_ANDROID)
  }

  private fun androidLibraryFrom(
    androidLibrary: Library,
    coordinates: IdeaKotlinDependencyCoordinates?
  ): LibraryReference {
    return computeForCoordinatesIfAbsent(coordinates) {
      val libraryInfo = androidLibrary.libraryInfo ?: error("libraryInfo missing for ${androidLibrary.key}")

      val androidLibraryData = androidLibrary.androidLibraryData ?: error("androidLibraryData missing for ${androidLibrary.key}")

      val artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@aar"
      val library = IdeAndroidLibraryImpl.create(
        artifactAddress = artifactAddress,
        name = coordinates?.toString() ?: artifactAddress,
        component = androidLibrary.getComponent(),
        folder = androidLibraryData.resFolder.convertAndDeduplicate().parentFile.deduplicateFile(),
        artifact = if (androidLibrary.hasArtifact()) androidLibrary.artifact.convertAndDeduplicate() else File(""),
        lintJar = if (androidLibrary.hasLintJar()) androidLibrary.lintJar.convertAndDeduplicate().path else null,
        srcJar = if (useAdditionalArtifactsFromLibraries && androidLibrary.hasSrcJar()) androidLibrary.srcJar.convertAndDeduplicate().path else null,
        docJar = if (useAdditionalArtifactsFromLibraries && androidLibrary.hasDocJar()) androidLibrary.docJar.convertAndDeduplicate().path else null,
        samplesJar = if (useAdditionalArtifactsFromLibraries && androidLibrary.hasSamplesJar()) androidLibrary.samplesJar.convertAndDeduplicate().path else null,
        manifest = androidLibraryData.manifest.convertAndDeduplicate().path ?: "",
        compileJarFiles = androidLibraryData.compileJarFilesList.map { it.convertAndDeduplicate().path },
        runtimeJarFiles = androidLibraryData.runtimeJarFilesList.map { it.convertAndDeduplicate().path },
        resFolder = androidLibraryData.resFolder.convertAndDeduplicate().path ?: "",
        resStaticLibrary = androidLibraryData.resStaticLibrary.convertAndDeduplicate(),
        assetsFolder = androidLibraryData.assetsFolder.convertAndDeduplicate().path ?: "",
        jniFolder = androidLibraryData.jniFolder.convertAndDeduplicate().path ?: "",
        aidlFolder = androidLibraryData.aidlFolder.convertAndDeduplicate().path ?: "",
        renderscriptFolder = androidLibraryData.renderscriptFolder.convertAndDeduplicate().path ?: "",
        proguardRules = androidLibraryData.proguardRules.convertAndDeduplicate().path ?: "",
        externalAnnotations = androidLibraryData.externalAnnotations.convertAndDeduplicate().path ?: "",
        publicResources = androidLibraryData.publicResources.convertAndDeduplicate().path ?: "",
        symbolFile = androidLibraryData.symbolFile.convertAndDeduplicate().path,
        deduplicate = { this.deduplicate() }
      )

      recordLibraryDependency(library)
    }
  }

  fun Library.getComponent() = if (hasLibraryInfo()) {
    when (libraryInfo.group) {
      "__local_aars__", "__wrapped_aars__", "__local_asars__", "artifacts" -> null
      else -> Component(libraryInfo.group, libraryInfo.name, Version.parse(libraryInfo.version))
    }
  } else {
    null
  }

  /**
   * Converts kotlin's dependency notion into the notion used in the IDE models, caches the result and returns a reference to the created
   * library.
   */
  fun recordDependency(dependency: IdeaKotlinDependency): LibraryReference? {
    val libraryReference = when (dependency) {
      is IdeaKotlinBinaryDependency -> {
        val dependencyInfo = dependency.extras[androidDependencyKey]

        if (dependencyInfo != null) {
          androidLibraryFrom(dependencyInfo.library, dependency.coordinates)
        } else if (dependency is IdeaKotlinResolvedBinaryDependency) {
          computeForCoordinatesIfAbsent(dependency.coordinates) {
            recordLibraryDependency(
              IdeJavaLibraryImpl(
                artifactAddress = dependency.coordinates.toString(),
                name = dependency.coordinates.toString(),
                component = dependency.coordinates?.let {
                  if (it.version != null) {
                    Component(it.group, it.module, Version.parse(it.version!!))
                  } else {
                    null
                  }
                },
                artifact = dependency.classpath.first(),
                srcJar = if (useAdditionalArtifactsFromLibraries) dependency.sourcesClasspath.firstOrNull() else null,
                docJar = if (useAdditionalArtifactsFromLibraries) dependency.documentationClasspath.firstOrNull() else null,
                samplesJar = null,
              )
            )
          }
        } else {
          null
        }
      }

      is IdeaKotlinSourceDependency -> {
        computeForCoordinatesIfAbsent(dependency.coordinates) {
          recordLibraryDependency(
            IdeModuleLibraryImpl(
              buildId = dependency.coordinates.buildId,
              projectPath = dependency.coordinates.projectPath,
              variant = null, // TODO(b/269755640): how to combine the flavors here in the right order?
              lintJar = null,
              sourceSet = wellKnownOrCreate(dependency.coordinates.sourceSetName)
            )
          )
        }
      }

      else -> null
    }

    return libraryReference
  }


  /**
   * Creates the library table data node and attaches it to the project. When that happens, we don't need the cache for libraries anymore,
   * and so they're disposed.
   */
  fun maybeCreateLibraryTable(projectNode: DataNode<ProjectData>) {
    if (ExternalSystemApiUtil.find(projectNode, AndroidProjectKeys.KMP_ANDROID_LIBRARY_TABLE) == null) {
      projectNode.createChild(
        AndroidProjectKeys.KMP_ANDROID_LIBRARY_TABLE,
        IdeResolvedLibraryTableImpl(
          libraries = libraries.map { listOf(it) }
        )
      )

      seenDependencies.clear()
      libraries.clear()
    }
  }

  fun createGradleAndroidModelData(
    moduleName: String,
    rootModulePath: File?,
    targetInfo: AndroidTarget,
    compilationInfoMap: Map<AndroidCompilation.CompilationType, Pair<KotlinCompilation, AndroidCompilation>>,
    sourceSetDependenciesMap: Map<String, Set<LibraryReference>>,
  ): GradleAndroidModelData {
    val (mainKotlinCompilation, mainAndroidCompilation) = compilationInfoMap[AndroidCompilation.CompilationType.MAIN]!!
    val (unitTestKotlinCompilation, unitTestAndroidCompilation) = compilationInfoMap[AndroidCompilation.CompilationType.UNIT_TEST] ?: Pair(null, null)
    val (androidTestKotlinCompilation, androidTestAndroidCompilation) = compilationInfoMap[AndroidCompilation.CompilationType.INSTRUMENTED_TEST] ?: Pair(null, null)

    val mainSourceSetDependencies = sourceSetDependenciesMap[mainAndroidCompilation.defaultSourceSetName]!!.map {
      IdeDependencyCoreImpl(
        target = it,
        dependencies = null
      )
    }
    val unitTestSourceSetDependencies = unitTestAndroidCompilation?.let { sourceSetDependenciesMap[unitTestAndroidCompilation.defaultSourceSetName] }?.map {
      IdeDependencyCoreImpl(
        target = it,
        dependencies = null
      )
    }
    val androidTestSourceSetDependencies = androidTestAndroidCompilation?.let { sourceSetDependenciesMap[androidTestAndroidCompilation.defaultSourceSetName] }?.map {
      IdeDependencyCoreImpl(
        target = it,
        dependencies = null
      )
    }

    val mainKotlinCompilerOptions =
      parseCommandLineArguments<K2JVMCompilerArguments>(mainKotlinCompilation.compilerArguments ?: emptyList())

    val mainBuildInformation = IdeBuildTasksAndOutputInformationImpl(
      assembleTaskName = mainAndroidCompilation.assembleTaskName,
      assembleTaskOutputListingFile = null,
      bundleTaskName = null,
      bundleTaskOutputListingFile = null,
      apkFromBundleTaskName = null,
      apkFromBundleTaskOutputListingFile = null,
    )

    val androidProject = IdeAndroidProjectImpl(
      agpVersion = targetInfo.agpVersion,
      projectPath = IdeProjectPathImpl(
        rootBuildId = targetInfo.rootBuildId.convertAndDeduplicate(),
        buildId = targetInfo.buildId.convertAndDeduplicate(),
        projectPath = targetInfo.projectPath.deduplicate()
      ),
      buildFolder = File(targetInfo.buildDir.absolutePath).deduplicateFile(),
      projectType = IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM,
      defaultSourceProvider = IdeSourceProviderContainerImpl(
        sourceProvider = mainKotlinCompilation.declaredSourceSets.firstOrNull {
          it.name == mainAndroidCompilation.defaultSourceSetName
        }?.let { sourceSet ->
          sourceSet.extras[androidSourceSetKey]?.sourceProvider?.convert(sourceSet, targetInfo.withJava)
        },
        extraSourceProviders = listOf(
          ARTIFACT_NAME_UNIT_TEST to unitTestKotlinCompilation?.declaredSourceSets?.firstOrNull {
            it.name == unitTestAndroidCompilation?.defaultSourceSetName
          }?.let { sourceSet ->
            sourceSet.extras[androidSourceSetKey]?.sourceProvider?.convert(sourceSet, targetInfo.withJava)
          },
          ARTIFACT_NAME_ANDROID_TEST  to androidTestKotlinCompilation?.declaredSourceSets?.firstOrNull {
            it.name == androidTestAndroidCompilation?.defaultSourceSetName
          }?.let { sourceSet ->
            sourceSet.extras[androidSourceSetKey]?.sourceProvider?.convert(sourceSet, targetInfo.withJava)
          }
        ).mapNotNull { (artifactName, sourceProvider) ->
          sourceProvider?.let {
            IdeExtraSourceProviderImpl(
              artifactName = artifactName,
              sourceProvider = sourceProvider
            )
          }
        }
      ),
      multiVariantData = null,
      flavorDimensions = emptyList(),
      compileTarget = mainAndroidCompilation.mainInfo.compileSdkTarget,
      bootClasspath = targetInfo.bootClasspathList.map { it.absolutePath.deduplicate() },
      signingConfigs = listOfNotNull(
        androidTestAndroidCompilation?.instrumentedTestInfo?.signingConfig?.convert()
      ),
      aaptOptions = IdeAaptOptionsImpl(IdeAaptOptions.Namespacing.DISABLED),
      lintOptions = IdeLintOptionsImpl(), // TODO(b/269755640): support lint in the IDE
      javaCompileOptions = IdeJavaCompileOptionsImpl(
        encoding = Charset.defaultCharset().name(),
        sourceCompatibility = mainKotlinCompilerOptions.jvmTarget ?: "1.8",
        targetCompatibility = mainKotlinCompilerOptions.jvmTarget ?: "1.8",
        isCoreLibraryDesugaringEnabled = targetInfo.isCoreLibraryDesugaringEnabled,
      ),
      resourcePrefix = null,
      buildToolsVersion = targetInfo.buildToolsVersion,
      isBaseSplit = false,
      dynamicFeatures = emptyList(),
      viewBindingOptions = null,
      dependenciesInfo = null,
      groupId = targetInfo.groupId,
      namespace = mainAndroidCompilation.mainInfo.namespace,
      agpFlags = targetInfo.flags.convert(),
      variantsBuildInformation = listOf(
        IdeVariantBuildInformationImpl(
          variantName = kotlinMultiplatformAndroidVariantName,
          mainBuildInformation
        )
      ),
      lintChecksJars = targetInfo.lintChecksJarsList.convertAndDeduplicate(),
      testNamespace = androidTestAndroidCompilation?.instrumentedTestInfo?.namespace,
      isKaptEnabled = false,
      desugarLibraryConfigFiles = targetInfo.desugarLibConfigList.convertAndDeduplicate(),
      baseFeature = null,
      basicVariants = listOf(
        IdeBasicVariantImpl(
          name = kotlinMultiplatformAndroidVariantName,
          applicationId = null,
          testApplicationId = androidTestAndroidCompilation?.instrumentedTestInfo?.namespace
        )
      ),
      defaultVariantName = kotlinMultiplatformAndroidVariantName
    )

    val mainArtifact = IdeAndroidArtifactCoreImpl(
      name = IdeArtifactName.MAIN,
      compileTaskName = mainAndroidCompilation.kotlinCompileTaskName,
      assembleTaskName = mainAndroidCompilation.assembleTaskName,
      classesFolder = mainKotlinCompilation.output.classesDirs,
      variantSourceProvider = null,
      multiFlavorSourceProvider = null,
      ideSetupTaskNames = emptyList(), // For now, there is no source generation tasks
      generatedSourceFolders = emptyList(), // For now, there is no generated sourced
      isTestArtifact = false,
      compileClasspathCore = IdeDependenciesCoreDirect(
        dependencies = mainSourceSetDependencies
      ),
      runtimeClasspathCore = IdeDependenciesCoreDirect(
        dependencies = mainSourceSetDependencies
      ),
      unresolvedDependencies = emptyList(),
      applicationId = null,
      signingConfigName = null,
      isSigned = false,
      generatedResourceFolders = emptyList(),
      additionalRuntimeApks = emptyList(),
      testOptions = null,
      abiFilters = emptySet(),
      buildInformation = mainBuildInformation,
      codeShrinker = CodeShrinker.R8.takeIf { mainAndroidCompilation.mainInfo.minificationEnabled },
      privacySandboxSdkInfo = null,
      desugaredMethodsFiles = targetInfo.desugaredMethodsFilesList.convertAndDeduplicate(),
      generatedClassPaths = emptyMap(),
      bytecodeTransforms = null,
    )

    val unitTestArtifact = unitTestAndroidCompilation?.let {
      IdeJavaArtifactCoreImpl(
        name = IdeArtifactName.UNIT_TEST,
        compileTaskName = unitTestAndroidCompilation.kotlinCompileTaskName,
        assembleTaskName = unitTestAndroidCompilation.assembleTaskName,
        classesFolder = unitTestKotlinCompilation?.output?.classesDirs ?: emptyList(),
        variantSourceProvider = null,
        multiFlavorSourceProvider = null,
        ideSetupTaskNames = emptyList(), // For now, there is no source generation tasks
        generatedSourceFolders = emptyList(), // For now, there is no generated sourced
        isTestArtifact = true,
        compileClasspathCore = IdeDependenciesCoreDirect(
          dependencies = unitTestSourceSetDependencies!!
        ),
        runtimeClasspathCore = IdeDependenciesCoreDirect(
          dependencies = unitTestSourceSetDependencies
        ),
        unresolvedDependencies = emptyList(),
        mockablePlatformJar = unitTestAndroidCompilation.unitTestInfo.mockablePlatformJar.convertAndDeduplicate(),
        generatedClassPaths = emptyMap(),
        bytecodeTransforms = null,
      )
    }

    val androidTestArtifact = androidTestAndroidCompilation?.let {
      IdeAndroidArtifactCoreImpl(
        name = IdeArtifactName.ANDROID_TEST,
        compileTaskName = androidTestAndroidCompilation.kotlinCompileTaskName,
        assembleTaskName = androidTestAndroidCompilation.assembleTaskName,
        classesFolder = androidTestKotlinCompilation?.output?.classesDirs ?: emptyList(),
        variantSourceProvider = null,
        multiFlavorSourceProvider = null,
        ideSetupTaskNames = emptyList(), // For now, there is no source generation tasks
        generatedSourceFolders = emptyList(), // For now, there is no generated sourced
        isTestArtifact = true,
        compileClasspathCore = IdeDependenciesCoreDirect(
          dependencies = androidTestSourceSetDependencies!!
        ),
        runtimeClasspathCore = IdeDependenciesCoreDirect(
          dependencies = androidTestSourceSetDependencies
        ),
        unresolvedDependencies = emptyList(),
        applicationId = androidTestAndroidCompilation.instrumentedTestInfo.namespace,
        signingConfigName = androidTestAndroidCompilation.instrumentedTestInfo.signingConfig?.name,
        isSigned = androidTestAndroidCompilation.instrumentedTestInfo.signingConfig != null,
        generatedResourceFolders = emptyList(),
        additionalRuntimeApks = emptyList(),
        testOptions = targetInfo.testInfo.convert(),
        abiFilters = emptySet(),
        buildInformation = IdeBuildTasksAndOutputInformationImpl(
          assembleTaskName = androidTestAndroidCompilation.assembleTaskName,
          assembleTaskOutputListingFile = androidTestAndroidCompilation.instrumentedTestInfo.assembleTaskOutputListingFile.absolutePath.deduplicate(),
          bundleTaskName = null,
          bundleTaskOutputListingFile = null,
          apkFromBundleTaskName = null,
          apkFromBundleTaskOutputListingFile = null
        ),
        codeShrinker = mainArtifact.codeShrinker,
        privacySandboxSdkInfo = null,
        desugaredMethodsFiles = targetInfo.desugaredMethodsFilesList.convertAndDeduplicate(),
        generatedClassPaths = emptyMap(),
        bytecodeTransforms = null,
      )
    }

    val androidMainVariant = IdeVariantCoreImpl(
      name = kotlinMultiplatformAndroidVariantName,
      displayName = kotlinMultiplatformAndroidVariantName,
      mainArtifact = mainArtifact,
      unitTestArtifact = unitTestArtifact,
      androidTestArtifact = androidTestArtifact,
      testFixturesArtifact = null,
      buildType = "", // TODO(b/288062702): figure out what will this affect
      productFlavors = emptyList(),
      minSdkVersion = mainAndroidCompilation.mainInfo.minSdkVersion.convert(),
      targetSdkVersion = null,
      maxSdkVersion = mainAndroidCompilation.mainInfo.maxSdkVersion,
      versionCode = null,
      versionNameSuffix = null,
      versionNameWithSuffix = null,
      instantAppCompatible = false,
      vectorDrawablesUseSupportLibrary = false,
      resourceConfigurations = emptyList(),
      resValues = emptyMap(),
      proguardFiles = mainAndroidCompilation.mainInfo.proguardFilesList.convertAndDeduplicate(),
      consumerProguardFiles = mainAndroidCompilation.mainInfo.consumerProguardFilesList.convertAndDeduplicate(),
      manifestPlaceholders = emptyMap(),
      testInstrumentationRunner = androidTestAndroidCompilation?.instrumentedTestInfo?.testInstrumentationRunner,
      testInstrumentationRunnerArguments = androidTestAndroidCompilation?.instrumentedTestInfo?.testInstrumentationRunnerArgumentsMap?.toMap() ?: emptyMap(),
      testedTargetVariants = emptyList(),
      runTestInSeparateProcess = false,
      deprecatedPreMergedApplicationId = null,
      deprecatedPreMergedTestApplicationId = null,
      desugaredMethodsFiles = targetInfo.desugaredMethodsFilesList.convertAndDeduplicate()
    )

    return GradleAndroidModelDataImpl(
      androidSyncVersion = ourAndroidSyncVersion,
      moduleName = moduleName,
      rootDirPath = rootModulePath!!,
      androidProject = androidProject,
      selectedVariantName = kotlinMultiplatformAndroidVariantName,
      variants = listOf(androidMainVariant)
    )
  }
}