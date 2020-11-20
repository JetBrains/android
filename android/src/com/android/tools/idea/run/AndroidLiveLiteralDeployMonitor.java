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
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
 */
class AndroidLiveLiteralDeployMonitor {

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
    LiveLiteralsService.Companion.getInstance(project).setEnabled(false);
    if (!StudioFlags.COMPOSE_DEPLOY_LIVE_LITERALS.get()) {
      return null;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), packageName);
    return () -> {
      LiveLiteralsService.Companion.getInstance(project).addOnLiteralsChangedListener(
        (Disposable) () -> {},
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
          LOGGER.info("Invoking Deployer.updateLiveLiteral for %s", packageName);
          deployer.updateLiveLiteral(installer, adb, packageName, params);

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
  @VisibleForTesting
  static String qualifyNameToHelperClassName(String name) {
    String helper = name;

    // Skip all the .<anonymous> placehold names since those are not used in naming the helper class.
    //  com.example.compose.MainActivity.Greeting.<anonymous>.<anonymous>.<anonymous> to
    //  com.example.compose.MainActivity.Greeting
    while (helper.endsWith(".<anonymous>")) {
      helper = helper.substring(0, helper.length() - ".<anonymous>".length());
    }

    // Skip over the composible function name.
    //  com.example.compose.MainActivity.Greeting to
    //  com.example.compose.MainActivity
    helper = helper.substring(0, helper.lastIndexOf("."));

    // The compiler will always name the helper class LiveLiterals$FooKt so add "Kt" even if we are looking at non-outer functions.
    //  com.example.compose.MainActivity
    //  com.example.compose.MainActivityKt
    if (!helper.endsWith("Kt")) {
      helper += "Kt";
    }

    // Append LiveLiterals$ as prefix for the helper.
    //  com.example.compose.MainActivityKt to
    //  com.example.compose.LiveLiterals$MainActivity
    helper = helper.substring(0, helper.lastIndexOf('.') + 1) +
             "LiveLiterals$" + helper.substring(helper.lastIndexOf('.') + 1);
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
