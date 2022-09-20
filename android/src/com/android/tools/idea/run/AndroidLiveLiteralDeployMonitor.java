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
import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.literals.LiteralReference;
import com.android.tools.idea.editors.literals.LiteralUsageReference;
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration;
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler;
import com.android.tools.idea.editors.literals.LiveLiteralsService;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
 */
public class AndroidLiveLiteralDeployMonitor {

  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveLiteralDeployMonitor.class));

  // Keys contains all projects currently with a listener registered in the editor.
  // We don't want to register multiple times because we would end up with multiple events per single update.
  // The value mapped to each object contains a listing of an unique string identifier to each LL and the
  // timestamp which that literal was seen updated.
  private static final Map<Project, Map<String, Long>> ACTIVE_PROJECTS = new HashMap<>();

  // Maps "Project featuring Composable" -> "Devices running that project" (with alive process).
  private static final Multimap<Project, String> ACTIVE_DEVICES = ArrayListMultimap.create();

  private static class LiteralChangesListener implements Disposable {
    private Project project;
    private final String packageName;

    private LiteralChangesListener(Project project, String packageName) {
      this.project = project;
      this.packageName = packageName;
    }

    @Override
    public void dispose() {
      ACTIVE_PROJECTS.remove(project);
      ACTIVE_DEVICES.removeAll(project);
      project = null;
    }

    public Unit onLiteralsChanged(List<? extends LiteralReference> changes) {
      long timestamp = System.nanoTime();
      AndroidLiveLiteralDeployMonitor.pushLiteralsToDevice(project, packageName, (List<LiteralReference>) changes, timestamp);
      return Unit.INSTANCE;
    }
  }

  /**
   * Returns a callback that, upon successful deployment of an Android application, can be invoked to
   * starts a service to monitor live literal changes in such project and live deploy to the device.
   *
   * This method mostly create a call back and it is locked to be thread-safe.
   */
  public static Callable<?> getCallback(Project project, String packageName, IDevice device) {
    String deviceId = device.getSerialNumber();
    LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(deviceId + "#" + packageName);

    // Live Edit will eventually replace Live Literals. They conflict with each other the only way the enable
    // one is to to disable the other.
    if (!LiveEditApplicationConfiguration.Companion.getInstance().isLiveLiterals()) {
      return null;
    }

    if (!supportLiveLiteral(device)) {
      return null;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), packageName);

    return () -> {
      synchronized (ACTIVE_PROJECTS) {
        if (!ACTIVE_PROJECTS.containsKey(project)) {
          LiveLiteralsService service = LiveLiteralsService.Companion.getInstance(project);
          LiteralChangesListener listener = new LiteralChangesListener(project, packageName);
          service.addOnLiteralsChangedListener(service, listener::onLiteralsChanged);
          Disposer.register(service, listener);
          ACTIVE_PROJECTS.put(project, new HashMap<>());
        } else {
          ACTIVE_PROJECTS.get(project).clear();
        }
      }

      LiveLiteralsMonitorHandler.DeviceType deviceType;
      if (device.isEmulator()) {
        deviceType = LiveLiteralsMonitorHandler.DeviceType.EMULATOR;
      }
      else {
        deviceType = LiveLiteralsMonitorHandler.DeviceType.PHYSICAL;
      }

      ACTIVE_DEVICES.put(project, deviceId);

      // Event a listener has been installed, we always need to re-enable as certain action can disable the service (such as a rebuild).
      LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(deviceId + "#" + packageName, deviceType);

      return null;
    };
  }

  private static void pushLiteralsToDevice(Project project, String packageName, List<LiteralReference> changes, long timestamp) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), packageName);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (ACTIVE_PROJECTS) {
        List<AndroidSessionInfo> sessions = AndroidSessionInfo.findActiveSession(project);
        if (sessions == null) {
          LOGGER.info("No running session found for %s", packageName);
          return;
        }

        // List of all literals and the timestamps which they were last updated.
        Map<String, Long> lastUpdate = ACTIVE_PROJECTS.get(project);

        // For error reporting, assume every client is missing until we successfully update everything.
        HashSet<String> missingClients = Sets.newHashSet(ACTIVE_DEVICES.get(project));

        for (AndroidSessionInfo session : sessions) {
          @NotNull ExecutionTarget target = session.getExecutionTarget();
          if (!(target instanceof AndroidExecutionTarget)) {
            continue;
          }
          for (IDevice iDevice : ((AndroidExecutionTarget)target).getRunningDevices()) {
            // We need to do this check once more. The reason is that we have one listener per project.
            // That means a listener is in charge of multiple devices. If we are here this only means,
            // at least one active device support live literals.
            if (!supportLiveLiteral(iDevice)) {
              continue;
            }

            missingClients.remove(iDevice.getSerialNumber());

            AdbClient adb = new AdbClient(iDevice, LOGGER);
            MetricsRecorder metrics = new MetricsRecorder();

            // TODO: Disable this if we are not on DAEMON mode? Or we should take whatever mode Studio Flag tells us to take.
            Installer installer = new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
            LiveUpdateDeployer deployer = new LiveUpdateDeployer();
            List<LiveUpdateDeployer.UpdateLiveLiteralParam> params = new ArrayList<>();
            for (LiteralReference change : changes) {
              for (LiteralUsageReference use : change.getUsages()) {
                // TODO: The key should be computed by Studio.
                // Once we reach production, we should NOT need to compute offset and use qualifyNameToHelperClassName.
                String key = "";
                int offset = use.getRange().getStartOffset();
                String helper = qualifyNameToHelperClassName(use.getFqName().toString());
                String type = constTypeToJvmType(change.getConstantValue());
                LiveUpdateDeployer.UpdateLiveLiteralParam param = new LiveUpdateDeployer.UpdateLiveLiteralParam(
                  key, offset, helper, type, change.getConstantValue().toString());

                String lookup = getLiteralTimeStampKey(adb.getSerial(), helper, offset);
                if (lastUpdate.getOrDefault(lookup,0L) < timestamp) {
                  params.add(param);
                  lastUpdate.put(lookup, timestamp);
                  LOGGER.info("Live Literal Value of type %s updated to %s",
                              type, change.getConstantValue().toString());
                } else {
                  LOGGER.warning("Live Literal Value of type %s not updated to %s since it outdated",
                                 type, change.getConstantValue().toString());
                }
              }
            }
            LOGGER.info("Invoking Deployer.updateLiveLiteral for %s", packageName);
            String deviceId = adb.getSerial() + "#" + packageName;
            String pushKey = String.valueOf(params.hashCode());
            LiveLiteralsService.getInstance(project).liveLiteralPushStarted(deviceId, pushKey);
            List<LiveUpdateDeployer.UpdateLiveEditError> errors = deployer.updateLiveLiteral(installer, adb, packageName, params);
            LiveLiteralsService.getInstance(project)
                .liveLiteralPushed(deviceId, pushKey, errors.stream().map(
                  e -> new LiveLiteralsMonitorHandler.Problem(LiveLiteralsMonitorHandler.Problem.Severity.ERROR, e.getMessage())
                ).collect(Collectors.toList()));
          }
        }

        for (String missingId : missingClients) {
          LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(missingId + "#" + packageName);
          ACTIVE_DEVICES.get(project).remove(missingId); // Modification is safe here as we are still under the ACTIVE_PROJECT lock.
        }
      }
    });
  }

  private static boolean supportLiveLiteral(IDevice device) {
    return device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.R);
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
    if (helper.indexOf(".") == -1) {
      // This normally would not happen since all functions in bytecode belong to
      // a class and therefore needs a least one dot (namespace.function).
      // It might be possible that the file is in the middle of editing and
      // is syntactically incorrect Kotlin so the editor is confused.
      // We are not going to crash with invalid index and instead we give in a
      // namespace. The agent will just warn the user about it and we can get a
      // bug report if that happens.
      helper = "no.name.space.from.LiveLiteralMonitor";
    } else {
      helper = helper.substring(0, helper.lastIndexOf("."));
    }

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
    Path path;
    if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      path = StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/deploy/installer/android-installer");
    } else {
      path = Paths.get(PathManager.getHomePath(), "plugins/android/resources/installer");
    }
    return path.toString();
  }

  public static String getLiteralTimeStampKey(String deviceId, String helper, int offset) {
    return String.format(Locale.ENGLISH,"[device=%s]-%s[offset=%d]", deviceId, helper, offset);
  }
}
