/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.Installer;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.deployer.DeployerException;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.IdeService;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnifiedDeployTask implements LaunchTask {

  public static final int MIN_API_VERSION = 26;
  private static final String ID = "UNIFIED_DEPLOY";
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("UnifiedDeployTask", ToolWindowId.RUN);

  @NotNull private final Project myProject;
  @NotNull private final DeployAction myAction;
  @NotNull private final Map<String, List<File>> myPackages;
  @Nullable private DeploymentErrorHandler myDeploymentErrorHandler;

  public static final Logger LOG = Logger.getInstance(UnifiedDeployTask.class);

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param project         the project that this task is running within.
   * @param action          the deployment action that this task will take.
   * @param packages        a map of application ids to apks representing the packages this task will deploy.
   */
  private UnifiedDeployTask(
    @NotNull Project project, @NotNull DeployAction action, @NotNull Map<String, List<File>> packages) {
    myProject = project;
    myAction = action;
    myPackages = packages;
  }

  @NotNull
  @Override
  public String getDescription() {
    return myAction.getName();
  }

  @Nullable
  @Override
  public String getFailureReason() {
    return myDeploymentErrorHandler != null ? myDeploymentErrorHandler.getFormattedErrorString() : null;
  }

  @Nullable
  @Override
  public NotificationListener getNotificationListener() {
    return myDeploymentErrorHandler != null ? myDeploymentErrorHandler.getNotificationListener() : null;
  }

  @Override
  public int getDuration() {
    return 20;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    LogWrapper logger = new LogWrapper(LOG);

    AdbClient adb = new AdbClient(device, logger);
    Installer installer = new AdbInstaller(getLocalInstaller(), adb, logger);
    DeploymentService service = DeploymentService.getInstance(myProject);
    IdeService ideService = new IdeService(myProject);
    Deployer deployer = new Deployer(adb, service.getDexDatabase(), service.getTaskRunner(), installer, ideService, logger);

    for (Map.Entry<String, List<File>> entry : myPackages.entrySet()) {
      String applicationId = entry.getKey();
      List<File> apkFiles = entry.getValue();
      try {
        myAction.deploy(myProject, device, deployer, applicationId, apkFiles);
      } catch (DeployerException e) {
        myDeploymentErrorHandler = new DeploymentErrorHandler(myAction, e);
        return false;
      }
    }

    NOTIFICATION_GROUP.createNotification(myAction.getName() + " successful", NotificationType.INFORMATION)
      .setImportant(false).notify(myProject);

    return true;
  }

  private String getLocalInstaller() {
    File path = new File(PathManager.getHomePath(), "plugins/android/resources/installer");
    if (!path.exists()) {
      // Development mode
      path = new File(PathManager.getHomePath(), "../../bazel-bin/tools/base/deploy/installer/android");
    }
    return path.getAbsolutePath();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Project project;
    private DeployAction action;
    private ImmutableMap.Builder<String, List<File>> packages;

    private Builder() {
      this.project = null;
      this.action = null;
      this.packages = ImmutableMap.builder();
    }

    public Builder setProject(@NotNull Project project) {
      this.project = project;
      return this;
    }

    public Builder setAction(DeployAction action) {
      this.action = action;
      return this;
    }

    public Builder addPackage(String applicationId, List<File> apkFiles) {
      this.packages.put(applicationId, apkFiles);
      return this;
    }

    public UnifiedDeployTask build() {
      Preconditions.checkNotNull(project);
      Preconditions.checkNotNull(action);
      return new UnifiedDeployTask(project, action, packages.build());
    }
  }
}
