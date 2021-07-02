/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.execution.ExecutionTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
 */
class AndroidLiveEditDeployMonitor {

  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static final LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveEditDeployMonitor.class));

  // Keys contains all projects currently with a listener registered in the editor.
  // We don't want to register multiple times because we would end up with multiple events per single update.
  // The value mapped to each object contains a listing of an unique string identifier to each LL and the
  // timestamp which that literal was seen updated.
  private static final Map<Project, Map<String, Long>> ACTIVE_PROJECTS = new HashMap<>();

  // Maps "Project featuring Composable" -> "Devices running that project" (with alive process).
  private static final Multimap<Project, String> ACTIVE_DEVICES = ArrayListMultimap.create();

  private static class EditsListener implements Disposable {
    private Project project;
    private final String packageName;

    private EditsListener(Project project, String packageName) {
      this.project = project;
      this.packageName = packageName;
    }

    @Override
    public void dispose() {
      ACTIVE_PROJECTS.remove(project);
      ACTIVE_DEVICES.removeAll(project);
      project = null;
    }

    public Unit onLiteralsChanged(List<? extends LiveEditService.MethodReference> changes) {
      long timestamp = System.nanoTime();
      AndroidLiveEditDeployMonitor.pushEditsToDevice(project, packageName, (List<LiveEditService.MethodReference>) changes, timestamp);
      return Unit.INSTANCE;
    }
  }

  static Runnable getCallback(Project project, String packageName, IDevice device) {
    String deviceId = device.getSerialNumber();

    // Live Edit will eventually replace Live Literals. They conflict with each other the only way the enable
    // one is to to disable the other.
    if (StudioFlags.COMPOSE_DEPLOY_LIVE_LITERALS.get() || !StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()) {
      return null;
    }

    if (!supportLiveEdits(device)) {
      return null;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), packageName);

    return () -> {
      synchronized (ACTIVE_PROJECTS) {
        if (!ACTIVE_PROJECTS.containsKey(project)) {
          LiveEditService service = LiveEditService.Companion.getInstance(project);
          EditsListener listener = new EditsListener(project, packageName);
          service.addOnEditListener(listener::onLiteralsChanged);
          Disposer.register(service, listener);
          ACTIVE_PROJECTS.put(project, new HashMap<>());
        } else {
          ACTIVE_PROJECTS.get(project).clear();
        }
      }

      ACTIVE_DEVICES.put(project, deviceId);
    };
  }

  private static void pushEditsToDevice(Project project, String packageName, List<LiveEditService.MethodReference> changes, long timestamp) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), packageName);

    AppExecutorUtil.createBoundedApplicationPoolExecutor("Live Edit Device Push", 1).submit(() -> {
      synchronized (ACTIVE_PROJECTS) {
        List<AndroidSessionInfo> sessions = AndroidSessionInfo.findActiveSession(project);
        if (sessions == null) {
          LOGGER.info("No running session found for %s", packageName);
          return;
        }

        for (AndroidSessionInfo session : sessions) {
          @NotNull ExecutionTarget target = session.getExecutionTarget();
          if (!(target instanceof AndroidExecutionTarget)) {
            continue;
          }
          for (IDevice iDevice : ((AndroidExecutionTarget)target).getRunningDevices()) {
            // We need to do this check once more. The reason is that we have one listener per project.
            // That means a listener is in charge of multiple devices. If we are here this only means,
            // at least one active device support live edits.
            if (!supportLiveEdits(iDevice)) {
              continue;
            }

            AdbClient adb = new AdbClient(iDevice, LOGGER);
            MetricsRecorder metrics = new MetricsRecorder();

            Installer installer = new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
            LiveUpdateDeployer deployer = new LiveUpdateDeployer();

            new AndroidLiveEditCodeGenerator().compile(project, changes,
                                                       (className, methodName, classData) -> {
              // TODO: Don't fire off one update per class file.
              LiveUpdateDeployer.UpdateLiveEditsParam param =
                new LiveUpdateDeployer.UpdateLiveEditsParam(className, methodName, classData);

              // TODO: Handle Errors
              deployer.updateLiveEdit(installer, adb, packageName, param);
              return Unit.INSTANCE;
            });
          }
        }
      }
    });
  }

  private static boolean supportLiveEdits(IDevice device) {
    return device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.R);
  }

  // TODO: Unify this part.
  private static String getLocalInstaller() {
    File path;
    if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      path = new File(StudioPathManager.getSourcesRoot(), "bazel-bin/tools/base/deploy/installer/android-installer");
    } else {
      path = new File(PathManager.getHomePath(), "plugins/android/resources/installer");
    }
    return path.getAbsolutePath();
  }
}
