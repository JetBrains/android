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
@file:JvmName("GradleWrapperImportCheck")
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.RESOLVER_LOG
import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.gradle.util.GradleWrapper.GRADLEW_PROPERTIES_PATH
import com.android.tools.idea.gradle.util.PersistentSHA256Checksums
import com.android.tools.idea.util.PropertiesFiles
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_DISTRIBUTIONSHA256SUM_REMOVED_FROM_WRAPPER
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_SHA_256_SUM
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.CompletableFuture

class InvalidGradleWrapperException(val wrapper: GradleWrapper) : AndroidSyncException()

/**
 * Validates that the state of Gradle wrapper before project import states.
 *
 *
 * If the validation fails then a [InvalidGradleWrapperException] will be thrown.
 * This will be handled in the [GradleWrapperImportChecker] to create a presentable
 * error to the user with any quick fixes that may be useful.
 */
fun validateGradleWrapper(projectPath: String) {
  val gradleWrapper = findGradleWrapper(projectPath) ?: return  // No properties file exists, no validation can be done

  try {
    if (gradleWrapper.distributionSha256Sum == null) return // No validation required

    // Perform the validation
    if (validateChecksums(gradleWrapper)) return
  } catch (exception: IOException) {
    RESOLVER_LOG.warn(exception)
  }

  // We failed, throw an exception to be caught by the issue checker
  throw InvalidGradleWrapperException(gradleWrapper)
}

/**
 * Validates the checksum for the given [GradleWrapper], returns true if the checksum is valid,
 * false otherwise.
 */
@Throws(IOException::class)
private fun validateChecksums(wrapper: GradleWrapper) : Boolean {
  val urlString = wrapper.distributionUrl ?: return false // Do not continue, SHA256 is defined but not the URL
  if (URL(urlString).protocol.equals("file", true)) return true // Checksum is not used for file's url
  val checksums = PersistentSHA256Checksums.getInstance()
  return checksums.isChecksumStored(wrapper.distributionUrl, wrapper.distributionSha256Sum)
}

private class GradleWrapperImportChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    if (issueData.error !is InvalidGradleWrapperException) return null
    val wrapper = (issueData.error as InvalidGradleWrapperException).wrapper

    updateUsageTracker(issueData.projectPath, AndroidStudioEvent.GradleSyncFailure.JDK8_REQUIRED)


    val message = """
      It is not fully supported to define $DISTRIBUTION_SHA_256_SUM in $GRADLEW_PROPERTIES_PATH.
      Using an incorrect value may freeze or crash Android Studio.
      Please manually verify or remove this property from all of included projects if applicable.
      For more details, see https://github.com/gradle/gradle/issues/9361.
    """.trimIndent()

    return BuildIssueComposer(message).apply {
      try {
        val distUrl = wrapper.distributionUrl
        val distSHA = wrapper.distributionSha256Sum
        if (!distUrl.isNullOrBlank() && !distSHA.isNullOrBlank()) {
          addQuickFix(ConfirmSHA256FromGradleWrapperQuickFix(distSHA, distUrl))
        }
        addQuickFix(RemoveSHA256FromGradleWrapperQuickFix())
        addQuickFix("Open Gradle wrapper properties", OpenFileQuickFix(wrapper.propertiesFilePath.toPath(), null))
      } catch (e: IOException) {
        Logger.getInstance(GradleWrapperImportChecker::class.java).warn(e)
      }
    }.composeBuildIssue()
  }
}

private class ConfirmSHA256FromGradleWrapperQuickFix(
  val distributionSHA: String,
  val distributionUrl: String
) : DescribedBuildIssueQuickFix {
  override val description: String = "Use \"${distributionSHA.take(9)}${if (distributionSHA.length > 9) "..." else ""}\"" +
                                     " as checksum for $distributionUrl and sync project"
  override val id: String = "confirm.SHA256.from.gradle.wrapper"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    // Add checksum to map of used checksums
    PersistentSHA256Checksums.getInstance().storeChecksum(distributionUrl, distributionSHA)

    // Invoke Gradle Sync.
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_DISTRIBUTIONSHA256SUM_CONFIRMED_BY_USER)
    return CompletableFuture.completedFuture(null)
  }
}

private class RemoveSHA256FromGradleWrapperQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "Remove $DISTRIBUTION_SHA_256_SUM and sync project"
  override val id: String = "remove.SHA256.from.gradle.wrapper"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Unit>()
    invokeLater {
      run {
        val wrapper = GradleWrapper.find(project) ?: return@run
        try {
          val properties = wrapper.properties
          if (properties.getProperty(DISTRIBUTION_SHA_256_SUM) == null) return@run

          // Remove distributionSha256Sum from Gradle wrapper.
          properties.remove(DISTRIBUTION_SHA_256_SUM)
          PropertiesFiles.savePropertiesToFile(properties, wrapper.propertiesFilePath, null)
        }
        catch (exception: IOException) {
          Logger.getInstance(RemoveSHA256FromGradleWrapperQuickFix::class.java).warn(
            "Failed to read file ${wrapper.propertiesFilePath.path}"
          )
        }
      }
      // Invoke Gradle Sync, maybe not when we don't do anything?
      GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_DISTRIBUTIONSHA256SUM_REMOVED_FROM_WRAPPER)
      future.complete(null)
    }
    return future
  }
}

private fun findGradleWrapper(projectPath: String) : GradleWrapper? {
  val propertiesFile = GradleWrapper.getDefaultPropertiesFilePath(File(projectPath))
  if (!propertiesFile.isFile) return null
  return GradleWrapper.get(propertiesFile, null)
}