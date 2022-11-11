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

import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_APP
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_ATOM
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_FEATURE
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_TEST
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.instantapp.InstantApps
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.util.text.nullize
import org.jetbrains.android.facet.AndroidFacet

/**
 * Application id provider for Gradle projects.
 */
class GradleApplicationIdProvider private constructor(
  private val androidFacet: AndroidFacet,
  private val forTests: Boolean,
  private val androidModel: GradleAndroidModel,
  private val basicVariant: IdeBasicVariant,
  private val variant: IdeVariant?
) : ApplicationIdProvider {

  companion object {
    @JvmStatic
    fun create(
      androidFacet: AndroidFacet,
      forTests: Boolean,
      androidModel: GradleAndroidModel,
      basicVariant: IdeBasicVariant,
      variant: IdeVariant
    ): GradleApplicationIdProvider {
      require(basicVariant.name == variant.name) { "variant.name(${variant.name}) != basicVariant.name(${basicVariant.name})" }
      return GradleApplicationIdProvider(androidFacet, forTests, androidModel, basicVariant, variant)
    }

    /**
     * Create a limited [ApplicationIdProvider]
     *
     * which does not expect module types that require base module resolution like dynamic features or test only modules.
     */
    @JvmStatic
    fun createForBaseModule(
      androidFacet: AndroidFacet,
      androidModel: GradleAndroidModel,
      basicVariant: IdeBasicVariant,
    ): GradleApplicationIdProvider {
      return GradleApplicationIdProvider(androidFacet, forTests = false, androidModel, basicVariant, variant = null)
    }
  }

  /** Returns the application ID (manifest package attribute) of the main APK - the app to launch, or the app under test. */
  override fun getPackageName(): String {

    fun getBaseFeatureApplicationIdProvider(baseFeatureGetter: (AndroidFacet) -> Module?): ApplicationIdProvider {
      val baseModule =
        baseFeatureGetter(androidFacet) ?: throw ApkProvisionException("Can't get base-app module for ${androidFacet.module.name}")
      val baseFacet = AndroidFacet.getInstance(baseModule)
        ?: throw ApkProvisionException("Can't get base-app Android Facet for ${androidFacet.module.name}")
      val baseModel =
        GradleAndroidModel.get(baseFacet) ?: throw ApkProvisionException("Can't get base-app Android Facet for ${androidFacet.module.name}")
      return createForBaseModule(baseFacet, baseModel, baseModel.selectedBasicVariant)
    }
    // Android library project doesn't produce APK except for instrumentation tests. And for instrumentation test,
    // AGP creates instrumentation APK only. Both test code and library code will be packaged into an instrumentation APK.
    // This is called self-instrumenting test: https://source.android.com/compatibility/tests/development/instr-self-e2e
    // For this reason, this method should return test package name for Android library project.
    val projectType = androidModel.androidProject.projectType
    val applicationId = when (projectType) {
      PROJECT_TYPE_LIBRARY -> testPackageName
      PROJECT_TYPE_TEST ->
        getTestProjectTargetApplicationIdProvider(
          variant ?: throw ApkProvisionException("Cannot resolve test only project target")
        )?.packageName

      PROJECT_TYPE_INSTANTAPP -> getBaseFeatureApplicationIdProvider(InstantApps::findBaseFeature).packageName
      PROJECT_TYPE_DYNAMIC_FEATURE -> getBaseFeatureApplicationIdProvider { DynamicAppUtils.getBaseFeature(it.holderModule) }.packageName
      PROJECT_TYPE_APP -> basicVariant.applicationId.nullize()
      PROJECT_TYPE_ATOM -> null
      PROJECT_TYPE_FEATURE -> if (androidModel.androidProject.isBaseSplit) androidModel.selectedVariant.mainArtifact.applicationId.nullize()
      else getBaseFeatureApplicationIdProvider(InstantApps::findBaseFeature).packageName
    }
    if (applicationId == null) {
      val errorMessage = "Could not get applicationId for ${androidFacet.module.name}. Project type: $projectType"
      logger.error(errorMessage, Throwable())
      throw ApkProvisionException(errorMessage)
    }
    return applicationId
  }

  /* Returns the application ID (manifest package attribute) of the test APK, or null if none. */
  override fun getTestPackageName(): String? {
    if (!forTests) return null

    val result =
      (if (androidModel.androidProject.projectType === PROJECT_TYPE_TEST) basicVariant.applicationId else basicVariant.testApplicationId)
        .nullize()

    return result
      ?: throw ApkProvisionException("[${androidFacet.module.name}] Unable to obtain test package.")
        .also { logger.error(it) }
  }

  // There is no tested variant or more than one (what should never happen currently) and then we can't get package name.

  // Note: Even though our project is a test module project we request an application id provider for the target module which is
  //       not a test module and the return provider is supposed to be used to obtain the non-test application id only and hence
  //       we create a `GradleApplicationIdProvider` instance in `forTests = false` mode.
  private fun getTestProjectTargetApplicationIdProvider(variant: IdeVariant): ApplicationIdProvider? {
    val testedTargetVariant =
      variant.testedTargetVariants.singleOrNull()
        ?: return null // There is no tested variant or more than one (what should never happen currently) and then we can't get package name.

    val targetFacet =
      androidFacet
        .holderModule
        .getGradleProjectPath()
        ?.let { GradleHolderProjectPath(it.buildRoot, testedTargetVariant.targetProjectPath) }
        ?.resolveIn(androidFacet.module.project)
        ?.androidFacet
        ?: return null

    val targetModel = GradleAndroidModel.get(targetFacet) ?: return null
    val targetBasicVariant = targetModel.findBasicVariantByName(testedTargetVariant.targetVariant) ?: return null

    // Note: Even though our project is a test module project we request an application id provider for the target module which is
    //       not a test module and the return provider is supposed to be used to obtain the non-test application id only and hence
    //       we create a `GradleApplicationIdProvider` instance in `forTests = false` mode.
    return createForBaseModule(targetFacet, targetModel, targetBasicVariant)
  }
}

/**
 * Default suffix for test packages (as added by Android Gradle plugin).
 */
private val logger = Logger.getInstance(GradleApplicationIdProvider::class.java)
