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
package com.android.tools.idea.welcome.wizard;

import com.android.annotations.concurrency.UiThread;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.SdkComponentTreeNode;
import com.android.tools.idea.welcome.install.InstallableSdkComponentTreeNode;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.nio.file.Paths;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UiThread
public abstract class SdkComponentsStepController {
  private final @Nullable Project myProject;
  private final @NotNull FirstRunWizardMode myMode;
  private final @NotNull SdkComponentTreeNode myRootNode;
  private final @NotNull ObjectValueProperty<AndroidSdkHandler> myLocalSdkHandlerProperty;

  private boolean myUserEditedPath = false;
  private PathValidator.Result mySdkDirectoryValidationResult;
  private boolean myWasForcedVisible = false;
  private boolean myLoading;

  public SdkComponentsStepController(
    @Nullable Project project,
    @NotNull FirstRunWizardMode mode,
    @NotNull SdkComponentTreeNode rootNode,
    @NotNull ObjectValueProperty<AndroidSdkHandler> localHandlerProperty
  ) {
    myProject = project;
    myMode = mode;
    myRootNode = rootNode;
    myLocalSdkHandlerProperty = localHandlerProperty;
  }

  public void startLoading() {
    myLoading = true;
    onLoadingStarted();
  }

  public void stopLoading() {
    myLoading = false;
    onLoadingFinished();
  }

  public void loadingError() {
    myLoading = false;
    onLoadingError();
  }

  public abstract void onLoadingStarted();
  public abstract void onLoadingFinished();
  public abstract void onLoadingError();

  public boolean validate(@NotNull String path) {
    if (!StringUtil.isEmpty(path)) {
      myUserEditedPath = true;
    }

    mySdkDirectoryValidationResult = PathValidator.forAndroidSdkLocation().validate(Paths.get(path));

    @NotNull Validator.Severity severity = mySdkDirectoryValidationResult.getSeverity();
    boolean ok = severity == Validator.Severity.OK;
    @Nullable String message = ok ? null : mySdkDirectoryValidationResult.getMessage();

    if (ok) {
      File filesystem = SdkComponentsStepUtils.getTargetFilesystem(path);

      if (!(filesystem == null || filesystem.getFreeSpace() > getComponentsSize())) {
        severity = Validator.Severity.ERROR;
        message = "Target drive does not have enough free space.";
      }
      else if (SdkComponentsStepUtils.isNonEmptyNonSdk(path)) {
        severity = Validator.Severity.WARNING;
        message = "Target folder is neither empty nor does it point to an existing SDK installation.";
      }
      else if (SdkComponentsStepUtils.isExistingSdk(path)) {
        severity = Validator.Severity.WARNING;
        message = "An existing Android SDK was detected. The setup wizard will only download missing or outdated SDK components.";
      }
    }

    setError(severity.getIcon(), myUserEditedPath ? message : null);

    if (myLoading) {
      return false;
    }
    return mySdkDirectoryValidationResult.getSeverity() != Validator.Severity.ERROR;
  }

  public long getComponentsSize() {
    long size = 0;
    for (InstallableSdkComponentTreeNode component : myRootNode.getChildrenToInstall()) {
      size += component.getDownloadSize();
    }
    return size;
  }

  public abstract void setError(@Nullable Icon icon, @Nullable String message);

  public boolean isStepVisible(boolean isCustomInstall, @NotNull String path) {
    if (myWasForcedVisible) {
      // If we showed it once due to a validation error (e.g. if we had a invalid path on the standard setup path),
      // we want to be sure it shows again (e.g. if we fix the path and then go backward and forward). Otherwise the experience is
      // confusing.
      return true;
    }
    else if (myMode.hasValidSdkLocation()) {
      return false;
    }

    if (isCustomInstall) {
      return true;
    }

    validate(path);

    myWasForcedVisible = mySdkDirectoryValidationResult.getSeverity() != Validator.Severity.OK;
    return myWasForcedVisible;
  }

  public void warnIfRequiredComponentsUnavailable() {
    if (!SdkComponentTreeNode.areAllRequiredComponentsAvailable(myRootNode)) {
      Messages.showWarningDialog(
        "Some required components are not available.\n" +
        "You can continue, but some functionality may not work correctly until they are installed.",
        "Required Component Missing");
    }
  }

  /**
   * Handles updates to the SDK path, triggering SDK component loading and refreshing the UI.  This method
   * is called when the user selects a new SDK installation location. It updates the internal SDK handler,
   * loads the SDK components available at the new location, and updates the UI to reflect the available
   * components.  The loading process happens on a background thread to avoid blocking the UI.
   *
   * @param sdkPath The new SDK path selected by the user.  Should not be empty.
   * @param modalityState The modality state to use when invoking UI updates.  This ensures that UI updates
   *                      happen on the correct thread and with the appropriate modality.
   * @return {@code true} if the SDK path was actually updated and processing started, {@code false} if the
   *         provided path is the same as the current path and no update was necessary.
   */
  public boolean onPathUpdated(@NotNull String sdkPath, @NotNull ModalityState modalityState) {
    File sdkLocation = new File(sdkPath);
    if (!FileUtil.filesEqual(myLocalSdkHandlerProperty.get().getLocation().toFile(), sdkLocation)) {
      if (sdkPath.isEmpty()) {
        // When setting the SDK location in tests, it first triggers the state update with an empty string
        // before triggering it with the updated value. If we try to load the SDK manager with an empty string,
        // it hangs for a long time (40+ seconds), making the tests slow.
        return false;
      }

      AndroidSdkHandler localHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, myLocalSdkHandlerProperty.get().toCompatiblePath(sdkLocation));
      myLocalSdkHandlerProperty.set(localHandler);

      StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
      startLoading();

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        localHandler.getSdkManager(progress)
          .loadSynchronously(
            RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
            null,
            ImmutableList.of(packages -> {
              myRootNode.updateState(localHandler);
              ApplicationManager.getApplication().invokeLater(this::stopLoading, modalityState);
            }),
            ImmutableList.of(() -> ApplicationManager.getApplication().invokeLater(this::loadingError, modalityState)),
            new StudioProgressRunner(false, false, "Finding Available SDK Components", myProject),
            new StudioDownloader(),
            StudioSettingsController.getInstance()
          );
        ApplicationManager.getApplication().invokeLater(this::reloadLicenseAgreementStep, modalityState);
      });

      return true;
    }

    return false;
  }

  public abstract void reloadLicenseAgreementStep();
}
