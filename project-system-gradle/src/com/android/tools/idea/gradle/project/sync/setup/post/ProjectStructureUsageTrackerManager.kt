/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.model.UsedFeatureRawText
import com.android.tools.idea.model.queryUsedFeaturesFromManifestIndex
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleAndroidModule
import com.google.wireless.android.sdk.stats.GradleBuildDetails
import com.google.wireless.android.sdk.stats.GradleLibrary
import com.google.wireless.android.sdk.stats.GradleModule
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManagerEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Ref
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.android.dom.manifest.UsesFeature
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future

class ProjectStructureUsageTrackerManager(private val project: Project) {

  private val operationsStates = mutableListOf<Future<*>>()

  fun trackProjectStructure() {
    val futureTaskOperation = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        doTrackProjectStructure()
      } catch(e: ProcessCanceledException) {
        throw e
      } catch (e: Throwable) {
        // Any errors in project tracking should not be displayed to the user.
        LOG.warn("Failed to track project structure", e)
      }
    }
    if (ApplicationManager.getApplication().isUnitTestMode) {
      operationsStates.removeIf { it.isDone }
      operationsStates.add(futureTaskOperation)
    }
  }

  private fun doTrackProjectStructure() {
    val allModules = ModuleManager
      .getInstance(project)
      .modules
      .filter { it.getGradleProjectPath() != null }

    fun countHolderModules(): Long {
      return allModules.asSequence().filter { it.getGradleProjectPath() is GradleHolderProjectPath }.count().toLong()
    }

    fun countExternalLibraries(): Long {
      val allLibraries = hashSetOf<Library>()
      allModules.asSequence()
        .map { ModuleRootManagerEx.getInstanceEx(it) }
        .forEach { module ->
          module
            .orderEntries()
            .withoutSdk()
            .withoutModuleSourceEntries()
            .withoutDepModules()
            .librariesOnly()
            .forEachLibrary {
              allLibraries.add(it)
              true // Continue processing.
            }
        }
      return allLibraries.size.toLong()
    }

    var appModel: GradleAndroidModel? = null
    var libModel: GradleAndroidModel? = null
    var appCount = 0
    var libCount = 0
    var dynamicFeatureCount = 0
    var testCount = 0
    var kmpCount = 0
    val gradleLibraries: MutableList<GradleLibrary> = ArrayList()
    for (facet in project.getAndroidFacets()) {
      val androidModel = GradleAndroidModel.get(facet)
      if (androidModel != null) {
        when (androidModel.androidProject.projectType) {
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> {
            libModel = androidModel
            libCount++
          }

          IdeAndroidProjectType.PROJECT_TYPE_APP -> {
            appModel = androidModel
            appCount++
            val gradleLibrary = trackExternalDependenciesInAndroidApp(androidModel)
            gradleLibraries.add(gradleLibrary)
          }

          IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> dynamicFeatureCount++
          IdeAndroidProjectType.PROJECT_TYPE_TEST -> testCount++
          IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM -> kmpCount++
          IdeAndroidProjectType.PROJECT_TYPE_ATOM -> Unit
          IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> Unit
          IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> Unit
        }
      }
    }

    // Ideally we would like to get data from an "app" module, but if the project does not have one (which would be unusual, we can use
    // an Android library one.)
    val model = appModel ?: libModel ?: return
    val gradleAndroidModules: MutableList<GradleAndroidModule> = ArrayList()
    val gradleNativeAndroidModules: MutableList<GradleNativeAndroidModule> = ArrayList()
    val appId = AnonymizerUtil.anonymizeUtf8(model.applicationId)
    val androidProject = model.androidProject
    var gradleVersionString = GradleVersions.getInstance().getGradleVersion(project)?.version
    if (gradleVersionString == null) {
      gradleVersionString = "0.0.0"
    }

    val gradleModule = GradleModule.newBuilder()
      .setTotalModuleCount(countHolderModules())
      .setAppModuleCount(appCount.toLong())
      .setLibModuleCount(libCount.toLong())
      .setDynamicFeatureModuleCount(dynamicFeatureCount.toLong())
      .setTestModuleCount(testCount.toLong())
      .setKotlinMultiplatformModuleCount(kmpCount.toLong())
      .build()

    for (facet in project.getAndroidFacets()) {
      val androidModel = GradleAndroidModel.get(facet)
      if (androidModel != null) {
        val moduleAndroidProject = androidModel.androidProject
        val androidModule = GradleAndroidModule.newBuilder()
        androidModule.setModuleName(AnonymizerUtil.anonymizeUtf8(facet.holderModule.name))
          .setSigningConfigCount(moduleAndroidProject.signingConfigs.size.toLong())
          .setIsLibrary(moduleAndroidProject.projectType === IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)
          .setBuildTypeCount(androidModel.buildTypeNames.size.toLong())
          .setFlavorCount(androidModel.productFlavorNames.size.toLong()).flavorDimension =
          moduleAndroidProject.flavorDimensions.size.toLong()
        if (!androidModule.isLibrary && isWatchHardwareRequired(facet)) {  // Ignore library modules to query Manifest Index less.
          androidModule.requiredHardware = UsesFeature.HARDWARE_TYPE_WATCH
        }
        gradleAndroidModules.add(androidModule.build())
      }
      var shouldReportNative = false
      val ndkModel = NdkModuleModel.get(facet.holderModule)
      var buildSystemType = NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE
      var moduleName = ""
      var ndkVersion = ""
      if (ndkModel != null) {
        shouldReportNative = true
        if (ndkModel.features.isBuildSystemNameSupported) {
          for (buildSystem in ndkModel.buildSystems) {
            buildSystemType = stringToBuildSystemType(buildSystem)
          }
        } else {
          buildSystemType = NativeBuildSystemType.GRADLE_EXPERIMENTAL
        }
        moduleName = AnonymizerUtil.anonymizeUtf8(ndkModel.moduleName)
        ndkVersion = ndkModel.ndkVersion
      }
      if (shouldReportNative) {
        val nativeModule = GradleNativeAndroidModule.newBuilder()
        nativeModule
          .setModuleName(moduleName)
          .setBuildSystemType(buildSystemType)
          .setNdkVersion(ndkVersion)
        gradleNativeAndroidModules.add(nativeModule.build())
      }
    }
    val gradleBuild = GradleBuildDetails
      .newBuilder()
      .setAppId(appId)
      .setAndroidPluginVersion(androidProject.agpVersion)
      .setGradleVersion(gradleVersionString)
      .addAllLibraries(gradleLibraries)
      .addModules(gradleModule)
      .addAllAndroidModules(gradleAndroidModules)
      .addAllNativeAndroidModules(gradleNativeAndroidModules)
      .setModuleCount(countHolderModules())
      .setLibCount(countExternalLibraries())

    val event =
      AndroidStudioEvent
        .newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.GRADLE)
        .setKind(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS)
        .setGradleBuildDetails(gradleBuild)

    UsageTracker.log(event.withProjectId(project))
  }

  private fun isWatchHardwareRequired(facet: AndroidFacet): Boolean {
    try {
      return DumbService.getInstance(project).runReadActionInSmartMode<Boolean> {
        val usedFeatures = facet.queryUsedFeaturesFromManifestIndex()
        (usedFeatures.contains(UsedFeatureRawText(UsesFeature.HARDWARE_TYPE_WATCH, null))
          || usedFeatures.contains(UsedFeatureRawText(UsesFeature.HARDWARE_TYPE_WATCH, "true")))
      }
    } catch (e : ProcessCanceledException) {
      throw e
    } catch (e: Throwable) {
      LOG.warn("Manifest Index could not be queried", e)
    }
    return false
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectStructureUsageTrackerManager {
      return project.getService(ProjectStructureUsageTrackerManager::class.java)
    }

    private val LOG = Logger.getInstance(
      ProjectStructureUsageTrackerSyncListener::class.java
    )

    @VisibleForTesting
    @JvmStatic
    fun stringToBuildSystemType(buildSystem: String): NativeBuildSystemType {
      return when (buildSystem) {
        "ndkBuild" -> NativeBuildSystemType.NDK_BUILD
        "cmake" -> NativeBuildSystemType.CMAKE
        "ndkCompile" -> NativeBuildSystemType.NDK_COMPILE
        "gradle" -> NativeBuildSystemType.GRADLE_EXPERIMENTAL
        else -> NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE
      }
    }

    private fun trackExternalDependenciesInAndroidApp(model: GradleAndroidModel): GradleLibrary {
      // Use Ref because lambda function argument to forEachVariant only works with final variables.
      val chosenVariant = Ref<IdeVariant?>()
      // We want to track the "release" variants.
      model.variants.forEach { variant: IdeVariant ->
        if ("release" == variant.buildType) {
          chosenVariant.set(variant)
        }
      }

      // If we could not find a "release" variant, pick the selected one.
      if (chosenVariant.get() == null) {
        chosenVariant.set(model.selectedVariant)
      }
      val dependencies = chosenVariant.get()!!.mainArtifact.compileClasspath
      return GradleLibrary.newBuilder()
        .setAarDependencyCount(dependencies.libraries.count { it is IdeAndroidLibrary }.toLong())
        .setJarDependencyCount(dependencies.libraries.count { it is IdeJavaLibrary }.toLong())
        .build()
    }
  }

  @TestOnly
  @Throws(Exception::class)
  fun consumeBulkOperationsState(stateConsumer: (Future<*>) -> Unit) {
    ThreadingAssertions.assertEventDispatchThread()
    assert(ApplicationManager.getApplication().isUnitTestMode)
    for (operationsState in operationsStates) {
      stateConsumer.invoke(operationsState)
    }
  }
}