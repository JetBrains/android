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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.SdkConstants
import com.android.repository.Revision
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.progress.StudioProgressRunner
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.CompletableFuture

/**
 * QuickFix to install a CMake version from the SDK.
 * If the version [myCmakeVersion] is passed to the quickfix, then it will be installed; otherwise, the latest version included in
 * the SDK should be installed.
 */
public class InstallCmakeQuickFix(cmakeVersion: Revision?) : BuildIssueQuickFix {
  override val id = "INSTALL_CMAKE"
  public val myCmakeVersion: Revision? = cmakeVersion

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    val progressIndicator = StudioLoggerProgressIndicator(javaClass)
    val sdkManager = sdkHandler.getSdkManager(progressIndicator)
    val progressRunner = StudioProgressRunner(false, false, "Loading Remote SDK", project)

    val onComplete = RepoManager.RepoLoadedCallback { packages: RepositoryPackages ->
      invokeLater(ModalityState.any()) {
        val cmakePackages = packages.getRemotePackagesForPrefix(SdkConstants.FD_CMAKE)
        val cmakePackage = if (myCmakeVersion == null) {
          // Install the latest version from the SDK.
          if (cmakePackages.size == 1) {
            ContainerUtil.getFirstItem(cmakePackages)
          }
          else {
            sdkHandler.getLatestRemotePackageForPrefix(
              SdkConstants.FD_CMAKE, false /* do not allow preview */, progressIndicator)
          }
        }
        else {
          // Install the version the user requested.
          cmakePackages.stream()
            .filter { remotePackage -> remotePackage!!.version == myCmakeVersion }
            .findFirst()
            .orElse(null)
        }

        if (cmakePackage != null) {
          // Found: Trigger installation of the package.
          val dialog = SdkQuickfixUtils.createDialogForPaths(project, ImmutableList.of(cmakePackage.path), true)
          if (dialog != null && dialog.showAndGet()) {
            GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_CMAKE_INSTALLED)
          }
          return@invokeLater
        }

        // Either no CMake versions were found, or the requested CMake version was not found.
        notifyCmakePackageNotFound(project)
      }
    }

    val onError = Runnable {
      invokeLater(ModalityState.any()) {
        Messages.showErrorDialog(project, "Failed to install CMake package", "Gradle Sync")
      }
    }
    sdkManager.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null,
                    ImmutableList.of(onComplete),
                    ImmutableList.of(onError), progressRunner,
                    StudioDownloader(), StudioSettingsController.getInstance(), false)

    return CompletableFuture.completedFuture<Any>(null)
  }

  /**
   * display error message to notify the user that a CMake package was not found.
   * @param project: the current Intellij project.
   */
  private fun notifyCmakePackageNotFound(project: Project) {
    if (myCmakeVersion == null) Messages.showErrorDialog(project, "Failed to obtain CMake package", "Gradle Sync")
    else Messages.showErrorDialog(project, "Failed to obtain CMake package version ${myCmakeVersion}", "Gradle Sync")
  }
}