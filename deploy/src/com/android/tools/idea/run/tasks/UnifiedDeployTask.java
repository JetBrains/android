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
import com.android.tools.deployer.DebuggerCodeSwapAdapter;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.Trace;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.deployer.DeployerException;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.util.DebuggerHelper;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnifiedDeployTask implements LaunchTask {

  public static final int MIN_API_VERSION = 27;

  public enum DeployType {
    // When there is no previous APK Install.
    INSTALL("Install"),

    // Only update Java classes. No resource change, no activity restarts.
    CODE_SWAP("Code swap"),

    // Everything, including resource changes.
    FULL_SWAP("Code and resource swap");

    @NotNull
    private final String myName;

    DeployType(@NotNull String name) {
      myName = name;
    }

    @NotNull
    public String getName() {
      return myName;
    }
  }

  private static final String ID = "UNIFIED_DEPLOY";

  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("UnifiedDeployTask", ToolWindowId.RUN);

  @NotNull private final Project myProject;

  @NotNull private final Collection<ApkInfo> myApks;

  @Nullable private DeploymentErrorHandler myDeploymentErrorHandler;

  public static final Logger LOG = Logger.getInstance(UnifiedDeployTask.class);

  private final DeployType type;

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param project         the project that this task is running within.
   * @param apks            the apks to deploy.
   * @param swap            whether to perform swap on a running app or to just install and restart.
   * @param activityRestart whether to restart activity upon swap.
   */
  public UnifiedDeployTask(@NotNull Project project, @NotNull Collection<ApkInfo> apks, DeployType type) {
    myProject = project;
    myApks = apks;
    this.type = type;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing APK";
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

  private String getLocalInstaller() {
    File path = new File(PathManager.getHomePath(), "plugins/android/resources/installer");
    if (!path.exists()) {
      // Development mode
      path = new File(PathManager.getHomePath(), "../../bazel-bin/tools/base/deploy/installer/android");
    }
    return path.getAbsolutePath();
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    LogWrapper logger = new LogWrapper(LOG);
    Trace.begin("UnifiedDeployeTask.perform()");
    for (ApkInfo apk : myApks) {
      LOG.info("Processing application:" + apk.getApplicationId());

      List<String> paths = apk.getFiles().stream().map(apkunit -> apkunit.getApkFile().getPath()).collect(Collectors.toList());
      AdbClient adb = new AdbClient(device, logger);
      Installer installer = new Installer(getLocalInstaller(), adb, logger);
      DeploymentService service = ServiceManager.getService(myProject, DeploymentService.class);
      Deployer deployer = new Deployer(adb, service.getDexDatabase(), service.getTaskRunner(), installer);
      try {
        switch (type) {
          case INSTALL:
            Trace.begin("Unified.install");
            deployer.install(paths);
            Trace.end();
            return true;
          case CODE_SWAP:
            Trace.begin("Unified.codeSwap");
            deployer.codeSwap(apk.getApplicationId(), paths, makeDebuggerAdapter(device, apk));
            Trace.end();
            break;
          case FULL_SWAP:
            Trace.begin("Unified.fullSwap");
            deployer.fullSwap(apk.getApplicationId(), paths);
            Trace.end();
            break;
          default:
            throw new UnsupportedOperationException("Not supported deployment type");
        }
      }
      catch (IOException e) {
        myDeploymentErrorHandler = new DeploymentErrorHandler("Error deploying APK");
        LOG.error("Error deploying APK", e);
        return false;
      }
      catch (DeployerException e) {
        myDeploymentErrorHandler = new DeploymentErrorHandler(type, e.getError(), e.getMessage());
        return false;
      }
      NOTIFICATION_GROUP.createNotification(type.getName() + " successful", NotificationType.INFORMATION)
        .setImportant(false).notify(myProject);
    }
    return true;
  }

  private DebuggerCodeSwapAdapter makeDebuggerAdapter(IDevice device, ApkInfo apk) throws IOException {
    if (!DebuggerHelper.hasDebuggersAttached(myProject)) {
      return null;
    }
    int pid = device.getClient(apk.getApplicationId()).getClientData().getPid();
    DebuggerCodeSwapAdapter adapter = new DebuggerCodeSwapAdapter() {
      @Override
      public void performSwap() {
        DebuggerHelper.startDebuggerTasksOnProject(myProject, ((project, session) -> {
          this.performSwapImpl(session.getProcess().getVirtualMachineProxy().getVirtualMachine());}));
      }

      @Override
      public void disableBreakPoints() {
        DebuggerHelper.waitFor(DebuggerHelper.disableBreakPoints(myProject));
      }

      @Override
      public void enableBreakPoints() {
        DebuggerHelper.waitFor(DebuggerHelper.enableBreakPoints(myProject));
      }
    };
    adapter.addAttachedPid(pid);
    return adapter;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }
}
