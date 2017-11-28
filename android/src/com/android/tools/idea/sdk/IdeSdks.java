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

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.sdk.AndroidSdks.SDK_NAME_PREFIX;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.google.common.base.Preconditions.checkState;
import static com.intellij.ide.impl.NewProjectUtil.applyJdkToProject;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;

public class IdeSdks {
  @NonNls public static final String MAC_JDK_CONTENT_PATH = "/Contents/Home";
  @NonNls private static final String ANDROID_SDK_PATH_KEY = "android.sdk.path";

  private static final Topic<IdeSdkChangeListener> IDE_SYNC_TOPIC = new Topic<>("IDE SDKs", IdeSdkChangeListener.class);

  @NotNull private final AndroidSdks myAndroidSdks;
  @NotNull private final Jdks myJdks;
  @NotNull private final EmbeddedDistributionPaths myEmbeddedDistributionPaths;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final MessageBus myMessageBus;

  @NotNull
  public static IdeSdks getInstance() {
    return ServiceManager.getService(IdeSdks.class);
  }

  @NotNull
  public static MessageBusConnection subscribe(@NotNull IdeSdkChangeListener listener, @NotNull Disposable parentDisposable) {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
    connection.subscribe(IDE_SYNC_TOPIC, listener);
    return connection;
  }

  public IdeSdks(@NotNull AndroidSdks androidSdks,
                 @NotNull Jdks jdks,
                 @NotNull EmbeddedDistributionPaths embeddedDistributionPaths,
                 @NotNull IdeInfo ideInfo,
                 @NotNull MessageBus messageBus) {
    myAndroidSdks = androidSdks;
    myJdks = jdks;
    myEmbeddedDistributionPaths = embeddedDistributionPaths;
    myIdeInfo = ideInfo;
    myMessageBus = messageBus;
  }

  /**
   * @return what the IDE is using as the home path for the Android SDK for new projects.
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

  @Nullable
  public File getAndroidNdkPath() {
    AndroidSdkHandler sdkHandler = myAndroidSdks.tryToChooseSdkHandler();
    LocalPackage ndk = sdkHandler.getLocalPackage(SdkConstants.FD_NDK, new StudioLoggerProgressIndicator(IdeSdks.class));
    return ndk == null ? null : ndk.getLocation();
  }

  @Nullable
  public File getJdkPath() {
    return doGetJdkPath(true);
  }

  @Nullable
  private File doGetJdkPath(boolean createJdkIfNeeded) {
    List<Sdk> androidSdks = getEligibleAndroidSdks();
    if (androidSdks.isEmpty() && createJdkIfNeeded) {
      // This happens when user has a fresh installation of Android Studio without an Android SDK, but with a JDK. Android Studio should
      // populate the text field with the existing JDK.
      Sdk jdk = myJdks.chooseOrCreateJavaSdk();
      if (jdk != null) {
        String jdkPath = jdk.getHomePath();
        if (jdkPath != null) {
          return toSystemDependentPath(jdkPath);
        }
      }
    }
    else {
      for (Sdk sdk : androidSdks) {
        AndroidSdkAdditionalData data = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
        assert data != null;
        Sdk jdk = data.getJavaSdk();
        if (jdk != null) {
          String jdkHomePath = jdk.getHomePath();
          if (jdkHomePath != null) {
            return toSystemDependentPath(jdkHomePath);
          }
        }
      }
    }
    return null;
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

  // Must run inside a WriteAction
  public void setJdkPath(@NotNull File path) {
    if (checkForJdk(path)) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      File canonicalPath = resolvePath(path);
      Sdk chosenJdk = null;

      if (myIdeInfo.isAndroidStudio()) {
        // Delete all JDKs in Android Studio. We want to have only one.
        List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
        for (final Sdk jdk : jdks) {
          ProjectJdkTable.getInstance().removeJdk(jdk);
        }
      }
      else {
        for (Sdk jdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
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

          ProjectManager projectManager = ApplicationManager.getApplication().getComponent(ProjectManager.class);
          Project[] openProjects = projectManager.getOpenProjects();
          for (Project project : openProjects) {
            applyJdkToProject(project, chosenJdk);
          }
        }
        else {
          throw new IllegalStateException("The resolved path '" + canonicalPath.getPath() + "' was not found");
        }
      }
    }
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

      myMessageBus.syncPublisher(IDE_SYNC_TOPIC).sdkPathChanged(path);

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
   * use the result from {@link IdeSdks#getAndroidSdkPath()()}
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
    ProjectManager projectManager = ApplicationManager.getApplication().getComponent(ProjectManager.class);
    Project[] openProjects = projectManager.getOpenProjects();
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
   * @return {@code true} if the given Android SDK path points to a valid Android SDK.
   */
  public boolean isValidAndroidSdkPath(@NotNull File path) {
    return validateAndroidSdk(path, false).success;
  }

  @NotNull
  public List<Sdk> createAndroidSdkPerAndroidTarget(@NotNull File androidSdkPath) {
    List<Sdk> sdks = createAndroidSdkPerAndroidTarget(androidSdkPath, null);
    AnAction sdkManagerAction = ActionManager.getInstance().getAction("WelcomeScreen.RunAndroidSdkManager");
    if (sdkManagerAction != null) {
      sdkManagerAction.update(null);
    }
    return sdks;
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
          Sdk sdk = myAndroidSdks.create(target, sdkData.getLocation(), name, ideJdk, true);
          if (sdk != null) {
            sdks.add(sdk);
          }
        }
      }
    }
    return sdks;
  }

  /**
   * @return {@code true} if an IntelliJ SDK with the default naming convention already exists for the given Android build target.
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
   * Indicates whether the IDE is using its embedded JDK. This JDK is used to invoke Gradle.
   *
   * @return {@code true} if the IDE is using the embedded JDK; {@code false} otherwise.
   */
  public boolean isUsingEmbeddedJdk() {
    File jdkPath = doGetJdkPath(false);
    return jdkPath != null && filesEqual(jdkPath, myEmbeddedDistributionPaths.getEmbeddedJdkPath());
  }

  /**
   * Makes the IDE use its embedded JDK or a JDK selected by the user. This JDK is used to invoke Gradle.
   */
  public void setUseEmbeddedJdk() {
    checkState(myIdeInfo.isAndroidStudio(), "This method is for use in Android Studio only.");
    setJdkPath(myEmbeddedDistributionPaths.getEmbeddedJdkPath());
  }

  /**
   * @return the JDK with the default naming convention, creating one if it is not set up.
   */
  @Nullable
  public Sdk getJdk() {
    return getJdk(JDK_1_8);
  }

  @Nullable
  private Sdk getJdk(@Nullable JavaSdkVersion preferredVersion) {
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

    // This happens when user has a fresh installation of Android Studio, and goes through the 'First Run' Wizard.
    if (myIdeInfo.isAndroidStudio()) {
      Sdk jdk = myJdks.createEmbeddedJdk();
      if (jdk != null) {
        assert isJdkCompatible(jdk, preferredVersion);
        return jdk;
      }
    }

    List<File> jdkPaths = getPotentialJdkPaths();
    for (File jdkPath : jdkPaths) {
      if (checkForJdk(jdkPath)) {
        Sdk jdk = createJdk(jdkPath);
        return isJdkCompatible(jdk, preferredVersion) ? jdk : null;
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

  private static boolean isJdkCompatible(@Nullable Sdk jdk, @Nullable JavaSdkVersion preferredVersion) {
    if (jdk == null) {
      return false;
    }
    if (preferredVersion == null) {
      return true;
    }
    return JavaSdk.getInstance().isOfVersionOrHigher(jdk, preferredVersion);
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

  public interface IdeSdkChangeListener {
    void sdkPathChanged(@NotNull File newSdkPath);
  }

  @TestOnly
  public static void removeJdksOn(@NotNull Disposable disposable) {
    Disposer.register(disposable, () -> WriteAction.run(() -> {
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }
    }));
  }
}
