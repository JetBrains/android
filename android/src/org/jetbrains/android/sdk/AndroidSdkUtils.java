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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.intellij.CommonBundle;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.actions.AndroidRunDdmsAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.*;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.setModuleSdk;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.android.actions.AndroidEnableAdbServiceAction.setAdbServiceEnabled;
import static org.jetbrains.android.facet.AndroidRootUtil.getProjectPropertyValue;
import static org.jetbrains.android.facet.AndroidRootUtil.getPropertyValue;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.util.AndroidCommonUtils.platformToolPath;
import static org.jetbrains.android.util.AndroidUtils.ANDROID_TARGET_PROPERTY;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidSdkUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkUtils");

  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
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
    Sdk jdk = Jdks.getInstance().chooseOrCreateJavaSdk();
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

  private static boolean tryToSetAndroidPlatform(@NotNull Module module, @NotNull Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      setModuleSdk(module, sdk);
      return true;
    }
    return false;
  }

  private static void setupPlatform(@NotNull Module module) {
    String targetHashString = getTargetHashStringFromPropertyFile(module);
    if (targetHashString != null && findAndSetSdkWithHashString(module, targetHashString)) {
      return;
    }

    PropertiesComponent component = PropertiesComponent.getInstance();
    if (component.isValueSet(DEFAULT_PLATFORM_NAME_PROPERTY)) {
      String defaultPlatformName = component.getValue(DEFAULT_PLATFORM_NAME_PROPERTY);
      Sdk defaultLib = ProjectJdkTable.getInstance().findJdk(defaultPlatformName, AndroidSdkType.getInstance().getName());
      if (defaultLib != null && tryToSetAndroidPlatform(module, defaultLib)) {
        return;
      }
    }
    for (Sdk sdk : AndroidSdks.getInstance().getAllAndroidSdks()) {
      AndroidPlatform platform = AndroidPlatform.getInstance(sdk);

      if (platform != null &&
          checkSdkRoots(sdk, platform.getTarget(), false) &&
          tryToSetAndroidPlatform(module, sdk)) {
        component.setValue(DEFAULT_PLATFORM_NAME_PROPERTY, sdk.getName());
        return;
      }
    }
  }

  @Nullable
  private static String getTargetHashStringFromPropertyFile(@NotNull Module module) {
    Pair<String, VirtualFile> targetProp = getProjectPropertyValue(module, ANDROID_TARGET_PROPERTY);
    return targetProp != null ? targetProp.getFirst() : null;
  }

  private static boolean findAndSetSdkWithHashString(@NotNull Module module, @NotNull String targetHashString) {
    Pair<String, VirtualFile> sdkDirProperty = getPropertyValue(module, FN_LOCAL_PROPERTIES, "sdk.dir");
    String sdkDir = sdkDirProperty != null ? sdkDirProperty.getFirst() : null;
    return findAndSetSdk(module, targetHashString, sdkDir);
  }

  /**
   * Finds a matching Android SDK and sets it in the given module.
   *
   * @param module           the module to set the found SDK to.
   * @param targetHashString compile target.
   * @param sdkPath          path, in the file system, of the Android SDK.
   * @return {@code true} if a matching Android SDK was found and set in the module; {@code false} otherwise.
   */
  public static boolean findAndSetSdk(@NotNull Module module, @NotNull String targetHashString, @Nullable String sdkPath) {
    File path = null;
    if (sdkPath != null) {
      path = new File(toSystemIndependentName(sdkPath));
    }

    Sdk sdk = AndroidSdks.getInstance().findSuitableAndroidSdk(targetHashString);
    if (sdk != null) {
      setModuleSdk(module, sdk);
      return true;
    }

    if (sdkPath != null && tryToCreateAndSetAndroidSdk(module, path, targetHashString)) {
      return true;
    }

    String androidHomeValue = System.getenv(ANDROID_HOME_ENV);
    if (androidHomeValue != null &&
        tryToCreateAndSetAndroidSdk(module, new File(toSystemIndependentName(androidHomeValue)), targetHashString)) {
      return true;
    }

    for (File dir : AndroidSdks.getInstance().getAndroidSdkPathsFromExistingPlatforms()) {
      if (tryToCreateAndSetAndroidSdk(module, dir, targetHashString)) {
        return true;
      }
    }
    return false;
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

  @VisibleForTesting
  static boolean tryToCreateAndSetAndroidSdk(@NotNull Module module, @NotNull File sdkPath, @NotNull String targetHashString) {
    Sdk sdk = AndroidSdks.getInstance().tryToCreate(sdkPath, targetHashString);
    if (sdk != null) {
      setModuleSdk(module, sdk);
      return true;
    }
    return false;
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
          AnAction sdkManagerAction = ActionManager.getInstance().getAction("WelcomeScreen.RunAndroidSdkManager");
          if (sdkManagerAction != null) {
            sdkManagerAction.update(null);
          }
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

  public static void setupAndroidPlatformIfNecessary(@NotNull Module module, boolean forceImportFromProperties) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();
    if (currentSdk == null || !AndroidSdks.getInstance().isAndroidSdk(currentSdk)) {
      setupPlatform(module);
      return;
    }
    if (forceImportFromProperties) {
      AndroidPlatform platform = AndroidPlatform.getInstance(currentSdk);
      if (platform != null) {
        String targetHashString = getTargetHashStringFromPropertyFile(module);
        String currentTargetHashString = platform.getTarget().hashString();

        if (targetHashString != null && !targetHashString.equals(currentTargetHashString)) {
          findAndSetSdkWithHashString(module, targetHashString);
        }
      }
    }
  }

  public static void openModuleDependenciesConfigurable(@NotNull Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  @Nullable
  public static Sdk findAppropriateAndroidPlatform(@NotNull IAndroidTarget target, @NotNull AndroidSdkData sdkData, boolean forMaven) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      String homePath = sdk.getHomePath();

      AndroidSdks androidSdks = AndroidSdks.getInstance();
      if (homePath != null && androidSdks.isAndroidSdk(sdk)) {
        AndroidSdkData currentSdkData = getSdkData(homePath);

        if (sdkData.equals(currentSdkData)) {
          AndroidSdkAdditionalData data = androidSdks.getAndroidSdkAdditionalData(sdk);
          if (data != null) {
            IAndroidTarget currentTarget = data.getBuildTarget(currentSdkData);
            if (currentTarget != null &&
                target.hashString().equals(currentTarget.hashString()) &&
                checkSdkRoots(sdk, target, forMaven)) {
              return sdk;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean checkSdkRoots(@NotNull Sdk sdk, @NotNull IAndroidTarget target, boolean forMaven) {
    String homePath = sdk.getHomePath();
    if (homePath == null) {
      return false;
    }
    AndroidSdks androidSdks = AndroidSdks.getInstance();
    AndroidSdkAdditionalData sdkAdditionalData = androidSdks.getAndroidSdkAdditionalData(sdk);
    Sdk javaSdk = sdkAdditionalData != null ? sdkAdditionalData.getJavaSdk() : null;
    if (javaSdk == null) {
      return false;
    }
    Set<VirtualFile> filesInSdk = Sets.newHashSet(sdk.getRootProvider().getFiles(CLASSES));

    List<VirtualFile> platformAndAddOnJars = androidSdks.getPlatformAndAddOnJars(target);
    for (VirtualFile file : platformAndAddOnJars) {
      if (filesInSdk.contains(file) == forMaven) {
        return false;
      }
    }
    boolean containsJarFromJdk = false;

    for (VirtualFile file : javaSdk.getRootProvider().getFiles(CLASSES)) {
      if (file.getFileType() instanceof ArchiveFileType && filesInSdk.contains(file)) {
        containsJarFromJdk = true;
      }
    }
    return containsJarFromJdk == forMaven;
  }

  @Nullable
  public static File getAdb(@NotNull Project project) {
    String path = System.getProperty(ADB_PATH_PROPERTY);
    if (path != null) {
      File adb = new File(path);
      if (adb.exists()) {
        return adb;
      }
      else {
        LOG.warn(String.format("%1$s was set to \"%2$s\", but no such file exists.", ADB_PATH_PROPERTY, path));
      }
    }
    AndroidSdkData data = getProjectSdkData(project);
    if (data == null) {
      data = getFirstAndroidModuleSdkData(project);
    }
    File adb = data == null ? null : new File(data.getLocation(), platformToolPath(FN_ADB));
    return adb != null && adb.exists() ? adb : null;
  }

  @Nullable
  private static AndroidSdkData getFirstAndroidModuleSdkData(Project project) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      AndroidPlatform androidPlatform = facet.getConfiguration().getAndroidPlatform();
      if (androidPlatform != null) {
        return androidPlatform.getSdkData();
      }
    }
    return null;
  }

  @Nullable
  private static AndroidSdkData getProjectSdkData(Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null) {
      AndroidPlatform platform = AndroidPlatform.getInstance(projectSdk);
      return platform != null ? platform.getSdkData() : null;
    }
    return null;
  }

  public static boolean activateDdmsIfNecessary(@NotNull Project project) {
    if (AndroidEnableAdbServiceAction.isAdbServiceEnabled()) {
      AndroidDebugBridge bridge = getDebugBridge(project);
      if (bridge != null && AdbService.isDdmsCorrupted(bridge)) {
        LOG.info("DDMLIB is corrupted and will be restarted");
        File adb = getAdb(project);
        if (adb == null) {
          throw new RuntimeException("Unable to locate Android SDK used by project: " + project.getName());
        }
        AdbService.getInstance().restartDdmlib(adb);
      }
    }
    else {
      OSProcessHandler ddmsProcessHandler = AndroidRunDdmsAction.getDdmsProcessHandler();
      if (ddmsProcessHandler != null) {
        String message = "Monitor will be closed to enable ADB integration. Continue?";
        int result = Messages.showYesNoDialog(project, message, "ADB Integration", Messages.getQuestionIcon());
        if (result != Messages.YES) {
          return false;
        }

        Runnable destroyingRunnable = () -> {
          if (!ddmsProcessHandler.isProcessTerminated()) {
            OSProcessManager.getInstance().killProcessTree(ddmsProcessHandler.getProcess());
            ddmsProcessHandler.waitFor();
          }
        };
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(destroyingRunnable, "Closing Monitor", true, project)) {
          return false;
        }
        setAdbServiceEnabled(project, true);
        return true;
      }

      int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                            AndroidBundle.message("android.ddms.disabled.dialog.title"), Messages.getQuestionIcon());
      if (result != Messages.YES) {
        return false;
      }
      setAdbServiceEnabled(project, true);
    }
    return true;
  }

  public static boolean isAndroidSdkAvailable() {
    return AndroidSdks.getInstance().tryToChooseAndroidSdk() != null;
  }

  public static boolean isGlassInstalled() {
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(AndroidSdkUtils.class);
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    Collection<IAndroidTarget> targets = handler.getAndroidTargetManager(progress).getTargets(progress);
    for (IAndroidTarget target : targets) {
      if (!target.isPlatform() && target.getName().startsWith("Glass Development Kit")) {
        return true;
      }
    }

    return false;
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
      return String.format("API %d+: %s", target.getVersion().getApiLevel(), target.getName());
    }
    String name = SdkVersionInfo.getAndroidName(target.getVersion().getApiLevel());
    if (isNotEmpty(name)) {
      return name;
    }
    String release = target.getProperty("ro.build.version.release"); //$NON-NLS-1$
    if (release != null) {
      return String.format("API %1$d: Android %2$s", version.getApiLevel(), release);
    }
    return String.format("API %1$d", version.getApiLevel());
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    AndroidSdkData data = getProjectSdkData(project);
    if (data == null) {
      data = getFirstAndroidModuleSdkData(project);
    }
    if (data == null) {
      return null;
    }

    AndroidDebugBridge bridge = null;
    boolean retry;
    do {
      File adb = getAdb(project);
      if (adb == null) {
        LOG.error("Unable to locate adb within SDK");
        return null;
      }

      Future<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
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

  /**
   * Refresh the library {@link VirtualFile}s in the given {@link Sdk}.
   *
   * After changes to installed Android SDK components, the contents of the {@link Sdk}s do not automatically get refreshed.
   * The referenced {@link VirtualFile}s can be obsolete, new files may be created, or files may be deleted. The result is that
   * references to Android classes may not be found in editors.
   * Removing and adding the libraries effectively refreshes the contents of the IDEA SDK, and references in editors work again.
   */
  public static void refreshLibrariesIn(@NotNull Sdk sdk) {
    VirtualFile[] libraries = sdk.getRootProvider().getFiles(CLASSES);

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeRoots(CLASSES);
    sdkModificator.commitChanges();

    sdkModificator = sdk.getSdkModificator();
    for (VirtualFile library : libraries) {
      sdkModificator.addRoot(library, CLASSES);
    }
    sdkModificator.commitChanges();
  }

  public static boolean isAndroidSdkManagerEnabled() {
    boolean sdkManagerDisabled = SystemProperties.getBooleanProperty("android.studio.sdk.manager.disabled", false);
    return !sdkManagerDisabled;
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
