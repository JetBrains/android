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
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.GradleVersion.VersionSegment;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import icons.AndroidIcons;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.android.tools.idea.gradle.dsl.model.GradleBuildModel.parseBuildFile;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.android.tools.idea.gradle.eclipse.GradleImport.escapeGroovyStringLiteral;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.service.notification.hyperlink.SearchInBuildFilesHyperlink.searchInBuildFiles;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE_TRANSLATE;
import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.*;
import static com.android.tools.idea.gradle.util.GradleBuilds.ENABLE_TRANSLATION_JVM_ARG;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.android.tools.idea.startup.GradleSpecificInitializer.GRADLE_DAEMON_TIMEOUT_MS;
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.WARNING;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getSettings;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtil.processFileRecursivelyWithoutIgnored;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.SystemProperties.getUserHome;
import static com.intellij.util.containers.ContainerUtil.addAll;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static java.util.Collections.sort;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  public static final ProjectSystemId GRADLE_SYSTEM_ID = GradleConstants.SYSTEM_ID;

  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);

  @NonNls private static final String ANDROID_PLUGIN_GROUP_ID = "com.android.tools.build";
  @NonNls private static final String ANDROID_PLUGIN_ARTIFACT_ID = "gradle";

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);

  private static final Pattern GRADLE_JAR_NAME_PATTERN = Pattern.compile("gradle-([^-]*)-(.*)\\.jar");

  /**
   * Finds characters that shouldn't be used in the Gradle path.
   * <p/>
   * I was unable to find any specification for Gradle paths. In my experiments, Gradle only failed with slashes. This list may grow if
   * we find any other unsupported characters.
   */
  private static final CharMatcher ILLEGAL_GRADLE_PATH_CHARS_MATCHER = CharMatcher.anyOf("\\/");
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN = Pattern.compile(".*-([^-]+)-([^.]+).zip");

  private GradleUtil() {
  }

  @NotNull
  public static Dependencies getDependencies(@NotNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
    if (modelVersion != null && androidModelSupportsDependencyGraph(modelVersion)) {
      return artifact.getCompileDependencies();
    }
    return artifact.getDependencies();
  }

  public static boolean androidModelSupportsDependencyGraph(@NotNull String modelVersion) {
    GradleVersion parsedVersion = GradleVersion.tryParse(modelVersion);
    return parsedVersion != null && androidModelSupportsDependencyGraph(parsedVersion);
  }

  public static boolean androidModelSupportsDependencyGraph(@NotNull GradleVersion modelVersion) {
    return false;
    // TODO enable once BaseArtifact#getCompiledDependencies is submitted in tools/base.
    // return modelVersion.compareIgnoringQualifiers("2.2.0") >= 0;
  }

  public static void clearStoredGradleJvmArgs(@NotNull final Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    final String existingJvmArgs = settings.getGradleVmOptions();
    settings.setGradleVmOptions(null);
    if (!isEmptyOrSpaces(existingJvmArgs)) {
      invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          String jvmArgs = existingJvmArgs.trim();
          final String msg =
            String.format("Starting with version 1.3, Android Studio no longer supports IDE-specific Gradle JVM arguments.\n\n" +
                          "Android Studio will now remove any stored Gradle JVM arguments.\n\n" +
                          "Would you like to copy these JVM arguments:\n%1$s\n" +
                          "to the project's gradle.properties file?\n\n" +
                          "(Any existing JVM arguments in the gradle.properties file will be overwritten.)", jvmArgs);

          int result = Messages.showYesNoDialog(project, msg, "Gradle Settings", Messages.getQuestionIcon());
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

              AndroidGradleNotification.getInstance(project).showBalloon("Gradle Settings", err, ERROR);
            }
          }
          else {
            String text =
              String.format("JVM arguments<br>\n'%1$s'<br>\nwere not copied to the project's gradle.properties file.", existingJvmArgs);
            AndroidGradleNotification.getInstance(project).showBalloon("Gradle Settings", text, WARNING);
          }
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
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject != null) {
      return androidProject.isLibrary() ? AndroidIcons.LibraryModule : AndroidIcons.AppModule;
    }
    return requiresAndroidModel(module.getProject()) ? AllIcons.Nodes.PpJdk : AllIcons.Nodes.Module;
  }

  @Nullable
  public static AndroidProject getAndroidProject(@NotNull Module module) {
    AndroidGradleModel gradleModel = AndroidGradleModel.get(module);
    return gradleModel != null ? gradleModel.getAndroidProject() : null;
  }

  @Nullable
  public static NativeAndroidProject getNativeAndroidProject(@NotNull Module module) {
    NativeAndroidGradleModel gradleModel = NativeAndroidGradleModel.get(module);
    return gradleModel != null ? gradleModel.getNativeAndroidProject() : null;
  }

  /**
   * Returns the Gradle "logical" path (using colons as separators) if the given module represents a Gradle project or sub-project.
   *
   * @param module the given module.
   * @return the Gradle path for the given module, or {@code null} if the module does not represent a Gradle project or sub-project.
   */
  @Nullable
  public static String getGradlePath(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
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
   * Returns the library dependencies in the given variant. This method checks dependencies in the main and test (as currently selected
   * in the UI) artifacts. The dependency lookup is not transitive (only direct dependencies are returned.)
   */
  @NotNull
  public static List<AndroidLibrary> getDirectLibraryDependencies(@NotNull Variant variant, @NotNull AndroidGradleModel androidModel) {
    List<AndroidLibrary> libraries = Lists.newArrayList();

    GradleVersion modelVersion = androidModel.getModelVersion();

    AndroidArtifact mainArtifact = variant.getMainArtifact();
    Dependencies dependencies = getDependencies(mainArtifact, modelVersion);
    libraries.addAll(dependencies.getLibraries());

    BaseArtifact testArtifact = androidModel.findSelectedTestArtifact(variant);
    if (testArtifact != null) {
      dependencies = getDependencies(testArtifact, modelVersion);
      libraries.addAll(dependencies.getLibraries());
    }
    return libraries;
  }

  @Nullable
  public static Module findModuleByGradlePath(@NotNull Project project, @NotNull String gradlePath) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
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
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null && gradleFacet.getGradleModel() != null) {
      return gradleFacet.getGradleModel().getBuildFile();
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
  public static File getGradleSettingsFilePath(@NotNull File dirPath) {
    return new File(dirPath, FN_SETTINGS_GRADLE);
  }

  @NotNull
  public static File getGradleWrapperPropertiesFilePath(@NotNull File projectRootDir) {
    return new File(projectRootDir, GRADLEW_PROPERTIES_PATH);
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file. An unexpected errors that occur while updating the file will
   * be displayed in an error dialog.
   *
   * @param project        the project containing the properties file to update.
   * @param gradleVersion  the Gradle version to update the property to.
   * @param propertiesFile the given Gradle wrapper properties file.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   */
  public static boolean updateGradleDistributionUrl(@NotNull Project project, @NotNull File propertiesFile, @NotNull String gradleVersion) {
    try {
      boolean updated = updateGradleDistributionUrl(gradleVersion, propertiesFile);
      if (updated) {
        VirtualFile virtualFile = findFileByIoFile(propertiesFile, true);
        if (virtualFile != null) {
          virtualFile.refresh(false, false);
        }
        return true;
      }
    }
    catch (IOException e) {
      String msg = String.format("Unable to update Gradle wrapper to use Gradle %1$s\n", gradleVersion);
      msg += e.getMessage();
      Messages.showErrorDialog(project, msg, "Unexpected Error");
    }
    return false;
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file.
   *
   * @param gradleVersion  the Gradle version to update the property to.
   * @param propertiesFile the given Gradle wrapper properties file.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   * correct value.
   * @throws IOException if something goes wrong when saving the file.
   */
  public static boolean updateGradleDistributionUrl(@NotNull String gradleVersion, @NotNull File propertiesFile) throws IOException {
    Properties properties = getProperties(propertiesFile);
    String gradleDistributionUrl = getGradleDistributionUrl(gradleVersion, false);
    String property = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (property != null && (property.equals(gradleDistributionUrl) || property.equals(getGradleDistributionUrl(gradleVersion, true)))) {
      return false;
    }
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, gradleDistributionUrl);
    savePropertiesToFile(properties, propertiesFile, null);
    return true;
  }

  @Nullable
  public static String getGradleWrapperVersion(@NotNull File propertiesFile) throws IOException {
    Properties properties = getProperties(propertiesFile);
    String url = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (url != null) {
      Matcher m = GRADLE_DISTRIBUTION_URL_PATTERN.matcher(url);
      if (m.matches()) {
        return m.group(1);
      }
    }
    return null;
  }

  @NotNull
  private static String getGradleDistributionUrl(@NotNull String gradleVersion, boolean binOnly) {
    String suffix = binOnly ? "bin" : "all";
    return String.format("https://services.gradle.org/distributions/gradle-%1$s-" + suffix + ".zip", gradleVersion);
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
      GradleExecutionSettings settings = getExecutionSettings(project, projectSettings.getExternalProjectPath(), GRADLE_SYSTEM_ID);
      if (settings != null) {
        // By setting the Gradle daemon timeout to -1, we don't allow IDEA to set it to 1 minute. Gradle daemons need to be reused as
        // much as possible. The default timeout is 3 hours.
        settings.setRemoteProcessIdleTtlInMs(GRADLE_DAEMON_TIMEOUT_MS);
      }
      return settings;
    }
    catch (IllegalArgumentException e) {
      LOG.info("Failed to obtain Gradle execution settings", e);
      return null;
    }
  }

  @Nullable
  public static File findWrapperPropertiesFile(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) {
      return null;
    }
    File baseDir = new File(basePath);
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(baseDir);
    return wrapperPropertiesFile.isFile() ? wrapperPropertiesFile : null;
  }

  @Nullable
  public static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    GradleSettings settings = (GradleSettings)getSettings(project, GRADLE_SYSTEM_ID);

    GradleSettings.MyState state = settings.getState();
    assert state != null;
    Set<GradleProjectSettings> allProjectsSettings = state.getLinkedExternalProjectsSettings();

    return getFirstNotNull(allProjectsSettings);
  }

  @Nullable
  private static GradleProjectSettings getFirstNotNull(@Nullable Set<GradleProjectSettings> allProjectSettings) {
    if (allProjectSettings != null) {
      for (GradleProjectSettings settings : allProjectSettings) {
        if (settings != null) {
          return settings;
        }
      }
    }
    return null;
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
    assert gradlePath.length() > 0;
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
  public static boolean hasModule(@Nullable Project project, @NotNull String gradlePath, boolean checkProjectFolder) {
    if (project == null) {
      return false;
    }
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (gradlePath.equals(getGradlePath(module))) {
        return true;
      }
    }
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
  }

  public static void cleanUpPreferences(@NotNull ExtensionPoint<ConfigurableEP<Configurable>> preferences,
                                        @NotNull List<String> bundlesToRemove) {
    List<ConfigurableEP<Configurable>> nonStudioExtensions = Lists.newArrayList();

    ConfigurableEP<Configurable>[] extensions = preferences.getExtensions();
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (bundlesToRemove.contains(extension.instanceClass)) {
        nonStudioExtensions.add(extension);
      }
    }

    for (ConfigurableEP<Configurable> toRemove : nonStudioExtensions) {
      preferences.unregisterExtension(toRemove);
    }
  }

  @Nullable
  public static GradleVersion getGradleVersion(@NotNull Project project) {
    String gradleVersion = getGradleVersionUsed(project);
    if (isNotEmpty(gradleVersion)) {
      // The version of Gradle used is retrieved one of the Gradle models. If that fails, we try to deduce it from the project's Gradle
      // settings.
      GradleVersion revision = GradleVersion.tryParse(removeTimestampFromGradleVersion(gradleVersion));
      if (revision != null) {
        return revision;
      }
    }

    GradleProjectSettings gradleSettings = getGradleProjectSettings(project);
    if (gradleSettings != null) {
      DistributionType distributionType = gradleSettings.getDistributionType();
      if (distributionType == DEFAULT_WRAPPED) {
        File wrapperPropertiesFile = findWrapperPropertiesFile(project);
        if (wrapperPropertiesFile != null) {
          try {
            String wrapperVersion = getGradleWrapperVersion(wrapperPropertiesFile);
            if (wrapperVersion != null) {
              return GradleVersion.tryParse(removeTimestampFromGradleVersion(wrapperVersion));
            }
          }
          catch (IOException e) {
            LOG.info("Failed to read Gradle version in wrapper", e);
          }
        }
      }
      else if (distributionType == LOCAL) {
        String gradleHome = gradleSettings.getGradleHome();
        if (isNotEmpty(gradleHome)) {
          File gradleHomePath = new File(gradleHome);
          return getGradleVersion(gradleHomePath);
        }
      }
    }
    return null;
  }

  /**
   * Attempts to figure out the Gradle version of the given distribution.
   *
   * @param gradleHomePath the path of the directory containing the Gradle distribution.
   * @return the Gradle version of the given distribution, or {@code null} if it was not possible to obtain the version.
   */
  @Nullable
  public static GradleVersion getGradleVersion(@NotNull File gradleHomePath) {
    File libDirPath = new File(gradleHomePath, "lib");

    for (File child : notNullize(libDirPath.listFiles())) {
      GradleVersion version = getGradleVersionFromJar(child);
      if (version != null) {
        return version;
      }
    }

    return null;
  }

  @VisibleForTesting
  @Nullable
  static GradleVersion getGradleVersionFromJar(@NotNull File libraryJarFile) {
    String fileName = libraryJarFile.getName();
    Matcher matcher = GRADLE_JAR_NAME_PATTERN.matcher(fileName);
    if (matcher.matches()) {
      // Obtain the version of Gradle from a library name (e.g. "gradle-core-2.0.jar")
      String version = matcher.group(2);
      return GradleVersion.tryParse(removeTimestampFromGradleVersion(version));
    }
    return null;
  }

  @NotNull
  private static String removeTimestampFromGradleVersion(@NotNull String gradleVersion) {
    int dashIndex = gradleVersion.indexOf('-');
    if (dashIndex != -1) {
      // in case this is a nightly (e.g. "2.4-20150409092851+0000").
      return gradleVersion.substring(0, dashIndex);
    }
    return gradleVersion;
  }

  /**
   * Creates the Gradle wrapper, using the latest supported version of Gradle, in the project at the given directory.
   *
   * @param projectDirPath the project's root directory.
   * @return {@code true} if the project already has the wrapper or the wrapper was successfully created; {@code false} if the wrapper was
   * not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   * @see SdkConstants#GRADLE_LATEST_VERSION
   */
  public static boolean createGradleWrapper(@NotNull File projectDirPath) throws IOException {
    return createGradleWrapper(projectDirPath, GRADLE_LATEST_VERSION);
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectDirPath the project's root directory.
   * @param gradleVersion  the version of Gradle to use.
   * @return {@code true} if the project already has the wrapper or the wrapper was successfully created; {@code false} if the wrapper was
   * not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   * @see SdkConstants#GRADLE_LATEST_VERSION
   */
  @VisibleForTesting
  public static boolean createGradleWrapper(@NotNull File projectDirPath, @NotNull String gradleVersion) throws IOException {
    File projectWrapperDirPath = new File(projectDirPath, FD_GRADLE_WRAPPER);
    if (!projectWrapperDirPath.isDirectory()) {
      File wrapperSrcDirPath = new File(TemplateManager.getTemplateRootFolder(), FD_GRADLE_WRAPPER);
      if (!wrapperSrcDirPath.exists()) {
        for (File root : TemplateManager.getExtraTemplateRootFolders()) {
          wrapperSrcDirPath = new File(root, FD_GRADLE_WRAPPER);
          if (wrapperSrcDirPath.exists()) {
            break;
          }
          else {
            wrapperSrcDirPath = null;
          }
        }
      }
      if (wrapperSrcDirPath == null) {
        return false;
      }
      copyDirContent(wrapperSrcDirPath, projectDirPath);
    }
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(projectDirPath);
    updateGradleDistributionUrl(gradleVersion, wrapperPropertiesFile);
    return true;
  }


  /**
   * Determines version of the Android gradle plugin (and model) used by the project. The result can be absent if there are no android
   * modules in the project or if the last sync has failed.
   *
   * @see #getAndroidGradleModelVersionFromBuildFile(Project)
   */
  @Nullable
  public static GradleVersion getAndroidGradleModelVersionInUse(@NotNull Project project) {
    Set<String> foundInLibraries = Sets.newHashSet();
    Set<String> foundInApps = Sets.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidProject androidProject = getAndroidProject(module);
      if (androidProject != null) {
        String modelVersion = androidProject.getModelVersion();
        if (androidProject.isLibrary()) {
          foundInLibraries.add(modelVersion);
        }
        else {
          foundInApps.add(modelVersion);
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

  /**
   * Tries to find the version of the Android Gradle plug-in declared in build.gradle files. If the project contains complicated build
   * logic, this may be incorrect.
   * <p/>
   * <p>
   * The version is returned as it is specified in build files if it does not use "+" notation.
   * </p>
   * <p>
   * If the version is using "+" notation for the "micro" portion, this method replaces the "+" with a zero. For example: "0.13.+" will be
   * returned as "0.13.0". In practice, the micro portion of the version is not used.
   * </p>
   * <p>
   * If the version in build files is "+" or uses "+" for the major or minor portions, this method will find the latest version in the local
   * Gradle cache.
   * </p>
   *
   * @param project the given project.
   * @return the version of the Android Gradle plug-in being used in the given project. (or an approximation.)
   * @see #getAndroidGradleModelVersionInUse(Project)
   */
  @Nullable
  public static GradleVersion getAndroidGradleModelVersionFromBuildFile(@NotNull final Project project) {
    final Ref<GradleVersion> modelVersionRef = new Ref<GradleVersion>();

    processBuildModelsRecursively(project, new Processor<GradleBuildModel>() {
      @Override
      public boolean process(GradleBuildModel buildModel) {
        DependenciesModel dependencies = buildModel.buildscript().dependencies();
        for (ArtifactDependencyModel dependency : dependencies.artifacts(CLASSPATH)) {
          if (isAndroidPlugin(dependency)) {
            String versionValue = dependency.version().value();
            if (versionValue != null) {
              GradleVersion version = GradleVersion.tryParse(versionValue);
              if (version != null) {
                modelVersionRef.set(version);
                return false; // we found the model version. Stop.
              }
            }
            break;
          }
        }
        return true;
      }
    });

    GradleVersion gradleVersion = modelVersionRef.get();

    if (gradleVersion != null) {
      VersionSegment majorSegment = gradleVersion.getMajorSegment();
      VersionSegment minorSegment = gradleVersion.getMinorSegment();
      // For the Android plug-in we don't care about the micro version. Major and minor only matter.
      if (majorSegment.acceptsGreaterValue() || (minorSegment != null && minorSegment.acceptsGreaterValue())) {
        GradleCoordinate foundInCache =
          findLatestVersionInGradleCache(ANDROID_PLUGIN_GROUP_ID, ANDROID_PLUGIN_GROUP_ID, null, project);
        if (foundInCache != null) {
          String revision = foundInCache.getRevision();
          return GradleVersion.tryParse(revision);
        }
      }
      VersionSegment microSegment = gradleVersion.getMicroSegment();
      if (microSegment != null && microSegment.acceptsGreaterValue()) {
        int major = gradleVersion.getMajor();
        int minor = gradleVersion.getMinor();
        return new GradleVersion(major, minor, 0);
      }
    }

    return gradleVersion;
  }

  @Nullable
  public static GradleCoordinate findLatestVersionInGradleCache(@NotNull GradleCoordinate original,
                                                                @Nullable String filter,
                                                                @Nullable Project project) {

    String groupId = original.getGroupId();
    String artifactId = original.getArtifactId();
    if (isNotEmpty(groupId) && isNotEmpty(artifactId)) {
      return findLatestVersionInGradleCache(groupId, artifactId, filter, project);
    }
    return null;
  }

  @Nullable
  public static GradleCoordinate findLatestVersionInGradleCache(@NotNull String groupId,
                                                                @NotNull String artifactId,
                                                                @Nullable String filter,
                                                                @Nullable Project project) {

    for (File gradleServicePath : getGradleServicePaths(project)) {
      GradleCoordinate version = findLatestVersionInGradleCache(gradleServicePath, groupId, artifactId, filter);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  @Nullable
  private static GradleCoordinate findLatestVersionInGradleCache(@NotNull File gradleServicePath,
                                                                 @NotNull String groupId,
                                                                 @NotNull String artifactId,
                                                                 @Nullable String filter) {
    File gradleCache = new File(gradleServicePath, "caches");
    if (gradleCache.exists()) {
      List<GradleCoordinate> coordinates = Lists.newArrayList();

      for (File moduleDir : notNullize(gradleCache.listFiles())) {
        if (!moduleDir.getName().startsWith("modules-") || !moduleDir.isDirectory()) {
          continue;
        }
        for (File metadataDir : notNullize(moduleDir.listFiles())) {
          if (!metadataDir.getName().startsWith("metadata-") || !metadataDir.isDirectory()) {
            continue;
          }
          File versionDir = new File(metadataDir, join("descriptors", groupId, artifactId));
          if (!versionDir.isDirectory()) {
            continue;
          }
          for (File version : notNullize(versionDir.listFiles())) {
            String name = version.getName();
            if ((filter == null || name.startsWith(filter)) && !name.isEmpty() && Character.isDigit(name.charAt(0))) {
              GradleCoordinate found = parseCoordinateString(groupId + ":" + artifactId + ":" + name);
              if (found != null) {
                coordinates.add(found);
              }
            }
          }
        }
      }
      if (!coordinates.isEmpty()) {
        sort(coordinates, GradleCoordinate.COMPARE_PLUS_LOWER);
        return coordinates.get(coordinates.size() - 1);
      }
    }
    return null;
  }

  public static void addLocalMavenRepoInitScriptCommandLineOption(@NotNull List<String> args) {
    if (isAndroidStudio() || ApplicationManager.getApplication().isUnitTestMode()) {
      File repoPath = findAndroidStudioLocalMavenRepoPath();
      if (repoPath != null && repoPath.isDirectory()) {
        addLocalMavenRepoInitScriptCommandLineOption(args, repoPath);
      }
    }
  }

  public static void addProfilerClassPathInitScriptCommandLineOption(@NotNull List<String> args) {
    File file = findAdditionalGradlePluginsLocation();
    String path = escapeGroovyStringLiteral(new File(file, "profiler-plugin.jar").getPath());
    String contents = "allprojects {\n" +
                      "  buildscript {\n" +
                      "    dependencies {\n" +
                      "      classpath files('" + path + "')\n" +
                      "    }\n" +
                      "  }\n" +
                      "}\n";

    addInitScriptCommandLineOption("asPerfClassPath", contents, args);
  }

  private static void addLocalMavenRepoInitScriptCommandLineOption(@NotNull List<String> args, @NotNull File repoPath) {
    String path = escapeGroovyStringLiteral(repoPath.getPath());
    String contents = "allprojects {\n" +
                      "  buildscript {\n" +
                      "    repositories {\n" +
                      "      maven { url '" + path + "'}\n" +
                      "    }\n" +
                      "  }\n" +
                      "  repositories {\n" +
                      "    maven { url '" + path + "'}\n" +
                      "  }\n" +
                      "}\n";
    addInitScriptCommandLineOption("asLocalRepo", contents, args);
  }

  @VisibleForTesting
  @Nullable
  static File addInitScriptCommandLineOption(@NotNull String name, @NotNull String contents, @NotNull List<String> args) {
    try {
      File file = createTempFile(name, DOT_GRADLE);
      file.deleteOnExit();
      writeToFile(file, contents);
      addAll(args, GradleConstants.INIT_SCRIPT_CMD_OPTION, file.getAbsolutePath());

      return file;
    }
    catch (IOException e) {
      LOG.warn("Failed to set up 'local repo' Gradle init script", e);
    }
    return null;
  }

  public static void attemptToUseEmbeddedGradle(@NotNull Project project) {
    if (isAndroidStudio()) {
      File wrapperPropertiesFile = findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile != null) {
        String gradleVersion = null;
        try {
          Properties properties = getProperties(wrapperPropertiesFile);
          String url = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
          gradleVersion = getGradleWrapperVersionOnlyIfComingForGradleDotOrg(url);
        }
        catch (IOException e) {
          LOG.warn("Failed to read file " + wrapperPropertiesFile.getPath());
        }
        if (gradleVersion != null &&
            isCompatibleWithEmbeddedGradleVersion(gradleVersion) &&
            !isWrapperInGradleCache(project, gradleVersion)) {
          File embeddedGradlePath = findEmbeddedGradleDistributionPath();
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

  private static boolean isWrapperInGradleCache(@NotNull Project project, @NotNull String gradleVersion) {
    String distFolderDirName = "gradle-" + gradleVersion;
    String wrapperDirNamePrefix = distFolderDirName + "-";

    // Try both distributions "all" and "bin".
    String[] wrapperDirNames = {wrapperDirNamePrefix + "all", wrapperDirNamePrefix + "bin"};

    for (File gradleServicePath : getGradleServicePaths(project)) {
      for (String wrapperDirName : wrapperDirNames) {
        File wrapperDirPath = new File(gradleServicePath, join("wrapper", "dists", wrapperDirName));
        if (wrapperDirPath.isDirectory()) {
          // There is a folder that contains the actual distribution
          // Example: // ~/.gradle/wrapper/dists/gradle-2.1-all/27drb4udbjf4k88eh2ffdc0n55/gradle-2.1
          for (File mayBeDistParent : notNullize(wrapperDirPath.listFiles())) {
            if (mayBeDistParent.isDirectory()) {
              for (File mayBeDistFolder : notNullize(mayBeDistParent.listFiles())) {
                if (mayBeDistFolder.isDirectory() && distFolderDirName.equals(mayBeDistFolder.getName())) {
                  return true;
                }
              }
            }
          }
        }
      }
    }

    return false;
  }

  @NotNull
  private static Collection<File> getGradleServicePaths(@Nullable Project project) {
    Set<File> paths = Sets.newLinkedHashSet();
    if (project != null) {
      // Use the one set in the IDE
      GradleSettings settings = GradleSettings.getInstance(project);
      String path = settings.getServiceDirectoryPath();
      if (isNotEmpty(path)) {
        File file = new File(path);
        if (file.isDirectory()) {
          paths.add(file);
        }
      }
    }
    // The default location: ~/.gradle
    File path = new File(getUserHome(), DOT_GRADLE);
    if (path.isDirectory()) {
      paths.add(path);
    }
    return paths;
  }

  /**
   * Returns {@code true} if the main artifact of the given Android model depends on the given artifact, which consists of a group id and an
   * artifact id, such as {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOn(@NonNull AndroidGradleModel androidModel, @NonNull String artifact) {
    Dependencies dependencies = androidModel.getSelectedMainCompileDependencies();
    return dependsOn(dependencies, artifact);
  }

  /**
   * Returns {@code true} if the androidTest artifact of the given Android model depends on the given artifact, which consists of a group id
   * and an artifact id, such as {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOnAndroidTest(@NonNull AndroidGradleModel androidModel, @NonNull String artifact) {
    Dependencies dependencies = androidModel.getSelectedAndroidTestCompileDependencies();
    return dependsOn(dependencies, artifact);
  }

  /**
   * Returns {@code true} if the given dependencies include the given artifact, which consists of a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param dependencies the Gradle dependencies object to check
   * @param artifact     the artifact
   * @return {@code true} if the dependencies include the given artifact (including transitively)
   */
  private static boolean dependsOn(@NonNull Dependencies dependencies, @NonNull String artifact) {
    for (AndroidLibrary library : dependencies.getLibraries()) {
      if (dependsOn(library, artifact, true)) {
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
    MavenCoordinates resolvedCoordinates = library.getResolvedCoordinates();
    if (resolvedCoordinates != null) {
      String s = resolvedCoordinates.getGroupId() + ':' + resolvedCoordinates.getArtifactId();
      if (artifact.equals(s)) {
        return true;
      }
    }

    if (transitively) {
      for (AndroidLibrary dependency : library.getLibraryDependencies()) {
        if (dependsOn(dependency, artifact, true)) {
          return true;
        }
      }
    }

    return false;
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

  @Nullable
  public static DataNode<ProjectData> getCachedProjectData(@NotNull Project project) {
    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    ExternalProjectInfo projectInfo = dataManager.getExternalProjectData(project, GRADLE_SYSTEM_ID, getBaseDirPath(project).getPath());
    return projectInfo != null ? projectInfo.getExternalProjectStructure() : null;
  }

  /**
   * Indicates whether <a href="https://code.google.com/p/android/issues/detail?id=170841">a known layout rendering issue</a> is present in
   * the given model.
   *
   * @param model the given model.
   * @return {@true} if the model has the layout rendering issue; {@code false} otherwise.
   */
  public static boolean hasLayoutRenderingIssue(@NotNull AndroidProject model) {
    String modelVersion = model.getModelVersion();
    return modelVersion.startsWith("1.2.0") || modelVersion.equals("1.2.1") || modelVersion.equals("1.2.2");
  }

  @Nullable
  public static VirtualFile findSourceJarForLibrary(@NotNull File libraryFilePath) {
    return findArtifactFileInRepository(libraryFilePath, "-sources.jar", true);
  }

  @Nullable
  public static VirtualFile findPomForLibrary(@NotNull File libraryFilePath) {
    return findArtifactFileInRepository(libraryFilePath, ".pom", false);
  }

  @Nullable
  private static VirtualFile findArtifactFileInRepository(@NotNull File libraryFilePath,
                                                          @NotNull String fileNameSuffix,
                                                          boolean searchInIdeCache) {
    VirtualFile realJarFile = findFileByIoFile(libraryFilePath, true);

    if (realJarFile == null) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    VirtualFile parent = realJarFile.getParent();
    String name = getNameWithoutExtension(libraryFilePath);
    String sourceFileName = name + fileNameSuffix;
    if (parent != null) {

      // Try finding sources in the same folder as the jar file. This is the layout of Maven repositories.
      VirtualFile sourceJar = parent.findChild(sourceFileName);
      if (sourceJar != null) {
        return sourceJar;
      }

      // Try the parent's parent. This is the layout of the repository cache in .gradle folder.
      parent = parent.getParent();
      if (parent != null) {
        for (VirtualFile child : parent.getChildren()) {
          if (!child.isDirectory()) {
            continue;
          }
          sourceJar = child.findChild(sourceFileName);
          if (sourceJar != null) {
            return sourceJar;
          }
        }
      }
    }

    if (searchInIdeCache) {
      // Try IDEA's own cache.
      File librarySourceDirPath = InternetAttachSourceProvider.getLibrarySourceDir();
      File sourceJar = new File(librarySourceDirPath, sourceFileName);
      return findFileByIoFile(sourceJar, true);
    }
    return null;
  }

  /**
   * Updates the Android Gradle plugin version, and optionally the Gradle version of a given project. This method notifies the user if
   * the version update failed.
   *
   * @param project                 the given project.
   * @param pluginVersion           the Android Gradle plugin version to update to.
   * @param gradleVersion           the Gradle version to update to.
   * @param invalidateSyncOnFailure indicates if the last project sync should be invalidated if the version update fails.
   * @return {@code true} if the plugin version was updated successfully; {@code false} otherwise.
   */
  public static boolean updateGradlePluginVersionAndNotifyFailure(@NotNull Project project,
                                                                  @NotNull String pluginVersion,
                                                                  @Nullable String gradleVersion,
                                                                  boolean invalidateSyncOnFailure) {
    if (updateGradlePluginVersion(project, pluginVersion, gradleVersion)) {
      GradleProjectImporter.getInstance().requestProjectSync(project, false, true /* generate sources */, true /* clean */, null);
      return true;
    }

    if (invalidateSyncOnFailure) {
      invalidateLastSync(project, String.format("Failed to update Android plugin to version '%1$s'", pluginVersion));
    }

    String msg = "Failed to update the version of the Android Gradle plugin.\n\n" +
                 "Please click 'OK' to perform a textual search and then update the build files manually.";
    Messages.showErrorDialog(project, msg, UNHANDLED_SYNC_ISSUE_TYPE);
    searchInBuildFiles(GRADLE_PLUGIN_NAME, project);

    return false;
  }

  public static void invalidateLastSync(@NotNull Project project, @NotNull String error) {
    GradleSyncState.getInstance(project).syncFailed(error);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        facet.setAndroidModel(null);
      }
    }
  }

  /**
   * Updates the Android Gradle plugin version, and optionally the Gradle version of a given project.
   *
   * @param project       the given project.
   * @param pluginVersion the Android Gradle plugin version to update to.
   * @param gradleVersion the Gradle version to update to.
   * @return {@code true} if the update of the plugin version succeeded.
   */
  public static boolean updateGradlePluginVersion(@NotNull final Project project,
                                                  @NotNull String pluginVersion,
                                                  @Nullable String gradleVersion) {
    final List<GradleBuildModel> modelsToUpdate = Lists.newArrayList();
    final GradleVersion parsedPluginVersion = GradleVersion.parse(pluginVersion);
    final Ref<Boolean> alreadyInCorrectVersion = new Ref<Boolean>(false);

    processBuildModelsRecursively(project, new Processor<GradleBuildModel>() {
      @Override
      public boolean process(GradleBuildModel buildModel) {
        DependenciesModel dependencies = buildModel.buildscript().dependencies();
        for (ArtifactDependencyModel dependency : dependencies.artifacts(CLASSPATH)) {
          if (isAndroidPlugin(dependency)) {
            String versionValue = dependency.version().value();
            if (versionValue != null && parsedPluginVersion.compareTo(versionValue) == 0) {
              alreadyInCorrectVersion.set(true);
            }
            else {
              dependency.setVersion(parsedPluginVersion.toString());
              modelsToUpdate.add(buildModel);
            }
            break;
          }
        }
        return true;
      }
    });

    boolean updateModels = !modelsToUpdate.isEmpty();
    if (updateModels) {
      runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          for (GradleBuildModel buildModel : modelsToUpdate) {
            buildModel.applyChanges();
          }
        }
      });
    }
    else if (alreadyInCorrectVersion.get()) {
      // No version was updated because the correct version is already applied.
      return true;
    }

    if (updateModels && isNotEmpty(gradleVersion)) {
      String basePath = project.getBasePath();
      if (basePath != null) {
        File wrapperPropertiesFilePath = getGradleWrapperPropertiesFilePath(new File(basePath));
        GradleVersion current = getGradleVersionInWrapper(wrapperPropertiesFilePath);
        if (current != null && !isSupportedGradleVersion(current)) {
          try {
            updateGradleDistributionUrl(gradleVersion, wrapperPropertiesFilePath);
          }
          catch (IOException e) {
            LOG.warn("Failed to update Gradle version in wrapper", e);
          }
        }
      }
    }
    return updateModels;
  }

  private static boolean isAndroidPlugin(@NotNull ArtifactDependencyModel dependency) {
    return ANDROID_PLUGIN_GROUP_ID.equals(dependency.group().value()) && ANDROID_PLUGIN_ARTIFACT_ID.equals(dependency.name().value());
  }

  public static void processBuildModelsRecursively(@NotNull final Project project, @NotNull final Processor<GradleBuildModel> processor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
          // Unlikely to happen: this is default project.
          return;
        }

        processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
          @Override
          public boolean process(VirtualFile virtualFile) {
            if (FN_BUILD_GRADLE.equals(virtualFile.getName())) {
              GradleBuildModel buildModel = parseBuildFile(virtualFile, project);
              return processor.process(buildModel);
            }
            return true;
          }
        });
      }
    });
  }

  @Nullable
  private static GradleVersion getGradleVersionInWrapper(@NotNull File wrapperPropertiesFilePath) {
    String version = null;
    try {
      version = getGradleWrapperVersion(wrapperPropertiesFilePath);
    }
    catch (IOException e) {
      LOG.warn("Failed to obtain Gradle version in wrapper", e);
    }
    if (isNotEmpty(version)) {
      return GradleVersion.tryParse(version);
    }
    return null;
  }

  public static void setBuildToolsVersion(@NotNull Project project, @NotNull String version) {
    final List<GradleBuildModel> modelsToUpdate = Lists.newArrayList();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        GradleBuildModel buildModel = GradleBuildModel.get(module);
        if (buildModel != null) {
          AndroidModel android = buildModel.android();
          if (!version.equals(android.buildToolsVersion())) {
            android.setBuildToolsVersion(version);
            modelsToUpdate.add(buildModel);
          }
        }
      }
    }

    if (!modelsToUpdate.isEmpty()) {
      runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          for (GradleBuildModel buildModel : modelsToUpdate) {
            buildModel.applyChanges();
          }
        }
      });
    }
  }
}
