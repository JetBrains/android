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

import static com.android.SdkConstants.FD_NDK;
import static com.android.SdkConstants.FD_NDK_SIDE_BY_SIDE;
import static com.android.repository.api.RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_NDK_INSTALLED;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.issues.processor.FixNdkVersionProcessor;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
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
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstallNdkHyperlink extends NotificationHyperlink {
  private static final String ERROR_TITLE = "Gradle Sync Error";
  private final List<VirtualFile> buildFiles;
  private final String searchPrefix;

  public InstallNdkHyperlink(@Nullable String preferredVersion, @NotNull List<VirtualFile> buildFiles) {
    super("install.ndk", preferredVersion == null
                         ? "Install latest NDK and sync project"
                         : String.format("Install NDK '%s' and sync project", preferredVersion));
    this.buildFiles = buildFiles;
    this.searchPrefix = preferredVersion == null
                        ? FD_NDK_SIDE_BY_SIDE
                        : FD_NDK_SIDE_BY_SIDE + ";" + preferredVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    // Remove any value old value from ndk.dir
    try {
      LocalProperties localProperties = new LocalProperties(project);
      localProperties.setAndroidNdkPath((File)null);
      localProperties.save();
    }
    catch (IOException e) {
      // If we couldn't remove ndk.dir continue on anyway. There will be a diagnostic
      // message from Android Gradle Plugin
    }

    // Install from NDK
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();

    StudioLoggerProgressIndicator progressIndicator = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = sdkHandler.getSdkManager(progressIndicator);

    StudioProgressRunner progressRunner = new StudioProgressRunner(false, false, "Loading Remote SDK", project);
    RepoManager.RepoLoadedCallback onComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {

        String ndkPath = null;
        Revision ndkRevision = null;
        // When NDK side-by-side is enabled, download side-by-side packages
        // go/ndk-sxs
        if (StudioFlags.NDK_SIDE_BY_SIDE_ENABLED.get()) {
          Collection<RemotePackage> ndkPackages =
            packages.getRemotePackagesForPrefix(searchPrefix);
          for (RemotePackage ndkPackage : ndkPackages) {
            if (ndkRevision == null || ndkRevision.compareTo(ndkPackage.getVersion()) < 0) {
              ndkRevision = ndkPackage.getVersion();
              ndkPath = ndkPackage.getPath();
            }
          }
        } else {
          Map<String, RemotePackage> remotePackages = packages.getRemotePackages();
          RemotePackage ndkPackage = remotePackages.get(FD_NDK);
          if (ndkPackage != null) {
            ndkPath = ndkPackage.getPath();
          }
        }
        if (ndkPath != null) {
          ModelWizardDialog dialog = createDialogForPaths(project, ImmutableList.of(ndkPath), true);
          if (dialog != null && dialog.showAndGet()) {
            LocalPackage highestLocalNdk = IdeSdks.getInstance().getHighestLocalNdkPackage(false);
            if (highestLocalNdk != null) {
              ApplicationManager.getApplication().invokeLater(() -> {
                new FixNdkVersionProcessor(project, buildFiles, highestLocalNdk.getVersion().toString()).run();
              });
            } else {
              GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_NDK_INSTALLED);
            }
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

  private static void notifyNdkPackageNotFound(@NotNull Project project) {
    Messages.showErrorDialog(project, "Failed to obtain NDK package", ERROR_TITLE);
  }
}
