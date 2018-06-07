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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.android.SdkConstants.FD_CMAKE;
import static com.android.repository.api.RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class InstallCMakeHyperlink extends NotificationHyperlink {
  public InstallCMakeHyperlink() {
    super("install.cmake", "Install CMake and sync project");
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
        Collection<RemotePackage> cmakePackages = packages.getRemotePackagesForPrefix(FD_CMAKE);
        if (!cmakePackages.isEmpty()) {
          RemotePackage cmakePackage;
          if (cmakePackages.size() == 1) {
            cmakePackage = getFirstItem(cmakePackages);
          }
          else {
            cmakePackage = sdkHandler.getLatestRemotePackageForPrefix(FD_CMAKE, false /* do not allow preview */, progressIndicator);
          }
          if (cmakePackage != null) {
            ModelWizardDialog dialog = createDialogForPaths(project, ImmutableList.of(cmakePackage.getPath()));
            if (dialog != null && dialog.showAndGet()) {
              GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
            }
            return;
          }
          notifyCMakePackageNotFound(project);
        }
      }, ModalityState.any());
    Runnable onError = () -> ApplicationManager.getApplication().invokeLater(
      () -> notifyCMakePackageNotFound(project),
      ModalityState.any());
    sdkManager.load(DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(onComplete), ImmutableList.of(onError), progressRunner,
                    new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  private static void notifyCMakePackageNotFound(@NotNull Project project) {
    Messages.showErrorDialog(project, "Failed to obtain CMake package", "Gradle Sync");
  }
}
