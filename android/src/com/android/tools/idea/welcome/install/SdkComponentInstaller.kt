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
package com.android.tools.idea.welcome.install

import com.android.annotations.concurrency.Slow
import com.android.repository.api.Downloader
import com.android.repository.api.InstallerFactory
import com.android.repository.api.LocalPackage
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.impl.installer.BasicInstallerFactory
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.ThrottledProgressWrapper
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.PackageResolutionException
import com.android.tools.idea.ui.ApplicationUtils
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.google.common.base.Function
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ModalityState
import java.io.File

/** Installs SDK components. */
class SdkComponentInstaller {
  @Throws(PackageResolutionException::class)
  fun getPackagesToInstall(
    sdkHandler: AndroidSdkHandler,
    components: Iterable<InstallableSdkComponentTreeNode>,
  ): List<RemotePackage> {
    // TODO: Prompt about connection in handoff case?
    val progress = StudioLoggerProgressIndicator(javaClass)
    val sdkManager = sdkHandler.getSdkManager(progress)
    val requests = components.flatMap { it.packagesToInstall }
    return SdkQuickfixUtils.resolve(requests, sdkManager.packages).map { it.remote!! }
  }

  /**
   * Installs all components in the `componentTree` that are configured to be installed. Once the
   * components have been installed, the SDK path and installer timestamp are stored in preferences.
   *
   * @param installableSdkComponents The SDK components to install
   * @param installContext Used to track installation progress
   * @param installerTimestamp Stored in preferences when installation is complete
   * @param modalityState Used when updating preferences
   * @param localHandler Used when running the `configure` step after components are installed
   * @param destination The directory to save the SDK components in
   */
  @Throws(WizardException::class)
  fun installComponents(
    installableSdkComponents: Collection<InstallableSdkComponentTreeNode>,
    installContext: InstallContext,
    installerTimestamp: String?,
    modalityState: ModalityState,
    localHandler: AndroidSdkHandler,
    destination: File,
  ) {
    if (installableSdkComponents.isEmpty()) {
      installContext.print("Nothing to do!", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    val INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE = 1.0
    val install =
      InstallSdkComponentsOperation(
        installContext,
        localHandler,
        installableSdkComponents,
        this,
        INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE,
      )

    try {
      install
        .then(SetPreference(installerTimestamp, modalityState))
        .then(ConfigureComponents(installContext, installableSdkComponents, localHandler))
        .then(CheckSdkOperation(installContext))
        .execute(destination)
    } catch (e: InstallationCancelledException) {
      installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  @Slow
  fun installPackages(
    sdkHandler: AndroidSdkHandler,
    packages: List<RemotePackage>,
    downloader: Downloader,
    progress: ProgressIndicator,
  ) {
    val throttledProgress = ThrottledProgressWrapper(progress)
    val sdkManager = sdkHandler.getSdkManager(throttledProgress)
    var progressMax = 0.0
    val progressIncrement = 0.9 / (packages.size * 2.0)
    val factory = BasicInstallerFactory()

    packages
      .map { factory.createInstaller(it, sdkManager, downloader) }
      .forEach { installer ->
        // Intentionally don't register any listeners on the installer, so we don't recurse on AEHD
        // TODO: This is a hack. Any future rewrite of this shouldn't require this behavior.
        progressMax += progressIncrement
        if (installer.prepare(throttledProgress.createSubProgress(progressMax))) {
          installer.complete(throttledProgress.createSubProgress(progressMax + progressIncrement))
        }
        progressMax += progressIncrement
        throttledProgress.fraction = progressMax
      }

    sdkManager.loadSynchronously(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      throttledProgress.createSubProgress(1.0),
      null,
      null,
    )
  }

  @Slow
  fun ensureSdkPackagesUninstalled(
    sdkHandler: AndroidSdkHandler,
    packageNames: Collection<String>,
    progress: ProgressIndicator,
  ) {
    val sdkManager = sdkHandler.getSdkManager(progress)
    val localPackages = sdkManager.packages.localPackages
    val packagesToUninstall = mutableListOf<LocalPackage>()
    for (packageName in packageNames) {
      val p = localPackages[packageName]
      if (p != null) {
        packagesToUninstall.add(p)
      } else {
        progress.logInfo("Package '$packageName' does not appear to be installed - ignoring")
      }
    }
    var progressMax = 0.0
    val progressIncrement = 0.9 / (packagesToUninstall.size * 2.0)
    val factory: InstallerFactory = BasicInstallerFactory()

    // This is pretty much symmetric to the installPackages() method above, so the same comments
    // apply.
    // Should we have registered listeners, AehdInstallListener would have invoked another instance
    // of AehdWizard.
    // The good news is that as of writing this,
    // this class is used in Welcome and Aehd wizards only, and plays the role of a utility class.
    // If we have more packages which require custom pre- and post-installation steps like AEHD,
    // then we might still need a way to invoke non-recursive / listener-free uninstall operations
    // for cleanup purposes
    // It's possible that a change in packaging API would make sense to support that later -
    // there is already some cleanup() support in operation chain implementation, but its limitation
    // is that cleanup()
    // is executed unconditionally, whereas in most cases it should be dependent on the next
    // operation success status -
    // like stack unwinding after an exception.
    packagesToUninstall
      .map { factory.createUninstaller(it, sdkManager) }
      .forEach { uninstaller ->
        progressMax += progressIncrement
        if (uninstaller.prepare(progress.createSubProgress(progressMax))) {
          uninstaller.complete(progress.createSubProgress(progressMax + progressIncrement))
        }
        progressMax += progressIncrement
        progress.fraction = progressMax
      }
    sdkManager.loadSynchronously(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      progress.createSubProgress(1.0),
      null,
      null,
    )
    progress.fraction = 1.0
  }

  private class SetPreference(
    private val myInstallerTimestamp: String?,
    private val myModalityState: ModalityState,
  ) : Function<File, File> {
    override fun apply(input: File): File {
      ApplicationUtils.invokeWriteActionAndWait(myModalityState) {
        IdeSdks.getInstance().setAndroidSdkPath(input)
        AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myInstallerTimestamp)
      }

      return input
    }
  }

  private class ConfigureComponents(
    private val myInstallContext: InstallContext,
    private val mySelectedComponents: Collection<InstallableSdkComponentTreeNode>,
    private val mySdkHandler: AndroidSdkHandler,
  ) : Function<File, File> {
    override fun apply(input: File): File {
      for (component in mySelectedComponents) {
        component.configure(myInstallContext, mySdkHandler)
      }
      return input
    }
  }
}
