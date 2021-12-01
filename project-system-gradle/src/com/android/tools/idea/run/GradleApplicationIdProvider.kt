/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_APP
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_ATOM
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_FEATURE
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_TEST
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.text.nullize
import org.jetbrains.android.facet.AndroidFacet

/**
 * Application id provider for Gradle projects.
 */
class GradleApplicationIdProvider(
  private val androidFacet: AndroidFacet,
  private val forTests: Boolean,
  private val androidModel: GradleAndroidModel,
  private val variant: IdeVariant
) : ApplicationIdProvider {

  override fun getPackageName(): String {
    // Android library project doesn't produce APK except for instrumentation tests. And for instrumentation test,
    // AGP creates instrumentation APK only. Both test code and library code will be packaged into an instrumentation APK.
    // This is called self-instrumenting test: https://source.android.com/compatibility/tests/development/instr-self-e2e
    // For this reason, this method should return test package name for Android library project.
    val projectType = androidModel.androidProject.projectType
    val applicationId = when (projectType) {
      PROJECT_TYPE_LIBRARY -> testPackageName
      PROJECT_TYPE_TEST -> getTargetApplicationIdProvider()?.packageName
      PROJECT_TYPE_INSTANTAPP -> tryToGetInstantAppApplicationId()
      PROJECT_TYPE_DYNAMIC_FEATURE -> tryToGetDynamicFeatureApplicationId()
      PROJECT_TYPE_APP -> AndroidModuleInfo.getInstance(androidFacet).getPackage().nullize()
      PROJECT_TYPE_ATOM -> null
      PROJECT_TYPE_FEATURE -> null
    }
    if (applicationId == null) {
      logger.warn("Could not get applicationId for ${androidFacet.module.name}. Project type: $projectType")
    }
    return applicationId ?: getApplicationIdFromModelOrManifest(androidFacet)
  }

  private fun tryToGetDynamicFeatureApplicationId(): String? {
    if (androidModel.androidProject.projectType !== PROJECT_TYPE_DYNAMIC_FEATURE) error("PROJECT_TYPE_DYNAMIC_FEATURE expected")
    val baseAppModule = DynamicAppUtils.getBaseFeature(androidFacet.module)
                        ?: return null.also { logger.warn("Can't get base-app module for ${androidFacet.module.name}") }
    val baseAppFacet = baseAppModule.androidFacet
                       ?: return null.also { logger.warn("Can't get base-app Android Facet for ${androidFacet.module.name}") }
    return getApplicationIdFromModelOrManifest(baseAppFacet)
  }

  private fun tryToGetInstantAppApplicationId(): String {
    if (androidModel.androidProject.projectType !== PROJECT_TYPE_INSTANTAPP) error("PROJECT_TYPE_INSTANTAPP expected")
    return getApplicationIdFromModelOrManifest(androidFacet)
  }

  override fun getTestPackageName(): String? {
    if (!forTests) return null

    if (androidModel.features.isBuildOutputFileSupported) {
      val artifactForAndroidTest = getArtifactForAndroidTest() ?: return null
      androidModel.getApplicationIdUsingCache(artifactForAndroidTest.buildInformation)
        .takeUnless { it == AndroidModel.UNINITIALIZED_APPLICATION_ID }
        ?.let { return it }
    }

    val projectType = androidModel.androidProject.projectType
    if (projectType === PROJECT_TYPE_TEST) {
      return variant.testApplicationId.nullize()
             ?: androidFacet.getModuleSystem().getPackageName().nullize()
             ?: throw ApkProvisionException("[" + androidFacet.module.name + "] Unable to obtain test package.")
    }

    // This is a Gradle project, there must be an AndroidGradleModel, but to avoid NPE we gracefully handle a null androidModel.
    // In the case of Gradle projects, either the merged flavor provides a test package name,
    // or we just append ".test" to the source package name.
    variant.testApplicationId?.let { return it }

    return when (projectType) {
      PROJECT_TYPE_DYNAMIC_FEATURE, PROJECT_TYPE_LIBRARY -> getApplicationIdFromModelOrManifest(androidFacet) + DEFAULT_TEST_PACKAGE_SUFFIX
      else -> packageName + DEFAULT_TEST_PACKAGE_SUFFIX
    }
  }

  // There is no tested variant or more than one (what should never happen currently) and then we can't get package name.

  // Note: Even though our project is a test module project we request an application id provider for the target module which is
  //       not a test module and the return provider is supposed to be used to obtain the non-test application id only and hence
  //       we create a `GradleApplicationIdProvider` instance in `forTests = false` mode.
  private fun getTargetApplicationIdProvider(): ApplicationIdProvider? {
    val testedTargetVariant =
      variant.testedTargetVariants.singleOrNull()
      ?: return null // There is no tested variant or more than one (what should never happen currently) and then we can't get package name.

    val targetFacet =
      findModuleByGradlePath(androidFacet.module.project, testedTargetVariant.targetProjectPath)?.androidFacet
      ?: return null

    val targetModel = GradleAndroidModel.get(targetFacet) ?: return null
    val targetVariant = targetModel.variants.find { it.name == testedTargetVariant.targetVariant } ?: return null

    // Note: Even though our project is a test module project we request an application id provider for the target module which is
    //       not a test module and the return provider is supposed to be used to obtain the non-test application id only and hence
    //       we create a `GradleApplicationIdProvider` instance in `forTests = false` mode.
    return GradleApplicationIdProvider(targetFacet, forTests = false, targetModel, targetVariant)
  }

  private fun getApplicationIdFromModelOrManifest(facet: AndroidFacet): String {
    return AndroidModuleInfo.getInstance(facet).getPackage().nullize()
           ?: throw ApkProvisionException("[" + androidFacet.module.name + "] Unable to obtain main package from manifest.")
  }

  private fun getArtifactForAndroidTest(): IdeAndroidArtifact? =
    if (androidModel.androidProject.projectType === PROJECT_TYPE_TEST) variant.mainArtifact else variant.androidTestArtifact

}

/**
 * Default suffix for test packages (as added by Android Gradle plugin).
 */
private const val DEFAULT_TEST_PACKAGE_SUFFIX = ".test"
private val logger = Logger.getInstance(GradleApplicationIdProvider::class.java)
