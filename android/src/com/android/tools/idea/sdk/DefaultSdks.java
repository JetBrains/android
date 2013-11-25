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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.android.utils.NullLogger;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class DefaultSdks {
  private static final Logger LOG = Logger.getInstance(DefaultSdks.class);
  private static final Pattern SDK_NAME_PATTERN = Pattern.compile(".*\\(\\d+\\)");

  private DefaultSdks() {
  }

  /**
   * @return what the IDE is using as the home path for the Android SDK for new projects.
   */
  @Nullable
  public static File getDefaultAndroidHome() {
    String sdkHome = null;
    Sdk sdk = getFirstAndroidSdk();
    if (sdk != null) {
      sdkHome = sdk.getHomePath();
    }
    if (sdkHome != null) {
      return new File(FileUtil.toSystemDependentName(sdkHome));
    }
    return null;
  }

  /**
   * @return the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   *         target installed in the SDK; which of those this method returns is not defined.
   */
  @Nullable
  private static Sdk getFirstAndroidSdk() {
    List<Sdk> allAndroidSdks = getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    return null;
  }

  /**
   * Sets the path of Android Studio's default Android SDK. This method should be called in a write action. It is assumed that the given
   * path has been validated by {@link #validateAndroidSdkPath(File)}. This method will fail silently if the given path is not valid.
   *
   * @param path                      the path of the Android SDK.
   * @param updateLocalPropertiesFile indicates whether local.properties file in all open projects should be updated with the given path.
   * @see com.intellij.openapi.application.Application#runWriteAction(Runnable)
   */
  public static List<Sdk> setDefaultAndroidHome(@NotNull File path, boolean updateLocalPropertiesFile) {
    if (validateAndroidSdkPath(path)) {
      // Set up a list of SDKs we don't need any more. At the end we'll delete them.
      List<Sdk> sdksToDelete = Lists.newArrayList();

      File resolved = resolvePath(path);
      String resolvedPath = resolved.getPath();
      // Parse out the new SDK. We'll need its targets to set up IntelliJ SDKs for each.
      AndroidSdkData sdkData = AndroidSdkData.parse(resolvedPath, NullLogger.getLogger());
      if (sdkData != null) {
        // Iterate over all current existing IJ Android SDKs
        for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
          if (sdk.getName().startsWith(AndroidSdkUtils.SDK_NAME_PREFIX)) {
            // Try to set the path in the IntelliJ SDK to this Android SDK.
            if (!setAndroidSdkPath(sdkData, sdk, resolvedPath)) {
              // There wasn't a target in the Android SDK for this IntelliJ SDK. Maybe it
              // points to an API target that doesn't exist in this Android SDK. Delete the
              // IntelliJ SDK.
              sdksToDelete.add(sdk);
            }
          }
        }
      }
      // If there are any API targets that we haven't created IntelliJ SDKs for yet, fill
      // those in.
      List<Sdk> sdks = createAndroidSdksForAllTargets(resolved);

      if (updateLocalPropertiesFile) {
        // Update the local.properties files for any open projects.
        updateLocalProperties(resolvedPath);
      }
      deleteSdks(sdksToDelete);
      return sdks;
    }
    return Collections.emptyList();
  }

  /**
   * @return {@code true} if the given Android SDK path points to a valid Android SDK.
   */
  public static boolean validateAndroidSdkPath(@NotNull File path) {
    return AndroidSdkType.validateAndroidSdk(path.getPath()).first;
  }

  /**
   * Creates a set of IntelliJ SDKs (one for each build target) corresponding to the Android SDK in the given directory, if SDKs with the
   * default naming convention and each individual build target do not already exist. If IntelliJ SDKs do exist, they are not updated.
   */
  @NotNull
  public static List<Sdk> createAndroidSdksForAllTargets(@NotNull File androidHome) {
    String path = androidHome.getPath();
    if (!path.endsWith(File.separator)) {
      path += File.separator;
    }
    List<Sdk> sdks = Lists.newArrayList();
    AndroidSdkData sdkData = AndroidSdkData.parse(path, NullLogger.getLogger());
    if (sdkData != null) {
      IAndroidTarget[] targets = sdkData.getTargets();
      Sdk defaultJdk = getDefaultJdk();
      for (IAndroidTarget target : targets) {
        if (target.isPlatform() && !doesDefaultAndroidSdkExist(target)) {
          String sdkName = AndroidSdkUtils.chooseNameForNewLibrary(target);
          Sdk platform = AndroidSdkUtils.createNewAndroidPlatform(target, path, sdkName, defaultJdk, true);
          sdks.add(platform);
        }
      }
    }
    return sdks;
  }

  /**
   * @return {@code true} if an IntelliJ SDK with the default naming convention already exists for the given Android build target.
   */
  private static boolean doesDefaultAndroidSdkExist(@NotNull IAndroidTarget target) {
    for (Sdk sdk : getEligibleAndroidSdks()) {
      IAndroidTarget platformTarget = getTargetFromEligibleSdk(sdk);
      if (platformTarget.getVersion().equals(target.getVersion())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static IAndroidTarget getTargetFromEligibleSdk(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = getAdditionalDataFromEligibleSdk(sdk);
    AndroidPlatform androidPlatform = data.getAndroidPlatform();
    assert androidPlatform != null;
    return androidPlatform.getTarget();
  }

  private static void updateLocalProperties(final String path) {
    ProjectManager projectManager = ApplicationManager.getApplication().getComponent(ProjectManager.class);
    Project[] openProjects = projectManager.getOpenProjects();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final List<String> projectNames = Lists.newArrayList();
      for (Project project : openProjects) {
        projectNames.add("'" + project.getName() + "'");
      }
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          String format =
            "The local.properties files in projects %1$s will be modified with the path of Android Studio's default Android SDK ('%2$s')";
          Messages.showErrorDialog(String.format(format, projectNames, path), "Sync Android SDKs");
        }
      });
    }
    for (Project project : openProjects) {
      try {
        LocalProperties localProperties = new LocalProperties(project);
        localProperties.setAndroidSdkPath(path);
        localProperties.save();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
  }

  private static void deleteSdks(@NotNull List<Sdk> sdksToDelete) {
    for (Sdk sdk : sdksToDelete) {
      SdkConfigurationUtil.removeSdk(sdk);
    }
  }

  @NotNull
  private static File resolvePath(@NotNull File path) {
    try {
      String resolvedPath = FileUtil.resolveShortWindowsName(path.getPath());
      return new File(resolvedPath);
    }
    catch (IOException e) {
      //file doesn't exist yet
    }
    return path;
  }

  /**
   * Sets the given Android SDK's home path to the given path, and resets all of its content roots.
   */
  private static boolean setAndroidSdkPath(@NotNull AndroidSdkData sdkData, @NotNull Sdk sdk, @NotNull String path) {
    String name = sdk.getName();
    if (!name.startsWith(AndroidSdkUtils.SDK_NAME_PREFIX) || SDK_NAME_PATTERN.matcher(name).matches()) {
      return false;
    }

    SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (!(data instanceof AndroidSdkAdditionalData)) {
      return false;
    }

    AndroidSdkAdditionalData androidSdkData = (AndroidSdkAdditionalData)data;
    AndroidPlatform androidPlatform = androidSdkData.getAndroidPlatform();
    if (androidPlatform == null) {
      return false;
    }

    IAndroidTarget target = sdkData.findTargetByApiLevel(Integer.toString(androidPlatform.getApiLevel()));
    if (target == null) {
      return false;
    }

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(path);
    sdkModificator.removeAllRoots();
    for (OrderRoot orderRoot : AndroidSdkUtils.getLibraryRootsForTarget(target, path, true)) {
      sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
    }
    ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);

    sdkModificator.commitChanges();
    return true;
  }

  /**
   * @return the JDK with the default naming convention, creating one if it is not set up.
   */
  @Nullable
  public static Sdk getDefaultJdk() {
    List<Sdk> androidSdks = getEligibleAndroidSdks();
    if (!androidSdks.isEmpty()) {
      Sdk androidSdk = androidSdks.get(0);
      AndroidSdkAdditionalData data = getAdditionalDataFromEligibleSdk(androidSdk);
      Sdk jdk = data.getJavaSdk();
      if (jdk != null) {
        return jdk;
      }
    }
    List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
    if (!jdks.isEmpty()) {
      return jdks.get(0);
    }
    VirtualFile javaHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getJavaHome());
    return javaHome != null ? createJdk(javaHome) : null;
  }

  /**
   * Filters through all Android SDKs and returns only those that have our special name prefix and which have additional data and a
   * platform.
   */
  @NotNull
  public static List<Sdk> getEligibleAndroidSdks() {
    List<Sdk> sdks = Lists.newArrayList();
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      SdkAdditionalData sdkData = sdk.getSdkAdditionalData();
      if (sdkData instanceof AndroidSdkAdditionalData) {
        AndroidSdkAdditionalData androidSdkData = (AndroidSdkAdditionalData)sdkData;
        if (sdk.getName().startsWith(AndroidSdkUtils.SDK_NAME_PREFIX) && androidSdkData.getAndroidPlatform() != null) {
          sdks.add(sdk);
        }
      }
    }
    return sdks;
  }

  @NotNull
  private static AndroidSdkAdditionalData getAdditionalDataFromEligibleSdk(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    assert data != null;
    return data;
  }

  /**
   * Creates an IntelliJ SDK for the JDK at the given location and returns it, or {@code null} if it could not be created successfully.
   */
  @Nullable
  public static Sdk createJdk(@NotNull VirtualFile homeDirectory) {
    Sdk newSdk = SdkConfigurationUtil.setupSdk(ProjectJdkTable.getInstance().getAllJdks(), homeDirectory, JavaSdk.getInstance(), true, null,
                                               AndroidSdkUtils.DEFAULT_JDK_NAME);
    if (newSdk != null) {
      SdkConfigurationUtil.addSdk(newSdk);
    }
    return newSdk;
  }
}
