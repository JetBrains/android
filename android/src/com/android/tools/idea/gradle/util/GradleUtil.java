/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_KTS;
import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.google.common.base.Splitter.on;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.trimLeading;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static java.util.Objects.requireNonNullElseGet;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
import static org.jetbrains.plugins.gradle.settings.DistributionType.BUNDLED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

import com.android.ide.common.gradle.Version;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetConfiguration;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.utils.BuildScriptUtil;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

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

  private GradleUtil() {
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return on(GRADLE_PATH_SEPARATOR).omitEmptyStrings().splitToList(gradlePath);
  }

  /**
   * Returns the build.gradle file in the given module. This method first checks if the Gradle model has the path of the build.gradle
   * file for the given module. If it doesn't find it, it tries to find a build.gradle inside the module's root directory (folder with .iml
   * file). If it is a root module without sources, it looks inside project's base path before looking in the module's root directory.
   *
   * @param module the given module.
   * @return the build.gradle file in the given module, or {@code null} if it cannot be found.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    GradleModuleModel moduleModel = getGradleModuleModel(module);
    if (moduleModel != null) {
      return moduleModel.getBuildFile();
    }

    File moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module);
    return moduleRoot != null ? getGradleBuildFile(moduleRoot) : null;
  }

  /**
   * Returns the build.gradle file in the given module.
   */
  @Nullable
  public static File getGradleBuildFilePath(@NotNull Module module) {
    GradleModuleModel moduleModel = getGradleModuleModel(module);
    if (moduleModel != null) {
      return moduleModel.getBuildFilePath();
    }

    File moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module);
    return moduleRoot != null ? getGradleBuildFilePath(moduleRoot) : null;
  }

  /**
   * Returns the virtual file representing a build.gradle or build.gradle.kts file in the directory at the given
   * parentDir. build.gradle.kts is only returned when build.gradle doesn't exist and build.gradle.kts exists.
   *
   * __Note__: Do __not__ use this method unless you have to, use {@link #getGradleBuildFile(Module)} instead.
   * This will return the actual build script that is used by Gradle rather than just guessing its location.
   *
   * __Note__: There is a {@link File} implementation of this method {@link BuildScriptUtil#findGradleBuildFile(File)}.
   * Prefer working with {@link VirtualFile}s if possible as these are more compatible with IDEAs testing infrastructure.
   *
   */
  @Nullable
  public static VirtualFile findGradleBuildFile(@NotNull VirtualFile parentDir) {
    return findFileWithNames(parentDir, FN_BUILD_GRADLE, FN_BUILD_GRADLE_KTS);
  }

  /**
   * Returns the virtual file representing a settings.gradle or settings.gradle.kts file in the directory at the given
   * parentDir. settings.gradle.kts is only returned when settings.gradle doesn't exist and settings.gradle.kts exists.
   *
   * __Note__: There is a {@link File} implementation of this method {@link BuildScriptUtil#findGradleSettingsFile(File)}.
   * Prefer working with {@link VirtualFile}s if possible as these are more compatible with IDEAs testing infrastructure.
   */
  @Nullable
  public static VirtualFile findGradleSettingsFile(@NotNull VirtualFile parentDir) {
    return findFileWithNames(parentDir, FN_SETTINGS_GRADLE, FN_SETTINGS_GRADLE_KTS);
  }

  /**
   * Finds and returns a file that exists as a child of the parentDir with one of the given names. This method will search for the
   * names in order and will return as soon as one is found.
   */
  @Nullable
  private static VirtualFile findFileWithNames(@NotNull VirtualFile parentDir, @NotNull String...names) {
    for (String name : names) {
      VirtualFile file = parentDir.findChild(name);
      if (file != null && !file.isDirectory()) {
        return file;
      }
    }
    return null;
  }

  @Nullable
  public static GradleModuleModel getGradleModuleModel(Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return null;
    }
    return gradleFacet.getGradleModuleModel();
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'. This method does not cause a VFS
   * refresh of the file, this should be done by the caller if it is likely that the file has just been created on disk.
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
    File gradleBuildFilePath = BuildScriptUtil.findGradleBuildFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleBuildFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'.
   */
  @Nullable
  public static File getGradleBuildFilePath(@NotNull File dirPath) {
    return BuildScriptUtil.findGradleBuildFile(dirPath);
  }

  @NotNull
  public static GradleExecutionSettings getOrCreateGradleExecutionSettings(@NotNull Project project) {
    GradleExecutionSettings executionSettings = getGradleExecutionSettings(project);
    if (IdeInfo.getInstance().isAndroidStudio()) {
      if (executionSettings == null) {
        File gradlePath = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionPath();
        assert gradlePath != null && gradlePath.isDirectory();
        executionSettings = new GradleExecutionSettings(gradlePath.getPath(), null, LOCAL, null, false);
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
  public static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    return GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
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
   * Checks if module with childPath is a direct child of module with parentPath,
   * meaning that childPath should have exactly one extra path segment in the end.
   */
  public static boolean isDirectChild(String childPath, String parentPath) {
    List<String> childSegments = getPathSegments(childPath);
    return !childSegments.isEmpty() && childSegments.subList(0, childSegments.size() - 1).equals(getPathSegments(parentPath));
  }


  /**
   * Returns gradle paths for parent modules of the given path.<br/>
   * For example:
   * <ul>
   * <li>":foo:bar:buz:lib" -> [":foo", ":foo:bar", ":foo:bar:buz"]</li>
   * </ul>
   */
  @NotNull
  public static Set<String> getAllParentModulesPaths(@NotNull String gradlePath) {
    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (String parentPath = getParentModulePath(gradlePath); !parentPath.isEmpty(); parentPath = getParentModulePath(parentPath)) {
      result.add(parentPath);
    }
    return result.build();
  }

  /**
   * Returns gradle path of a parent of provided path.
   * Empty string is returned for the root module.
   */
  @NotNull
  public static String getParentModulePath(@NotNull String gradlePath) {
    int parentPathEnd = gradlePath.lastIndexOf(GRADLE_PATH_SEPARATOR);
    if (parentPathEnd <= 0) {
      return "";
    }
    else {
      return gradlePath.substring(0, parentPathEnd);
    }
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

  private static boolean isCompatibleWithEmbeddedGradleVersion(@NotNull String gradleVersion) {
    return gradleVersion.equals(GRADLE_LATEST_VERSION);
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

  /**
   * This method calculates the path for user gradle.properties file based on gradle user home folder
   * defined in execution settings for this project.
   *
   * In case this is not possible use default location as described in {@link  GradleUtil#getUserGradlePropertiesFile()}.
   */
  @NotNull
  public static File getUserGradlePropertiesFile(@NotNull Project project) {
    GradleExecutionSettings settings = getGradleExecutionSettings(project);
    if (settings != null) {
      String gradleHomePath = settings.getServiceDirectory();
      if (!Strings.isNullOrEmpty(gradleHomePath)) {
        return new File(gradleHomePath, FN_GRADLE_PROPERTIES);
      }
    }
    return getUserGradlePropertiesFile();
  }

  /**
   * Calculates location of user gradle.properties based on system properties and environment variables. See
   * <a href="https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties">gradle properties</a>
   * section in gradle documentation for the context.
   * @return file pointing to gradle.properties in gradle user home.
   */
  @NotNull
  private static File getUserGradlePropertiesFile() {
    String gradleUserHome = System.getProperty("gradle.user.home");
    if (Strings.isNullOrEmpty(gradleUserHome)) {
      gradleUserHome = System.getenv("GRADLE_USER_HOME");
    }
    if (Strings.isNullOrEmpty(gradleUserHome)) {
      gradleUserHome = join(System.getProperty("user.home"), DOT_GRADLE);
    }
    return new File(gradleUserHome, FN_GRADLE_PROPERTIES);
  }

  /**
   * This method converts a configuration name from (for example) "compile" to "implementation" if the
   * Gradle plugin version is 3.0 or higher.
   *
   * @param configuration The original configuration name, such as "androidTestCompile"
   * @param pluginVersion The plugin version. If null, assumed to be current.
   * @param preferApi     If true, will use "api" instead of "implementation" for new configurations
   * @return the right configuration name to use
   */
  @NotNull
  public static String mapConfigurationName(@NotNull String configuration,
                                            @Nullable AgpVersion pluginVersion,
                                            boolean preferApi) {
    return mapConfigurationName(configuration, pluginVersion != null && !pluginVersion.isAtLeastIncludingPreviews(3, 0, 0), preferApi);
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
    AgpVersion agpVersion = AgpVersion.tryParse(requireNonNullElseGet(pluginVersion, LatestKnownPluginVersionProvider.INSTANCE::get));
    return mapConfigurationName(configuration, agpVersion, preferApi);
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
        }
        else {
          return s.substring(0, suffixBegin) + suffix;
        }
      }
    }

    return s;
  }

  /**
   * Given a project, return what types of build files are used.
   *
   * @param   project Project to analyse
   * @return  A set containing values from {{@link{DOT_GRADLE}, {@link{DOT_KTS}}
   */
  public static Set<String> projectBuildFilesTypes(@NotNull Project project) {
    HashSet<String> result = new HashSet<>();
    addBuildFileType(result, getGradleBuildFilePath(getBaseDirPath(project)));
    ReadAction.run(() -> {
      for(Module module : ModuleManager.getInstance(project).getModules()) {
        addBuildFileType(result, getGradleBuildFilePath(module));
      }
    });
    return result;
  }

  private static void addBuildFileType(@NotNull HashSet<String> result, @Nullable File buildFile) {
    if (buildFile != null) {
      String buildFileName = buildFile.getName().toLowerCase(Locale.getDefault());
      if (buildFileName.endsWith(DOT_GRADLE)) {
        result.add(DOT_GRADLE);
      }
      else if (buildFileName.endsWith(DOT_KTS)) {
        result.add(DOT_KTS);
      }
    }
  }

  /**
   * Get last known AGP version from a project. It can be null if it has not been setup.
   */
  @Nullable
  public static String getLastKnownAndroidGradlePluginVersion(@NotNull Project project) {
    for (Module module : ProjectFacetManager.getInstance(project).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      GradleFacetConfiguration configuration = gradleFacet.getConfiguration();
      String version = configuration.LAST_KNOWN_AGP_VERSION;
      if (version != null) {
        // All versions should be the same, return version from first module found
        return version;
      }
    }
    return null;
  }

  /**
   * Get last successful AGP version from a project. It can be null if sync has never been successful.
   */
  @Nullable
  public static String getLastSuccessfulAndroidGradlePluginVersion(@NotNull Project project) {
    for (Module module : ProjectFacetManager.getInstance(project).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      GradleFacetConfiguration configuration = gradleFacet.getConfiguration();
      String version = configuration.LAST_SUCCESSFUL_SYNC_AGP_VERSION;
      if (version != null) {
        // All versions should be the same, return version from first module found
        return version;
      }
    }
    return null;
  }

  /**
   * Computes a library name intended for display purposes; names may not be unique
   * (and separator is always ":"). It will only show the artifact id, if that id contains slashes, otherwise
   * it will include the last component of the group id (unless identical to the artifact id).
   * <p>
   * E.g.
   * com.android.support.test.espresso:espresso-core:3.0.1@aar -> espresso-core:3.0.1
   * android.arch.lifecycle:extensions:1.0.0-beta1@aar -> lifecycle:extensions:1.0.0-beta1
   * com.google.guava:guava:11.0.2@jar -> guava:11.0.2
   */
  @NotNull
  public static String getDependencyDisplayName(@NotNull String artifactAddress) {
    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString(artifactAddress);
    if (coordinates != null) {
      String name = coordinates.getArtifactId();

      // For something like android.arch.lifecycle:runtime, instead of just showing "runtime",
      // we show "lifecycle:runtime"
      if (!name.contains("-")) {
        String groupId = coordinates.getGroupId();
        int index = groupId.lastIndexOf('.'); // okay if it doesn't exist
        String groupSuffix = groupId.substring(index + 1);
        if (!groupSuffix.equals(name)) { // e.g. for com.google.guava:guava we'd end up with "guava:guava"
          name = groupSuffix + ":" + name;
        }
      }

      Version version = coordinates.getLowerBoundVersion();
      if (version != null && !"unspecified".equals(version.toString())) {
        name += ":" + version;
      }
      return name;
    }
    return trimLeading(artifactAddress, ':');
  }
}
