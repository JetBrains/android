/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveLiteralDeployer;
import com.android.tools.idea.editors.literals.LiteralReference;
import com.android.tools.idea.editors.literals.LiteralUsageReference;
import com.android.tools.idea.editors.literals.LiveLiteralsService;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
 */
class AndroidLiveLiteralDeployMonitor {

  private static final Object MAPPING_LOCK = new Object();
  private static final Map<Project, List<String>> CONNECTED_PROJECT = new WeakHashMap();

  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveLiteralDeployMonitor.class));


  /**
   * Returns a callback that, upon successful deployment of an Android application, can be invoked to
   * starts a service to monitor live literal changes in such project and live deploy to the device.
   *
   * This method mostly create a call back and it is locked to be thread-safe.
   */
  static Runnable getCallback(Project project, String packageName) {
    synchronized (MAPPING_LOCK) {
      if (!StudioFlags.COMPOSE_DEPLOY_LIVE_LITERALS.get()) {
        return null;
      }

      List<String> packageNames = CONNECTED_PROJECT.get(project);
      if (packageNames == null) {
        packageNames = new ArrayList<>();
        packageNames.add(packageName);
        CONNECTED_PROJECT.put(project, packageNames);
      }
      else {
        if (!packageNames.contains(packageName)) {
          packageNames.add(packageName);
        }
        return null;
      }
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), packageName);
    return () -> {
      LiveLiteralsService.Companion.getInstance(project).addOnLiteralsChangedListener(
        (Disposable) () -> {
          CONNECTED_PROJECT.remove(project);
        },
        (changes) -> {
          AndroidLiveLiteralDeployMonitor.pushLiteralsToDevice(project, packageName, (List<LiteralReference>)changes);
          return null;
        }
      );
      LiveLiteralsService.Companion.getInstance(project).setEnabled(true);
    };
  }

  private static void pushLiteralsToDevice(Project project, String packageName, List<LiteralReference> changes) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), packageName);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
          AdbClient adb = new AdbClient(iDevice, LOGGER);
          MetricsRecorder metrics = new MetricsRecorder();

          // TODO: Use client data from DDMLib.
          // String pkname = session.getClient(device).getClientData().getPackageName();

          // TODO: Disable this if we are not on DAEMON mode? Or we should take whatever mode Studio Flag tells us to take.
          Installer installer = new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
          LiveLiteralDeployer deployer = new LiveLiteralDeployer();
          List<LiveLiteralDeployer.UpdateLiveLiteralParam> params = new ArrayList<>();
          for (LiteralReference change : changes) {
            for (LiteralUsageReference use : change.getUsages()) {
              // TODO: The key should be computed by Studio.
              // Once we reach production, we should NOT need to compute offset and use qualifyNameToHelperClassName.
              String key = "";
              int offset = use.getRange().getStartOffset();
              String helper = qualifyNameToHelperClassName(use.getFqName().toString());
              String type = constTypeToJvmType(change.getConstantValue());
              LiveLiteralDeployer.UpdateLiveLiteralParam param = new LiveLiteralDeployer.UpdateLiveLiteralParam(
                key, offset, helper, type, change.getConstantValue().toString());
              params.add(param);
              LOGGER.info("Live Literal Value of type %s updated to %s", type, change.getConstantValue().toString());
            }
          }

          List<String> packageNames = new ArrayList<>();
          synchronized (MAPPING_LOCK) {
            packageNames.addAll(CONNECTED_PROJECT.getOrDefault(project, ImmutableList.of()));
          }
          for (String targetName : packageNames) {
            LOGGER.info("Invoking Deployer.updateLiveLiteral for %s", packageName);
            deployer.updateLiveLiteral(installer, adb, targetName, params);
          }
        }
      }
    });
  }


  private static String constTypeToJvmType(Object constValue) {
    if (constValue instanceof Character) {
      return "C";
    } else if (constValue instanceof Byte) {
      return "B";
    } else if (constValue instanceof Integer) {
      return "I";
    } else if (constValue instanceof Long) {
      return "J";
    } else if (constValue instanceof Short) {
      return "S";
    } else if (constValue instanceof Boolean) {
      return "B";
    } else if (constValue instanceof Float) {
      return "F";
    } else if (constValue instanceof Double) {
      return "D";
    } else if (constValue instanceof Boolean) {
      return "Z";
    } else {
      return "Ljava/lang/String;";
    }
  }

  // TODO: Temp solution. We should NOT do this.
  //       Compose API / Live Literal service should do the translation and figure out the key instead of us looking this up
  //       on the device in runtime.
  private static String qualifyNameToHelperClassName(String name) {
    String helper = name;
    while (helper.lastIndexOf(".<anonymous>") != -1) {
      helper = helper.substring(0, helper.lastIndexOf(".<anonymous>"));
    }
    helper = helper.substring(0, helper.lastIndexOf("Kt.") + "Kt".length());
    helper = helper.substring(0, helper.lastIndexOf('.') + 1) + "LiveLiterals$" + helper.substring(helper.lastIndexOf('.') + 1);
    return helper;
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
