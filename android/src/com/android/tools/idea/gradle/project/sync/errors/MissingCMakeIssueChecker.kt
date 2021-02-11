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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.SdkConstants
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallCmakeQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetCmakeDirQuickFix
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.io.File
import java.io.IOException
import java.util.function.Consumer

/**
 * Extended version of the "Revision" class with orAbove semantics.
 * [revision] : The numerical revision requested to be installed.
 * [orHigher] : If any version above the requested revision can satisfy the request.
 */
class RevisionOrHigher(val revision: Revision, val orHigher: Boolean)

// The default CMake version (sdk-internal) and the equivalent version used when it is reported to the user.
private const val FORK_CMAKE_SDK_VERSION = "3.6.4111459"
private const val FORK_CMAKE_REPORTED_VERSION = "3.6.0"

// Parsed default cmake version (sdk-internal and user-facing).
private val ourForkCmakeSdkVersion = Revision.parseRevision(FORK_CMAKE_SDK_VERSION)
private val ourForkCmakeReportedVersion = Revision.parseRevision(FORK_CMAKE_REPORTED_VERSION)

/**
 * Finds whether the requested cmake version can be installed from the SDK.
 *
 * @param cmakePackages  Remote CMake packages available in the SDK.
 * @param requestedCmake The CMake version requested by the user.
 * @return The version that best matches the requested version, null if no match was found.
 */
@VisibleForTesting
fun findBestMatch(cmakePackages: Collection<RemotePackage>, requestedCmake: RevisionOrHigher): Revision? {
  var foundVersion: Revision? = null
  for (remotePackage in cmakePackages) {
    var remoteCmake = remotePackage.version

    // If the version in the remote package is the fork version, we use its user friendly equivalent.
    if (remoteCmake == ourForkCmakeSdkVersion) {
      remoteCmake = ourForkCmakeReportedVersion
    }
    if (!versionSatisfies(remoteCmake, requestedCmake)) {
      continue
    }
    if (foundVersion == null) {
      foundVersion = remoteCmake
      continue
    }
    if (remoteCmake.compareTo(foundVersion, Revision.PreviewComparison.IGNORE) > 0) {
      // Among all matching Cmake versions, use the highest version one (ignore preview version).
      foundVersion = remoteCmake
      continue
    }
  }
  return foundVersion
}

@VisibleForTesting
fun parseRevisionOrHigher(version: String, firstLine: String): RevisionOrHigher? {
  return try {
    RevisionOrHigher(Revision.parseRevision(version), firstLine.contains("'$version' or higher"))
  }
  catch (e: NumberFormatException) {
    // Cannot parse version string.
    null
  }
}

/**
 * @param candidateCmake the cmake version that is available in the SDK.
 * @param requestedCmake the cmake version (or the minimum cmake version) that we are looking for.
 * @return true if the version represented by candidateCmake is a good match for the version represented by requestedCmake. The preview
 * version (i.e., 4th component) is always ignored when performing the matching.
 */
@VisibleForTesting
fun versionSatisfies(candidateCmake: Revision, requestedCmake: RevisionOrHigher): Boolean {
  val result = candidateCmake.compareTo(requestedCmake.revision, Revision.PreviewComparison.IGNORE)
  return result == 0 || requestedCmake.orHigher && result >= 0
}

open class MissingCMakeIssueChecker : GradleIssueChecker {
  private val FAILED_TO_FIND_CMAKE = "Failed to find CMake."
  private val UNABLE_TO_GET_CMAKE_VERSION = "Unable to get the CMake version"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null

    val buildIssueComposer = when {
       (matchesCannotFindCmake(message) || matchesTriedInstall(message) || matchesCmakeWithVersion(message)) -> {
         BuildIssueComposer(message)
      }
      message.startsWith(FAILED_TO_FIND_CMAKE) || message.startsWith(UNABLE_TO_GET_CMAKE_VERSION) -> {
        updateUsageTracker(issueData.projectPath, GradleSyncFailure.MISSING_CMAKE)
        BuildIssueComposer("Failed to find CMake.")
      }
      else -> return null
    }

    // Get quickFixes.
    val firstLine = message.lines()[0]
    val version = extractCmakeVersionFromError(firstLine)
    if (version == null) {
      // Generic install of CMake.
      buildIssueComposer.addQuickFix("Install CMake", InstallCmakeQuickFix(null))
      return buildIssueComposer.composeBuildIssue()
    }

    // Get the Cmake version to install from the error string; if not found, return as showing the quickFix is useless in such case.
    val requestedCmake =
      parseRevisionOrHigher(version, firstLine) ?: return buildIssueComposer.composeBuildIssue()

    val sdkManager = getSdkManager()
    val remoteCmakePackages = sdkManager.packages.getRemotePackagesForPrefix(SdkConstants.FD_CMAKE)
    // Fetch the Cmake version that satisfies the request from the SDK. If no version matches, no need to show the quickFix.
    val foundCmakeVersion =
      findBestMatch(remoteCmakePackages, requestedCmake) ?: return buildIssueComposer.composeBuildIssue()

    val localCmakePackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_CMAKE)
    val alreadyInstalledCmake = getAlreadyInstalled(localCmakePackages, foundCmakeVersion)
    if (alreadyInstalledCmake != null) {
      // A suitable CMake was already installed.
      try {
        // Get the cmake.dir property from locaissueData.error.getRootCause()l.properties. If none exists, prompt the user to set one
        val cmakeDir= getLocalProperties(issueData.projectPath)
        if (cmakeDir == null) {
          buildIssueComposer.addQuickFix("Set cmake.dir in local.properties to '${alreadyInstalledCmake}' .",
                                  SetCmakeDirQuickFix(alreadyInstalledCmake))
          return buildIssueComposer.composeBuildIssue()
        }

        // If the cmakeDirPath is the same as the path we found then there's no point in offering a hyperlink.
        if (cmakeDir.path === alreadyInstalledCmake.path) return buildIssueComposer.composeBuildIssue()

        // There is a cmake.dir setting in local.properties, prompt the user replace it with
        // the one we found.
        buildIssueComposer.addQuickFix("Replace cmake.dir in local.properties with '${alreadyInstalledCmake}' .",
                                SetCmakeDirQuickFix(alreadyInstalledCmake))
        return buildIssueComposer.composeBuildIssue()
      }
      catch (e: IOException) {
        // Couldn't access local.properties for some reason. Don't show a link because we likely won't be able to write to that file.
        return buildIssueComposer.composeBuildIssue()
      }
    }

    // Offer a version-specific install of Cmake.
    buildIssueComposer.addQuickFix("Install Cmake ${foundCmakeVersion}", InstallCmakeQuickFix(foundCmakeVersion))

    return buildIssueComposer.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return matchesCannotFindCmake(failureCause) || matchesTriedInstall(failureCause) || matchesCmakeWithVersion(failureCause) ||
      failureCause.startsWith(FAILED_TO_FIND_CMAKE) || failureCause.startsWith(UNABLE_TO_GET_CMAKE_VERSION)
  }

  /**
   * @param cmakePackages local CMake installations available in the SDK.
   * @param cmakeVersion  the cmake version that we are looking for.
   * @return path to CMake if already installed.
   */
  private fun getAlreadyInstalled(cmakePackages: Collection<LocalPackage>, cmakeVersion: Revision): File? {
    for (localCmakePackage in cmakePackages) {
      if (localCmakePackage.version == cmakeVersion) {
        val fileOp = AndroidSdks.getInstance().tryToChooseSdkHandler().fileOp
        return fileOp.toFile(localCmakePackage.location)
      }
    }
    return null
  }

  protected open fun getLocalProperties(projectPath: String): File? {
    return LocalProperties(File(projectPath)).androidCmakePath
  }

  protected open fun getSdkManager(): RepoManager {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val progressIndicator = StudioLoggerProgressIndicator(javaClass)
    return sdkHandler.getSdkManager(progressIndicator)
  }

  private fun matchesCannotFindCmake(message: String): Boolean {
    return message.startsWith("CMake") && message.contains("was not found in PATH or by cmake.dir property")
  }

  /**
   * @param message the error message
   * @return whether the given error message was generated by the Android Gradle Plugin failing to download the CMake package.
   */
  private fun matchesTriedInstall(message: String): Boolean {
    return (message.startsWith("Failed to install the following Android SDK packages as some licences have not been accepted.") ||
            message.startsWith("Failed to install the following SDK components:")) &&
           (message.contains("CMake") || message.contains("cmake"))
  }

  private fun matchesCmakeWithVersion(message: String): Boolean {
    return message.startsWith("Unable to find CMake with version:");
  }

  private fun extractCmakeVersionFromError(firstLine: String): String? {
    var startIndex = firstLine.indexOf('\'')
    var endIndex = firstLine.indexOf('\'', startIndex + 1)
    if (startIndex != -1 && endIndex != -1) return firstLine.substring(startIndex + 1, endIndex)

    // Extract Cmake version from Error in version within.
    startIndex = firstLine.indexOf("version: ")
    if (startIndex == -1) return null
    endIndex = firstLine.indexOf(" within", startIndex + 1)
    if (endIndex == -1) return null
    return firstLine.substring(startIndex + 9, endIndex)
  }
}