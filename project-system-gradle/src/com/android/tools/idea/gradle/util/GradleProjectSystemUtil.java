/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static org.jetbrains.plugins.gradle.settings.DistributionType.BUNDLED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetConfiguration;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.utils.BuildScriptUtil;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleSourceSetData;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleProjectSystemUtil {

  public static final ProjectSystemId GRADLE_SYSTEM_ID = GradleConstants.SYSTEM_ID;
  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);
  private static final Logger LOG = Logger.getInstance(GradleProjectSystemUtil.class);
  /**
   * Finds characters that shouldn't be used in the Gradle path.
   * <p/>
   * I was unable to find any specification for Gradle paths. In my experiments, Gradle only failed with slashes. This list may grow if
   * we find any other unsupported characters.
   */
  private static final CharMatcher ILLEGAL_GRADLE_PATH_CHARS_MATCHER = CharMatcher.anyOf("\\/");

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
    File generatedFolder = new File(buildFolder, FilenameConstants.GENERATED);
    File safeArgClassSources = FileUtils.join(generatedFolder, FD_SOURCE_GEN, FilenameConstants.SAFE_ARG_CLASS_SOURCES);

    return FileUtil.isAncestor(safeArgClassSources, folder, false);
  }

  /**
   * Wrapper around {@link IdeBaseArtifact#getGeneratedSourceFolders()} that skips the aapt sources folder when light classes are used by the
   * IDE.
   */
  public static Collection<File> getGeneratedSourceFoldersToUse(
    @NotNull IdeBaseArtifactCore artifact,
    @NotNull IdeAndroidProject androidProject
  ) {
    File buildFolder = androidProject.getBuildFolder();
    return artifact.getGeneratedSourceFolders()
      .stream()
      .filter(folder -> !isAaptGeneratedSourcesFolder(folder, buildFolder))
      .filter(folder -> !isDataBindingGeneratedBaseClassesFolder(folder, buildFolder))
      .filter(folder -> !isSafeArgGeneratedSourcesFolder(folder, buildFolder))
      .collect(Collectors.toList());
  }

  @NotNull
  public static String createFullTaskName(@NotNull String gradleProjectPath, @NotNull String taskName) {
    if (gradleProjectPath.endsWith(GRADLE_PATH_SEPARATOR)) {
      // Prevent double colon when dealing with root module (e.g. "::assemble");
      return gradleProjectPath + taskName;
    }
    return gradleProjectPath + GRADLE_PATH_SEPARATOR + taskName;
  }

  /**
   * Determines the version of the Kotlin plugin in use in the external (Gradle) project with root at projectPath.  The result can
   * be absent if: there are no Kotlin modules in the project; or there are multiple Kotlin modules using different versions of the
   * Kotlin compiler; or if sync has never succeeded in this session.
   */
  public static @Nullable String getKotlinVersionInUse(@NotNull Project project, @NotNull String gradleProjectPath) {
    DataNode<ProjectData> projectData = ExternalSystemApiUtil.findProjectNode(project, GRADLE_SYSTEM_ID, gradleProjectPath);
    if (projectData == null) return null;
    final String[] kotlinVersion = {null};
    final boolean[] foundVersion = {false};
    projectData.visit((node) -> {
      if (node.getKey().equals( KotlinGradleSourceSetData.Companion.getKEY())) {
        KotlinGradleSourceSetData data = (KotlinGradleSourceSetData)node.getData();
        String kotlinPluginVersion = data.getKotlinPluginVersion();
        if (kotlinPluginVersion != null) {
          if (!foundVersion[0]) {
            kotlinVersion[0] = data.getKotlinPluginVersion();
            foundVersion[0] = true;
          }
          else if (!kotlinPluginVersion.equals(kotlinVersion[0])) {
            kotlinVersion[0] = null;
          }
        }
      }
    });
    return kotlinVersion[0];
  }

  /**
   * Determines version of the Android gradle plugin (and model) used by the project. The result can be absent if there are no android
   * modules in the project or if the last sync has failed.
   */
  @Nullable
  public static AgpVersion getAndroidGradleModelVersionInUse(@NotNull Project project) {
    Set<String> foundInLibraries = Sets.newHashSet();
    Set<String> foundInApps = Sets.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {

      GradleAndroidModel androidModel = GradleAndroidModel.get(module);
      if (androidModel != null) {
        IdeAndroidProject androidProject = androidModel.getAndroidProject();
        String modelVersion = androidProject.getAgpVersion();
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

    return found != null ? AgpVersion.tryParse(found) : null;
  }

  @Nullable
  public static AgpVersion getAndroidGradleModelVersionInUse(@NotNull Module module) {
    GradleAndroidModel androidModel = GradleAndroidModel.get(module);
    if (androidModel != null) {
      IdeAndroidProject androidProject = androidModel.getAndroidProject();
      return AgpVersion.tryParse(androidProject.getAgpVersion());
    }

    return null;
  }

  /**
   * Returns the list of {@link Module modules} to build for a given base module.
   */
  @NotNull
  public static List<Module> getModulesToBuild(@NotNull Module module) {
    return Stream
      .concat(Stream.of(module), getModuleSystem(module).getDynamicFeatureModules().stream())
      .collect(Collectors.toList());
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleProjectSystemUtil.class);
  }

  /**
   * Attempts to retrieve the {@link GradleAndroidModel} for the module containing the given file.
   *
   * @param file           the given file.
   * @param honorExclusion if {@code true}, this method will return {@code null} if the given file is "excluded".
   * @return the {@code GradleAndroidModel} for the module containing the given file, or {@code null} if the module is not an Android
   * module.
   */
  @Nullable
  public static GradleAndroidModel findAndroidModelInModule(@NotNull Project project, @NotNull VirtualFile file, boolean honorExclusion) {
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file, honorExclusion);
    if (module == null) {
      return null;
    }

    if (module.isDisposed()) {
      getLogger().warn("Attempted to get an Android Facet from a disposed module");
      return null;
    }

    return GradleAndroidModel.get(module);
  }

  /**
   * Attempts to retrieve the {@link GradleAndroidModel} for the module containing the given file.
   * <p/>
   * This method will return {@code null} if the file is "excluded" or if the module the file belongs to is not an Android module.
   *
   * @param file the given file.
   * @return the {@code GradleAndroidModel} for the module containing the given file, or {@code null} if the file is "excluded" or if the
   * module the file belongs to is not an Android module.
   */
  @Nullable
  public static GradleAndroidModel findAndroidModelInModule(@NotNull Project project, @NotNull VirtualFile file) {
    return findAndroidModelInModule(project, file, true /* ignore "excluded files */);
  }

  @NotNull
  public static List<Module> getAppHolderModulesSupportingBundleTask(@NotNull Project project) {
    return ProjectStructure.getInstance(project).getAppHolderModules().stream()
      .filter(GradleProjectSystemUtil::supportsBundleTask)
      .collect(Collectors.toList());
  }

  /**
   * Returns {@code true} if the module supports the "bundle" task, i.e. if the Gradle
   * plugin associated to the module is of high enough version number and supports
   * the "Bundle" tool.
   */
  public static boolean supportsBundleTask(@NotNull Module module) {
    GradleAndroidModel androidModule = GradleAndroidModel.get(module);
    if (androidModule == null) {
      return false;
    }
    return !StringUtil.isEmpty(androidModule.getSelectedVariant().getMainArtifact().getBuildInformation().getBundleTaskName());
  }

  public static void attemptToUseEmbeddedGradle(@NotNull Project project) {
    if (!StudioFlags.USE_DEVELOPMENT_OFFLINE_REPOS.get()) {
      return;
    }
    if (IdeInfo.getInstance().isAndroidStudio()) {
      GradleWrapper gradleWrapper = GradleWrapper.find(project);
      if (gradleWrapper != null) {
        String gradleVersion = null;
        try {
          String url = gradleWrapper.getDistributionUrl();
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
    if (url == null) {
      return null;
    }

    int foundIndex = url.indexOf("://");
    if (foundIndex == -1) {
      return null;
    }

    String protocol = url.substring(0, foundIndex);
    if (!protocol.equals("http") && !protocol.equals("https")) {
      return null;
    }

    String expectedPrefix = protocol + "://services.gradle.org/distributions/gradle-";
    if (!url.startsWith(expectedPrefix)) {
      return null;
    }

    // look for "-" before "bin" or "all"
    foundIndex = url.indexOf('-', expectedPrefix.length());
    if (foundIndex == -1) {
      return null;
    }

    String version = url.substring(expectedPrefix.length(), foundIndex);
    return isNotEmpty(version) ? version : null;
  }

  private static boolean isCompatibleWithEmbeddedGradleVersion(@NotNull String gradleVersion) {
    return gradleVersion.equals(GRADLE_LATEST_VERSION);
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
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'.
   */
  @Nullable
  public static File getGradleBuildFilePath(@NotNull File dirPath) {
    return BuildScriptUtil.findGradleBuildFile(dirPath);
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
   * Tests if the Gradle path is valid and return index of the offending character or -1 if none.
   */
  public static int isValidGradlePath(@NotNull String gradlePath) {
    return ILLEGAL_GRADLE_PATH_CHARS_MATCHER.indexIn(gradlePath);
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return on(GRADLE_PATH_SEPARATOR).omitEmptyStrings().splitToList(gradlePath);
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
   * Obtains the default path for the module (Gradle sub-project) with the given name inside the given directory.
   */
  @NotNull
  public static File getModuleDefaultPath(@NotNull VirtualFile parentDir, @NotNull String gradlePath) {
    assert !gradlePath.isEmpty();
    String relativePath = getDefaultPhysicalPathFromGradlePath(gradlePath);
    return new File(virtualToIoFile(parentDir), relativePath);
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
   * <p>
   * In case this is not possible use default location as described in {@link #getUserGradlePropertiesFile()}.
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
}
