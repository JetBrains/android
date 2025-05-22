/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.annotations.concurrency.UiThread
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.ui.validation.validators.PathValidator.Companion.forAndroidSdkLocation
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.SdkComponentTreeNode
import com.android.tools.idea.welcome.install.SdkComponentTreeNode.Companion.areAllRequiredComponentsAvailable
import com.android.tools.idea.welcome.wizard.SdkComponentsStepUtils.getTargetFilesystem
import com.android.tools.idea.welcome.wizard.SdkComponentsStepUtils.isExistingSdk
import com.android.tools.idea.welcome.wizard.SdkComponentsStepUtils.isNonEmptyNonSdk
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Paths
import javax.swing.Icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@UiThread
abstract class SdkComponentsStepController(
  private val project: Project?,
  private val mode: FirstRunWizardMode,
  private val rootNode: SdkComponentTreeNode,
  private val localSdkHandlerProperty: ObjectValueProperty<AndroidSdkHandler>,
) : Disposable {
  private val coroutineScope = AndroidCoroutineScope(this)
  private var loadingJob: Job? = null

  private var userEditedPath = false
  private var sdkDirectoryValidationResult = Validator.Result.OK
  private var wasForcedVisible = false
  private var loading = false

  override fun dispose() {}

  fun startLoading() {
    loading = true
    onLoadingStarted()
  }

  fun stopLoading() {
    loading = false
    onLoadingFinished()
  }

  fun loadingError() {
    loading = false
    onLoadingError()
  }

  abstract fun onLoadingStarted()

  abstract fun onLoadingFinished()

  abstract fun onLoadingError()

  fun validate(path: String): Boolean {
    if (!path.isEmpty()) {
      userEditedPath = true
    }

    sdkDirectoryValidationResult = forAndroidSdkLocation().validate(Paths.get(path))

    var severity = sdkDirectoryValidationResult.severity
    val ok = severity == Validator.Severity.OK
    var message = sdkDirectoryValidationResult.message.takeUnless { ok }

    if (ok) {
      val filesystem = getTargetFilesystem(path)

      if (filesystem != null && filesystem.getFreeSpace() < this.componentsSize) {
        severity = Validator.Severity.ERROR
        message = "Target drive does not have enough free space."
      } else if (isNonEmptyNonSdk(path)) {
        severity = Validator.Severity.WARNING
        message =
          "Target folder is neither empty nor does it point to an existing SDK installation."
      } else if (isExistingSdk(path)) {
        severity = Validator.Severity.WARNING
        message =
          "An existing Android SDK was detected. The setup wizard will only download missing or outdated SDK components."
      }
    }

    setError(severity.icon, message.takeIf { userEditedPath })

    if (loading) {
      return false
    }
    return sdkDirectoryValidationResult.severity != Validator.Severity.ERROR
  }

  val componentsSize: Long
    get() = rootNode.childrenToInstall.sumOf { it.downloadSize }

  abstract fun setError(icon: Icon?, message: String?)

  fun isStepVisible(isCustomInstall: Boolean, path: String): Boolean {
    when {
      // If we showed it once due to a validation error (e.g. if we had an invalid path on the
      // standard setup path), we want to be sure it shows again (e.g. if we fix the path and then
      // go backward and forward). Otherwise, the experience is confusing.
      wasForcedVisible -> return true
      mode.hasValidSdkLocation() -> return false
      isCustomInstall -> return true
      else -> {
        validate(path)

        wasForcedVisible = sdkDirectoryValidationResult.severity != Validator.Severity.OK
        return wasForcedVisible
      }
    }
  }

  fun warnIfRequiredComponentsUnavailable() {
    if (!areAllRequiredComponentsAvailable(rootNode)) {
      Messages.showWarningDialog(
        "Some required components are not available.\n" +
          "You can continue, but some functionality may not work correctly until they are installed.",
        "Required Component Missing",
      )
    }
  }

  /**
   * Handles updates to the SDK path, triggering SDK component loading and refreshing the UI. This
   * method is called when the user selects a new SDK installation location. It updates the internal
   * SDK handler, loads the SDK components available at the new location, and updates the UI to
   * reflect the available components. The loading process happens on a background thread to avoid
   * blocking the UI.
   *
   * @param sdkPath The new SDK path selected by the user. Should not be empty.
   * @param modalityState The modality state to use when invoking UI updates. This ensures that UI
   *   updates happen on the correct thread and with the appropriate modality.
   * @return `true` if the SDK path was actually updated and processing started, `false` if the
   *   provided path is the same as the current path and no update was necessary.
   */
  fun onPathUpdated(sdkPath: String, modalityState: ModalityState): Boolean {
    val sdkLocation = File(sdkPath)
    val currentSdkLocation = localSdkHandlerProperty.get().location?.toFile()
    if (!FileUtil.filesEqual(currentSdkLocation, sdkLocation)) {
      if (sdkPath.isEmpty()) {
        // When setting the SDK location in tests, it first triggers the state update with an empty
        // string before triggering it with the updated value. If we try to load the SDK manager
        // with an empty string, it hangs for a long time (40+ seconds), making the tests slow.
        return false
      }

      val localHandler =
        AndroidSdkHandler.getInstance(
          AndroidLocationsSingleton,
          localSdkHandlerProperty.get().toCompatiblePath(sdkLocation),
        )
      localSdkHandlerProperty.set(localHandler)

      val progress = StudioLoggerProgressIndicator(javaClass)
      startLoading()
      loadingJob?.cancel()
      loadingJob =
        coroutineScope.launch(Dispatchers.Default) {
          localHandler
            .getRepoManager(progress)
            .loadSynchronously(
              RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
              null,
              listOf(
                RepoLoadedListener {
                  rootNode.updateState(localHandler)
                  coroutineScope.launch(Dispatchers.EDT + modalityState.asContextElement()) {
                    stopLoading()
                  }
                }
              ),
              listOf(
                Runnable {
                  coroutineScope.launch(Dispatchers.EDT + modalityState.asContextElement()) {
                    loadingError()
                  }
                }
              ),
              StudioProgressRunner(false, false, "Finding Available SDK Components", project),
              StudioDownloader(),
              StudioSettingsController.getInstance(),
            )
          coroutineScope.launch(Dispatchers.EDT + modalityState.asContextElement()) {
            reloadLicenseAgreementStep()
          }
        }

      return true
    }

    return false
  }

  abstract fun reloadLicenseAgreementStep()
}
