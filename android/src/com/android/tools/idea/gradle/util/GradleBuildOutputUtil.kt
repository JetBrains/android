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
@file:JvmName("GradleBuildOutputUtil")

package com.android.tools.idea.gradle.util

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.build.GenericBuiltArtifacts
import com.android.ide.common.build.GenericBuiltArtifactsLoader.loadFromFile
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.AndroidStartupActivity
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.DynamicAppUtils.useSelectApksFromBundleBuilder
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import java.io.File

/**
 * Utility methods to find APK/Bundle output file or folder.
 */

private val LOG: Logger get() = Logger.getInstance("GradleBuildOutputUtil.kt")

enum class OutputType {
  Apk,
  ApkFromBundle,
  Bundle
}

/**
 * Find the output listing file to use to locate the generated build output.
 *
 * This method finds the output file from [IdeVariantBuildInformation] for non-test variants, from [IdeAndroidArtifact] for test variants.
 * This is because [IdeVariantBuildInformation] only contains non-test variants.
 * The related fields in [IdeAndroidArtifact] are subject to removal, after test variants being added to [IdeVariantBuildInformation] in the future.
 */
fun getOutputListingFile(androidModel: AndroidModuleModel, variantName: String, outputType: OutputType, isTest: Boolean): String? {
  return if (isTest) {
    androidModel.selectedVariant.androidTestArtifact?.let { getOutputListingFileFromAndroidArtifact(it, outputType) }
  }
  else {
    getOutputListingFileFromVariantBuildInformation(androidModel, variantName, outputType)
  }
}

/**
 * Retrieve the location of generated APK or Bundle for the given run configuration.
 *
 * This method finds the location from build output listing file if it is supported, falls back to
 * ArtifactOutput model otherwise.
 *
 * If the generated file is a bundle file, this method returns the location of bundle.
 * If the generated file is a single APK, this method returns the location of the apk.
 * If the generated files are multiple APKs, this method returns the folder that contains the APKs.
 */
fun getApkForRunConfiguration(module: Module, configuration: AndroidRunConfigurationBase, isTest: Boolean): File? {
  val androidModel = AndroidModuleModel.get(module)
  androidModel ?: return null
  if (androidModel.features.isBuildOutputFileSupported) {
    // Get output from listing file.
    return getOutputFileOrFolderFromListingFile(androidModel, androidModel.selectedVariant.name, getOutputType(module, configuration),
                                                isTest)
  }
  else {
    // Get output from deprecated ArtifactOutput model.
    val artifact = if (isTest) {
      androidModel.selectedVariant.androidTestArtifact
    }
    else {
      androidModel.selectedVariant.mainArtifact
    }
    return artifact?.outputs?.firstOrNull()?.outputFile
  }
}

/**
 * Retrieve the location of generated APK or Bundle for the given variant.
 *
 * This method returns null if build output listing file is not supported.
 *
 * If the generated file is a bundle file, this method returns the location of bundle.
 * If the generated file is a single APK, this method returns the location of the apk.
 * If the generated files are multiple APKs, this method returns the folder that contains the APKs.
 */
fun getOutputFileOrFolderFromListingFile(androidModel: AndroidModuleModel,
                                         variantName: String,
                                         outputType: OutputType,
                                         isTest: Boolean): File? {
  val listingFile = getOutputListingFile(androidModel, variantName, outputType, isTest)
  if (listingFile != null) {
    return getOutputFileOrFolderFromListingFile(listingFile)
  }
  LOG.warn("Could not find output listing file. Build may have failed.")
  return null
}

@VisibleForTesting
fun getOutputFileOrFolderFromListingFile(listingFile: String): File? {
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    val artifacts = builtArtifacts.elements
    if (!artifacts.isEmpty()) {
      val output = File(artifacts.iterator().next().outputFile)
      return if (artifacts.size > 1) output.parentFile else output
    }
  }
  LOG.warn("Failed to read Json output file from ${listingFile}. Build may have failed.")
  return null
}

private fun getOutputType(module: Module, configuration: AndroidRunConfigurationBase): OutputType {
  return if (useSelectApksFromBundleBuilder(module, configuration, null)) {
    OutputType.ApkFromBundle
  }
  else {
    OutputType.Apk
  }
}

/**
 * Find the output listing file to use from [IdeVariantBuildInformation].
 */
private fun getOutputListingFileFromVariantBuildInformation(androidModel: AndroidModuleModel,
                                                            variantName: String,
                                                            outputType: OutputType): String? {
  val buildInformation = androidModel.androidProject.variantsBuildInformation.firstOrNull {
    it.variantName == variantName
  }
  buildInformation ?: return null
  return when (outputType) {
    OutputType.Apk -> buildInformation.buildInformation.assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> buildInformation.buildInformation.apkFromBundleTaskOutputListingFile
    else -> { // OutputType.Bundle
      buildInformation.buildInformation.bundleTaskOutputListingFile
    }
  }
}

/**
 * Find the output listing file to use from [IdeAndroidArtifact].
 *
 * TODO: replace this method with [getOutputListingFileFromVariantBuildInformation] when [IdeVariantBuildInformation] contains test variants.
 */
private fun getOutputListingFileFromAndroidArtifact(testArtifact: IdeAndroidArtifact, outputType: OutputType): String? {
  return when (outputType) {
    OutputType.Apk -> testArtifact.buildInformation.assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> testArtifact.buildInformation.apkFromBundleTaskOutputListingFile
    else -> { // OutputType.Bundle
      testArtifact.buildInformation.bundleTaskOutputListingFile
    }
  }
}

fun getGenericBuiltArtifact(androidModel: AndroidModuleModel, variantName: String) : GenericBuiltArtifacts? {
  val listingFile = getOutputListingFileFromVariantBuildInformation(androidModel, variantName, OutputType.Apk) ?: return null
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    return builtArtifacts
  }

  LOG.warn("Failed to read Json output file from ${listingFile}. Build may have failed.")
  return null
}

class LastBuildOrSyncService {
  // Do not set outside of tests or this class!!
  @Volatile var lastBuildOrSyncTimeStamp = -1L
    @VisibleForTesting set
}

internal class LastBuildOrSyncListener: ExternalSystemTaskNotificationListenerAdapter() {
  override fun onEnd(id: ExternalSystemTaskId) {
    id.findProject()?.also { project ->
      ServiceManager.getService(project, LastBuildOrSyncService::class.java).lastBuildOrSyncTimeStamp = System.currentTimeMillis()
    }
  }
}

/**
 * This should not really be used, but we currently do not use the intellij build infra and therefore do not get
 * events for build. If we move to using this and the events from running tasks trigger the GenericBuiltArtifactsCacheCleaner then
 * this should be removed.
 */
internal class LastBuildOrSyncStartupActivity : AndroidStartupActivity {
  @UiThread
  override fun runActivity(project: Project, disposable: Disposable) {
    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        if (context == null) return
        val service = ServiceManager.getService(context.project, LastBuildOrSyncService::class.java)
        service.lastBuildOrSyncTimeStamp = System.currentTimeMillis()
      }
    })

    val service = ServiceManager.getService(project, LastBuildOrSyncService::class.java)
    service.lastBuildOrSyncTimeStamp = System.currentTimeMillis()
  }
}

@TestOnly
fun emulateStartupActivityForTest(project: Project) = AndroidStartupActivity.STARTUP_ACTIVITY.findExtension(
  LastBuildOrSyncStartupActivity::class.java)?.runActivity(project, project)

data class GenericBuiltArtifactsWithTimestamp(val genericBuiltArtifacts: GenericBuiltArtifacts?, val timeStamp : Long)
