/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.sdk;

import static com.android.SdkConstants.FN_ADB;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.setModuleSdk;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.util.AndroidBuildCommonUtils.platformToolPath;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.CommonBundle;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class AndroidSdkUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkUtils");

  public static final String DEFAULT_JDK_NAME = "JDK";
  public static final String ADB_PATH_PROPERTY = "android.adb.path";

  private AndroidSdkUtils() {
  }

  /**
   * Creates a new IDEA Android SDK. User is prompt for the paths of the Android SDK and JDK if necessary.
   *
   * @param sdkPath the path of Android SDK.
   * @return the created IDEA Android SDK, or {@null} if it was not possible to create it.
   */
  @Nullable
  public static Sdk createNewAndroidPlatform(@Nullable String sdkPath, boolean promptUser) {
    Sdk jdk = IdeSdks.getInstance().getJdk();
    if (sdkPath != null && jdk != null) {
      sdkPath = toSystemIndependentName(sdkPath);
      IAndroidTarget target = findBestTarget(sdkPath);
      if (target != null) {
        Sdk sdk =
          AndroidSdks.getInstance().create(target, new File(sdkPath), AndroidSdks.getInstance().chooseNameForNewLibrary(target), jdk, true);
        if (sdk != null) {
          return sdk;
        }
      }
    }
    String jdkPath = jdk == null ? null : jdk.getHomePath();
    return promptUser ? promptUserForSdkCreation(null, sdkPath, jdkPath) : null;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull String sdkPath) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    if (sdkData != null) {
      IAndroidTarget[] targets = sdkData.getTargets();
      if (targets.length == 1) {
        return targets[0];
      }
      return findBestTarget(targets);
    }
    return null;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull IAndroidTarget[] targets) {
    IAndroidTarget bestTarget = null;
    int maxApiLevel = -1;
    for (IAndroidTarget target : targets) {
      AndroidVersion version = target.getVersion();
      if (target.isPlatform() && !version.isPreview() && version.getApiLevel() > maxApiLevel) {
        bestTarget = target;
        maxApiLevel = version.getApiLevel();
      }
    }
    return bestTarget;
  }

  public static String getTargetPresentableName(@NotNull IAndroidTarget target) {
    return target.isPlatform() ? target.getName() : target.getName() + " (" + target.getVersionName() + ')';
  }

  public static boolean targetHasId(@NotNull IAndroidTarget target, @NotNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  /**
   * Reload SDK information and update the source root of the SDK.
   */
  public static void updateSdkSourceRoot(@NotNull Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    if (platform != null) {
      IAndroidTarget target = platform.getTarget();
      SdkModificator sdkModificator = sdk.getSdkModificator();
      sdkModificator.removeRoots(SOURCES);
      AndroidSdks.getInstance().findAndSetPlatformSources(target, sdkModificator);
      sdkModificator.commitChanges();
    }
  }

  @Nullable
  private static Sdk promptUserForSdkCreation(@Nullable IAndroidTarget target,
                                              @Nullable String androidSdkPath,
                                              @Nullable String jdkPath) {
    Ref<Sdk> sdkRef = new Ref<>();
    Runnable task = () -> {
      SelectSdkDialog dlg = new SelectSdkDialog(jdkPath, androidSdkPath);
      dlg.setModal(true);
      if (dlg.showAndGet()) {
        Sdk sdk = createNewAndroidPlatform(target, dlg.getAndroidHome(), dlg.getJdkHome());
        sdkRef.set(sdk);
        if (sdk != null) {
          IdeSdks.updateWelcomeRunAndroidSdkAction();
        }
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      task.run();
      return sdkRef.get();
    }
    application.invokeAndWait(task, ModalityState.any());
    return sdkRef.get();
  }

  @Nullable
  private static Sdk createNewAndroidPlatform(@Nullable IAndroidTarget target, @NotNull String androidSdkPath, @NotNull String jdkPath) {
    if (isNotEmpty(jdkPath)) {
      jdkPath = toSystemIndependentName(jdkPath);
      Sdk jdk = Jdks.getInstance().createJdk(jdkPath);
      if (jdk != null) {
        androidSdkPath = toSystemIndependentName(androidSdkPath);
        if (target == null) {
          target = findBestTarget(androidSdkPath);
        }
        if (target != null) {
          return AndroidSdks.getInstance()
            .create(target, new File(androidSdkPath), AndroidSdks.getInstance().chooseNameForNewLibrary(target), jdk, true);
        }
      }
    }
    return null;
  }

  public static void openModuleDependenciesConfigurable(@NotNull Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  /**
   * Finds and returns the adb executable.
   * <p>
   *   ADB executable can come from 2 places: The project's Android SDK or from {@link #ADB_PATH_PROPERTY},
   *   with the latter having higher priority.
   * </p>
   * @param project the project from which SDK path will be determined, if the SDK is required to find ADB.
   * @return ADB file in SDK path specified by ADB_PATH_PROPERTY or the project, or default SDK if project is null
   */
  @Nullable
  @Deprecated
  public static File getAdb(@Nullable Project project) {
    return findAdb(project).adbPath;
  }

  public static @NotNull AdbSearchResult findAdb(@Nullable Project project) {
    List<String> searchedPaths = new ArrayList<>(3);
    String path = System.getProperty(ADB_PATH_PROPERTY);
    searchedPaths.add(String.format("ADB_PATH_PROPERTY (%s): '%s'", ADB_PATH_PROPERTY, Strings.isNullOrEmpty(path) ? "<not set>" : path));
    if (path != null) {
      Path adb = Paths.get(path);
      if (Files.exists(adb)) {
        return new AdbSearchResult(adb, searchedPaths);
      }
    }

    if (project != null) {
      AndroidSdkData data = getProjectSdkData(project);
      if (data == null) {
        data = getFirstAndroidModuleSdkData(project);
        searchedPaths.add(String.format("Android SDK location from first Android Module in Project: %s",
                                        data == null ? "<not present>" : String.format("'%s'", data.getLocation())));
      } else {
        searchedPaths.add(String.format("Android SDK location from Project: '%s'", data.getLocation()));
      }
      if (data != null) {
        Path adb = data.getLocation().resolve(platformToolPath(FN_ADB));
        if (Files.exists(adb)) {
          return new AdbSearchResult(adb, searchedPaths);
        }
      }
    }

    // If project is null, or non-android project (e.g. react-native), we'll use the global default path
    File sdkPath = IdeSdks.getInstance().getAndroidSdkPath();
    if (sdkPath != null) {
      Path dir = sdkPath.toPath();
      searchedPaths.add(String.format("Android SDK location from global settings: '%s'", dir));
      Path adb = dir.resolve(platformToolPath(FN_ADB));
      if (Files.exists(adb)) {
        return new AdbSearchResult(adb, searchedPaths);
      }
    }

    String pathProperty = EnvironmentUtil.getValue("PATH");
    if (pathProperty != null) {
      for (String dir : pathProperty.split(System.getProperty("path.separator"))) {
        searchedPaths.add(String.format("From the PATH environment variable: '%s'", dir));
        Path adb = Paths.get(dir, FN_ADB);
        if (Files.exists(adb)) {
          return new AdbSearchResult(adb, searchedPaths);
        }
      }
    }

    return new AdbSearchResult(null, searchedPaths);
  }

  @Nullable
  public static AndroidSdkData getFirstAndroidModuleSdkData(Project project) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      AndroidPlatform androidPlatform = AndroidPlatform.getInstance(facet.getModule());
      if (androidPlatform != null) {
        return androidPlatform.getSdkData();
      }
    }
    return null;
  }

  @Nullable
  public static AndroidSdkData getProjectSdkData(Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null) {
      AndroidPlatform platform = AndroidPlatform.getInstance(projectSdk);
      return platform != null ? platform.getSdkData() : null;
    }
    return null;
  }

  public static boolean isAndroidSdkAvailable() {
    return AndroidSdks.getInstance().tryToChooseAndroidSdk() != null;
  }

  /**
   * For a given target, returns a brief user-facing string that describes the platform, including the API level, platform version number,
   * and codename. Does the right thing with pre-release platforms.
   */
  @NotNull
  public static String getTargetLabel(@NotNull IAndroidTarget target) {
    if (!target.isPlatform()) {
      return String.format("%1$s (API %2$s)", target.getFullName(), target.getVersion().getApiString());
    }
    AndroidVersion version = target.getVersion();
    if (version.isPreview()) {
      return String.format(Locale.US, "API %d+: %s", target.getVersion().getApiLevel(), target.getName());
    }
    String name = SdkVersionInfo.getAndroidName(target.getVersion().getApiLevel());
    if (isNotEmpty(name)) {
      return name;
    }
    String release = target.getProperty("ro.build.version.release"); //$NON-NLS-1$
    if (release != null) {
      return String.format(Locale.US, "API %1$d: Android %2$s", version.getApiLevel(), release);
    }
    return String.format(Locale.US, "API %1$d", version.getApiLevel());
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    AndroidSdkData data = getProjectSdkData(project);
    if (data == null) {
      data = getFirstAndroidModuleSdkData(project);
    }
    if (data == null) {
      LOG.warn("Fail to find project SDK data.");
    }

    AndroidDebugBridge bridge = null;
    boolean retry;
    do {
      AdbSearchResult searchResult = findAdb(project);
      if (searchResult.adbPath == null) {
        NotificationGroup
          .balloonGroup("Android Debug Bridge (adb)")
          .createNotification(
            "Unable to locate adb in project/module settings. Locations searched:<br>" + String.join("<br>", searchResult.searchedPaths),
            NotificationType.ERROR)
          .setImportant(true)
          .notify(project);
        LOG.warn("Unable to locate adb.");
        return null;
      }

      Future<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(searchResult.adbPath);
      MyMonitorBridgeConnectionTask task = new MyMonitorBridgeConnectionTask(project, future);
      ProgressManager.getInstance().run(task);

      if (task.wasCanceled()) { // if the user cancelled the dialog
        return null;
      }

      retry = false;
      try {
        bridge = future.get();
      }
      catch (InterruptedException e) {
        break;
      }
      catch (ExecutionException e) {
        // timed out waiting for bridge, ask the user what to do
        String message = "ADB not responding. If you'd like to retry, then please manually kill \"" + FN_ADB + "\" and click 'Restart'";
        retry = Messages.showYesNoDialog(project, message, CommonBundle.getErrorTitle(), "&Restart", "&Cancel", Messages.getErrorIcon()) ==
                Messages.YES;
      }
    }
    while (retry);

    return bridge;
  }

  public static boolean isAndroidSdkManagerEnabled() {
    boolean sdkManagerDisabled = SystemProperties.getBooleanProperty("android.studio.sdk.manager.disabled", false);
    return !sdkManagerDisabled;
  }

  public static class AdbSearchResult {
    public final @Nullable File adbPath;
    public final @NotNull List<String> searchedPaths;

    public AdbSearchResult(@Nullable Path adbPath, @NotNull List<String> searchedPaths) {
      this.adbPath = adbPath == null ? null : adbPath.toFile();
      this.searchedPaths = searchedPaths;
    }
  }

  private static class MyMonitorBridgeConnectionTask extends Task.Modal {
    private final Future<AndroidDebugBridge> myFuture;
    private boolean myCancelled; // set/read only on EDT

    public MyMonitorBridgeConnectionTask(@Nullable Project project, Future<AndroidDebugBridge> future) {
      super(project, "Waiting for adb", true);
      myFuture = future;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      while (!myFuture.isDone()) {
        try {
          myFuture.get(200, TimeUnit.MILLISECONDS);
        }
        catch (Exception ignored) {
          // all we need to know is whether the future completed or not..
        }

        if (indicator.isCanceled()) {
          return;
        }
      }
    }

    @Override
    public void onCancel() {
      myCancelled = true;
    }

    public boolean wasCanceled() {
      return myCancelled;
    }
  }
}
