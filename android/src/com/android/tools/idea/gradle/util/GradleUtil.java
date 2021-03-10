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
import static com.android.SdkConstants.EXT_GRADLE;
import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FD_RES_CLASS;
import static com.android.SdkConstants.FD_SOURCE_GEN;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE_TRANSLATE;
import static com.android.tools.idea.gradle.util.GradleBuildOutputUtil.getOutputFileOrFolderFromListingFile;
import static com.android.tools.idea.gradle.util.GradleBuilds.ENABLE_TRANSLATION_JVM_ARG;
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.SystemProperties.getUserHome;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static icons.StudioIcons.Shell.Filetree.ANDROID_MODULE;
import static icons.StudioIcons.Shell.Filetree.ANDROID_TEST_ROOT;
import static icons.StudioIcons.Shell.Filetree.FEATURE_MODULE;
import static icons.StudioIcons.Shell.Filetree.INSTANT_APPS;
import static icons.StudioIcons.Shell.Filetree.LIBRARY_MODULE;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
import static org.jetbrains.jps.model.serialization.PathMacroUtil.DIRECTORY_STORE_NAME;
import static org.jetbrains.plugins.gradle.settings.DistributionType.BUNDLED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeAndroidLibrary;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeAndroidProjectType;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeLibrary;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetConfiguration;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.utils.BuildScriptUtil;
import com.android.utils.FileUtils;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.facet.ProjectFacetManager;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.Icon;
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
  private static final Pattern PLUGIN_VERSION_PATTERN = Pattern.compile("[012]\\..*");

  private GradleUtil() {
  }

  /**
   * Returns the path of the folder ".idea/caches" in the given project. The returned path is an absolute path.
   *
   * @param project the given project.
   * @return the path of the folder ".idea/caches" in the given project.
   */
  @NotNull
  public static File getCacheFolderRootPath(@NotNull Project project) {
    return new File(project.getBasePath(), join(DIRECTORY_STORE_NAME, "caches"));
  }

  /**
   * Get the main output APK file for the selected variant.
   * The method uses output listing file if it is supported, in which case, the output file will be empty if the project is never built.
   * If build output listing file is not supported, then AndroidArtifact::getOutputs will be used.
   *
   * @return the main output file for selected variant.
   */
  @Nullable
  public static File getOutputFile(@NotNull AndroidModuleModel androidModel) {
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      return getOutputFileOrFolderFromListingFile(androidModel, androidModel.getSelectedVariant().getName(), OutputType.Apk, false);
    }
    else {
      List<IdeAndroidArtifactOutput> outputs = androidModel.getMainArtifact().getOutputs();
      if (outputs.isEmpty()) return null;
      IdeAndroidArtifactOutput output = getFirstItem(outputs);
      assert output != null;
      return output.getOutputFile();
    }
  }

  @NotNull
  public static Icon getModuleIcon(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      return getAndroidModuleIcon(androidModel);
    }
    return AndroidProjectInfo.getInstance(module.getProject()).requiresAndroidModel() ? AllIcons.Nodes.Module : ANDROID_MODULE;
  }

  @NotNull
  public static Icon getAndroidModuleIcon(@NotNull AndroidModuleModel androidModuleModel) {
    return getAndroidModuleIcon(androidModuleModel.getAndroidProject().getProjectType());
  }

  @NotNull
  public static Icon getAndroidModuleIcon(@NotNull IdeAndroidProjectType androidProjectType) {
    switch (androidProjectType) {
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
        return ModuleManager.getInstance(module.getProject()).getModuleGrouper(null).getGroupPath(module).size() <= 1;
      }
    }
    return false;
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
   * Returns true if the file given by the file exists, is a file and ends with either ".gradle"
   * or ".gradle.kts".
   *
   * __Note__: There is a {@link File} implementation of this method {@link BuildScriptUtil#isGradleScript(File)}.
   * Prefer working with {@link VirtualFile}s if possible as these are more compatible with IDEAs testing infrastructure.
   */
  public static boolean isGradleScript(@NotNull VirtualFile file) {
    return !file.isDirectory() && (file.getName().endsWith(EXT_GRADLE) || file.getName().endsWith(EXT_GRADLE_KTS));
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
   * Returns the VirtualFile corresponding to the Gradle settings file for the given directory, this method will not attempt to refresh the
   * file system which means it is safe to be called from a read action. If the most up to date information is needed then the caller
   * should use {@link BuildScriptUtil#findGradleSettingsFile(File)} along with
   * {@link com.intellij.openapi.vfs.VfsUtil#findFileByIoFile(File, boolean)}
   * to ensure a refresh occurs.
   *
   * @param dirPath the path to find the Gradle settings file for.
   * @return the VirtualFile representing the Gradle settings file or null if it was unable to be found or the file is invalid.
   */
  @Nullable
  public static VirtualFile getGradleSettingsFile(@NotNull File dirPath) {
    File gradleSettingsFilePath = BuildScriptUtil.findGradleSettingsFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleSettingsFilePath, false);
    return (result != null && result.isValid()) ? result : null;
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

      File jdkPath = IdeSdks.getInstance().getJdkPath();
      if (jdkPath != null) {
        executionSettings.setJavaHome(jdkPath.getPath());
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
    File location = getModuleDefaultPath(project.getBaseDir(), gradlePath);
    if (location.isFile()) {
      return true;
    }
    if (location.isDirectory()) {
      File[] children = location.listFiles();
      return children == null || children.length > 0;
    }
    return false;
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
        IdeAndroidProject androidProject = androidModel.getAndroidProject();
        String modelVersion = androidProject.getModelVersion();
        if (androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
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

  @Nullable
  public static GradleVersion getAndroidGradleModelVersionInUse(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      IdeAndroidProject androidProject = androidModel.getAndroidProject();
      return GradleVersion.tryParse(androidProject.getModelVersion());
    }

    return null;
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
   * Returns {@code true} if the given dependencies include the given artifact, which consists of a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param dependencies the Gradle dependencies object to check
   * @param artifact     the artifact
   * @return {@code true} if the dependencies include the given artifact (including transitively)
   */
  private static boolean dependsOnAndroidLibrary(@NonNull IdeDependencies dependencies, @NonNull String artifact) {
    for (IdeLibrary library : dependencies.getAndroidLibraries()) {
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
   * @param library  the Gradle library to check
   * @param artifact the artifact
   * @return {@code true} if the project depends on the given artifact
   */
  public static boolean dependsOn(@NonNull IdeLibrary library, @NonNull String artifact) {
    return getDependencyVersion(library, artifact) != null;
  }

  private static String getDependencyVersion(@NonNull IdeLibrary library, @NonNull String artifact) {
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

  /**
   * Find the Library whose exploded aar folder matches given directory.
   *
   * @param bundleDir The directory to search for.
   * @param variant   The variant.
   * @return the Library matches contains given bundleDir
   */
  @Nullable
  public static IdeLibrary findLibrary(@NotNull File bundleDir, @NotNull IdeVariant variant) {
    IdeAndroidArtifact artifact = variant.getMainArtifact();
    IdeDependencies dependencies = artifact.getLevel2Dependencies();
    for (IdeAndroidLibrary library : dependencies.getAndroidLibraries()) {
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
        }
        else {
          return s.substring(0, suffixBegin) + suffix;
        }
      }
    }

    return s;
  }

  /**
   * Checks if the given folder contains sources generated by aapt. When the IDE uses light R and Manifest classes, these folders are not
   * marked as sources of the module.
   *
   * <p>Note that folder names used by AGP suggest this is only for generated R.java files (generated/source/r,
   * generate/not_namespaced_r_class_sources) but in reality this is where aapt output goes, so this includes Manifest.java if custom
   * permissions are defined in the manifest.
   */
  public static boolean isAaptGeneratedSourcesFolder(@NotNull File folder, @NotNull File buildFolder) {
    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);

    // Folder used in 3.1 and below. Additional level added below for androidTest.
    File generatedSourceR = FileUtils.join(generatedFolder, FD_SOURCE_GEN, FD_RES_CLASS);
    // Naming convention used in 3.2 and above, if R.java files are generated at all.
    File rClassSources = new File(generatedFolder, FilenameConstants.NOT_NAMESPACED_R_CLASS_SOURCES);

    return FileUtil.isAncestor(generatedSourceR, folder, false) || FileUtil.isAncestor(rClassSources, folder, false);
  }

  /**
   * Checks if the given folder contains "Binding" base classes generated by data binding. The IDE provides light versions of these classes,
   * so it can be useful to ignore them as source folders.
   *
   * See {@link FilenameConstants#DATA_BINDING_BASE_CLASS_SOURCES} for a bit more detail.
   *
   * TODO(b/129543943): Investigate moving this logic into the data binding module
   */
  @VisibleForTesting
  public static boolean isDataBindingGeneratedBaseClassesFolder(@NotNull File folder, @NotNull File buildFolder) {
    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);
    File dataBindingSources = new File(generatedFolder, FilenameConstants.DATA_BINDING_BASE_CLASS_SOURCES);
    return FileUtil.isAncestor(dataBindingSources, folder, false);
  }

  /**
   * Checks if the given folder contains navigation arg classes by safe arg. When the IDE uses light safe arg classes,
   * these folders are not marked as sources of the module.
   */
  public static boolean isSafeArgGeneratedSourcesFolder(@NotNull File folder, @NotNull File buildFolder) {
    if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return false;

    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);
    File safeArgClassSources = FileUtils.join(generatedFolder, FD_SOURCE_GEN, FilenameConstants.SAFE_ARG_CLASS_SOURCES);

    return FileUtil.isAncestor(safeArgClassSources, folder, false);
  }

  /**
   * Wrapper around {@link IdeBaseArtifact#getGeneratedSourceFolders()} that skips the aapt sources folder when light classes are used by the
   * IDE.
   */
  public static Collection<File> getGeneratedSourceFoldersToUse(@NotNull IdeBaseArtifact artifact, @NotNull AndroidModuleModel model) {
    File buildFolder = model.getAndroidProject().getBuildFolder();
    return artifact.getGeneratedSourceFolders()
      .stream()
      .filter(folder -> !isAaptGeneratedSourcesFolder(folder, buildFolder))
      .filter(folder -> !isDataBindingGeneratedBaseClassesFolder(folder, buildFolder))
      .filter(folder -> !isSafeArgGeneratedSourcesFolder(folder, buildFolder))
      .collect(Collectors.toList());
  }

  /**
   * Given a project, return what types of build files are used.
   *
   * @param   project Project to analyse
   * @return  A set containing values from {{@link{DOT_GRADLE}, {@link{DOT_KTS}}
   */
  public static Set<String> projectBuildFilesTypes(@NotNull Project project) {
    HashSet<String> result = new HashSet<>();
    addBuildFileType(result, getGradleBuildFile(getBaseDirPath(project)));
    for(Module module : ModuleManager.getInstance(project).getModules()) {
      addBuildFileType(result, getGradleBuildFile(module));
    }
    return result;
  }

  public static boolean hasKtsBuildFiles(@NotNull Project project) {
    return projectBuildFilesTypes(project).contains(DOT_KTS);
  }

  private static void addBuildFileType(@NotNull HashSet<String> result, @Nullable VirtualFile buildFile) {
    if (buildFile != null) {
      String buildFileExtension = buildFile.getExtension();
      if (buildFileExtension == null) {
        return;
      }
      buildFileExtension = "." + buildFileExtension;
      if (buildFileExtension.equalsIgnoreCase(DOT_GRADLE)) {
        result.add(DOT_GRADLE);
      }
      else if (buildFileExtension.equalsIgnoreCase(DOT_KTS)) {
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
}
