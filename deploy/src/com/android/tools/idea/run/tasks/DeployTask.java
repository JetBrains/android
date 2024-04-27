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

import static com.android.tools.idea.run.ApkInfo.AppInstallOption.FORCE_QUERYABLE;
import static com.android.tools.idea.run.ApkInfo.AppInstallOption.GRANT_ALL_PERMISSIONS;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.InstallOptions;
import com.android.tools.deployer.model.App;
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.ApkInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class DeployTask extends AbstractDeployTask {

  private static final Logger LOG = Logger.getInstance(DeployTask.class);
  public static final String ID = "DEPLOY";

  private final String[] userInstallOptions;
  private final boolean installOnAllUsers;

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param project  the project that this task is running within.
   * @param packages a collection of apks representing the packages this task will deploy.
   */
  public DeployTask(@NotNull Project project,
                    @NotNull Collection<ApkInfo> packages,
                    String userInstallOptions,
                    boolean installOnAllUsers,
                    boolean alwaysInstallWithPm,
                    Computable<String> installPathProvider) {
    super(project, packages, false, alwaysInstallWithPm, installPathProvider);
    if (userInstallOptions != null && !userInstallOptions.isEmpty()) {
      userInstallOptions = userInstallOptions.trim();
      this.userInstallOptions = userInstallOptions.split("\\s");
    } else {
      this.userInstallOptions = new String[0];
    }
    this.installOnAllUsers = installOnAllUsers;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }


  @Override
  protected Deployer.Result perform(IDevice device,
                                    Deployer deployer,
                                    @NotNull ApkInfo apkInfo,
                                    @NotNull Canceller canceller) throws DeployerException {
    // All installations default to allow debuggable APKs
    InstallOptions.Builder options = InstallOptions.builder().setAllowDebuggable();

    // We default to install only on the current user because we run only apps installed on the
    // current user. Installing on "all" users causes the device to only update on users that the app
    // is already installed, failing to run if it's not installed on the current user.
    if (!installOnAllUsers && device.getVersion().isGreaterOrEqualThan(24)) {
      options.setInstallOnUser(InstallOptions.CURRENT_USER);
    }

    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user
    // interaction/display. However, regular installation will not grant some permissions until the next device reboot.
    // Installing with "-g" guarantees that the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      options.setGrantAllPermissions();
    }

    // To avoid permission pop-up during instrumented test, an app can request to have all permisssion granted by default.
    Set<ApkInfo.AppInstallOption> requiredInstallOptions = apkInfo.getRequiredInstallOptions();
    if (requiredInstallOptions.contains(GRANT_ALL_PERMISSIONS)
        && device.getVersion().isGreaterOrEqualThan(GRANT_ALL_PERMISSIONS.minSupportedApiLevel)) {
      options.setGrantAllPermissions();
    }

    // Some test services APKs running during intrumented tests require to be visible to allow Binder communication.
    if (requiredInstallOptions.contains(FORCE_QUERYABLE)
        && device.getVersion().isGreaterOrEqualThan(FORCE_QUERYABLE.minSupportedApiLevel)) {
      options.setForceQueryable();
    }

    // API 28 changes how the instant property is set on app install.
    // We can add --full to pmInstallOptions to restore the previous behavior,
    // where an app's instant flag is reset on install, and avoid errors installing
    // a non-instant app over its instant version with the device still treating
    // the app as instant.
    if (device.getVersion().isGreaterOrEqualThan(28)) {
      options.setInstallFullApk();
    }

    // After installing and after returning from the install request, the package manager issues a force-stop to the
    // app it has just finished installing. This force-stop will compete with the "am start" issued from Studio to
    // occasionally prevent the app from starting (if the force-stop from pm is issued after our "am start").
    //
    // Since the app has already been stopped from Studio, requesting "--dont-kill" prevent the package manager from
    // issuing a "force-stop", but still yield the expecting behavior that app is restarted after install. Note that
    // this functionality is only valid for Android Nougat or above.
    boolean isDontKillSupported = device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.N);
    if (isDontKillSupported) {
      options.setDontKill();
    }

    // We can just append this, since all these options get string-joined in the end anyways.
    if (userInstallOptions != null) {
      options.setUserInstallOptions(userInstallOptions);
    }

    // Skip verification if possible.
    options.setSkipVerification(device, apkInfo.getApplicationId());

    LOG.info("Installing application: " + apkInfo.getApplicationId());
    Deployer.InstallMode installMode = Deployer.InstallMode.DELTA;
    if (!StudioFlags.DELTA_INSTALL.get()) {
        installMode = Deployer.InstallMode.FULL;
    }

    options.setCancelChecker(canceller);

    App app = getAppToInstall(apkInfo);
    Deployer.Result result = deployer.install(app, options.build(), installMode);

    // Manually force-stop the application if we set --dont-kill above.
    if (!result.skippedInstall && isDontKillSupported) {
      device.forceStop(app.getAppId());
    }
    return result;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Install";
  }


  @NotNull
  @Override
  protected String createSkippedApkInstallMessage(List<String> skippedApkList, boolean all) {
    if (all) {
      return "App restart successful without requiring a re-install.";
    } else {
      return "App restart successful without re-installing the following APK(s): " +
             skippedApkList.stream().collect(Collectors.joining(", "));
    }
  }
}