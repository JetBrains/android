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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.SdkConstants;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.SettingsController;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.repository.api.RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingCMakeErrorHandler extends SyncErrorHandler {
  @Nullable private final AndroidSdkHandler mySdkHandler;
  @NotNull private final Downloader myDownloader;
  @NotNull private final SettingsController mySettingsController;
  @Nullable private final RemotePackage myCmakePackage;

  public MissingCMakeErrorHandler() {
    this(null, new StudioDownloader(), StudioSettingsController.getInstance());
  }

  @VisibleForTesting
  MissingCMakeErrorHandler(@Nullable AndroidSdkHandler sdkHandler,
                           @NotNull Downloader downloader,
                           @Nullable SettingsController settingsController) {
    mySdkHandler = sdkHandler;
    myDownloader = downloader;
    mySettingsController = settingsController;
    myCmakePackage = getCmakePackage();
  }

  @Nullable
  private RemotePackage getCmakePackage() {
    // We need to statically fetch the SDK handler each time because the location might change.
    // TODO: remove the need for doing this each time.
    AndroidSdkHandler sdkHandler = mySdkHandler;
    if (sdkHandler == null) {
      sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    }

    ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    sdkHandler.getSdkManager(progress).loadSynchronously(DEFAULT_EXPIRATION_PERIOD_MS, progress, myDownloader,
                                                         mySettingsController);
    RemotePackage cmakePackage = sdkHandler.getLatestRemotePackageForPrefix(SdkConstants.FD_CMAKE, false, progress);
    return cmakePackage;
  }

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull NotificationData notification, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && getFirstLineMessage(text).startsWith("Failed to find CMake.") && myCmakePackage != null) {
      updateUsageTracker();
      return "Failed to find CMake.";
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    NotificationHyperlink installCMakeLink = getInstallCMakeNotificationHyperlink(myCmakePackage.getPath());
    hyperlinks.add(installCMakeLink);
    return hyperlinks;
  }

  @NotNull
  private NotificationHyperlink getInstallCMakeNotificationHyperlink(@NotNull String cmakePackagePath) {
    return new NotificationHyperlink("install.cmake", "Install CMake and sync project") {
      @Override
      protected void execute(@NotNull Project project) {
        ModelWizardDialog dialog = createDialogForPaths(project, ImmutableList.of(cmakePackagePath));
        if (dialog != null && dialog.showAndGet()) {
          GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, null);
        }
      }
    };
  }
}