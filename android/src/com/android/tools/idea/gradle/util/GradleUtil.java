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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
<<<<<<< HEAD
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
=======
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
>>>>>>> goog/upstream-ij17
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.IdeSdks;
<<<<<<< HEAD
import com.android.utils.SdkUtils;
=======
>>>>>>> goog/upstream-ij17
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
<<<<<<< HEAD
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
=======
import java.util.*;
>>>>>>> goog/upstream-ij17

import static com.android.SdkConstants.*;
import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE_TRANSLATE;
import static com.android.tools.idea.gradle.util.GradleBuilds.ENABLE_TRANSLATION_JVM_ARG;
<<<<<<< HEAD
import static com.android.tools.idea.startup.GradleSpecificInitializer.GRADLE_DAEMON_TIMEOUT_MS;
=======
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
>>>>>>> goog/upstream-ij17
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.WARNING;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.SystemProperties.getUserHome;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static icons.StudioIcons.Shell.Filetree.*;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
<<<<<<< HEAD
import static org.jetbrains.jps.model.serialization.PathMacroUtil.DIRECTORY_STORE_NAME;
=======
import static org.jetbrains.plugins.gradle.settings.DistributionType.BUNDLED;
>>>>>>> goog/upstream-ij17
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  public static final ProjectSystemId GRADLE_SYSTEM_ID = GradleConstants.SYSTEM_ID;

  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);

  /**
   * Finds characters that shouldn't be used in the Gradle path.
   * <p/>
   * I was unable to find any specification for Gradle paths. In my experiments, Gradle only failed with slashes. This list may grow if
   * we find any other unsupported characters.
   */
  private static final CharMatcher ILLEGAL_GRADLE_PATH_CHARS_MATCHER = CharMatcher.anyOf("\\/");
  private static final Pattern PLUGIN_VERSION_PATTERN = Pattern.compile("[012]\\..*");

  private GradleUtil() {
  }

<<<<<<< HEAD
  /**
   * Returns the path of the folder ".idea/caches" in the given project. The returned path is an absolute path.
   *
   * @param project the given project.
   * @return the path of the folder ".idea/caches" in the given project.
   */
  @NotNull
  public static File getCacheFolderRootPath(@NotNull Project project) {
    return new File(project.getBasePath(), join(DIRECTORY_STORE_NAME, "caches"));
=======
  @NotNull
  public static Collection<File> getGeneratedSourceFolders(@NotNull BaseArtifact artifact) {
    try {
      Collection<File> folders = artifact.getGeneratedSourceFolders();
      // JavaArtifactImpl#getGeneratedSourceFolders returns null even though BaseArtifact#getGeneratedSourceFolders is marked as @NonNull.
      // See https://code.google.com/p/android/issues/detail?id=216236
      //noinspection ConstantConditions
      return folders != null ? folders : Collections.emptyList();
    }
    catch (UnsupportedMethodException e) {
      // Model older than 1.2.
    }
    return Collections.emptyList();
  }

  @NotNull
  public static Dependencies getDependencies(@NotNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
    return artifact.getDependencies();
  }

  public static boolean androidModelSupportsInstantApps(@NotNull GradleVersion modelVersion) {
    return modelVersion.compareIgnoringQualifiers("2.3.0") >= 0;
>>>>>>> goog/upstream-ij17
  }

  public static void clearStoredGradleJvmArgs(@NotNull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    String existingJvmArgs = settings.getGradleVmOptions();
    settings.setGradleVmOptions(null);
    if (!isEmptyOrSpaces(existingJvmArgs)) {
      invokeAndWaitIfNeeded((Runnable)() -> {
        String jvmArgs = existingJvmArgs.trim();
        String msg =
          String.format("Starting with version 1.3, Android Studio no longer supports IDE-specific Gradle JVM arguments.\n\n" +
                        "Android Studio will now remove any stored Gradle JVM arguments.\n\n" +
                        "Would you like to copy these JVM arguments:\n%1$s\n" +
                        "to the project's gradle.properties file?\n\n" +
                        "(Any existing JVM arguments in the gradle.properties file will be overwritten.)", jvmArgs);

        int result = Messages.showYesNoDialog(project, msg, "Gradle Settings", getQuestionIcon());
        if (result == Messages.YES) {
          try {
            GradleProperties gradleProperties = new GradleProperties(project);
            gradleProperties.setJvmArgs(jvmArgs);
            gradleProperties.save();
          }
          catch (IOException e) {
            String err = String.format("Failed to copy JVM arguments '%1$s' to the project's gradle.properties file.", existingJvmArgs);
            LOG.info(err, e);

            String cause = e.getMessage();
            if (isNotEmpty(cause)) {
              err += String.format("<br>\nCause: %1$s", cause);
            }

            AndroidNotification.getInstance(project).showBalloon("Gradle Settings", err, ERROR);
          }
        }
        else {
          String text =
            String.format("JVM arguments<br>\n'%1$s'<br>\nwere not copied to the project's gradle.properties file.", existingJvmArgs);
          AndroidNotification.getInstance(project).showBalloon("Gradle Settings", text, WARNING);
        }
      });
    }
  }

  public static boolean isSupportedGradleVersion(@NotNull GradleVersion gradleVersion) {
    GradleVersion supported = GradleVersion.parse(GRADLE_MINIMUM_VERSION);
    return supported.compareTo(gradleVersion) <= 0;
  }

  /**
   * This is temporary, until the model returns more outputs per artifact.
   * Deprecating since the model 0.13 provides multiple outputs per artifact if split apks are enabled.
   */
  @Deprecated
  @NotNull
  public static AndroidArtifactOutput getOutput(@NotNull AndroidArtifact artifact) {
    Collection<AndroidArtifactOutput> outputs = artifact.getOutputs();
    assert !outputs.isEmpty();
    AndroidArtifactOutput output = getFirstItem(outputs);
    assert output != null;
    return output;
  }

  @NotNull
  public static Icon getModuleIcon(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      return getAndroidModuleIcon(androidModel);
    }
    return AndroidProjectInfo.getInstance(module.getProject()).requiresAndroidModel() ? AllIcons.Nodes.PpJdk : ANDROID_MODULE;
  }

  @NotNull
  public static Icon getAndroidModuleIcon(@NotNull AndroidModuleModel androidModuleModel) {
    switch (androidModuleModel.getAndroidProject().getProjectType()) {
      case PROJECT_TYPE_APP:
        return ANDROID_MODULE;
      case PROJECT_TYPE_FEATURE:
        return FEATURE_MODULE;
      case PROJECT_TYPE_INSTANTAPP:
        return INSTANT_APPS;
      case PROJECT_TYPE_LIBRARY:
        return LIBRARY_MODULE;
      case PROJECT_TYPE_TEST:
        return ANDROID_TEST_ROOT;
      default:
        return ANDROID_MODULE;
    }
  }

  @Nullable
  public static IdeAndroidProject getAndroidProject(@NotNull Module module) {
    AndroidModuleModel gradleModel = AndroidModuleModel.get(module);
    return gradleModel != null ? gradleModel.getAndroidProject() : null;
  }

  @Nullable
  public static NativeAndroidProject getNativeAndroidProject(@NotNull Module module) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    return ndkModuleModel != null ? ndkModuleModel.getAndroidProject() : null;
  }

  /**
   * Returns the Gradle "logical" path (using colons as separators) if the given module represents a Gradle project or sub-project.
   *
   * @param module the given module.
   * @return the Gradle path for the given module, or {@code null} if the module does not represent a Gradle project or sub-project.
   */
  @Nullable
  public static String getGradlePath(@NotNull Module module) {
    GradleFacet facet = GradleFacet.getInstance(module);
    return facet != null ? facet.getConfiguration().GRADLE_PROJECT_PATH : null;
  }

  /**
   * Returns whether the given module is the module corresponding to the project root (i.e. gradle path of ":") and has no source roots.
   * <p/>
   * The default Android Studio projects create an empty module at the root level. In theory, users could add sources to that module, but
   * we expect that most don't and keep that as a module simply to tie together other modules.
   */
  public static boolean isRootModuleWithNoSources(@NotNull Module module) {
    if (ModuleRootManager.getInstance(module).getSourceRoots().length == 0) {
      String gradlePath = getGradlePath(module);
      if (gradlePath == null || gradlePath.equals(":")) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return list of the module dependencies in the given variant. This method checks dependencies in the main and test (as currently selected
   * in the UI) artifacts.
   */
  @NotNull
  public static List<Library> getModuleDependencies(@NotNull IdeVariant variant) {
    List<Library> libraries = Lists.newArrayList();

    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();
    IdeDependencies dependencies = mainArtifact.getLevel2Dependencies();
    libraries.addAll(dependencies.getModuleDependencies());

    for (IdeBaseArtifact testArtifact : variant.getTestArtifacts()) {
      dependencies = testArtifact.getLevel2Dependencies();
      libraries.addAll(dependencies.getModuleDependencies());
    }
    return libraries;
  }

  @Nullable
  public static Module findModuleByGradlePath(@NotNull Project project, @NotNull String gradlePath) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        if (gradlePath.equals(gradleFacet.getConfiguration().GRADLE_PROJECT_PATH)) {
          return module;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return on(GRADLE_PATH_SEPARATOR).omitEmptyStrings().splitToList(gradlePath);
  }

  /**
   * Returns the build.gradle file in the given module. This method first checks if the Gradle model has the path of the build.gradle
   * file for the given module. If it doesn't find it, it tries to find a build.gradle inside the module's root directory.
   *
   * @param module the given module.
   * @return the build.gradle file in the given module, or {@code null} if it cannot be found.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet != null && gradleFacet.getGradleModuleModel() != null) {
      return gradleFacet.getGradleModuleModel().getBuildFile();
    }
    // At the time we're called, module.getModuleFile() may be null, but getModuleFilePath returns the path where it will be created.
    File moduleFilePath = new File(module.getModuleFilePath());
    File parentFile = moduleFilePath.getParentFile();
    return parentFile != null ? getGradleBuildFile(parentFile) : null;
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the build.gradle file in the directory at the given path, or {@code null} if there is no build.gradle file in the given
   * directory path.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File dirPath) {
    File gradleBuildFilePath = getGradleBuildFilePath(dirPath);
    return findFileByIoFile(gradleBuildFilePath, true);
  }

  /**
   * Returns the path of a build.gradle file in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will return the path '~/myProject/myModule/build.gradle'. Please note that a build.gradle file
   * may not exist at the returned path.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the path of a build.gradle file in the directory at the given path.
   */
  @NotNull
  public static File getGradleBuildFilePath(@NotNull File dirPath) {
    return new File(dirPath, FN_BUILD_GRADLE);
  }

  @Nullable
  public static VirtualFile getGradleSettingsFile(@NotNull File dirPath) {
    File gradleSettingsFilePath = getGradleSettingsFilePath(dirPath);
    return findFileByIoFile(gradleSettingsFilePath, true);
  }

  @NotNull
  private static File getGradleSettingsFilePath(@NotNull File dirPath) {
    return new File(dirPath, FN_SETTINGS_GRADLE);
  }

<<<<<<< HEAD
  @Nullable
  public static GradleExecutionSettings getOrCreateGradleExecutionSettings(@NotNull Project project) {
=======
  @NotNull
  public static GradleExecutionSettings getOrCreateGradleExecutionSettings(@NotNull Project project, boolean useEmbeddedGradle) {
>>>>>>> goog/upstream-ij17
    GradleExecutionSettings executionSettings = getGradleExecutionSettings(project);
    if (IdeInfo.getInstance().isAndroidStudio()) {
      if (executionSettings == null) {
        File gradlePath = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionPath();
        assert gradlePath != null && gradlePath.isDirectory();
        executionSettings = new GradleExecutionSettings(gradlePath.getPath(), null, LOCAL, null, false);
        File jdkPath = IdeSdks.getInstance().getJdkPath();
        if (jdkPath != null) {
          executionSettings.setJavaHome(jdkPath.getPath());
        }
      }
    }
    if(executionSettings == null) {
      executionSettings = new GradleExecutionSettings(null, null, BUNDLED, null, false);
    }
    return executionSettings;
  }

  @Nullable
  public static GradleExecutionSettings getGradleExecutionSettings(@NotNull Project project) {
    GradleProjectSettings projectSettings = getGradleProjectSettings(project);
    if (projectSettings == null) {
      File baseDirPath = getBaseDirPath(project);
      String msg = String
        .format("Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'", project.getName(), baseDirPath.getPath());
      LOG.info(msg);
      return null;
    }

    try {
      return getExecutionSettings(project, projectSettings.getExternalProjectPath(), GRADLE_SYSTEM_ID);
    }
    catch (IllegalArgumentException e) {
      LOG.info("Failed to obtain Gradle execution settings", e);
      return null;
    }
  }

  @Nullable
  private static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    return GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
  }

  @VisibleForTesting
  @Nullable
  static String getGradleInvocationJvmArg(@Nullable BuildMode buildMode) {
    if (ASSEMBLE_TRANSLATE == buildMode) {
      return AndroidGradleSettings.createJvmArg(ENABLE_TRANSLATION_JVM_ARG, true);
    }
    return null;
  }

  public static void stopAllGradleDaemonsAndRestart() {
    DefaultGradleConnector.close();
    Application application = ApplicationManager.getApplication();
    if (application instanceof ApplicationImpl) {
      ((ApplicationImpl)application).restart(true);
    }
    else {
      application.restart();
    }
  }

  /**
   * Converts a Gradle project name into a system dependent path relative to root project. Please note this is the default mapping from a
   * Gradle "logical" path to a physical path. Users can override this mapping in settings.gradle and this mapping may not always be
   * accurate.
   * <p/>
   * E.g. ":module" becomes "module" and ":directory:module" is converted to "directory/module"
   */
  @NotNull
  public static String getDefaultPhysicalPathFromGradlePath(@NotNull String gradlePath) {
    List<String> segments = getPathSegments(gradlePath);
    return join(toStringArray(segments));
  }

  /**
   * Obtains the default path for the module (Gradle sub-project) with the given name inside the given directory.
   */
  @NotNull
  public static File getModuleDefaultPath(@NotNull VirtualFile parentDir, @NotNull String gradlePath) {
    assert !gradlePath.isEmpty();
    String relativePath = getDefaultPhysicalPathFromGradlePath(gradlePath);
    return new File(virtualToIoFile(parentDir), relativePath);
  }

  /**
   * Tests if the Gradle path is valid and return index of the offending character or -1 if none.
   */
  public static int isValidGradlePath(@NotNull String gradlePath) {
    return ILLEGAL_GRADLE_PATH_CHARS_MATCHER.indexIn(gradlePath);
  }

  /**
   * Checks if the project already has a module with given Gradle path.
   */
  public static boolean hasModule(@Nullable Project project, @NotNull String gradlePath) {
    if (project == null) {
      return false;
    }
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (gradlePath.equals(getGradlePath(module))) {
        return true;
      }
    }
<<<<<<< HEAD
    File location = getModuleDefaultPath(project.getBaseDir(), gradlePath);
    if (location.isFile()) {
      return true;
    }
    if (location.isDirectory()) {
      File[] children = location.listFiles();
      return children == null || children.length > 0;
    }
    return false;
=======
    if (checkProjectFolder) {
      File location = getModuleDefaultPath(project.getBaseDir(), gradlePath);
      if (location.isFile()) {
        return true;
      }
      else if (location.isDirectory()) {
        File[] children = location.listFiles();
        return children == null || children.length > 0;
      }
      else {
        return false;
      }
    }
    else {
      return false;
    }
>>>>>>> goog/upstream-ij17
  }

  /**
   * Determines version of the Android gradle plugin (and model) used by the project. The result can be absent if there are no android
   * modules in the project or if the last sync has failed.
   */
  @Nullable
  public static GradleVersion getAndroidGradleModelVersionInUse(@NotNull Project project) {
    Set<String> foundInLibraries = Sets.newHashSet();
    Set<String> foundInApps = Sets.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {

      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        AndroidProject androidProject = androidModel.getAndroidProject();
        String modelVersion = androidProject.getModelVersion();
        if (androidModel.getAndroidProject().getProjectType() == PROJECT_TYPE_APP) {
          foundInApps.add(modelVersion);
        }
        else {
          foundInLibraries.add(modelVersion);
        }
      }
    }

    String found = null;

    // Prefer the version in app.
    if (foundInApps.size() == 1) {
      found = getOnlyElement(foundInApps);
    }
    else if (foundInApps.isEmpty() && foundInLibraries.size() == 1) {
      found = getOnlyElement(foundInLibraries);
    }

    return found != null ? GradleVersion.tryParse(found) : null;
  }

<<<<<<< HEAD
  @Nullable
  public static GradleVersion getAndroidGradleModelVersionInUse(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      AndroidProject androidProject = androidModel.getAndroidProject();
      return GradleVersion.tryParse(androidProject.getModelVersion());
    }

    return null;
  }

=======
>>>>>>> goog/upstream-ij17
  public static void attemptToUseEmbeddedGradle(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      GradleWrapper gradleWrapper = GradleWrapper.find(project);
      if (gradleWrapper != null) {
        String gradleVersion = null;
        try {
          Properties properties = gradleWrapper.getProperties();
          String url = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
          gradleVersion = getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
        }
        catch (IOException e) {
          LOG.warn("Failed to read file " + gradleWrapper.getPropertiesFilePath().getPath());
        }
        if (gradleVersion != null &&
            isCompatibleWithEmbeddedGradleVersion(gradleVersion) &&
            !GradleLocalCache.getInstance().containsGradleWrapperVersion(gradleVersion, project)) {
          File embeddedGradlePath = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionPath();
          if (embeddedGradlePath != null) {
            GradleProjectSettings gradleSettings = getGradleProjectSettings(project);
            if (gradleSettings != null) {
              gradleSettings.setDistributionType(LOCAL);
              gradleSettings.setGradleHome(embeddedGradlePath.getPath());
            }
          }
        }
      }
    }
  }

  @VisibleForTesting
  @Nullable
  static String getGradleWrapperVersionOnlyIfComingForGradleDotOrg(@Nullable String url) {
    if (url != null) {
      int foundIndex = url.indexOf("://");
      if (foundIndex != -1) {
        String protocol = url.substring(0, foundIndex);
        if (protocol.equals("http") || protocol.equals("https")) {
          String expectedPrefix = protocol + "://services.gradle.org/distributions/gradle-";
          if (url.startsWith(expectedPrefix)) {
            // look for "-" before "bin" or "all"
            foundIndex = url.indexOf('-', expectedPrefix.length());
            if (foundIndex != -1) {
              String version = url.substring(expectedPrefix.length(), foundIndex);
              if (isNotEmpty(version)) {
                return version;
              }
            }
          }
        }
      }
    }
    return null;
  }

  // Currently, the latest Gradle version is 2.2.1, and we consider 2.2 and 2.2.1 as compatible.
  private static boolean isCompatibleWithEmbeddedGradleVersion(@NotNull String gradleVersion) {
    return gradleVersion.equals(GRADLE_MINIMUM_VERSION) || gradleVersion.equals(GRADLE_LATEST_VERSION);
  }

  /**
   * Returns {@code true} if the main artifact of the given Android model depends on the given artifact, which consists of a group id and an
   * artifact id, such as {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOn(@NonNull AndroidModuleModel androidModel, @NonNull String artifact) {
    IdeDependencies dependencies = androidModel.getSelectedMainCompileLevel2Dependencies();
    return dependsOnAndroidLibrary(dependencies, artifact);
  }

  /**
   * Same as {@link #dependsOn(AndroidModuleModel, String)} but searches the list of Java Libraries
   */
  public static boolean dependsOnJavaLibrary(@NonNull AndroidModuleModel androidModel, @NonNull String artifact) {
    IdeDependencies dependencies = androidModel.getSelectedMainCompileLevel2Dependencies();
    for (Library library : dependencies.getJavaLibraries()) {
      if (dependsOn(library, artifact)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@link GradleVersion} of the artifact if the project depends on the given artifact, or {@code null} if project doesn't depend on the artifact.
   */
  @Nullable
  public static GradleVersion getModuleDependencyVersion(@NonNull AndroidModuleModel androidModel, @NonNull String artifact) {
<<<<<<< HEAD
    IdeDependencies dependencies = androidModel.getSelectedMainCompileLevel2Dependencies();
    for (Library library : dependencies.getAndroidLibraries()) {
      String version = getDependencyVersion(library, artifact);
=======
    Dependencies dependencies = androidModel.getSelectedMainCompileDependencies();
    for (AndroidLibrary library : dependencies.getLibraries()) {
      String version = getDependencyVersion(library, artifact, true);
>>>>>>> goog/upstream-ij17
      if (version != null) {
        return GradleVersion.tryParse(version);
      }
    }
    return null;
  }

  /**
   * Returns {@code true} if the androidTest artifact of the given Android model depends on the given artifact, which consists of a group id
   * and an artifact id, such as {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOnAndroidTest(@NonNull AndroidModuleModel androidModel, @NonNull String artifact) {
    IdeDependencies dependencies = androidModel.getSelectedAndroidTestCompileDependencies();
    if (dependencies == null) {
      return false;
    }
    return dependsOnAndroidLibrary(dependencies, artifact);
  }

  /**
   * Returns {@code true} if the given dependencies include the given artifact, which consists of a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param dependencies the Gradle dependencies object to check
   * @param artifact     the artifact
   * @return {@code true} if the dependencies include the given artifact (including transitively)
   */
  private static boolean dependsOnAndroidLibrary(@NonNull IdeDependencies dependencies, @NonNull String artifact) {
    for (Library library : dependencies.getAndroidLibraries()) {
      if (dependsOn(library, artifact)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if the given library depends on the given artifact, which consists a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param library      the Gradle library to check
   * @param artifact     the artifact
   * @param transitively if {@code false}, checks only direct dependencies, otherwise checks transitively
   * @return {@code true} if the project depends on the given artifact
   */
  public static boolean dependsOn(@NonNull AndroidLibrary library, @NonNull String artifact, boolean transitively) {
    return getDependencyVersion(library, artifact, transitively) != null;
  }

  private static String getDependencyVersion(@NonNull AndroidLibrary library, @NonNull String artifact, boolean transitively) {
    MavenCoordinates resolvedCoordinates = library.getResolvedCoordinates();
    //noinspection ConstantConditions
    if (resolvedCoordinates != null) {
      if (artifact.endsWith(resolvedCoordinates.getArtifactId()) &&
          artifact.equals(resolvedCoordinates.getGroupId() + ':' + resolvedCoordinates.getArtifactId())) {
        return resolvedCoordinates.getVersion();
      }
    }

    if (transitively) {
      for (AndroidLibrary dependency : library.getLibraryDependencies()) {
        String version = getDependencyVersion(dependency, artifact, true);
        if (version != null) {
          return version;
        }
      }
    }
    return null;
  }

  /**
   * Returns {@code true} if the given library depends on the given artifact, which consists a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param library  the Gradle library to check
   * @param artifact the artifact
   * @return {@code true} if the project depends on the given artifact
   */
  public static boolean dependsOn(@NonNull Library library, @NonNull String artifact) {
    return getDependencyVersion(library, artifact) != null;
  }

  private static String getDependencyVersion(@NonNull Library library, @NonNull String artifact) {
    GradleCoordinate resolvedCoordinates = GradleCoordinate.parseCoordinateString(library.getArtifactAddress());
    if (resolvedCoordinates != null) {
      if (artifact.equals(resolvedCoordinates.getGroupId() + ':' + resolvedCoordinates.getArtifactId())) {
        return resolvedCoordinates.getRevision();
      }
    }
    return null;
  }

  public static boolean hasCause(@NotNull Throwable e, @NotNull Class<?> causeClass) {
    // We want to ignore class loader difference, that's why we just compare fully-qualified class names here.
    String causeClassName = causeClass.getName();
    for (Throwable ex = e; ex != null; ex = ex.getCause()) {
      if (causeClassName.equals(ex.getClass().getName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static File getGradleUserSettingsFile() {
    String homePath = getUserHome();
    if (homePath == null) {
      return null;
    }
    return new File(homePath, join(DOT_GRADLE, FN_GRADLE_PROPERTIES));
  }

  public static void setBuildToolsVersion(@NotNull Project project, @NotNull String version) {
    List<GradleBuildModel> modelsToUpdate = Lists.newArrayList();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          AndroidModel android = buildModel.android();
          if (android != null && !version.equals(android.buildToolsVersion().value())) {
            android.setBuildToolsVersion(version);
            modelsToUpdate.add(buildModel);
          }
<<<<<<< HEAD
        }
      }
    }

    if (!modelsToUpdate.isEmpty()) {
      runWriteCommandAction(project, () -> {
        for (GradleBuildModel buildModel : modelsToUpdate) {
          buildModel.applyChanges();
        }
      });
    }
  }

  /**
   * Find the Library whose exploded aar folder matches given directory.
   *
   * @param bundleDir The directory to search for.
   * @param variant   The variant.
   * @return the Library matches contains given bundleDir
   */
  @Nullable
  public static Library findLibrary(@NotNull File bundleDir, @NotNull IdeVariant variant) {
    IdeAndroidArtifact artifact = variant.getMainArtifact();
    IdeDependencies dependencies = artifact.getLevel2Dependencies();
    for (Library library : dependencies.getAndroidLibraries()) {
      if (filesEqual(bundleDir, library.getFolder())) {
        return library;
      }
    }
    return null;
  }

  /**
   * This method converts a configuration name from (for example) "compile" to "implementation" if the
   * Gradle plugin version is 3.0 or higher.
   *
   * @param configuration The original configuration name, such as "androidTestCompile"
   * @param pluginVersion The plugin version number, such as 3.0.0-alpha1. If null, assumed to be current.
   * @param preferApi     If true, will use "api" instead of "implementation" for new configurations
   * @return the right configuration name to use
   */
  @NotNull
  public static String mapConfigurationName(@NotNull String configuration,
                                            @Nullable GradleVersion pluginVersion,
                                            boolean preferApi) {
    return mapConfigurationName(configuration, pluginVersion != null ? pluginVersion.toString() : null, preferApi);
  }

  /**
   * This method converts a configuration name from (for example) "compile" to "implementation" if the
   * Gradle plugin version is 3.0 or higher.
   *
   * @param configuration The original configuration name, such as "androidTestCompile"
   * @param pluginVersion The plugin version number, such as 3.0.0-alpha1. If null, assumed to be current.
   * @param preferApi     If true, will use "api" instead of "implementation" for new configurations
   * @return the right configuration name to use
   */
  @NotNull
  public static String mapConfigurationName(@NotNull String configuration,
                                            @Nullable String pluginVersion,
                                            boolean preferApi) {

    boolean compatibilityNames = pluginVersion != null && PLUGIN_VERSION_PATTERN.matcher(pluginVersion).matches();
    return mapConfigurationName(configuration, compatibilityNames, preferApi);
  }

  /**
   * This method converts a configuration name from (for example) "compile" to "implementation" if the
   * Gradle plugin version is 3.0 or higher.
   *
   * @param configuration         The original configuration name, such as "androidTestCompile"
   * @param useCompatibilityNames Whether we should use compatibility names
   * @param preferApi             If true, will use "api" instead of "implementation" for new configurations
   * @return the right configuration name to use
   */
  @NotNull
  private static String mapConfigurationName(@NotNull String configuration,
                                             boolean useCompatibilityNames,
                                             boolean preferApi) {
    if (useCompatibilityNames) {
      return configuration;
    }

    configuration = replaceSuffixWithCase(configuration, "compile", preferApi ? "api" : "implementation");
    configuration = replaceSuffixWithCase(configuration, "provided", "compileOnly");
    configuration = replaceSuffixWithCase(configuration, "apk", "runtimeOnly");

    return configuration;
  }

  /**
   * Returns true if we should use compatibility configuration names (such as "compile") instead
   * of the modern configuration names (such as "api" or "implementation") for the given project
   *
   * @param project the project to consult
   * @return true if we should use compatibility configuration names
   */
  public static boolean useCompatibilityConfigurationNames(@NotNull Project project) {
    return useCompatibilityConfigurationNames(getAndroidGradleModelVersionInUse(project));
  }

  /**
   * Returns true if we should use compatibility configuration names (such as "compile") instead
   * of the modern configuration names (such as "api" or "implementation") for the given Gradle version
   *
   * @param gradleVersion the Gradle plugin version to check
   * @return true if we should use compatibility configuration names
   */
  public static boolean useCompatibilityConfigurationNames(@Nullable GradleVersion gradleVersion) {
    return gradleVersion != null && gradleVersion.getMajor() < 3;
  }


  /**
   * Replaces the given suffix in the string, preserving the case in the string, e.g.
   * replacing "foo" with "bar" will result in "bar", and replacing "myFoo" with "bar"
   * will result in "myBar". (This is not a general purpose method; it assumes that
   * the only non-lowercase letter is the first letter of the suffix.)
   */
  private static String replaceSuffixWithCase(String s, String suffix, String newSuffix) {
    if (SdkUtils.endsWithIgnoreCase(s, suffix)) {
      int suffixBegin = s.length() - suffix.length();
      if (Character.isUpperCase(s.charAt(suffixBegin))) {
        return s.substring(0, suffixBegin) + Character.toUpperCase(newSuffix.charAt(0)) + newSuffix.substring(1);
      }
      else {
        if (suffixBegin == 0) {
          return newSuffix;
=======
>>>>>>> goog/upstream-ij17
        }
        else {
          return s.substring(0, suffixBegin) + suffix;
        }
      }
    }

    return s;
  }
}
