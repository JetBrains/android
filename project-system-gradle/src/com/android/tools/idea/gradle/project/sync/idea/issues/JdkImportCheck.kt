/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("JdkImportCheck")
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.gradle.project.sync.quickFixes.DownloadAndroidStudioQuickFix
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.Jdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class JdkImportCheckException(reason: String) : AndroidSyncException(reason)

/**
 * Validates the state of the project Gradle JDK.
 *
 * If we find that the JDK is not valid then we throw a [JdkImportCheckException] which is then
 * caught in the [JdkImportIssueChecker] which creates an errors message with the appropriate
 * quick fixes.
 */
fun validateProjectGradleJdk(javaHome: String) {
  // This method is a wrapper to provide a Jdk to checkJdkErrorMessage. Tests are run directly on checkJdkErrorMessage.
  @Suppress("UnstableApiUsage")
  val jdk: Sdk = ExternalSystemJdkProvider.getInstance().createJdk(null, javaHome)
  try {
    validateJdk(jdk)
  }
  finally {
    (jdk as? Disposable)?.let { Disposer.dispose(jdk) }
  }
}

@VisibleForTesting
fun validateJdk(jdk: Sdk) {
  val jdkValidationError = validateAndGetErrors(jdk) ?: return // Valid jdk
  throw JdkImportCheckException(jdkValidationError)
}

class JdkImportIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = when {
      issueData.error is JdkImportCheckException -> issueData.error.message!!
      issueData.error.message?.contains("Unsupported major.minor version 52.0") == true -> {
        invokeLater {
          updateUsageTracker(issueData.projectPath, GradleSyncFailure.JDK8_REQUIRED)
        }
        "${issueData.error.message!!}\nPlease use JDK 8 or newer."
      }
      else -> return null
    }

    return BuildIssueComposer(message).apply {
      if (IdeInfo.getInstance().isAndroidStudio) {
        val ideaProject = fetchIdeaProjectForGradleProject(issueData.projectPath)
        if (ideaProject != null) {
          val gradleInstallation = (GradleInstallationManager.getInstance() as AndroidStudioGradleInstallationManager)
          if (!gradleInstallation.isUsingJavaHomeJdk(ideaProject)) {
            addUseJavaHomeQuickFix(this)
          }
        }

        if (issueQuickFixes.isEmpty()) {
          val embeddedJdkPath = IdeSdks.getInstance().embeddedJdkPath
          // TODO: Check we REALLY need to check isJdkRunnableOnPlatform. This spawns a process.
          if (Jdks.isJdkRunnableOnPlatform(embeddedJdkPath.toAbsolutePath().toString())) {
            addQuickFix(UseEmbeddedJdkQuickFix())
          } else {
            addQuickFix(DownloadAndroidStudioQuickFix())
          }
        }
      }

      addQuickFix(SelectJdkFromFileSystemQuickFix())
    }.composeBuildIssue()
  }

  private fun addUseJavaHomeQuickFix(composer: BuildIssueComposer) {
    val ideSdks = IdeSdks.getInstance()
    val jdkFromHome = IdeSdks.getJdkFromJavaHome()
    if (jdkFromHome != null && ideSdks.validateJdkPath(Paths.get(jdkFromHome)) != null) {
      composer.addQuickFix(UseJavaHomeAsJdkQuickFix())
    }
  }
}

private class UseJavaHomeAsJdkQuickFix() : DescribedBuildIssueQuickFix {
  override val description: String = "Set Android Studio to use the same JDK as Gradle and sync project"
  override val id: String = "use.java.home.as.jdk"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Nothing>()
    invokeLater {
      runWriteAction { JdkUtils.setProjectGradleJvmToUseJavaHome(project, project.basePath.orEmpty()) }
      GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_CURRENT)
      future.complete(null)
    }
    return future
  }
}

private class UseEmbeddedJdkQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "Use embedded JDK as Gradle JDK (${IdeSdks.getInstance().embeddedJdkPath})"
  override val id: String = "use.embedded.jdk"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Nothing>()
    invokeLater {
      runWriteAction { JdkUtils.setProjectGradleJvmToUseEmbeddedJdk(project, project.basePath.orEmpty()) }
      GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_EMBEDDED)
      future.complete(null)
    }
    return future
  }
}

public class SelectJdkFromFileSystemQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "Change Gradle JDK..."
  override val id: String = "select.jdk.from.new.psd"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val service = ProjectSettingsService.getInstance(project)
    if (service is AndroidProjectSettingsService) {
      service.chooseJdkLocation()
    } else {
      service.chooseAndSetSdk()
    }
    return CompletableFuture.completedFuture(null)
  }
}

/**
 * Verify the Jdk in the following ways,
 * 1. Jdk location has been set and has a valid Jdk home directory.
 * 2. The selected Jdk has the same version with IDE, this is to avoid serialization problems.
 * 3. The Jdk installation is complete, i.e. the has java executable, runtime and etc.
 * 4. The selected Jdk is compatible with current platform.
 * Returns null if the [Sdk] is valid, an error message otherwise.
 */
private fun validateAndGetErrors(jdk: Sdk): String? {
  val jdkHomePath = jdk.homePath ?: return "Could not find valid Jdk home from the selected Jdk location."
  val selectedJdkMsg = "Selected Jdk location is $jdkHomePath.\n"
  // Check if the version of selected Jdk is the same with the Jdk IDE uses.
  val runningJdkVersion = IdeSdks.getInstance().runningVersionOrDefault
  if (!StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.get() && !IdeSdks.isJdkSameVersion(Paths.get(jdkHomePath), runningJdkVersion)) {
    return "The version of selected Jdk doesn't match the Jdk used by Studio. Please choose a valid Jdk " +
           runningJdkVersion.description + " directory.\n" + selectedJdkMsg
  }
  // Check Jdk installation is complete.
  if (!JdkUtil.checkForJdk(jdkHomePath)) {
    return "The Jdk installation is invalid.\n$selectedJdkMsg"
  }
  // Check if the Jdk is compatible with platform.
  return if (!Jdks.isJdkRunnableOnPlatform(jdk)) {
    "The selected Jdk could not run on current OS.\n" +
    "If you are using embedded Jdk, please make sure to download Android Studio bundle compatible\n" +
    "with the current OS. For example, for x86 systems please choose a 32 bits download option.\n" +
    selectedJdkMsg
  }
  else {
    val ideInfo = IdeInfo.getInstance()
    if (ideInfo.isAndroidStudio || ideInfo.isGameTools) {
      // Recreate JDK table information for this JDK (b/187205058)
      if (StudioFlags.GRADLE_SYNC_RECREATE_JDK.get()) {
        WriteAction.runAndWait<RuntimeException> {
          IdeSdks.getInstance().recreateOrAddJdkInTable(jdkHomePath, jdk.name)
        }
      }
    }
    return null
  }
}

@TestOnly
fun createNewGradleJvmProjectJdk(project: Project, parent: Disposable): ProjectJdkImpl {
  val gradleExecutionSettings =
    (ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID) as GradleManager).executionSettingsProvider.`fun`(
      Pair(
        project,
        project.guessProjectDir()!!.path
      )
    )
  @Suppress("UnstableApiUsage")
  val sdk = ExternalSystemJdkProvider.getInstance().createJdk(null, gradleExecutionSettings.javaHome!!)
  Disposer.register(parent, sdk as Disposable)
  return sdk as ProjectJdkImpl
}
