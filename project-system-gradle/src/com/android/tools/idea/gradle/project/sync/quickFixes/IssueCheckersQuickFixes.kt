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
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.AgpVersion
import com.android.repository.Revision
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.issues.processor.FixBuildToolsProcessor
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture

class CreateGradleWrapperQuickFix : BuildIssueQuickFix {
  override val id = "migrate.gradle.wrapper"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    invokeLater {
      val projectDirPath = Projects.getBaseDirPath(project)
      try {
        GradleWrapper.create(projectDirPath, project)
        val settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)
        if (settings != null) {
          settings.distributionType = DistributionType.DEFAULT_WRAPPED
        }

        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_WRAPPER_CREATED)
        future.complete(null)
      }
      catch (e: IOException) {
        Messages.showErrorDialog(project, "Failed to create Gradle wrapper: " + e.message, "Quick Fix")
        future.completeExceptionally(e)
      }
    }
    return future
  }
}

class DownloadAndroidStudioQuickFix : DescribedBuildIssueQuickFix {
  override val description: String = "See Android Studio download options"
  override val id: String = "download.android.studio"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    BrowserUtil.browse("http://developer.android.com/studio/index.html#downloads")
    return CompletableFuture.completedFuture(null)
  }
}

/**
 * This QuickFix upgrades the Gradle model to the version in [SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION] and Gradle
 * to the version in [SdkConstants.GRADLE_LATEST_VERSION].
 */
class FixAndroidGradlePluginVersionQuickFix(givenPluginVersion: AgpVersion?, givenGradleVersion: GradleVersion?) : BuildIssueQuickFix {
  override val id = "fix.gradle.elements"
  val pluginVersion = givenPluginVersion ?: AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val gradleVersion = givenGradleVersion ?: GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      val updater = AndroidPluginVersionUpdater.getInstance(project)
      if (updater.updatePluginVersion(pluginVersion, gradleVersion)) {
        val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
        GradleSyncInvoker.getInstance().requestProjectSync(project, request)
      }
      future.complete(null)
    }
    return future
  }
}

class InstallBuildToolsQuickFix(private val version: String,
                                private val buildFiles: List<VirtualFile>,
                                private val removeBuildTools: Boolean): BuildIssueQuickFix {
  override val id = "install.build.tools"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    invokeLater {
      val minBuildToolsVersion = Revision.parseRevision(version)
      val dialog = SdkQuickfixUtils.createDialogForPaths(project, listOf(DetailsTypes.getBuildToolsPath(minBuildToolsVersion)))
      if (dialog != null && dialog.showAndGet()) {
        if (buildFiles.isNotEmpty()) {
          val processor = FixBuildToolsProcessor(project, buildFiles, version, true, removeBuildTools)
          processor.setPreviewUsages(true)
          processor.run()
        }
        else {
          GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_BUILD_TOOLS_INSTALLED)
        }
      }
      future.complete(null)
    }
    return future
  }
}

/**
 * QuickFix to install a CMake version from the SDK.
 * If the version [myCmakeVersion] is passed to the quickfix, then it will be installed; otherwise, the latest version included in
 * the SDK should be installed.
 */
class InstallCmakeQuickFix(cmakeVersion: Revision?) : BuildIssueQuickFix {
  override val id = "INSTALL_CMAKE"
  val myCmakeVersion: Revision? = cmakeVersion

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val progressIndicator = StudioLoggerProgressIndicator(javaClass)
    val sdkManager = sdkHandler.getSdkManager(progressIndicator)
    val progressRunner = StudioProgressRunner(false, false, "Loading Remote SDK", project)

    val onComplete = RepoManager.RepoLoadedListener { packages: RepositoryPackages ->
      invokeLater(ModalityState.any()) {
        val cmakePackages = packages.getRemotePackagesForPrefix(SdkConstants.FD_CMAKE)
        val cmakePackage = if (myCmakeVersion == null) {
          // Install the latest version from the SDK.
          if (cmakePackages.size == 1) {
            ContainerUtil.getFirstItem(cmakePackages)
          }
          else {
            sdkHandler.getLatestRemotePackageForPrefix(
              SdkConstants.FD_CMAKE, null, false /* do not allow preview */, progressIndicator)
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
          future.complete(null)
          return@invokeLater
        }

        // Either no CMake versions were found, or the requested CMake version was not found.
        notifyCmakePackageNotFound(project)
        future.complete(null)
      }
    }

    val onError = Runnable {
      invokeLater(ModalityState.any()) {
        Messages.showErrorDialog(project, "Failed to install CMake package", "Gradle Sync")
        future.complete(null)
      }
    }
    sdkManager.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null,
                    ImmutableList.of(onComplete),
                    ImmutableList.of(onError), progressRunner,
                    StudioDownloader(), StudioSettingsController.getInstance())

    return future
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

class OpenFileAtLocationQuickFix(val myFilePosition: FilePosition) : BuildIssueQuickFix {
  override val id = "open.file"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    val projectFile = project.projectFile ?: return CompletableFuture.completedFuture<Any>(null)
    invokeLater {
      val file = projectFile.parent.fileSystem.findFileByPath(myFilePosition.file.path)
      if (file != null) {
        val openFile = OpenFileDescriptor(project, file, myFilePosition.startLine, myFilePosition.startColumn, false)
        if (openFile.canNavigate()) {
          openFile.navigate(true)
        }
      }
      future.complete(null)
    }
    return future
  }
}

class OpenLinkQuickFix(val link: String) : BuildIssueQuickFix {
  override val id = "open.more.details"
  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      BrowserUtil.browse(link)
      future.complete(null)
    }
    return future
  }
}

class OpenPluginBuildFileQuickFix : BuildIssueQuickFix {
  override val id = "open.plugin.build.file"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      if (project.isInitialized) {
        val pluginInfo = AndroidPluginInfo.findFromBuildFiles(project) ?: return@invokeLater
        if (pluginInfo.pluginBuildFile != null) {
          val openFile = OpenFileDescriptor(project, pluginInfo.pluginBuildFile!!, -1, -1, false)
          if (openFile.canNavigate()) openFile.navigate(true)
        }
      }
      else Messages.showErrorDialog(project, "Failed to find plugin version on Gradle files.", "Quick Fix")
      future.complete(null)
    }
    return future
  }
}

class OpenProjectStructureQuickfix : BuildIssueQuickFix {
  override val id = "open.jdk.ndk.settings"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val service = ProjectSettingsService.getInstance(project)
    if (service is AndroidProjectSettingsService) {
      service.openSdkSettings()
    }
    return CompletableFuture.completedFuture<Any>(null)
  }
}

class SetCmakeDirQuickFix(private val myPath: File) : BuildIssueQuickFix {
  override val id = "SET_CMAKE_DIR"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      val localProperties = LocalProperties(project)
      localProperties.androidCmakePath = myPath
      localProperties.save()
      GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_CMAKE_INSTALLED)
      future.complete(null)
    }
    return future
  }
}

class SyncProjectRefreshingDependenciesQuickFix : BuildIssueQuickFix {
  override val id = "sync.project"
  val linkText = "Re-download dependencies and sync project (requires network)"
  private val EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY = Key.create<Array<String>>("extra.gradle.command.line.options")

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, arrayOf("--refresh-dependencies"))
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_REFRESH_DEPENDENCIES)
    return CompletableFuture.completedFuture<Any>(null)
  }
}

class ToggleOfflineModeQuickFix(private val myEnableOfflineMode: Boolean) : BuildIssueQuickFix {
  override val id = "enable.disable.offline.mode"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      GradleSettings.getInstance(project).isOfflineWork = myEnableOfflineMode
      val trigger = GradleSyncStats.Trigger.TRIGGER_QF_OFFLINE_MODE_DISABLED
      GradleSyncInvoker.getInstance().requestProjectSync(project, trigger)
      future.complete(null)
    }
    return future
  }
}