/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.sdk.AndroidSdks.SDK_NAME_PREFIX;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.ide.impl.NewProjectUtil.applyJdkToProject;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_11;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.projectRoots.JdkUtil.isModularRuntime;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;
import static com.intellij.openapi.util.io.FileUtil.resolveShortWindowsName;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Android Studio has single JDK and single Android SDK. Both can be configured via ProjectStructure dialog.
 * IDEA has many JDKs and single Android SDK.
 * <p>
 * All the methods like {@code getJdk()}, {@code getJdkPath()} assume this single JDK in Android Studio. In AS it is used in three ways:
 * <ol>
 *   <li> Project JDK for imported projects
 *   <li> Gradle JVM for gradle execution
 *   <li> Parent JDK for all the registered Android SDKs
 * </ol>
 * <p>
 * In IDEA this JDK is used as:
 * <ol>
 *   <li> Preferred JDK for new projects
 *   <li> Preferred JVM for gradle execution
 *   <li> Parent JDK for newly created Android SDKs
 * </ol>
 * <p>
 * In IDEA user can update gradle/project/android JDK independently after the project has been opened, so they all can be different.
 * This class only holds reasonable defaults for new entities.
 * <p>
 * In Android Studio user can update the JDK, and this will change all the usages all together. So normally AS users cannot have different
 * JDKs for different purposes.
 */
public class IdeSdks {
  @NonNls public static final String MAC_JDK_CONTENT_PATH = "/Contents/Home";
  @NonNls private static final String ANDROID_SDK_PATH_KEY = "android.sdk.path";
  @NotNull public static final JavaSdkVersion DEFAULT_JDK_VERSION = JDK_11;
  @NotNull public static final String JDK_LOCATION_ENV_VARIABLE_NAME = "STUDIO_GRADLE_JDK";
  @NotNull private static final Logger LOG = Logger.getInstance(IdeSdks.class);
  private static final JavaSdkVersion MIN_JDK_VERSION = JDK_1_8;
  private static final JavaSdkVersion MAX_JDK_VERSION = JDK_11; // the largest LTS JDK compatible with SdkConstants.GRADLE_LATEST_VERSION = "6.1.1"

  @NotNull private final AndroidSdks myAndroidSdks;
  @NotNull private final Jdks myJdks;
  @NotNull private final EmbeddedDistributionPaths myEmbeddedDistributionPaths;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final Map<String, LocalPackage> localPackagesByPrefix = new HashMap<>();

  private final EnvVariableSettings myEnvVariableSettings = new EnvVariableSettings();

  @NotNull
  public static IdeSdks getInstance() {
    return ApplicationManager.getApplication().getService(IdeSdks.class);
  }

  public IdeSdks() {
    this(AndroidSdks.getInstance(), Jdks.getInstance(), EmbeddedDistributionPaths.getInstance(), IdeInfo.getInstance());
  }

  @NonInjectable
  @VisibleForTesting
  public IdeSdks(@NotNull AndroidSdks androidSdks,
                 @NotNull Jdks jdks,
                 @NotNull EmbeddedDistributionPaths embeddedDistributionPaths,
                 @NotNull IdeInfo ideInfo) {
    myAndroidSdks = androidSdks;
    myJdks = jdks;
    myEmbeddedDistributionPaths = embeddedDistributionPaths;
    myIdeInfo = ideInfo;
  }

  /**
   * Returns the directory that the IDE is using as the home path for the Android SDK for new projects.
   */
  @Nullable
  public File getAndroidSdkPath() {
    // We assume that every time new android sdk path is applied, all existing ide android sdks are removed and replaced by newly
    // created ide android sdks for the platforms downloaded for the new android sdk. So, we bring the first ide android sdk configured
    // at the moment and deduce android sdk path from it.
    String sdkHome = null;
    Sdk sdk = getFirstAndroidSdk();
    if (sdk != null) {
      sdkHome = sdk.getHomePath();
    }
    if (sdkHome != null) {
      File candidate = toSystemDependentPath(sdkHome);
      // Check if the sdk home is still valid. See https://code.google.com/p/android/issues/detail?id=197401 for more details.
      if (isValidAndroidSdkPath(candidate)) {
        return candidate;
      }
    }

    // b/138107196: Accessing the default project instance from integration tests can deadlock if the default project itself
    // is in the process of being automatically disposed, which is new in IJ 2019.2
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }

    // There is a possible case that android sdk which path was applied previously (setAndroidSdkPath()) didn't have any
    // platforms downloaded. Hence, no ide android sdk was created and we can't deduce android sdk location from it.
    // Hence, we fallback to the explicitly stored android sdk path here.
    PropertiesComponent component = PropertiesComponent.getInstance(ProjectManager.getInstance().getDefaultProject());
    String sdkPath = component.getValue(ANDROID_SDK_PATH_KEY);
    if (sdkPath != null) {
      File candidate = new File(sdkPath);
      if (isValidAndroidSdkPath(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Prefix is a string prefix match on SDK package can be like 'ndk', 'ndk;19', or a full package name like 'ndk;19.1.2'
   */
  @Nullable
  public LocalPackage getSpecificLocalPackage(@NotNull String prefix) {
    if (localPackagesByPrefix.containsKey(prefix)) {
      return localPackagesByPrefix.get(prefix);
    }
    AndroidSdkHandler sdkHandler = myAndroidSdks.tryToChooseSdkHandler();
    LocalPackage result = sdkHandler.getLatestLocalPackageForPrefix(
      prefix,
      null,
      true, // All specific version to be preview
      new StudioLoggerProgressIndicator(IdeSdks.class));
    if (result != null) {
      // Don't cache nulls so we can check again later.
      setSpecificLocalPackage(prefix, result);
    }
    return result;
  }

  @VisibleForTesting
  public void setSpecificLocalPackage(@NotNull String prefix, @NotNull LocalPackage localPackage) {
    localPackagesByPrefix.put(prefix, localPackage);
  }

  @Nullable
  public LocalPackage getHighestLocalNdkPackage(boolean allowPreview) {
    return getHighestLocalNdkPackage(allowPreview, null);
  }
  @Nullable
  public LocalPackage getHighestLocalNdkPackage(boolean allowPreview, @Nullable Predicate<Revision> filter) {
    AndroidSdkHandler sdkHandler = myAndroidSdks.tryToChooseSdkHandler();
    // Look first at NDK side-by-side locations.
    // See go/ndk-sxs
    LocalPackage ndk = sdkHandler.getLatestLocalPackageForPrefix(
      SdkConstants.FD_NDK_SIDE_BY_SIDE,
      filter,
      allowPreview,
      new StudioLoggerProgressIndicator(IdeSdks.class));
    if (ndk != null) {
      return ndk;
    }
    LocalPackage ndkPackage = sdkHandler.getLocalPackage(SdkConstants.FD_NDK, new StudioLoggerProgressIndicator(IdeSdks.class));
    if (filter != null && ndkPackage != null && filter.test(ndkPackage.getVersion())) {
      return ndkPackage;
    }
    return null;
  }

  @Nullable
  public File getAndroidNdkPath() {
    return getAndroidNdkPath(null);
  }

  @Nullable
  public File getAndroidNdkPath(@Nullable Predicate<Revision> filter) {
    LocalPackage ndk = getHighestLocalNdkPackage(false, filter);
    if (ndk != null) {
      return FileOpUtils.toFileUnsafe(ndk.getLocation());
    }
    return null;
  }

  @Nullable
  public File getJdkPath() {
    return doGetJdkPath(true);
  }

  @Nullable
  private File doGetJdkPath(boolean createJdkIfNeeded) {
    if (isUsingEnvVariableJdk()) {
      return getEnvVariableJdkFile();
    }
    JavaSdkVersion sdkVersion = getRunningVersionOrDefault();
    Sdk jdk = getExistingJdk(sdkVersion);
    if (createJdkIfNeeded && (jdk == null || jdk.getHomePath() == null)) {
      jdk = createNewJdk(sdkVersion);
    }
    if (jdk != null && jdk.getHomePath() != null) {
      return new File(jdk.getHomePath());
    }
    return null;
  }

  /**
   * Clean environment variable settings initialization, this method should only be used by tests that called
   * IdeSdks#initializeJdkEnvVariable(java.lang.String).
   */
  public void cleanJdkEnvVariableInitialization() {
    myEnvVariableSettings.cleanInitialization();
  }

  /**
   * Allow to override the value of the environment variable {JDK_LOCATION_ENV_VARIABLE_NAME}, this method should only be used by tests that
   * need to use a different JDK from a thread that can perform write actions.
   */
  public void overrideJdkEnvVariable(@Nullable String envVariableValue) {
    myEnvVariableSettings.overrideValue(envVariableValue);
  }

  /**
   * Indicate if the user has selected the JDK location pointed by {@value JDK_LOCATION_ENV_VARIABLE_NAME}. This is the default when Studio
   * starts with a valid {@value JDK_LOCATION_ENV_VARIABLE_NAME}.
   * @return {@code true} iff {@value JDK_LOCATION_ENV_VARIABLE_NAME} is valid and is the current JDK location selection.
   */
  public boolean isUsingEnvVariableJdk() {
    return myEnvVariableSettings.isUseJdkEnvVariable();
  }

  /**
   * Check if environment variable {@value JDK_LOCATION_ENV_VARIABLE_NAME} is defined.
   * @return {@code true} iff the variable is defined
   */
  public boolean isJdkEnvVariableDefined() {
    return myEnvVariableSettings.isJdkEnvVariableDefined();
  }

  /**
   * Check if the JDK Location pointed by {@value JDK_LOCATION_ENV_VARIABLE_NAME} is valid
   * @return {@code true} iff the variable is defined and it points to a valid JDK Location (as checked by
   *          {@link IdeSdks#validateJdkPath(File)})
   */
  public boolean isJdkEnvVariableValid() {
    return myEnvVariableSettings.IsJdkEnvVariableValid();
  }

  /**
   * Return the JDK Location pointed by {@value JDK_LOCATION_ENV_VARIABLE_NAME}
   * @return A valid JDK location iff environment variable {@value JDK_LOCATION_ENV_VARIABLE_NAME} is set to a valid JDK Location
   */
  @Nullable
  public File getEnvVariableJdkFile() {
    return myEnvVariableSettings.getJdkFile();
  }


  /**
   * Return the value set to environment variable {@value JDK_LOCATION_ENV_VARIABLE_NAME}
   * @return The value set, {@code null} if it was not defined.
   */
  @Nullable
  public String getEnvVariableJdkValue() {
    return myEnvVariableSettings.getVariableValue();
  }

  /**
   * Indicate if {@value JDK_LOCATION_ENV_VARIABLE_NAME} should be used as JDK location or not. This setting can be changed iff the
   * environment variable points to a valid JDK location.
   * @param useJdkEnvVariable indicates is the environment variable should be used or not.
   * @return {@code true} if this setting can be changed.
   */
  public boolean setUseEnvVariableJdk(boolean useJdkEnvVariable) {
    return myEnvVariableSettings.setUseJdkEnvVariable(useJdkEnvVariable);
  }

  /**
   * @return the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   * target installed in the SDK; which of those this method returns is not defined.
   */
  @Nullable
  private Sdk getFirstAndroidSdk() {
    List<Sdk> allAndroidSdks = getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    return null;
  }

  /**
   * Sets the JDK in the given path to be used if valid. Must be run inside a WriteAction.
   * @param path, folder in which the JDK is looked for.
   * @return the JDK in the given path if valid, null otherwise.
   */
  public Sdk setJdkPath(@NotNull File path) {
    if (checkForJdk(path)) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      File canonicalPath = resolvePath(path);
      Sdk chosenJdk = null;

      ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
      if (myIdeInfo.isAndroidStudio()) {
        // Delete all JDKs in Android Studio. We want to have only one.
        List<Sdk> jdks = projectJdkTable.getSdksOfType(JavaSdk.getInstance());
        for (final Sdk jdk : jdks) {
          projectJdkTable.removeJdk(jdk);
        }
      }
      else {
        for (Sdk jdk : projectJdkTable.getSdksOfType(JavaSdk.getInstance())) {
          if (pathsEqual(jdk.getHomePath(), canonicalPath.getPath())) {
            chosenJdk = jdk;
            break;
          }
        }
      }

      if (chosenJdk == null) {
        if (canonicalPath.isDirectory()) {
          chosenJdk = createJdk(canonicalPath);
          if (chosenJdk == null) {
            // Unlikely to happen
            throw new IllegalStateException("Failed to create IDEA JDK from '" + path.getPath() + "'");
          }
          setJdkOfAndroidSdks(chosenJdk);

          Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          for (Project project : openProjects) {
            applyJdkToProject(project, chosenJdk);
          }
        }
        else {
          throw new IllegalStateException("The resolved path '" + canonicalPath.getPath() + "' was not found");
        }
      }
      setUseEnvVariableJdk(false);
      return chosenJdk;
    }
    return null;
  }

  /**
   * Iterates through all Android SDKs and makes them point to the given JDK.
   */
  private void setJdkOfAndroidSdks(@NotNull Sdk jdk) {
    for (Sdk sdk : myAndroidSdks.getAllAndroidSdks()) {
      AndroidSdkAdditionalData oldData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
      if (oldData == null) {
        continue;
      }
      oldData.setJavaSdk(jdk);
      SdkModificator modificator = sdk.getSdkModificator();
      modificator.setSdkAdditionalData(oldData);
      modificator.commitChanges();
    }
  }

  @NotNull
  public List<Sdk> setAndroidSdkPath(@NotNull File path, @Nullable Project currentProject) {
    return setAndroidSdkPath(path, null, currentProject);
  }

  /**
   * Sets the path of Android Studio's Android SDK. This method should be called in a write action. It is assumed that the given path has
   * been validated by {@link #isValidAndroidSdkPath(File)}. This method will fail silently if the given path is not valid.
   *
   * @param path the path of the Android SDK.
   * @see com.intellij.openapi.application.Application#runWriteAction(Runnable)
   */
  @NotNull
  public List<Sdk> setAndroidSdkPath(@NotNull File path, @Nullable Sdk javaSdk, @Nullable Project currentProject) {
    if (isValidAndroidSdkPath(path)) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();

      // There is a possible case that no platform is downloaded for the android sdk which path is given as an argument
      // to the current method. Hence, no ide android sdk is configured and our further android sdk lookup
      // (check project jdk table for the configured ide android sdk and deduce the path from it) wouldn't work. So, we save
      // given path as well in order to be able to fallback to it later if there is still no android sdk configured within the ide.
      if (currentProject != null && !currentProject.isDisposed()) {
        String sdkPath = toCanonicalPath(path.getAbsolutePath());

        PropertiesComponent.getInstance(currentProject).setValue(ANDROID_SDK_PATH_KEY, sdkPath);
        if (!currentProject.isDefault()) {
          // Store default sdk path for default project as well in order to be able to re-use it for another ide projects if necessary.
          PropertiesComponent component = PropertiesComponent.getInstance(ProjectManager.getInstance().getDefaultProject());
          component.setValue(ANDROID_SDK_PATH_KEY, sdkPath);
        }
      }

      // Since removing SDKs is *not* asynchronous, we force an update of the SDK Manager.
      // If we don't force this update, AndroidSdks will still use the old SDK until all SDKs are properly deleted.
      updateSdkData(path);

      // Set up a list of SDKs we don't need any more. At the end we'll delete them.
      List<Sdk> sdksToDelete = new ArrayList<>();

      File resolved = resolvePath(path);
      // Parse out the new SDK. We'll need its targets to set up IntelliJ SDKs for each.
      AndroidSdkData sdkData = getSdkData(resolved, true);
      if (sdkData != null) {
        // Iterate over all current existing IJ Android SDKs
        for (Sdk sdk : myAndroidSdks.getAllAndroidSdks()) {
          if (sdk.getName().startsWith(SDK_NAME_PREFIX)) {
            sdksToDelete.add(sdk);
          }
        }
      }
      for (Sdk sdk : sdksToDelete) {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }

      // If there are any API targets that we haven't created IntelliJ SDKs for yet, fill those in.
      List<Sdk> sdks = createAndroidSdkPerAndroidTarget(resolved, javaSdk);

      afterAndroidSdkPathUpdate(resolved);

      return sdks;
    }
    return Collections.emptyList();
  }

  private void updateSdkData(@NotNull File path) {
    AndroidSdkData oldSdkData = getSdkData(path);
    myAndroidSdks.setSdkData(oldSdkData);
  }

  /**
   * Updates ProjectJdkTable based on what is currently available on Android SDK path and what SDK Manager says
   *
   * @param currentProject used to get Android SDK path. If {@code null} or if it does not have Android SDK path setup this function will
   *                       use the result from {@link IdeSdks#getAndroidSdkPath()()}
   */
  public void updateFromAndroidSdkPath(@Nullable Project currentProject) {
    File sdkDir = null;
    if (currentProject != null && !currentProject.isDisposed()) {
      String sdkPath = PropertiesComponent.getInstance(currentProject).getValue(ANDROID_SDK_PATH_KEY);
      if (sdkPath != null) {
        sdkDir = new File(sdkPath);
      }
    }
    // Current project is null or it does not have ANDROID_SDK_PATH_KEY set
    if (sdkDir == null) {
      sdkDir = getAndroidSdkPath();
    }
    assert sdkDir != null;
    assert isValidAndroidSdkPath(sdkDir);
    updateSdkData(sdkDir);
    // See what Android Sdk's no longer exist and remove them
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    for (Sdk sdk : getEligibleAndroidSdks()) {
      VirtualFile homeDir = sdk.getHomeDirectory();
      if (homeDir == null || !homeDir.exists()) {
        jdkTable.removeJdk(sdk);
      }
      else {
        IAndroidTarget target = getTarget(sdk);
        File targetFile = new File(target.getLocation());
        if (!targetFile.exists()) {
          // Home folder exists but does not contain target
          jdkTable.removeJdk(sdk);
        }
      }
    }

    // Add new SDK's from SDK manager
    File resolved = resolvePath(sdkDir);
    createAndroidSdkPerAndroidTarget(resolved);
  }

  private static void afterAndroidSdkPathUpdate(@NotNull File androidSdkPath) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) {
      return;
    }

    AndroidSdkEventListener[] eventListeners = AndroidSdkEventListener.EP_NAME.getExtensions();
    for (Project project : openProjects) {
      if (!AndroidProjectInfo.getInstance(project).requiresAndroidModel()) {
        continue;
      }
      for (AndroidSdkEventListener listener : eventListeners) {
        listener.afterSdkPathChange(androidSdkPath, project);
      }
    }
  }

  /**
   * Returns true if the given Android SDK path points to a valid Android SDK.
   */
  public boolean isValidAndroidSdkPath(@NotNull File path) {
    return validateAndroidSdk(path, false).success;
  }

  @NotNull
  public List<Sdk> createAndroidSdkPerAndroidTarget(@NotNull File androidSdkPath) {
    List<Sdk> sdks = createAndroidSdkPerAndroidTarget(androidSdkPath, null);
    updateWelcomeRunAndroidSdkAction();
    return sdks;
  }

  public static void updateWelcomeRunAndroidSdkAction() {
    ActionManager actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
    if (actionManager == null) {
      return;
    }

    AnAction sdkManagerAction = actionManager.getAction("WelcomeScreen.RunAndroidSdkManager");
    if (sdkManagerAction != null) {
      sdkManagerAction.update(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataId -> null));
    }
  }

  /**
   * Creates a set of IntelliJ SDKs (one for each build target) corresponding to the Android SDK in the given directory, if SDKs with the
   * default naming convention and each individual build target do not already exist. If IntelliJ SDKs do exist, they are not updated.
   */
  @NotNull
  private List<Sdk> createAndroidSdkPerAndroidTarget(@NotNull File androidSdkPath, @Nullable Sdk javaSdk) {
    AndroidSdkData sdkData = getSdkData(androidSdkPath);
    if (sdkData == null) {
      return Collections.emptyList();
    }
    IAndroidTarget[] targets = sdkData.getTargets(false /* do not include add-ons */);
    if (targets.length == 0) {
      return Collections.emptyList();
    }
    List<Sdk> sdks = new ArrayList<>();
    Sdk ideJdk = javaSdk != null ? javaSdk : getJdk();
    if (ideJdk != null) {
      for (IAndroidTarget target : targets) {
        if (target.isPlatform() && !doesIdeAndroidSdkExist(target)) {
          String name = myAndroidSdks.chooseNameForNewLibrary(target);
          Sdk sdk = myAndroidSdks.create(target, sdkData.getLocationFile(), name, ideJdk, true);
          if (sdk != null) {
            sdks.add(sdk);
          }
        }
      }
    }
    return sdks;
  }

  /**
   * Returns true if an IntelliJ SDK with the default naming convention already exists for the given Android build target.
   */
  private boolean doesIdeAndroidSdkExist(@NotNull IAndroidTarget target) {
    for (Sdk sdk : getEligibleAndroidSdks()) {
      IAndroidTarget platformTarget = getTarget(sdk);
      AndroidVersion version = target.getVersion();
      AndroidVersion existingVersion = platformTarget.getVersion();
      if (existingVersion.equals(version)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static IAndroidTarget getTarget(@NotNull Sdk sdk) {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(sdk);
    assert androidPlatform != null;
    return androidPlatform.getTarget();
  }

  @NotNull
  private static File resolvePath(@NotNull File path) {
    try {
      String resolvedPath = resolveShortWindowsName(path.getPath());
      return new File(resolvedPath);
    }
    catch (IOException e) {
      //file doesn't exist yet
    }
    return path;
  }

  /**
   * Indicates whether the IDE is Android Studio and it is using its embedded JDK. This JDK is used to invoke Gradle.
   *
   * @return true if the embedded JDK is used
   */
  public boolean isUsingEmbeddedJdk() {
    if (!myIdeInfo.isAndroidStudio()) {
      return false;
    }
    File jdkPath = doGetJdkPath(false);
    return jdkPath != null && filesEqual(jdkPath, getEmbeddedJdkPath());
  }

  /**
   * Makes the IDE use its embedded JDK or a JDK selected by the user. This JDK is used to invoke Gradle.
   */
  public void setUseEmbeddedJdk() {
    checkState(myIdeInfo.isAndroidStudio(), "This method is for use in Android Studio only.");
    File embeddedJdkPath = getEmbeddedJdkPath();
    assert embeddedJdkPath != null;
    setJdkPath(embeddedJdkPath);
  }

  @Nullable
  public File getEmbeddedJdkPath() {
    if (!myIdeInfo.isAndroidStudio()) {
      return null;
    }
    return myEmbeddedDistributionPaths.getEmbeddedJdkPath();
  }

  /**
   * Indicates whether the IDE is Android Studio and it is using JAVA_HOME as its JDK.
   *
   * @return true if JAVA_HOME is used as JDK
   */
  public boolean isUsingJavaHomeJdk() {
    return isUsingJavaHomeJdk(ApplicationManager.getApplication().isUnitTestMode());
  }

  @VisibleForTesting
  boolean isUsingJavaHomeJdk(boolean assumeUnitTest) {
    if (!myIdeInfo.isAndroidStudio()) {
      return false;
    }
    // Do not create Jdk in ProjectJDKTable when running from unit tests, to prevent leaking
    File jdkPath =  assumeUnitTest ? doGetJdkPath(false) : getJdkPath();
    return isSameAsJavaHomeJdk(jdkPath);
  }

  /**
   * Indicates whether the passed path is the same as JAVA_HOME.
   *
   * @param path Path to test.
   *
   * @return true if JAVA_HOME is the same as path
   */
  public static boolean isSameAsJavaHomeJdk(@Nullable File path) {
    String javaHome = getJdkFromJavaHome();
    return javaHome != null && filesEqual(path, toSystemDependentPath(javaHome));
  }

  /**
   * Get JDK path based on the value of JAVA_HOME environment variable. If this variable is not defined or does not correspond to a valid
   * JDK folder then look into java.home system property. This method will try to get the environment from a terminal when possible and if
   * not, use the current environment.
   *
   * @return null if no JDK can be found, or the path where the JDK is located.
   */
  @Nullable
  public static String getJdkFromJavaHome() {
    // Try terminal environment first
    String terminalValue = doGetJdkFromPathOrParent(EnvironmentUtil.getValue("JAVA_HOME"));
    if (!isNullOrEmpty(terminalValue)) {
      return terminalValue;
    }
    // Now try with current environment
    String envVariableValue = doGetJdkFromPathOrParent(System.getenv("JAVA_HOME"));
    if (!isNullOrEmpty(envVariableValue)) {
      return envVariableValue;
    }
    // Then system property
    return doGetJdkFromPathOrParent(SystemProperties.getJavaHome());
  }

  @VisibleForTesting
  @Nullable
  static String doGetJdkFromPathOrParent(@Nullable String path) {
    if (isNullOrEmpty(path)) {
      return null;
    }
    File pathFile = new File(toSystemDependentName(path));
    String result = doGetJdkFromPath(pathFile);
    if (result != null) {
      return result;
    }
    // Sometimes JAVA_HOME is set to a JRE inside a JDK, see if this is the case
    File parentFile = pathFile.getParentFile();
    if (parentFile != null) {
      return doGetJdkFromPath(parentFile);
    }
    return null;
  }

  @Nullable
  private static String doGetJdkFromPath(@NotNull File file) {
    if (checkForJdk(file)) {
      return file.getPath();
    }
    if (SystemInfo.isMac) {
      File potentialPath = new File(file, MAC_JDK_CONTENT_PATH);
      if (potentialPath.isDirectory() && checkForJdk(potentialPath)) {
        return potentialPath.getPath();
      }
    }
    return null;
  }

  /**
   * @return the JDK with the default naming convention, creating one if it is not set up.
   */
  @Nullable
  public Sdk getJdk() {
    // b/161405154  If STUDIO_GRADLE_JDK is valid and selected then return the corresponding Sdk
    if (myEnvVariableSettings.isUseJdkEnvVariable()) {
      return myEnvVariableSettings.getSdk();
    }
    return getJdk(getRunningVersionOrDefault());
  }

  @Nullable
  private Sdk getJdk(@Nullable JavaSdkVersion preferredVersion) {
    Sdk existingJdk = getExistingJdk(preferredVersion);
    if (existingJdk != null) return existingJdk;
    return createNewJdk(preferredVersion);
  }

  @Nullable
  private Sdk getExistingJdk(@Nullable JavaSdkVersion preferredVersion) {
    List<Sdk> androidSdks = getEligibleAndroidSdks();
    if (!androidSdks.isEmpty()) {
      Sdk androidSdk = androidSdks.get(0);
      AndroidSdkAdditionalData data = myAndroidSdks.getAndroidSdkAdditionalData(androidSdk);
      assert data != null;
      Sdk jdk = data.getJavaSdk();
      if (isJdkCompatible(jdk, preferredVersion)) {
        return jdk;
      }
    }
    JavaSdk javaSdk = JavaSdk.getInstance();

    List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    if (!jdks.isEmpty()) {
      for (Sdk jdk : jdks) {
        if (isJdkCompatible(jdk, preferredVersion)) {
          return jdk;
        }
      }
    }
    return null;
  }

  @Nullable
  private Sdk createNewJdk(@Nullable JavaSdkVersion preferredVersion) {
    // The following code tries to detect the best JDK (partially duplicates com.android.tools.idea.sdk.Jdks#chooseOrCreateJavaSdk)
    // This happens when user has a fresh installation of Android Studio, and goes through the 'First Run' Wizard.
    if (myIdeInfo.isAndroidStudio()) {
      Sdk jdk = myJdks.createEmbeddedJdk();
      if (jdk != null) {
        assert isJdkCompatible(jdk, preferredVersion);
        return jdk;
      }
    }

    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    Set<String> checkedJdkPaths = jdks.stream().map(Sdk::getHomePath).collect(Collectors.toSet());
    List<File> jdkPaths = getPotentialJdkPaths();
    for (File jdkPath : jdkPaths) {
      if (checkedJdkPaths.contains(jdkPath.getAbsolutePath())){
        continue; // already checked: didn't fit
      }

      if (checkForJdk(jdkPath)) {
        Sdk jdk = createJdk(jdkPath); // TODO-ank: this adds JDK to the project even if the JDK is not compatible and will be skipped
        if (isJdkCompatible(jdk, preferredVersion) ) {
          return jdk;
        }
      }
      // On Linux, the returned path is the folder that contains all JDKs, instead of a specific JDK.
      if (SystemInfo.isLinux) {
        for (File child : notNullize(jdkPath.listFiles())) {
          if (child.isDirectory() && checkForJdk(child)) {
            Sdk jdk = myJdks.createJdk(child.getPath());
            if (isJdkCompatible(jdk, preferredVersion)) {
              return jdk;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Finds all potential folders that may contain Java SDKs.
   * Those folders are guaranteed to exist but they may not be valid Java homes.
   */
  @NotNull
  private static List<File> getPotentialJdkPaths() {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<String> jdkPaths = Lists.newArrayList(javaSdk.suggestHomePaths());
    jdkPaths.add(SystemProperties.getJavaHome());
    jdkPaths.add(0, System.getenv("JDK_HOME"));
    List<File> virtualFiles = Lists.newArrayListWithCapacity(jdkPaths.size());
    for (String jdkPath : jdkPaths) {
      if (jdkPath != null) {
        File javaHome = new File(jdkPath);
        if (javaHome.isDirectory()) {
          virtualFiles.add(javaHome);
        }
      }
    }
    return virtualFiles;
  }

  /**
   * @return {@code true} if JDK can be safely used as a project JDK for a project with android modules,
   * parent JDK for Android SDK or as a gradle JVM to run builds with Android modules
   */
  public boolean isJdkCompatible(@Nullable Sdk jdk) {
    return isJdkCompatible(jdk, MIN_JDK_VERSION);
  }

  @Contract("null, _ -> false")
  public boolean isJdkCompatible(@Nullable Sdk jdk, @Nullable JavaSdkVersion preferredVersion) {
    if (jdk == null) {
      return false;
    }
    if (!(jdk.getSdkType() instanceof JavaSdk)) {
      return false;
    }
    if (preferredVersion == null) {
      return true;
    }
    if (!JavaSdk.getInstance().isOfVersionOrHigher(jdk, JDK_1_8)) {
      return false;
    }
    if (StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.get()) {
      return true;
    }
    JavaSdkVersion jdkVersion = JavaSdk.getInstance().getVersion(jdk);
    if (jdkVersion == null) {
      return false;
    }

    return isJdkVersionCompatible(preferredVersion, jdkVersion);
  }

  @VisibleForTesting
  boolean isJdkVersionCompatible(@NotNull JavaSdkVersion preferredVersion, @NotNull JavaSdkVersion jdkVersion) {
    return jdkVersion.compareTo(preferredVersion) >= 0 && jdkVersion.compareTo(MAX_JDK_VERSION) <= 0;
  }

  /**
   * Filters through all Android SDKs and returns only those that have our special name prefix and which have additional data and a
   * platform.
   */
  @NotNull
  public List<Sdk> getEligibleAndroidSdks() {
    List<Sdk> sdks = new ArrayList<>();
    for (Sdk sdk : myAndroidSdks.getAllAndroidSdks()) {
      if (sdk.getName().startsWith(SDK_NAME_PREFIX) && AndroidPlatform.getInstance(sdk) != null) {
        sdks.add(sdk);
      }
    }
    return sdks;
  }

  public boolean hasConfiguredAndroidSdk(){
    return getAndroidSdkPath() != null;
  }

  /**
   * Creates an IntelliJ SDK for the JDK at the given location and returns it, or {@code null} if it could not be created successfully.
   */
  @Nullable
  private Sdk createJdk(@NotNull File homeDirectory) {
    return myJdks.createJdk(homeDirectory.getPath());
  }

  public interface AndroidSdkEventListener {
    ExtensionPointName<AndroidSdkEventListener> EP_NAME = ExtensionPointName.create("com.android.ide.sdkEventListener");

    /**
     * Notification that the path of the IDE's Android SDK path has changed.
     *
     * @param sdkPath the new Android SDK path.
     * @param project one of the projects currently open in the IDE.
     */
    void afterSdkPathChange(@NotNull File sdkPath, @NotNull Project project);
  }

  public boolean isJdk7Supported(@Nullable AndroidSdkData sdkData) {
    if (sdkData != null) {
      ProgressIndicator progress = new StudioLoggerProgressIndicator(Jdks.class);
      LocalPackage info = sdkData.getSdkHandler().getLocalPackage(SdkConstants.FD_PLATFORM_TOOLS, progress);
      if (info != null) {
        Revision revision = info.getVersion();
        if (revision.getMajor() >= 19) {
          JavaSdk jdk = JavaSdk.getInstance();
          Sdk sdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdk);
          if (sdk != null) {
            JavaSdkVersion version = jdk.getVersion(sdk);
            if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  @TestOnly
  public static void removeJdksOn(@NotNull Disposable disposable) {
    // TODO: remove when all tests correctly pass the early disposable instead of the project.
    if (disposable instanceof ProjectEx) {
      disposable = ((ProjectEx)disposable).getEarlyDisposable();
    }

    Disposer.register(disposable, () -> WriteAction.run(() -> {
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }
    }));
  }

  /**
   * Validates that the given directory belongs to a valid JDK installation.
   * @param file the directory to validate.
   * @return the path of the JDK installation if valid, or {@code null} if the path is not valid.
   */
  @Nullable
  public File validateJdkPath(@NotNull File file) {
    File possiblePath = null;
    if (checkForJdk(file)) {
      possiblePath = file;
    }
    else if (SystemInfo.isMac) {
      File macPath = new File(file, MAC_JDK_CONTENT_PATH);
      if (macPath.isDirectory() && checkForJdk(macPath)) {
        possiblePath = macPath;
      }
    }
    if (possiblePath != null) {
      if (StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.get() || isJdkSameVersion(possiblePath, getRunningVersionOrDefault())) {
        return possiblePath;
      }
      else {
        LOG.warn("Trying to use JDK with different version: " + possiblePath);
      }
    }
    else {
      showValidateDetails(file);
      if (SystemInfo.isMac) {
        showValidateDetails(new File(file, MAC_JDK_CONTENT_PATH));
      }
    }
    return null;
  }

  private static void showValidateDetails(@NotNull File homePath) {
    LOG.warn("Could not validate JDK at " + homePath + ":");
    LOG.warn("  File exists: " + homePath.exists());
    LOG.warn("  Javac: " + (new File(homePath, "bin/javac").isFile() || new File(homePath, "bin/javac.exe").isFile()));
    LOG.warn("  JDK: " + new File(homePath, "jre/lib/rt.jar").exists());
    LOG.warn("  JRE: " + new File(homePath, "lib/rt.jar").exists());
    LOG.warn("  Jigsaw JDK/JRE: " + isModularRuntime(homePath));
    LOG.warn("  Apple JDK: " + new File(homePath, "../Classes/classes.jar").exists());
    LOG.warn("  IBM JDK: " + new File(homePath, "jre/lib/vm.jar").exists());
    LOG.warn("  Custom build: " + new File(homePath, "classes").isDirectory());
  }

  /**
   * Look for the Java version currently used in this order:
   *   - System property "java.version" (should be what the IDE is currently using)
   *   - Embedded JDK
   *   - {@link IdeSdks#DEFAULT_JDK_VERSION}
   */
  @NotNull
  public JavaSdkVersion getRunningVersionOrDefault() {
    String versionString = System.getProperty("java.version");
    if (versionString != null) {
      JavaSdkVersion currentlyRunning = JavaSdkVersion.fromVersionString(versionString);
      if (currentlyRunning != null) {
        return currentlyRunning;
      }
    }
    JavaSdkVersion embeddedVersion = Jdks.getInstance().findVersion(myEmbeddedDistributionPaths.getEmbeddedJdkPath());
    return embeddedVersion != null ? embeddedVersion : DEFAULT_JDK_VERSION;
  }

  /**
   * Tells whether the given location is a valid JDK location and its version is the one expected.
   * @param jdkLocation File with the JDK location.
   * @param expectedVersion The expected java version.
   * @return true if the folder is a valid JDK location and it has the given version.
   */
  @Contract("null, _ -> false")
  public static boolean isJdkSameVersion(@Nullable File jdkLocation, @NotNull JavaSdkVersion expectedVersion) {
    if (jdkLocation == null) {
      return false;
    }
    JavaSdkVersion version = Jdks.getInstance().findVersion(jdkLocation);
    return version != null && version.compareTo(expectedVersion) == 0;
  }

  private class EnvVariableSettings {
    private Sdk mySdk;
    private String myVariableValue;
    private File myJdkFile;
    private boolean myUseJdkEnvVariable;
    private boolean myInitialized;
    private final Object myInitializationLock = new Object();

    public EnvVariableSettings() {
      cleanInitialization();
    }

    public void cleanInitialization() {
      synchronized (myInitializationLock) {
        myVariableValue = null;
        myJdkFile = null;
        mySdk = null;
        myUseJdkEnvVariable = false;
        myInitialized = false;
      }
    }

    private void initialize() {
      synchronized (myInitializationLock) {
        if (myInitialized) {
          return;
        }
      }
      initialize(System.getenv(JDK_LOCATION_ENV_VARIABLE_NAME));
    }

    private void initialize(@Nullable String value) {
      // Read env variable only once and initialize the settings accordingly. myInitialized == false means that this function has not been
      // called yet.
      File envVariableJdkFile;
      synchronized (myInitializationLock) {
        if (myInitialized) {
          return;
        }
        if (value == null) {
          setInitializationAsNotDefined();
          return;
        }
        envVariableJdkFile = validateJdkPath(new File(toSystemDependentName(value)));
        if (envVariableJdkFile == null) {
          setInitializationAsDefinedButInvalid(value);
          LOG.warn("The provided JDK path is invalid: " + value);
          return;
        }
      }
      // Environment variable is defined and valid, make sure it is safe to use EDT to prevent a deadlock (b/174675513)
      File finalEnvVariableJdkFile = envVariableJdkFile;
      Runnable createJdkTask = () -> {
        synchronized (myInitializationLock) {
          // Check initialization again (another thread could have called this already when waiting for EDT)
          if (!myInitialized) {
            try {
              @Nullable Sdk jdk = createJdk(finalEnvVariableJdkFile);
              if (jdk != null) {
                setInitialization(value, finalEnvVariableJdkFile, jdk);
                LOG.info("Using Gradle JDK from " + JDK_LOCATION_ENV_VARIABLE_NAME + "=" + value);
              }
              else {
                setInitializationAsDefinedButInvalid(value);
                LOG.warn("Could not use provided jdk from " + value);
              }
            }
            catch (Throwable exc) {
              setInitializationAsDefinedButInvalid(value);
              LOG.warn("Could not use provided jdk from " + value, exc);
            }
          }
        }
      };
      Application application = ApplicationManager.getApplication();
      boolean onReadAction = application.isReadAccessAllowed();
      boolean hasWriteIntendLock = application.isWriteThread();
      if (onReadAction && !hasWriteIntendLock) {
        // Cannot initialize if write access is not allowed since this would cause a deadlock while waiting for EDT.
        application.invokeLater(createJdkTask);
        throw new AssertionError("Cannot create JDK from a read action without write intend");
      }
      else {
        application.invokeAndWait(createJdkTask);
      }
    }

    private void setInitializationAsNotDefined() {
      setInitialization(/* envVariableValue */ null, /* file */ null, /* sdk */null);
    }

    private void setInitializationAsDefinedButInvalid(@NotNull String envVariableValue) {
      setInitialization(envVariableValue, /* file */ null, /* sdk */null);
    }

    private void setInitialization(@Nullable String variableValue, @Nullable File jdkFile, @Nullable Sdk sdk) {
      myVariableValue = variableValue;
      myJdkFile = jdkFile;
      mySdk = sdk;
      myUseJdkEnvVariable = (variableValue != null) && (jdkFile != null) && (sdk != null);
      myInitialized = true;
    }

    public boolean isUseJdkEnvVariable() {
      initialize();
      return myUseJdkEnvVariable;
    }

    boolean isJdkEnvVariableDefined() {
      initialize();
      return myVariableValue != null;
    }

    public boolean IsJdkEnvVariableValid() {
      initialize();
      return mySdk != null;
    }

    public File getJdkFile() {
      initialize();
      return myJdkFile;
    }

    public Sdk getSdk() {
      initialize();
      return mySdk;
    }

    public String getVariableValue() {
      initialize();
      return myVariableValue;
    }

    public boolean setUseJdkEnvVariable(boolean use) {
      initialize();
      // Allow changes only when the Jdk is valid.
      if (!this.IsJdkEnvVariableValid()) {
        return false;
      }
      myUseJdkEnvVariable = use;
      return true;
    }

    public void overrideValue(@Nullable String value) {
      ExternalSystemApiUtil.doWriteAction(() -> {
        // Need to lock initialization to prevent other threads to use the environment variable instead of the override value.
        synchronized (myInitializationLock) {
          myInitialized = false;
          initialize(value);
        }
      });
    }
  }
}
