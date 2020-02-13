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

import com.android.ide.common.build.GenericBuiltArtifactsLoader.loadFromFile
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.DynamicAppUtils.useSelectApksFromBundleBuilder
import com.android.tools.idea.log.LogWrapper
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import java.io.File

/**
 * Utility methods to find APK/Bundle output file or folder.
 */

private val LOG: Logger get() = logger(::LOG)

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
fun getApkForRunConfiguration(module: Module, configuration: RunConfiguration, isTest: Boolean): File? {
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
    @Suppress("DEPRECATION")
    return artifact?.outputs?.firstOrNull()?.mainOutputFile?.outputFile
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

private fun getOutputType(module: Module, configuration: RunConfiguration): OutputType {
  return if (useSelectApksFromBundleBuilder(module, configuration, listOf())) {
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
    OutputType.Apk -> buildInformation.assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> buildInformation.apkFromBundleTaskOutputListingFile
    else -> { // OutputType.Bundle
      buildInformation.bundleTaskOutputListingFile
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
    OutputType.Apk -> testArtifact.assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> testArtifact.apkFromBundleTaskOutputListingFile
    else -> { // OutputType.Bundle
      testArtifact.bundleTaskOutputListingFile
    }
  }
}