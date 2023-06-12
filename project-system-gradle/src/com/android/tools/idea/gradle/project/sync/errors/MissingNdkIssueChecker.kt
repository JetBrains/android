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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.repository.Revision
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.FixNdkVersionProcessor
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

private const val VERSION_PATTERN = "(?<version>([0-9]+)(?:\\.([0-9]+)(?:\\.([0-9]+))?)?([\\s-]*)?(?:(rc|alpha|beta|\\.)([0-9]+))?)"
private val PREFERRED_VERSION_PATTERNS = listOf(
  "NDK not configured. Download it with SDK manager. Preferred NDK version is '$VERSION_PATTERN'.*".toRegex(),
  "No version of NDK matched the requested version $VERSION_PATTERN.*".toRegex())

class MissingNdkIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = errorMessage(issueData) ?: return null

    val preferredVersion = tryExtractPreferredNdkDownloadVersion(message)
    val quickFixes = mutableListOf<BuildIssueQuickFix>()
    var description = "$message\n"
    val ideSdks = IdeSdks.getInstance()
    val localRevision: String?
    if (preferredVersion != null) {
      // Preferred version can be found in the message, and it is available locally
      localRevision = ideSdks.getSpecificLocalPackage("ndk;$preferredVersion")?.version?.toString()
    }
    else if (matchesNdkNotConfigured(message) ||
          matchesKnownLocatorIssue(message) ||
          matchesTriedInstall(message)) {

      // Error message matches but it does not contain a preferred version, find highest version available locally.
      localRevision = (ideSdks.getHighestLocalNdkPackage( /* No previews first */false) ?:
                       ideSdks.getHighestLocalNdkPackage(true /* Then previews */))?.version?.toString()
    }
    else {
      return null
    }

    val gradleVersion = issueData.buildEnvironment?.gradle?.gradleVersion;

    if (gradleVersion != null && GradleVersion.version(gradleVersion).baseVersion <= GradleVersion.version("6.2")) {
      // If the version of AGP is too old to support android.ndkVersion then don't offer to download an NDK.
      // We can't know the AGP version when sync has failed so use older gradle version as a proxy.
      // Older AGP don't support android.ndkVersion so don't offer a hyperlink to set that value.
      return null
    }

    if (localRevision != null) {
      // Update project if a local version can be used
      description += appendQuickFix(quickFixes, FixNdkVersionQuickFix(localRevision), "Update NDK version to $localRevision and sync project")
    } else {
      // Otherwise install preferred version (if the message has one, use latest if not))
      description += appendQuickFix(quickFixes, InstallNdkQuickFix(preferredVersion?.toString()), if (preferredVersion != null)
        "Install NDK '$preferredVersion' and sync project" else "Install latest NDK and sync project")
    }

    return object: BuildIssue {
      override val title: String = "NDK not configured."
      override val description: String = description
      override val quickFixes: List<BuildIssueQuickFix> = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return tryExtractPreferredNdkDownloadVersion(failureCause) != null ||
           matchesNdkNotConfigured(failureCause) ||
           matchesKnownLocatorIssue(failureCause) ||
           matchesTriedInstall(failureCause)
  }

  private fun appendQuickFix(quickFixes: MutableList<BuildIssueQuickFix>,
                             quickFix: BuildIssueQuickFix,
                             message: String): String {
    quickFixes += quickFix
    return "\n<a href=\"${quickFix.id}\">$message</a>"
  }

  private fun errorMessage(issueData: GradleIssueData): String? {
    val rootCauseAndLocation = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error)
    val rootCause = rootCauseAndLocation.first ?: return null
    return rootCause.message
  }

  private fun matchesNdkNotConfigured(errorMessage: String): Boolean {
    return errorMessage.startsWith("NDK not configured.") ||
           errorMessage.startsWith("NDK location not found.") ||
           errorMessage.startsWith("Requested NDK version") ||
           errorMessage.startsWith("No version of NDK matched the requested version")
  }

  private fun matchesKnownLocatorIssue(errorMessage: String): Boolean {
    return (errorMessage.startsWith("Specified android.ndkVersion")
            && errorMessage.contains("does not have enough precision")) ||
           (errorMessage.startsWith("Location specified by ndk.dir"))
  }

  private fun matchesTriedInstall(errorMessage: String): Boolean {
    return (errorMessage.contains(
      "Failed to install the following Android SDK packages as some licences have not been accepted.") || errorMessage.contains(
      "Failed to install the following SDK components:")) && errorMessage.contains("NDK")
  }

  class FixNdkVersionQuickFix(val version: String) : BuildIssueQuickFix {
    override val id: String = "fix.ndk.version.quickfix"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Any>()
      ApplicationManager.getApplication().invokeLater {
        try {
          // Remove any value old value from ndk.dir
          val localProperties = LocalProperties(project)
          localProperties.androidNdkPath = null
          localProperties.save()

          // Rewrite android.ndkVersion.
          val buildFiles = ModuleManager.getInstance(project).modules.mapNotNull { GradleUtil.getGradleBuildFile(it) }
          val processor = FixNdkVersionProcessor(project, buildFiles, version)
          processor.run()
          future.complete(null)
        }
        catch (e: Exception) {
          future.completeExceptionally(e)
        }
      }
      return future
    }
  }

  class InstallNdkQuickFix(private val preferredVersion: String?): BuildIssueQuickFix {
    override val id: String
      get() = "install.ndk.quickfix"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Any>()
      ApplicationManager.getApplication().invokeLater {
        try {
          val buildFiles = ModuleManager.getInstance(project).modules.mapNotNull { GradleUtil.getGradleBuildFile(it) }
          InstallNdkHyperlink(preferredVersion, buildFiles).execute(project)
          future.complete(null)
        }
        catch (e: Exception) {
          future.completeExceptionally(e)
        }
      }
      return future
    }
  }
}

/**
 * Try to recover preferred NDK version from the error message
 */
fun tryExtractPreferredNdkDownloadVersion(text : String) : Revision? {
  for(pattern in PREFERRED_VERSION_PATTERNS) {
    val result = pattern.matchEntire(text) ?: continue
    val version = result.groups["version"]!!.value
    return Revision.parseRevision(version)
  }
  return null
}
