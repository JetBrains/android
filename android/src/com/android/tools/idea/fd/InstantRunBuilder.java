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
package com.android.tools.idea.fd;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.AppState;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.run.BeforeRunBuilder;
import com.android.tools.idea.gradle.run.GradleTaskRunner;
import com.android.tools.idea.run.AndroidRunConfigContext;
import com.android.tools.idea.run.InstalledApkCache;
import com.android.tools.idea.run.InstalledPatchCache;
import com.google.common.hash.HashCode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.android.builder.model.AndroidProject.OPTIONAL_COMPILATION_STEPS;

public class InstantRunBuilder implements BeforeRunBuilder {
  private static final Logger LOG = Logger.getInstance(InstantRunBuilder.class);

  @Nullable private final IDevice myDevice;
  private final InstantRunContext myInstantRunContext;
  private final AndroidRunConfigContext myRunContext;
  private final InstantRunTasksProvider myTasksProvider;
  private final RunAsValidator myRunAsValidator;
  private final InstalledApkCache myInstalledApkCache;
  private final InstantRunClientDelegate myInstantRunClientDelegate;

  public InstantRunBuilder(@Nullable IDevice device,
                           @NotNull InstantRunContext instantRunContext,
                           @NotNull AndroidRunConfigContext runConfigContext,
                           @NotNull InstantRunTasksProvider tasksProvider,
                           @NotNull RunAsValidator runAsValidator) {
    this(device,
         instantRunContext,
         runConfigContext,
         tasksProvider,
         runAsValidator,
         ServiceManager.getService(InstalledApkCache.class),
         new InstantRunClientDelegate() {
         });
  }

  @VisibleForTesting
  InstantRunBuilder(@Nullable IDevice device,
                    @NotNull InstantRunContext instantRunContext,
                    @NotNull AndroidRunConfigContext runConfigContext,
                    @NotNull InstantRunTasksProvider tasksProvider,
                    @NotNull RunAsValidator runAsValidator,
                    @NotNull InstalledApkCache installedApkCache,
                    @NotNull InstantRunClientDelegate delegate) {
    myDevice = device;
    myInstantRunContext = instantRunContext;
    myRunContext = runConfigContext;
    myTasksProvider = tasksProvider;
    myRunAsValidator = runAsValidator;
    myInstalledApkCache = installedApkCache;
    myInstantRunClientDelegate = delegate;
  }

  @Override
  public boolean build(@NotNull GradleTaskRunner taskRunner, @NotNull List<String> commandLineArguments) throws InterruptedException,
                                                                                                                InvocationTargetException {
    BuildModeChoice buildModeChoice = getBuildMode();
    if (buildModeChoice.mode != BuildMode.HOT) {
      LOG.info(buildModeChoice.mode + ": " + buildModeChoice.why);
    }

    List<String> args = new ArrayList<>(commandLineArguments);

    FileChangeListener.Changes fileChanges = myInstantRunContext.getFileChangesAndReset();
    if (buildModeChoice.mode == BuildMode.HOT || buildModeChoice.mode == BuildMode.COLD) { // build for incremental deploy
      List<String> incrementalArgs = new ArrayList<>(args);
      incrementalArgs.add(getInstantDevProperty(buildModeChoice.mode, fileChanges));
      boolean success = taskRunner.run(myTasksProvider.getIncrementalBuildTasks(), null, incrementalArgs);
      if (!success) {
        return false;
      }

      InstantRunBuildInfo buildInfo = myInstantRunContext.getInstantRunBuildInfo();
      if (buildInfo != null && !needsFullRebuild(buildInfo)) {
        return true;
      }

      // fall through and do a full build
    }

    args.add(getInstantDevProperty(BuildMode.FULL, null));

    List<String> tasks = new LinkedList<>();
    if (buildModeChoice.mode == BuildMode.CLEAN) {
      tasks.addAll(myTasksProvider.getCleanAndGenerateSourcesTasks());
    }
    tasks.addAll(myTasksProvider.getFullBuildTasks());
    return taskRunner.run(tasks, null, args);
  }

  @NotNull
  private BuildModeChoice getBuildMode() {
    if (myRunContext.isCleanRerun()) {
      return new BuildModeChoice(BuildMode.CLEAN, InstantRunBuildCauses.USER_REQUESTED_CLEAN_RERUN);
    }

    String fullBuildReason = needsFullBuild(myDevice);
    if (fullBuildReason != null) {
      return new BuildModeChoice(BuildMode.FULL, fullBuildReason);
    }

    String coldSwapReason = needsColdswapPatches(myDevice);
    if (coldSwapReason != null) {
      return new BuildModeChoice(BuildMode.COLD, coldSwapReason);
    }

    return new BuildModeChoice(BuildMode.HOT, "");
  }

  @Nullable
  private String needsFullBuild(@Nullable IDevice device) {
    if (device == null) {
      return InstantRunBuildCauses.NO_DEVICE;
    }

    if (!buildTimestampsMatch(device)) {
      return InstantRunBuildCauses.MISMATCHING_TIMESTAMPS;
    }

    AndroidVersion deviceVersion = device.getVersion();
    if (!InstantRunManager.isInstantRunCapableDeviceVersion(deviceVersion)) {
      return "Instant Run is disabled: <br>" +
             "Instant Run does not support deployment to targets with API levels 14 or below.<br><br>" +
             "To use Instant Run, deploy to a target with API level 15 or higher.";
    }

    if (!InstantRunManager.hasLocalCacheOfDeviceData(device, myInstantRunContext)) {
      return InstantRunBuildCauses.FIRST_INSTALLATION_TO_DEVICE;
    }

    // Normally, all files are saved when Gradle runs (in GradleInvoker#executeTasks). However, we need to save the files
    // a bit earlier than that here (turning the Gradle file save into a no-op) because the we need to check whether the
    // manifest file or a resource referenced from the manifest has changed since the last build.
    if (ApplicationManager.getApplication() != null) { // guard against invoking this in unit tests
      GradleInvoker.saveAllFilesSafely();
    }

    if (manifestChanged(device)) {
      return InstantRunBuildCauses.MANIFEST_CHANGED;
    }

    if (manifestResourceChanged(device)) {
      return InstantRunBuildCauses.MANIFEST_RESOURCE_CHANGED;
    }

    if (!isAppRunning(device)) { // freeze-swap scenario
      if (!deviceVersion.isGreaterOrEqualThan(21)) { // don't support cold swap on API < 21
        return InstantRunBuildCauses.COLD_SWAP_REQUIRES_API21;
      }

      if (!myRunAsValidator.hasWorkingRunAs(device)) {
        return InstantRunBuildCauses.NO_RUN_AS;
      }
    }

    return null;
  }

  @Nullable
  private String needsColdswapPatches(@NotNull IDevice device) {
    if (!isAppRunning(device)) {
      return InstantRunBuildCauses.APP_NOT_RUNNING;
    }

    if (myInstantRunContext.usesMultipleProcesses()) {
      return InstantRunBuildCauses.MULTI_PROCESS_APP;
    }

    return null;
  }

  private boolean isAppRunning(@NotNull IDevice device) {
    // for an app to considered running, in addition to it actually running, it also must be launched by the same executor
    // (i.e. run followed by run, or debug followed by debug). If the last session was for running, and this session is for debugging,
    // then very likely the process is in the middle of being terminated since we don't reuse the same run session.
    boolean isAppRunning = myInstantRunClientDelegate.isAppInForeground(device, myInstantRunContext);
    return isAppRunning && myRunContext.isSameExecutorAsPreviousSession();
  }

  private enum BuildMode {
    HOT,    // incremental build
    COLD,   // incremental build w/ cold swap patches
    FULL,   // full build
    CLEAN,  // clean build
  }

  private static class BuildModeChoice {
    @NotNull public final BuildMode mode;
    @NotNull public final String why;

    private BuildModeChoice(@NotNull BuildMode mode, @NotNull String why) {
      this.mode = mode;
      this.why = why;
    }
  }

  private static String getInstantDevProperty(@NotNull BuildMode buildMode, @Nullable FileChangeListener.Changes changes) {
    StringBuilder sb = new StringBuilder(50);
    sb.append("-P");
    sb.append(OPTIONAL_COMPILATION_STEPS);
    sb.append("=INSTANT_DEV");

    if (buildMode == BuildMode.HOT) {
      appendChangeInfo(sb, changes);
    }
    else {
      sb.append(",RESTART_ONLY");
    }

    return sb.toString();
  }

  private static void appendChangeInfo(@NotNull StringBuilder sb, @Nullable FileChangeListener.Changes changes) {
    if (changes == null) {
      return;
    }

    if (!changes.nonSourceChanges) {
      if (changes.localResourceChanges) {
        sb.append(",LOCAL_RES_ONLY");
      }
      if (changes.localJavaChanges) {
        sb.append(",LOCAL_JAVA_ONLY");
      }
    }
  }

  private static boolean needsFullRebuild(@NotNull InstantRunBuildInfo buildInfo) {
    if (!buildInfo.getArtifacts().isEmpty()) {
      return false;
    }

    // If we are forced to do a cold swap, but we didn't get any artifacts, then issue a rebuild. This happens in a few scenarios:
    //    a) There was a change in the resource id assignment, which caused some resource id used in the manifest to change. In such a case,
    //       the verifier status is set to BINARY_MANIFEST_CHANGED
    //    b) The build detected a cold swap scenario (e.g. field added), but we don't support cold swap at the target device's API level.
    // Note that this check looks at the verifier status being set because the verifier status could be empty if there were no changes,
    // but the buildInfo.canHotswap() treats that differently
    return !buildInfo.getVerifierStatus().isEmpty();
  }

  /**
   * Returns whether the device has the same timestamp as the existing build on disk.
   */
  private boolean buildTimestampsMatch(@NotNull IDevice device) {
    InstantRunBuildInfo instantRunBuildInfo = myInstantRunContext.getInstantRunBuildInfo();
    String localTimestamp = instantRunBuildInfo == null ? null : instantRunBuildInfo.getTimeStamp();
    if (StringUtil.isEmpty(localTimestamp)) {
      InstantRunManager.LOG.info("Local build timestamp is empty!");
      return false;
    }

    if (InstantRunClient.USE_BUILD_ID_TEMP_FILE) {
      // If the build id is saved in /data/local/tmp, then the build id isn't cleaned when the package is uninstalled.
      // So we first check that the app is still installed. Note: this doesn't yet guarantee that you have uninstalled and then
      // re-installed a different apk with the same package name..
      // https://code.google.com/p/android/issues/detail?id=198715

      String pkgName = myInstantRunContext.getApplicationId();

      // check whether the package is installed on the device: we do this by checking the package manager for whether the app exists,
      // but we could potentially simplify this to just checking whether the package folder exists
      if (myInstalledApkCache.getInstallState(device, pkgName) == null) {
        InstantRunManager.LOG.info("Package " + pkgName + " was not detected on the device.");
        return false;
      }
    }

    String deviceBuildTimestamp = myInstantRunClientDelegate.getDeviceBuildTimestamp(device, myInstantRunContext);

    InstantRunManager.LOG.info(String.format("Build timestamps: Local: %1$s, Device: %2$s", localTimestamp, deviceBuildTimestamp));
    return localTimestamp.equals(deviceBuildTimestamp);
  }

  /**
   * Returns true if the manifest has changed since the last manifest push to the device.
   */
  private boolean manifestChanged(@NotNull IDevice device) {
    InstalledPatchCache cache = myInstantRunContext.getInstalledPatchCache();

    HashCode current = myInstantRunContext.getManifestHash();
    HashCode installed = cache.getInstalledManifestTimestamp(device, myInstantRunContext.getApplicationId());

    return installed == null || !installed.equals(current);
  }

  /**
   * Returns true if a resource referenced from the manifest has changed since the last manifest push to the device.
   */
  public boolean manifestResourceChanged(@NotNull IDevice device) {
    InstalledPatchCache cache = myInstantRunContext.getInstalledPatchCache();

    // See if the resources have changed.
    // Since this method can be called before we've built, we're looking at the previous
    // manifest now. However, manifest edits are treated separately (see manifestChanged()),
    // so the goal here is to look for the referenced resources from the manifest
    // (when the manifest itself hasn't been edited) and see if any of *them* have changed.
    HashCode currentHash = myInstantRunContext.getManifestResourcesHash();
    HashCode installedHash = cache.getInstalledManifestResourcesHash(device, myInstantRunContext.getApplicationId());
    if (installedHash != null && !installedHash.equals(currentHash)) {
      return true;
    }

    return false;
  }

  /**
   * {@link InstantRunClientDelegate} adds a level of indirection to accessing instant run specific data from the device,
   * in order to facilitate mocking it out in tests.
   */
  interface InstantRunClientDelegate {
    default String getDeviceBuildTimestamp(@NotNull IDevice device, @NotNull InstantRunContext instantRunContext) {
      return InstantRunClient.getDeviceBuildTimestamp(device, instantRunContext.getApplicationId(), InstantRunManager.ILOGGER);
    }

    /**
     * @return whether the app associated with the given module is already running on the given device and listening for IR updates
     */
    default boolean isAppInForeground(@NotNull IDevice device, @NotNull InstantRunContext context) {
      return InstantRunManager.getInstantRunClient(context).getAppState(device) == AppState.FOREGROUND;
    }
  }
}
