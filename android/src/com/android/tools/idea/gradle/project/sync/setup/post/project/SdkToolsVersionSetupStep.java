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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.repository.Revision;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FD_TOOLS;
import static com.android.tools.idea.sdk.VersionCheck.MIN_TOOLS_REV;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.notification.NotificationType.INFORMATION;

public class SdkToolsVersionSetupStep extends ProjectSetupStep {
  @NotNull private final IdeSdks myIdeSdks;
  private volatile boolean myNewSdkVersionToolsInfoAlreadyShown;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public SdkToolsVersionSetupStep() {
    this(IdeSdks.getInstance());
  }

  @VisibleForTesting
  SdkToolsVersionSetupStep(@NotNull IdeSdks ideSdks) {
    myIdeSdks = ideSdks;
  }

  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    if (myNewSdkVersionToolsInfoAlreadyShown) {
      return;
    }

    File androidHome = myIdeSdks.getAndroidSdkPath();
    if (androidHome != null && !VersionCheck.isCompatibleVersion(androidHome)) {
      InstallSdkToolsHyperlink hyperlink = new InstallSdkToolsHyperlink(MIN_TOOLS_REV);
      String message = "Version " + MIN_TOOLS_REV + " or later is required.";
      AndroidNotification.getInstance(project).showBalloon("Android SDK Tools", message, INFORMATION, hyperlink);
      myNewSdkVersionToolsInfoAlreadyShown = true;
    }
  }

  @Override
  public boolean invokeOnFailedSync() {
    return true;
  }

  @VisibleForTesting
  boolean isNewSdkVersionToolsInfoAlreadyShown() {
    return myNewSdkVersionToolsInfoAlreadyShown;
  }

  @VisibleForTesting
  static class InstallSdkToolsHyperlink extends NotificationHyperlink {
    @NotNull private final Revision myVersion;

    InstallSdkToolsHyperlink(@NotNull Revision version) {
      super("install.sdk.tools", "Install latest SDK Tools");
      myVersion = version;
    }

    @Override
    protected void execute(@NotNull Project project) {
      List<String> requested = Lists.newArrayList();
      if (myVersion.getMajor() == 23) {
        Revision minBuildToolsRev = new Revision(20, 0, 0);
        requested.add(DetailsTypes.getBuildToolsPath(minBuildToolsRev));
      }
      requested.add(FD_TOOLS);
      ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
      if (dialog != null && dialog.showAndGet()) {
        GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
      }
    }

    @VisibleForTesting
    @NotNull
    Revision getVersion() {
      return myVersion;
    }
  }
}
