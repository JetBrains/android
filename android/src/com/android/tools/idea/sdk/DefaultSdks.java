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
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.android.sdk.AndroidSdkUtils.chooseNameForNewLibrary;
import static org.jetbrains.android.sdk.AndroidSdkUtils.createNewAndroidPlatform;

public final class DefaultSdks {
  private static final Logger LOG = Logger.getInstance(DefaultSdks.class);

  private static final String ERROR_DIALOG_TITLE = "Project SDK Update";

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

  @Nullable
  public static File getDefaultJavaHome() {
    List<Sdk> androidSdks = getEligibleAndroidSdks();
    if (androidSdks.isEmpty()) {
      // This happens when user has a fresh installation of Android Studio without an Android SDK, but with a JDK. Android Studio should
      // populate the text field with the existing JDK.
      Sdk jdk = Jdks.chooseOrCreateJavaSdk();
      if (jdk != null) {
        String jdkHomePath = jdk.getHomePath();
        if (jdkHomePath != null) {
          return new File(FileUtil.toSystemDependentName(jdkHomePath));
        }
      }
    }
    else {
      for (Sdk sdk : androidSdks) {
        AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
        assert data != null;
        Sdk jdk = data.getJavaSdk();
        if (jdk != null) {
          String jdkHomePath = jdk.getHomePath();
          if (jdkHomePath != null) {
            return new File(FileUtil.toSystemDependentName(jdkHomePath));
          }
        }
      }
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

  public static void setDefaultJavaHome(@NotNull File path) {
    // Set up a list of SDKs we don't need any more. At the end we'll delete them.
    List<Sdk> sdksToDelete = Lists.newArrayList();

    if (JavaSdk.checkForJdk(path)) {
      File canonicalPath = resolvePath(path);
      // Try to set this path into the "default" JDK associated with the IntelliJ SDKs.
      Sdk defaultJdk = getDefaultJdk();
      if (defaultJdk != null) {
        setJdkPath(defaultJdk, canonicalPath);

        // Flip through the IntelliJ SDKs and make sure they point to this JDK.
        updateAllSdks(defaultJdk);
      }
      else {
        // We didn't have a JDK set at all. Try to create one.
        VirtualFile virtualPath = VfsUtil.findFileByIoFile(canonicalPath, true);
        if (virtualPath != null) {
          defaultJdk = createJdk(virtualPath);
        }
      }
      if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
        // Now iterate through all the JDKs and delete any that aren't the default one.
        List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
        if (defaultJdk != null) {
          for (Sdk jdk : jdks) {
            if (jdk.getName() != defaultJdk.getName()) {
              sdksToDelete.add(defaultJdk);
            }
            else {
              // This may actually be a different copy of the SDK than what we obtained from the JDK. Set its path to be sure.
              setJdkPath(jdk, canonicalPath);
            }
          }
        }
      }
      for (final Sdk sdk : sdksToDelete) {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }
    }
  }

  public static List<Sdk> setDefaultAndroidHome(@NotNull File path) {
    return setDefaultAndroidHome(path, null);
  }

  /**
   * Sets the given JDK's home path to the given path, and resets all of its content roots.
   */
  private static void setJdkPath(@NotNull Sdk sdk, @NotNull File path) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(path.getPath());
    sdkModificator.removeAllRoots();
    ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
    sdkModificator.commitChanges();
    JavaSdk.getInstance().setupSdkPaths(sdk);
  }

  /**
   * Iterates through all Android SDKs and makes them point to the given JDK.
   */
  private static void updateAllSdks(@NotNull Sdk jdk) {
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      AndroidSdkAdditionalData oldData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (oldData == null) {
        continue;
      }
      oldData.setJavaSdk(jdk);
      SdkModificator modificator = sdk.getSdkModificator();
      modificator.setSdkAdditionalData(oldData);
      modificator.commitChanges();
    }
  }

  /**
   * Sets the path of Android Studio's default Android SDK. This method should be called in a write action. It is assumed that the given
   * path has been validated by {@link #isValidAndroidSdkPath(File)}. This method will fail silently if the given path is not valid.
   *
   *
   * @param path the path of the Android SDK.
   * @see com.intellij.openapi.application.Application#runWriteAction(Runnable)
   */
  public static List<Sdk> setDefaultAndroidHome(@NotNull File path, @Nullable Sdk javaSdk) {
    if (isValidAndroidSdkPath(path)) {
      assert ApplicationManager.getApplication().isWriteAccessAllowed();

      // Since removing SDKs is *not* asynchronous, we force an update of the SDK Manager.
      // If we don't force this update, AndroidSdkUtils will still use the old SDK until all SDKs are properly deleted.
      AndroidSdkData oldSdkData = AndroidSdkData.getSdkData(path);
      AndroidSdkUtils.setSdkData(oldSdkData);

      // Set up a list of SDKs we don't need any more. At the end we'll delete them.
      List<Sdk> sdksToDelete = Lists.newArrayList();

      File resolved = resolvePath(path);
      String resolvedPath = resolved.getPath();
      // Parse out the new SDK. We'll need its targets to set up IntelliJ SDKs for each.
      AndroidSdkData sdkData = AndroidSdkData.getSdkData(resolvedPath);
      if (sdkData != null) {
        // Iterate over all current existing IJ Android SDKs
        for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
          if (sdk.getName().startsWith(AndroidSdkUtils.SDK_NAME_PREFIX)) {
            sdksToDelete.add(sdk);
          }
        }
      }
      for (Sdk sdk : sdksToDelete) {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }

      // If there are any API targets that we haven't created IntelliJ SDKs for yet, fill those in.
      List<Sdk> sdks = createAndroidSdksForAllTargets(resolved, javaSdk);

      // Update the local.properties files for any open projects.
      updateLocalPropertiesAndSync(resolved);

      return sdks;
    }
    return Collections.emptyList();
  }

  /**
   * @return {@code true} if the given Android SDK path points to a valid Android SDK.
   */
  public static boolean isValidAndroidSdkPath(@NotNull File path) {
    return AndroidSdkType.validateAndroidSdk(path.getPath()).getFirst();
  }

  @NotNull
  public static List<Sdk> createAndroidSdksForAllTargets(@NotNull File androidHome) {
    List<Sdk> sdks = createAndroidSdksForAllTargets(androidHome, null);
    RunAndroidSdkManagerAction.updateInWelcomePage(null);
    return sdks;
  }

  /**
   * Creates a set of IntelliJ SDKs (one for each build target) corresponding to the Android SDK in the given directory, if SDKs with the
   * default naming convention and each individual build target do not already exist. If IntelliJ SDKs do exist, they are not updated.
   */
  @NotNull
  private static List<Sdk> createAndroidSdksForAllTargets(@NotNull File androidHome, @Nullable Sdk javaSdk) {
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(androidHome);
    if (sdkData == null) {
      return Collections.emptyList();
    }
    IAndroidTarget[] targets = sdkData.getTargets();
    if (targets.length == 0) {
      return Collections.emptyList();
    }
    List<Sdk> sdks = Lists.newArrayList();
    Sdk defaultJdk = javaSdk != null ? javaSdk : getDefaultJdk();
    for (IAndroidTarget target : targets) {
      if (target.isPlatform() && !doesDefaultAndroidSdkExist(target)) {
        String name = chooseNameForNewLibrary(target);
        Sdk sdk = createNewAndroidPlatform(target, sdkData.getLocation().getPath(), name, defaultJdk, true);
        sdks.add(sdk);
      }
    }
    return sdks;
  }

  /**
   * @return {@code true} if an IntelliJ SDK with the default naming convention already exists for the given Android build target.
   */
  private static boolean doesDefaultAndroidSdkExist(@NotNull IAndroidTarget target) {
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
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    assert data != null;
    AndroidPlatform androidPlatform = data.getAndroidPlatform();
    assert androidPlatform != null;
    return androidPlatform.getTarget();
  }

  private static void updateLocalPropertiesAndSync(@NotNull final File sdkHomePath) {
    ProjectManager projectManager = ApplicationManager.getApplication().getComponent(ProjectManager.class);
    Project[] openProjects = projectManager.getOpenProjects();
    if (openProjects.length == 0) {
      return;
    }
    final List<String> projectsToUpdateNames = Lists.newArrayList();
    List<Pair<Project, LocalProperties>> localPropertiesToUpdate = Lists.newArrayList();

    for (Project project : openProjects) {
      if (!Projects.isGradleProject(project)) {
        continue;
      }
      try {
        LocalProperties localProperties = new LocalProperties(project);
        if (!FileUtil.filesEqual(sdkHomePath, localProperties.getAndroidSdkPath())) {
          localPropertiesToUpdate.add(Pair.create(project, localProperties));
          projectsToUpdateNames.add("'" + project.getName() + "'");
        }
      }
      catch (IOException e) {
        // Exception thrown when local.properties file exists but cannot be read (e.g. no writing permissions.)
        logAndShowErrorWhenUpdatingLocalProperties(project, e, "read", sdkHomePath);
      }
    } if (!localPropertiesToUpdate.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            String format =
              "The local.properties files in projects %1$s will be modified with the path of Android Studio's default Android SDK:\n" +
              "'%2$s'";
            Messages.showErrorDialog(String.format(format, projectsToUpdateNames, sdkHomePath), "Sync Android SDKs");
          }
        });
      }
      GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
      for (Pair<Project, LocalProperties> toUpdate : localPropertiesToUpdate) {
        Project project = toUpdate.getFirst();
        try {
          LocalProperties localProperties = toUpdate.getSecond();
          if (!FileUtil.filesEqual(sdkHomePath, localProperties.getAndroidSdkPath())) {
            localProperties.setAndroidSdkPath(sdkHomePath);
            localProperties.save();
          }
        }
        catch (IOException e) {
          logAndShowErrorWhenUpdatingLocalProperties(project, e, "update", sdkHomePath);
          // No point in syncing project if local.properties is pointing to the wrong SDK.
          continue;
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          // Don't sync in tests. For now.
          continue;
        }
        projectImporter.requestProjectSync(project, null);
      }
    }
  }

  private static void logAndShowErrorWhenUpdatingLocalProperties(@NotNull Project project,
                                                                 @NotNull Exception error,
                                                                 @NotNull String action,
                                                                 @NotNull File sdkHomePath) {
    LOG.info(error);
    String msg = String.format("Unable to %1$s local.properties file in project '%2$s'.\n\n" +
                               "Cause: %3$s\n\n" +
                               "Please manually update the file's '%4$s' property value to \n" +
                               "'%5$s'\n" +
                               "and sync the project with Gradle files.", action, project.getName(), getMessage(error),
                               SdkConstants.SDK_DIR_PROPERTY, sdkHomePath.getPath());
    Messages.showErrorDialog(project, msg, ERROR_DIALOG_TITLE);
  }

  @NotNull
  private static String getMessage(@NotNull Exception e) {
    String cause = e.getMessage();
    if (Strings.isNullOrEmpty(cause)) {
      cause = "[Unknown]";
    }
    return cause;
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
   * @return the JDK with the default naming convention, creating one if it is not set up.
   */
  @Nullable
  public static Sdk getDefaultJdk() {
    List<Sdk> androidSdks = getEligibleAndroidSdks();
    if (!androidSdks.isEmpty()) {
      Sdk androidSdk = androidSdks.get(0);
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)androidSdk.getSdkAdditionalData();
      assert data != null;
      Sdk jdk = data.getJavaSdk();
      if (jdk != null) {
        return jdk;
      }
    }
    List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
    if (!jdks.isEmpty()) {
      return jdks.get(0);
    }
    final Collection<String> jdkPaths = JavaSdk.getInstance().suggestHomePaths();
    VirtualFile javaHome = null;

    for (String jdkPath : jdkPaths) {
      javaHome = jdkPath != null ? LocalFileSystem.getInstance().findFileByPath(jdkPath) : null;

      if (javaHome != null) {
        break;
      }
    }
    if (javaHome == null) {
      javaHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getJavaHome());
    }
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

  /**
   * Creates an IntelliJ SDK for the JDK at the given location and returns it, or {@code null} if it could not be created successfully.
   */
  @Nullable
  private static Sdk createJdk(@NotNull VirtualFile homeDirectory) {
    Sdk newSdk = SdkConfigurationUtil.setupSdk(ProjectJdkTable.getInstance().getAllJdks(), homeDirectory, JavaSdk.getInstance(), true, null,
                                               AndroidSdkUtils.DEFAULT_JDK_NAME);
    if (newSdk != null) {
      SdkConfigurationUtil.addSdk(newSdk);
    }
    return newSdk;
  }
}
