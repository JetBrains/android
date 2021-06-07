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
import com.android.tools.idea.AndroidStartupActivity
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
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
 * Retrieve the location of generated APK or Bundle for the given run configuration.
 *
 * This method finds the location from build output listing file if it is supported, falls back to
 * ArtifactOutput model otherwise.
 *
 * If the generated file is a bundle file, this method returns the location of bundle.
 * If the generated file is a single APK, this method returns the location of the apk.
 * If the generated files are multiple APKs, this method returns the folder that contains the APKs.
 */
@Deprecated("This method supports a limited set of AGP versions and devices only. Use ApkProviders instead.")
fun getApkForRunConfiguration(module: Module, configuration: AndroidRunConfigurationBase, isTest: Boolean): File? {
  val androidModel = AndroidModuleModel.get(module) ?: return null
  val selectedVariant = androidModel.selectedVariant
  val artifact =
    if (isTest) selectedVariant.androidTestArtifact ?: return null
    else selectedVariant.mainArtifact

  return if (androidModel.features.isBuildOutputFileSupported)
    artifact.buildInformation
      .getOutputFilesFromListingFile(getOutputType(module, configuration))
      .let {
        if (it.size > 1) it.first().parentFile
        else it.firstOrNull()
      }
  else artifact.outputs.firstOrNull()?.outputFile
}

fun IdeBuildTasksAndOutputInformation.getOutputFilesFromListingFile(
  outputType: OutputType
): List<File> {
  val listingFile = getOutputListingFile(outputType)
  if (listingFile != null) {
    return getOutputFilesFromListingFile(listingFile)
  }
  LOG.warn("Could not find output listing file. Build may have failed.")
  return emptyList()
}

@VisibleForTesting
fun getOutputFilesFromListingFile(listingFile: String): List<File> {
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    val items = builtArtifacts.elements.map { File(it.outputFile) }
    // NOTE: These strings come from com.android.build.api.artifact.ArtifactKind.DIRECTORY and alike.
    return if (builtArtifacts.elementType == null || builtArtifacts.elementType == "Directory") {
      items.flatMap { fileOrDirectory ->
        runCatching {
          if (fileOrDirectory.isDirectory) fileOrDirectory.listFiles()?.toList().orEmpty()
          else listOf(fileOrDirectory)
        }
          .getOrElse { e ->
            LOG.warn("Error reading list of APK files from build output directory '$fileOrDirectory'.", e)
            emptyList()
          }
      }
    }
    else {
      items
    }
  }
  LOG.warn("Failed to read Json output file from ${listingFile}. Build may have failed.")
  return emptyList()
}

private fun getOutputType(module: Module, configuration: AndroidRunConfigurationBase): OutputType {
  return if (useSelectApksFromBundleBuilder(module, configuration, null)) {
    OutputType.ApkFromBundle
  }
  else {
    OutputType.Apk
  }
}

private fun Collection<IdeVariantBuildInformation>.variantOutputInformation(variantName: String): IdeBuildTasksAndOutputInformation? {
  return firstOrNull { it.variantName == variantName }?.buildInformation
}

internal fun IdeBuildTasksAndOutputInformation.getOutputListingFile(outputType: OutputType): String? {
  return when (outputType) {
    OutputType.Apk -> assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> apkFromBundleTaskOutputListingFile
    else -> bundleTaskOutputListingFile
  }
}

fun getGenericBuiltArtifact(androidModel: AndroidModuleModel, variantName: String): GenericBuiltArtifacts? {
  val variantBuildInformation = androidModel.androidProject.variantsBuildInformation.variantOutputInformation(variantName) ?: return null
  val listingFile = variantBuildInformation.getOutputListingFile(OutputType.Apk) ?: return null
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    return builtArtifacts
  }

  LOG.warn("Failed to read Json output file from ${listingFile}. Build may have failed.")
  return null
}

class LastBuildOrSyncService {
  // Do not set outside of tests or this class!!
  @Volatile
  var lastBuildOrSyncTimeStamp = -1L
    @VisibleForTesting set
}

internal class LastBuildOrSyncListener : ExternalSystemTaskNotificationListenerAdapter() {
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

data class GenericBuiltArtifactsWithTimestamp(val genericBuiltArtifacts: GenericBuiltArtifacts?, val timeStamp: Long)
