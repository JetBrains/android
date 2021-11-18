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
import com.android.ddmlib.IDevice
import com.android.ide.common.build.GenericBuiltArtifacts
import com.android.ide.common.build.GenericBuiltArtifactsLoader.loadFromFile
import com.android.tools.idea.AndroidStartupActivity
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.util.DynamicAppUtils.useSelectApksFromBundleBuilder
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
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
 * Retrieve the location of generated APK or APK from Bundle for the given run configuration.
 *
 * If the generated file is a bundle file, this method returns the location of the single APK extracted from the bundle.
 * If the generated file is a single APK, this method returns the location of the apk.
 * If the generated files are multiple APKs, this method returns the folder that contains the APKs.
 */
@Deprecated("This method supports the case of one app and one test APks only. Use ApkProviders instead.")
fun getSingleApkOrParentFolderForRunConfiguration(
  module: Module,
  configuration: AndroidRunConfigurationBase,
  isTest: Boolean,
  device: IDevice
): File? {
  val projectSystem = module.project.getProjectSystem()
  val apkProvider = projectSystem.getApkProvider(configuration) ?: return null
  val applicationIdProvider = projectSystem.getApplicationIdProvider(configuration) ?: return null
  val applicationId = if (isTest) applicationIdProvider.testPackageName else applicationIdProvider.packageName
  val apks =
    apkProvider.getApks(device).asSequence()
      .filter { info -> info.applicationId == applicationId }
      .flatMap { it.files.asSequence() }
      .map { it.apkFile }
      .distinct()
      .toList()
  return if (apks.size == 1) apks[0]
  else apks.map { it.parentFile }.singleOrNull()
}

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

fun Collection<IdeVariantBuildInformation>.variantOutputInformation(variantName: String): IdeBuildTasksAndOutputInformation? {
  return firstOrNull { it.variantName == variantName }?.buildInformation
}

fun IdeBuildTasksAndOutputInformation.getOutputListingFileOrLogError(outputType: OutputType): String? {
  return getOutputListingFile(outputType)
    .also {
      if (it == null) {
        LOG.error(Throwable("Output listing build file is not available for output type $outputType in $this"))
      }
    }
}

fun IdeBuildTasksAndOutputInformation.getOutputListingFile(outputType: OutputType) =
  when (outputType) {
    OutputType.Apk -> assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> apkFromBundleTaskOutputListingFile
    else -> bundleTaskOutputListingFile
  }

fun loadBuildOutputListingFile(listingFile: String): GenericBuiltArtifacts? {
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    return builtArtifacts
  }

  LOG.warn("Failed to read Json output file from ${listingFile}. Build may have failed.")
  return null
}

fun getBuildOutputListingFile(
  outputType: OutputType,
  variantBuildInformation: IdeBuildTasksAndOutputInformation?
): String? {
  return variantBuildInformation?.getOutputListingFile(outputType)
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
      project.getService(LastBuildOrSyncService::class.java).lastBuildOrSyncTimeStamp = System.currentTimeMillis()
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
        val service = context.project.getService(LastBuildOrSyncService::class.java)
        service.lastBuildOrSyncTimeStamp = System.currentTimeMillis()
      }
    })

    val service = project.getService(LastBuildOrSyncService::class.java)
    service.lastBuildOrSyncTimeStamp = System.currentTimeMillis()
  }
}

@TestOnly
fun emulateStartupActivityForTest(project: Project) = AndroidStartupActivity.STARTUP_ACTIVITY.findExtension(
  LastBuildOrSyncStartupActivity::class.java)?.runActivity(project, project)

data class GenericBuiltArtifactsWithTimestamp(val genericBuiltArtifacts: GenericBuiltArtifacts?, val timeStamp: Long) {
  companion object {
    @JvmStatic
    fun mostRecentNotNull(vararg items: GenericBuiltArtifactsWithTimestamp?): GenericBuiltArtifactsWithTimestamp? =
      items.filterNotNull().maxByOrNull { it.timeStamp }
  }
}
