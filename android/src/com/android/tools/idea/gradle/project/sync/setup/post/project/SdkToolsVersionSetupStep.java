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

import static com.android.SdkConstants.FD_TOOLS;
import static com.android.tools.idea.sdk.VersionCheck.MIN_TOOLS_REV;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_SDK_VERSION_INSTALLED;
import static com.intellij.notification.NotificationType.INFORMATION;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Slow;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.gradle.internal.impldep.org.simpleframework.util.thread.DirectExecutor;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdkToolsVersionSetupStep extends ProjectSetupStep {
  private static final com.android.repository.api.ProgressIndicator REPO_LOGGER =
    new StudioLoggerProgressIndicator(SdkToolsVersionSetupStep.class);

  @NotNull private final IdeSdks myIdeSdks;
  @NotNull private final Supplier<? extends RepoManager> myRepoManagerSupplier;
  @NotNull private final Supplier<? extends ExecutorService> myExecutorServiceSupplier;
  private volatile boolean myNewSdkVersionToolsInfoAlreadyShown;
  private volatile boolean myNewSdkVersionToolsInfoCheckInProgress;


  @SuppressWarnings("unused") // Instantiated by IDEA
  public SdkToolsVersionSetupStep() {
    this(IdeSdks.getInstance(), REPO_MANAGER_SUPPLIER, () -> ApplicationManager.getApplication().isUnitTestMode()
                                                             ? MoreExecutors.newDirectExecutorService()
                                                             : AppExecutorUtil.getAppExecutorService());
  }

  @VisibleForTesting
  SdkToolsVersionSetupStep(
      @NotNull IdeSdks ideSdks,
      @NotNull Supplier<? extends RepoManager> repoManagerSupplier,
      @NonNull Supplier<? extends ExecutorService> executorServiceSupplier) {
    myIdeSdks = ideSdks;
    myRepoManagerSupplier = repoManagerSupplier;
    myExecutorServiceSupplier = executorServiceSupplier;
  }

  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    if (myNewSdkVersionToolsInfoAlreadyShown || myNewSdkVersionToolsInfoCheckInProgress) {
      // If we're already running a check for this, we return this.
      // If the pop-up has been shown (and dismissed by the user) already, we don't try to show again.
      return;
    }

    File androidHome = myIdeSdks.getAndroidSdkPath();
    if (androidHome != null) {
      // checkToolsPackage can end-up doing some I/O to look for the tools package, so we offload it to a background thread.
      myNewSdkVersionToolsInfoCheckInProgress = true;
      myExecutorServiceSupplier.get().submit(() -> {
        if (!checkToolsPackage(project, androidHome)) {
          InstallSdkToolsHyperlink hyperlink = new InstallSdkToolsHyperlink(MIN_TOOLS_REV);
          String message = "Version " + MIN_TOOLS_REV + " or later is required.";
          AndroidNotification.getInstance(project).showBalloon("Android SDK Tools", message, INFORMATION, hyperlink);
          myNewSdkVersionToolsInfoAlreadyShown = true;
        }
        myNewSdkVersionToolsInfoCheckInProgress = false;
      });
    }
  }

  @Slow
  private boolean checkToolsPackage(@NotNull Project project, @NotNull File androidHome) {
    // First we use VersionCheck since it's less expensive and directly checks only the "tools" folder under androidHome directory.
    return VersionCheck.isCompatibleVersion(androidHome) || checkToolsPackageUsingRepoManager(project);
  }

  private boolean checkToolsPackageUsingRepoManager(@NotNull Project project) {
    RepoManager mgr = myRepoManagerSupplier.get();
    if (mgr == null) {
      return false;
    }

    mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
             new StudioProgressRunner(true, false, "Finding Available SDK Components", project),
             null, // We force null downloader, to not touch the network at all.
             StudioSettingsController.getInstance(), true);
    return mgr.getPackages().getLocalPackages().containsKey("tools");
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
  public static class InstallSdkToolsHyperlink extends NotificationHyperlink {
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
        GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_QF_SDK_VERSION_INSTALLED);
      }
    }

    @VisibleForTesting
    @NotNull
    Revision getVersion() {
      return myVersion;
    }
  }

  /** Supply a RepoManager from {@link AndroidSdks}. */
  private static final Supplier<RepoManager> REPO_MANAGER_SUPPLIER = () -> {
      AndroidSdkData data = AndroidSdks.getInstance().tryToChooseAndroidSdk();
      if (data == null) {
        return null;
      }

      RepoManager mgr = data.getSdkHandler().getSdkManager(REPO_LOGGER);
      if (mgr.getLocalPath() == null) {
        return null;
      }

      return mgr;
    };
}
