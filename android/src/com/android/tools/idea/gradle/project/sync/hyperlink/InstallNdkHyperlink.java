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

import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.SelectNdkDialog;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.android.SdkConstants.FD_NDK;
import static com.android.repository.api.RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

public class InstallNdkHyperlink extends NotificationHyperlink {
  private static final String ERROR_TITLE = "Gradle Sync Error";

  public InstallNdkHyperlink() {
    super("install.ndk", "Install NDK and sync project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    File path = getNdkPath(project);
    if (path != null) {
      // Try to install SDK in local.properties.
      SelectNdkDialog dialog = new SelectNdkDialog(path.getPath(), false, true /* show "download" link */);
      dialog.setModal(true);
      if (dialog.showAndGet() && setNdkPath(project, dialog.getAndroidNdkPath())) {
        // Saving NDK path is successful.
        GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
      }
      return;
    }

    // There is no path. Try installing from SDK.
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();

    StudioLoggerProgressIndicator progressIndicator = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = sdkHandler.getSdkManager(progressIndicator);

    StudioProgressRunner progressRunner = new StudioProgressRunner(false, false, "Loading Remote SDK", project);
    RepoManager.RepoLoadedCallback onComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {
        Map<String, RemotePackage> remotePackages = packages.getRemotePackages();
        RemotePackage ndkPackage = remotePackages.get(FD_NDK);
        if (ndkPackage != null) {
          ModelWizardDialog dialog = createDialogForPaths(project, ImmutableList.of(ndkPackage.getPath()));
          if (dialog != null && dialog.showAndGet()) {
            GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
          }
          return;
        }
        notifyNdkPackageNotFound(project);
      }, ModalityState.any());
    Runnable onError = () -> ApplicationManager.getApplication().invokeLater(
      () -> notifyNdkPackageNotFound(project),
      ModalityState.any());
    sdkManager.load(DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(onComplete), ImmutableList.of(onError), progressRunner,
                    new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  @Nullable
  private static File getNdkPath(@NotNull Project project) {
    try {
      return new LocalProperties(project).getAndroidNdkPath();
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file of Project '%1$s'", project.getName());
      Logger.getInstance(InstallNdkHyperlink.class).info(msg, e);
    }
    return null;
  }

  private static boolean setNdkPath(@NotNull Project project, @Nullable String ndkPath) {
    LocalProperties localProperties;
    try {
      localProperties = new LocalProperties(project);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file of Project '%1$s':\n%2$s", project.getName(), e.getMessage());
      Messages.showErrorDialog(project, msg, ERROR_TITLE);
      return false;
    }
    try {
      localProperties.setAndroidNdkPath(ndkPath == null ? null : new File(ndkPath));
      localProperties.save();
    }
    catch (IOException e) {
      String msg =
        String.format("Unable to save local.properties file of Project '%1$s: %2$s", localProperties.getPropertiesFilePath().getPath(),
                      e.getMessage());
      Messages.showErrorDialog(project, msg, ERROR_TITLE);
      return false;
    }
    return true;
  }

  private static void notifyNdkPackageNotFound(@NotNull Project project) {
    Messages.showErrorDialog(project, "Failed to obtain NDK package", ERROR_TITLE);
  }
}
