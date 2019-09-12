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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.android.SdkConstants.FD_CMAKE;
import static com.android.repository.api.RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_CMAKE_INSTALLED;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstallCMakeHyperlink extends NotificationHyperlink {
  /**
   * The version of CMake that this hyperlink will try to install. Null if the cmake version is not
   * important and the latest version included in the SDK should be installed.
   */
  @Nullable private Revision myCmakeVersion;

  /**
   * Constructs a hyperlink to install the default version of CMake in the SDK.
   */
  public InstallCMakeHyperlink() {
    super("install.cmake", "Install CMake");
    myCmakeVersion = null;
  }

  /**
   * Constructs a hyperlink to install a specific version of CMake from the SDK.
   *
   * @param cmakeVersion The version of CMake to install.
   */
  public InstallCMakeHyperlink(@NotNull Revision cmakeVersion) {
    super("install.cmake", "Install CMake " + cmakeVersion.toString());
    this.myCmakeVersion = cmakeVersion;
  }

  @Nullable
  public Revision getCmakeVersion() {
    return myCmakeVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    // We need to statically fetch the SDK handler each time because the location might change.
    // TODO: remove the need for doing this each time.
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();

    StudioLoggerProgressIndicator progressIndicator = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = sdkHandler.getSdkManager(progressIndicator);

    StudioProgressRunner progressRunner = new StudioProgressRunner(false, false, "Loading Remote SDK", project);
    RepoManager.RepoLoadedCallback onComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {
        RemotePackage cmakePackage;
        Collection<RemotePackage> cmakePackages = packages.getRemotePackagesForPrefix(FD_CMAKE);

        if (myCmakeVersion == null) {
          // Install the latest version from the SDK.
          if (cmakePackages.size() == 1) {
            cmakePackage = getFirstItem(cmakePackages);
          }
          else {
            cmakePackage = sdkHandler.getLatestRemotePackageForPrefix(FD_CMAKE, false /* do not allow preview */, progressIndicator);
          }
        }
        else {
          // Install the version the user requested.
          cmakePackage = cmakePackages.stream()
                                      .filter(remotePackage -> remotePackage.getVersion().equals(myCmakeVersion))
                                      .findFirst()
                                      .orElse(null);
        }

        if (cmakePackage != null) {
          // Found: Trigger installation of the package.
          ModelWizardDialog dialog = createDialogForPaths(project, ImmutableList.of(cmakePackage.getPath()), true);
          if (dialog != null && dialog.showAndGet()) {
            GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_CMAKE_INSTALLED);
          }
          return;
        }

        // Either no CMake versions were found, or the requested CMake version was not found.
        notifyCMakePackageNotFound(project, myCmakeVersion);
      }, ModalityState.any());
    Runnable onError = () -> ApplicationManager.getApplication().invokeLater(
      () -> notifyCMakePackageInstallError(project),
      ModalityState.any());
    sdkManager.load(DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(onComplete), ImmutableList.of(onError), progressRunner,
                    new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  /**
   * Displays an error dialog to inform the user that a CMake package was not found.
   *
   * @param project      The current IntelliJ project.
   * @param cmakeVersion The version of CMake that was searched for.
   */
  private static void notifyCMakePackageNotFound(@NotNull Project project, @Nullable Revision cmakeVersion) {
    if (cmakeVersion == null) {
      Messages.showErrorDialog(project, "Failed to obtain CMake package", "Gradle Sync");
    }
    else {
      Messages.showErrorDialog(project, "Failed to obtain CMake package version " + cmakeVersion.toString(), "Gradle Sync");
    }
  }

  /**
   * Displays an error dialog to inform the user that the CMake install failed.
   *
   * @param project THe current IntelliJ project.
   */
  private static void notifyCMakePackageInstallError(@NotNull Project project) {
    Messages.showErrorDialog(project, "Failed to install CMake package", "Gradle Sync");
  }
}
