/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.DelegatingProgressIndicator
import com.android.repository.api.Downloader
import com.android.repository.api.Installer
import com.android.repository.api.InstallerFactory
import com.android.repository.api.LocalPackage
import com.android.repository.api.PackageOperation
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.repository.api.SettingsController
import com.android.repository.api.Uninstaller
import com.android.repository.api.UpdatablePackage
import com.android.repository.impl.installer.AbstractPackageOperation
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.progress.RawProgressReporterAdapter
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.google.common.annotations.VisibleForTesting
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.AndroidPluginDisposable

private val log: Logger
  get() = logger<InstallTask>()

/** Task that installs SDK packages. */
class InstallTask(
  private val installerFactory: InstallerFactory,
  private val sdkHandler: AndroidSdkHandler,
  private val settingsController: SettingsController = StudioSettingsController.getInstance(),
  private val logger: ProgressIndicator = StudioLoggerProgressIndicator(InstallTask::class.java),
  val installRequests: Collection<UpdatablePackage> = emptyList(),
  val uninstallRequests: Collection<LocalPackage> = emptyList(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  val prepareCompleteCallback: (() -> Unit)? = null,
  val completeCallback: ((List<RepoPackage>) -> Unit)? = null,
) {
  private val repoManager: RepoManager = sdkHandler.getRepoManagerAndLoadSynchronously(logger)
  private var isBackgrounded: Boolean = false

  fun onCancel() {
    logger.cancel()
  }

  /**
   * This task is always run in the background, but there's another progress indicator shown in the foreground. This should be called when
   * the foreground progress is closed, thus making it look like we're in the background.
   */
  fun foregroundIndicatorClosed() {
    isBackgrounded = true
  }

  /** Runs the installation with IntelliJ's [withBackgroundProgress] if a project is available. */
  suspend fun run(
    project: Project? = null,
    title: String = "Installing Android SDK",
    cancellable: Boolean = true,
  ): List<RepoPackage> {
    val targetProject =
      (project ?: IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project)?.takeUnless { it.isDisposed || it.isDefault }
        ?: ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed && !it.isDefault }

    return if (targetProject != null) {
      withBackgroundProgress(targetProject, title, cancellable) {
        reportRawProgress { reporter ->
          val progressAdapter = RawProgressReporterAdapter(reporter)
          val combinedProgress =
            DelegatingProgressIndicator(logger).apply {
              addDelegate(progressAdapter)
            }
          execute(combinedProgress)
        }
      }
    } else {
      withContext(ioDispatcher) {
        execute(logger)
      }
    }
  }

  /** Asynchronous launch helper for Java / non-suspending callers. */
  @JvmOverloads
  fun runAsync(
    project: Project? = null,
    coroutineScope: CoroutineScope = AndroidPluginDisposable.getApplicationInstance().createCoroutineScope(),
    title: String = "Installing Android SDK",
    cancellable: Boolean = true,
  ): Job = coroutineScope.launch {
    run(project, title, cancellable)
  }

  /** Synchronous / non-coroutine execution entry point. */
  fun run(indicator: ProgressIndicator): List<RepoPackage> {
    return execute(indicator)
  }

  @VisibleForTesting
  fun execute(progress: ProgressIndicator): List<RepoPackage> {
    val failures = mutableListOf<RepoPackage>()
    val operations = mutableMapOf<RepoPackage, PackageOperation>()

    if (installRequests.isNotEmpty()) {
      logger.logInfo("Packages to install: ")
      for (install in installRequests) {
        val remote = install.remote
        if (remote != null) {
          logger.logInfo("- ${remote.displayName} (${remote.path})")
          operations[remote] = getOrCreateInstaller(remote)
        }
      }
      logger.logInfo("\n")
    }

    if (uninstallRequests.isNotEmpty()) {
      logger.logInfo("Packages to uninstall: ")
      for (uninstall in uninstallRequests) {
        logger.logInfo("- ${uninstall.displayName} (${uninstall.path})")
        operations[uninstall] = getOrCreateUninstaller(uninstall)
      }
      logger.logInfo("\n")
    }

    try {
      while (operations.isNotEmpty()) {
        progress.fraction = 0.0
        preparePackages(operations, failures, progress)
        prepareCompleteCallback?.invoke()
        progress.checkCanceled()
        if (!isBackgrounded) {
          completePackages(operations, failures, progress.createSubProgress(0.9), progress)
          progress.fraction = 0.9
        } else {
          progress.fraction = 1.0
          showPrepareCompleteNotification(operations.keys)
          return failures
        }
      }
    } finally {
      if (failures.isNotEmpty()) {
        logger.logInfo("Failed packages:")
        for (p in failures) {
          logger.logInfo("- ${p.displayName} (${p.path})")
        }
      }
    }
    // Use a simple progress indicator here so we don't pick up the log messages from the reload.
    val reloadProgress = StudioLoggerProgressIndicator(javaClass)
    repoManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, reloadProgress, null, settingsController)
    completeCallback?.invoke(failures)
    progress.fraction = 1.0
    return failures
  }

  /**
   * Complete installation of the given packages using the given operations. If a package is completed successfully, it is removed from
   * {@code operations}. If a package fails to be installed and has a fallback operation, the fallback is added to {@code operations}, and
   * it is the responsibility of the caller to retry. If a package fails to be installed and has no fallback, it is added to {@code
   * failures}.
   */
  @VisibleForTesting
  fun completePackages(
    operations: MutableMap<RepoPackage, PackageOperation>,
    failures: MutableList<RepoPackage>,
    progress: ProgressIndicator,
    taskProgressIndicator: ProgressIndicator,
  ) {
    var progressMax = 0.0
    val packages = operations.keys.toList()
    val progressIncrement = 1.0 / packages.size

    for (p in packages) {
      taskProgressIndicator.checkCanceled()
      val installer = operations[p] ?: continue
      progressMax += progressIncrement

      if (!installer.complete(progress.createSubProgress(progressMax))) {
        taskProgressIndicator.checkCanceled()
        progress.fraction = progressMax
        val fallback = installer.fallbackOperation
        if (fallback != null) {
          progress.logWarning("Failed to complete operation using ${installer.javaClass.name}, retrying with ${fallback.javaClass.name}")
          operations[p] = fallback
        } else {
          failures.add(p)
          operations.remove(p)
        }
      } else {
        operations.remove(p)
        progress.fraction = progressMax
      }
    }
  }

  private fun getOrCreateInstaller(remote: RemotePackage): PackageOperation {
    var op = repoManager.getInProgressInstallOperation(remote)
    if (op !is Installer) {
      val downloader: Downloader =
        StudioDownloader().apply {
          val localPath = repoManager.localPath
          if (localPath != null) {
            setDownloadIntermediatesLocation(localPath.resolve(AbstractPackageOperation.DOWNLOAD_INTERMEDIATES_DIR_FN))
          }
        }
      op = installerFactory.createInstaller(remote, repoManager, downloader)
    }
    return op
  }

  private fun getOrCreateUninstaller(local: LocalPackage): PackageOperation {
    val op = repoManager.getInProgressInstallOperation(local)
    if (op !is Uninstaller || op.installStatus == PackageOperation.InstallStatus.FAILED) {
      return installerFactory.createUninstaller(local, repoManager)
    }
    return op
  }

  /**
   * Prepare the given packages using the given operations. If preparation for a package fails, it is retried with the {@link
   * PackageOperation#getFallbackOperation() fallback operation}. If fallbacks also fail, the package is removed from {@code
   * packageOperationMap} and added to {@code failures}.
   */
  @VisibleForTesting
  fun preparePackages(
    packageOperationMap: MutableMap<RepoPackage, PackageOperation>,
    failures: MutableList<RepoPackage>,
    progress: ProgressIndicator,
  ) {
    val packages = packageOperationMap.keys.toList()
    var progressIncrement = 1.0 / (packages.size * 2.0)
    var wasBackgrounded = false

    for (pack in packages) {
      progress.checkCanceled()
      var op = packageOperationMap[pack]
      var success = false

      while (op != null) {
        if (isBackgrounded && !wasBackgrounded) {
          progressIncrement *= 2.0
          progress.fraction = progress.fraction * 2.0
          wasBackgrounded = isBackgrounded
        }
        val currentProgress = progress.fraction
        try {
          val progressMax = currentProgress + progressIncrement
          success = op.prepare(progress.createSubProgress(progressMax))
          progress.checkCanceled()
          progress.fraction = progressMax
        } catch (e: ProcessCanceledException) {
          throw e
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn(e)
        }

        if (success) {
          packageOperationMap[pack] = op
          break
        }
        op = op.fallbackOperation
        if (op != null) {
          progress.fraction = currentProgress
        }
      }
      if (!success) {
        failures.add(pack)
        packageOperationMap.remove(pack)
      }
    }
  }

  private fun showPrepareCompleteNotification(packages: Collection<RepoPackage>) {
    val notificationListener =
      object : NotificationListener.Adapter() {
        override fun hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
          if (event.description == "install") {
            val dialogForPaths = SdkQuickfixUtils.createDialogForPackages(null, installRequests, uninstallRequests, true)
            dialogForPaths?.show()
          }
          notification.expire()
        }
      }

    val group = NotificationGroupManager.getInstance().getNotificationGroup("SDK Install")
    val openProjects = ProjectManager.getInstance().openProjects
    val openProjectsOrNull = openProjects.ifEmpty { arrayOf<Project?>(null) }

    ApplicationManager.getApplication()
      .invokeLater(
        {
          for (p in openProjectsOrNull) {
            val message =
              if (packages.size == 1) {
                val pack = packages.first()
                val op = repoManager.getInProgressInstallOperation(pack)
                val opName = if (op == null || op is Installer) "Install" else "Uninstall"
                "${opName}ation of '${pack.displayName}' is ready to continue<br/><a href=\"install\">$opName Now</a>"
              } else {
                "${packages.size} packages are ready to install or uninstall<br/><a href=\"install\">Continue</a>"
              }
            group.createNotification("SDK Install", message, NotificationType.INFORMATION).setListener(notificationListener).notify(p)
          }
        },
        ModalityState.nonModal(),
        {
          packages.none { pack ->
            repoManager.getInProgressInstallOperation(pack)?.installStatus == PackageOperation.InstallStatus.PREPARED
          }
        },
      )
  }

  private fun ProgressIndicator.checkCanceled() {
    if (isCanceled) {
      throw ProcessCanceledException()
    }
  }
}
