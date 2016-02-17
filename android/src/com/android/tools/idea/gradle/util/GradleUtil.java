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
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.PreciseRevision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.gradle.project.ChooseGradleHomeDialog;
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.SmartHashSet;
import icons.AndroidIcons;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

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
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE_TRANSLATE;
import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPath;
import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.findEmbeddedGradleDistributionPath;
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
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExecutionSettings;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getSettings;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.*;
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

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);
  private static final Pattern GRADLE_JAR_NAME_PATTERN = Pattern.compile("gradle-(.*)-(.*)\\.jar");
  private static final String SOURCES_JAR_NAME_SUFFIX = "-sources.jar";

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

  public static boolean isSupportedGradleVersion(@NotNull FullRevision gradleVersion) {
    FullRevision supported = FullRevision.parseRevision(GRADLE_MINIMUM_VERSION);
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
  public static List<AndroidLibrary> getDirectLibraryDependencies(@NotNull Variant variant,
                                                                  @NotNull AndroidGradleModel androidModel) {
    List<AndroidLibrary> libraries = Lists.newArrayList();
    libraries.addAll(variant.getMainArtifact().getDependencies().getLibraries());
    BaseArtifact testArtifact = androidModel.findSelectedTestArtifact(variant);
    if (testArtifact != null) {
      libraries.addAll(testArtifact.getDependencies().getLibraries());
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
    } else {
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
  public static FullRevision getGradleVersion(@NotNull Project project) {
    String gradleVersion = getGradleVersionUsed(project);
    if (isNotEmpty(gradleVersion)) {
      // The version of Gradle used is retrieved one of the Gradle models. If that fails, we try to deduce it from the project's Gradle
      // settings.
      FullRevision revision = parseRevision(removeTimestampFromGradleVersion(gradleVersion));
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
              return parseRevision(removeTimestampFromGradleVersion(wrapperVersion));
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
  public static FullRevision getGradleVersion(@NotNull File gradleHomePath) {
    File libDirPath = new File(gradleHomePath, "lib");

    for (File child : notNullize(libDirPath.listFiles())) {
      FullRevision version = getGradleVersionFromJar(child);
      if (version != null) {
        return version;
      }
    }

    return null;
  }

  @VisibleForTesting
  @Nullable
  static FullRevision getGradleVersionFromJar(@NotNull File libraryJarFile) {
    String fileName = libraryJarFile.getName();
    Matcher matcher = GRADLE_JAR_NAME_PATTERN.matcher(fileName);
    if (matcher.matches()) {
      // Obtain the version of Gradle from a library name (e.g. "gradle-core-2.0.jar")
      String version = matcher.group(2);
      try {
        return PreciseRevision.parseRevision(removeTimestampFromGradleVersion(version));
      }
      catch (NumberFormatException e) {
        LOG.warn(String.format("Unable to parse version '%1$s' (obtained from file '%2$s')", version, fileName));
      }
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
  public static FullRevision getAndroidGradleModelVersionInUse(@NotNull Project project) {
    Set<String> pluginVersionsUsedInProject = new SmartHashSet<String>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidProject androidProject = getAndroidProject(module);
      if (androidProject == null) {
        continue;
      }
      pluginVersionsUsedInProject.add(androidProject.getModelVersion());
    }

    // This should pretty much always be the case, but let's be safe here.
    if (pluginVersionsUsedInProject.size() == 1) {
      return parseRevision(getOnlyElement(pluginVersionsUsedInProject));
    }

    return null;
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
  public static FullRevision getAndroidGradleModelVersionFromBuildFile(@NotNull final Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // This is default project.
      return null;
    }
    final Ref<FullRevision> modelVersionRef = new Ref<FullRevision>();
    processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          File fileToCheck = virtualToIoFile(virtualFile);
          try {
            String contents = loadFile(fileToCheck);
            FullRevision version = getAndroidGradleModelVersionFromBuildFile(contents, project);
            if (version != null) {
              modelVersionRef.set(version);
              return false; // we found the model version. Stop.
            }
          }
          catch (IOException e) {
            LOG.warn("Failed to read contents of " + fileToCheck.getPath());
          }
        }
        return true;
      }
    });

    return modelVersionRef.get();
  }

  @VisibleForTesting
  @Nullable
  static FullRevision getAndroidGradleModelVersionFromBuildFile(@NotNull String fileContents, @Nullable Project project) {
    GradleCoordinate found = getPluginDefinition(fileContents, GRADLE_PLUGIN_NAME);
    if (found != null) {
      String revision = getAndroidGradleModelVersion(found, project);
      if (isNotEmpty(revision)) {
        return parseRevision(revision);
      }
    }
    return null;
  }

  @Nullable
  private static FullRevision parseRevision(@NotNull String revision) {
    try {
      return PreciseRevision.parseRevision(revision);
    }
    catch (NumberFormatException e) {
      LOG.info("Failed to parse revision '" + revision + "'", e);
    }
    return null;
  }

  /**
   * Returns target plugin's definition.
   *
   * @param fileContents target Gradle build file contents
   * @param pluginName   target plugin's name in a form {@code group-id:artifact-id:}
   * @return target plugin's definition if found; {@code null} otherwise
   */
  @Nullable
  public static GradleCoordinate getPluginDefinition(@NotNull String fileContents, @NotNull String pluginName) {
    String definition = findStringLiteral(pluginName, fileContents, new Function<Pair<String, GroovyLexer>, String>() {
      @Override
      public String fun(Pair<String, GroovyLexer> pair) {
        return pair.getFirst();
      }
    });
    return isNotEmpty(definition) ? parseCoordinateString(definition) : null;
  }

  /**
   * Updates the version of a Gradle dependency used in a build.gradle file.
   *
   * @param project           the project containing the build.gradle file.
   * @param buildFileDocument document of the build.gradle file, which declares the version of the dependency.
   * @param dependencyName    the name of the dependency to look for.
   * @param versionTask       returns the version of the dependency to update the file to.
   * @return {@code true} if the build.gradle file was updated; {@code false} otherwise.
   */
  public static boolean updateGradleDependencyVersion(@NotNull Project project,
                                                      @NotNull final Document buildFileDocument,
                                                      @NotNull final String dependencyName,
                                                      @NotNull final Computable<String> versionTask) {
    String contents = buildFileDocument.getText();
    final TextRange range = findStringLiteral(dependencyName, contents, new Function<Pair<String, GroovyLexer>, TextRange>() {
      @Override
      public TextRange fun(Pair<String, GroovyLexer> pair) {
        GroovyLexer lexer = pair.getSecond();
        return TextRange.create(lexer.getTokenStart() + 1 + dependencyName.length(), lexer.getTokenEnd() - 1);
      }
    });
    if (range != null) {
      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          buildFileDocument.replaceString(range.getStartOffset(), range.getEndOffset(), versionTask.compute());
        }
      });
      return true;
    }
    return false;
  }

  @Nullable
  public static TextRange findDependency(@NotNull final String dependency, @NotNull String contents) {
    return findStringLiteral(dependency, contents, new Function<Pair<String, GroovyLexer>, TextRange>() {
      @Override
      public TextRange fun(Pair<String, GroovyLexer> pair) {
        GroovyLexer lexer = pair.getSecond();
        return TextRange.create(lexer.getTokenStart() + 1, lexer.getTokenEnd() - 1);
      }
    });
  }

  @Nullable
  private static <T> T findStringLiteral(@NotNull String textToSearchPrefix,
                                         @NotNull String fileContents,
                                         @NotNull Function<Pair<String, GroovyLexer>, T> consumer) {
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(fileContents);
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (type == GroovyTokenTypes.mSTRING_LITERAL) {
        String text = unquoteString(lexer.getTokenText());
        if (text.startsWith(textToSearchPrefix)) {
          return consumer.fun(Pair.create(text, lexer));
        }
      }
      lexer.advance();
    }
    return null;
  }

  /**
   * Delegates to the {@link #forPluginDefinition(String, String, Function)} and just returns target plugin's definition string (unquoted).
   *
   * @param fileContents target gradle config text
   * @param pluginName   target plugin's name in a form <code>'group-id:artifact-id:'</code>
   * @return target plugin's definition string if found (unquoted); <code>null</code> otherwise
   * @see #forPluginDefinition(String, String, Function)
   */
  @Nullable
  public static String getPluginDefinitionString(@NotNull String fileContents, @NotNull String pluginName) {
    return forPluginDefinition(fileContents, pluginName, new Function<Pair<String, GroovyLexer>, String>() {
      @Override
      public String fun(Pair<String, GroovyLexer> pair) {
        return pair.getFirst();
      }
    });
  }

  /**
   * Checks given file contents (assuming that it's build.gradle config) and finds target plugin's definition (given the plugin
   * name in a form <code>'group-id:artifact-id:'</code>. Supplies given callback with the plugin definition string (unquoted) and
   * a {@link GroovyLexer} which state points to the plugin definition string (quoted).
   * <p/>
   * Example:
   * <pre>
   *     buildscript {
   *       repositories {
   *         mavenCentral()
   *       }
   *       dependencies {
   *         classpath 'com.google.appengine:gradle-appengine-plugin:1.9.4'
   *       }
   *     }
   * </pre>
   * Suppose that this method is called for the given build script content and
   * <code>'com.google.appengine:gradle-appengine-plugin:'</code> as a plugin name argument. Given callback is supplied by a
   * string <code>'com.google.appengine:gradle-appengine-plugin:1.9.4'</code> (without quotes) and a {@link GroovyLexer} which
   * {@link GroovyLexer#getTokenStart() points} to the string <code>'com.google.appengine:gradle-appengine-plugin:1.9.4'</code>
   * (with quotes), i.e. we can get exact text range for the target string in case we need to do something like replacing plugin's
   * version.
   *
   * @param fileContents target gradle config text
   * @param pluginName   target plugin's name in a form <code>'group-id:artifact-id:'</code>
   * @param consumer     a callback to be notified for the target plugin's definition string
   * @param <T>          given callback's return type
   * @return given callback's call result if target plugin definition is found; <code>null</code> otherwise
   */
  @Nullable
  public static <T> T forPluginDefinition(@NotNull String fileContents,
                                          @NotNull String pluginName,
                                          @NotNull Function<Pair<String, GroovyLexer>, T> consumer) {
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(fileContents);
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (type == GroovyTokenTypes.mSTRING_LITERAL) {
        String text = unquoteString(lexer.getTokenText());
        if (text.startsWith(pluginName)) {
          return consumer.fun(Pair.create(text, lexer));
        }
      }
      lexer.advance();
    }
    return null;
  }

  @Nullable
  private static String getAndroidGradleModelVersion(@NotNull GradleCoordinate coordinate, @Nullable Project project) {
    String revision = coordinate.getFullRevision();
    if (isNotEmpty(revision)) {
      if (!coordinate.acceptsGreaterRevisions()) {
        return revision;
      }

      // For the Android plug-in we don't care about the micro version. Major and minor only matter.
      int major = coordinate.getMajorVersion();
      int minor = coordinate.getMinorVersion();
      if (coordinate.getMicroVersion() == -1 && major >= 0 && minor > 0) {
        return major + "." + minor + "." + 0;
      }
    }
    GradleCoordinate latest = findLatestVersionInGradleCache(coordinate, null, project);
    return latest != null ? latest.getFullRevision() : null;
  }

  @Nullable
  public static GradleCoordinate findLatestVersionInGradleCache(@NotNull GradleCoordinate original,
                                                                @Nullable String filter,
                                                                @Nullable Project project) {

    for (File gradleServicePath : getGradleServicePaths(project)) {
      GradleCoordinate version = findLatestVersionInGradleCache(gradleServicePath, original, filter);
      if (version != null) {
        return version;
      }
    }

    return null;
  }

  @Nullable
  private static GradleCoordinate findLatestVersionInGradleCache(@NotNull File gradleServicePath,
                                                                 @NotNull GradleCoordinate original,
                                                                 @Nullable String filter) {
    File gradleCache = new File(gradleServicePath, "caches");
    if (gradleCache.exists()) {
      List<GradleCoordinate> coordinates = Lists.newArrayList();

      String groupId = original.getGroupId();
      String artifactId = original.getArtifactId();
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

  @VisibleForTesting
  @Nullable
  static File addLocalMavenRepoInitScriptCommandLineOption(@NotNull List<String> args, @NotNull File repoPath) {
    try {
      File file = createTempFile("asLocalRepo", DOT_GRADLE);
      file.deleteOnExit();

      String contents = "allprojects {\n" +
                        "  buildscript {\n" +
                        "    repositories {\n" +
                        "      maven { url '" + GradleImport.escapeGroovyStringLiteral(repoPath.getPath()) + "'}\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
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
   * Returns {@code true} if the given Android model depends on the given artifact, which consists of a group id and an artifact id, such as
   * {@link SdkConstants#APPCOMPAT_LIB_ARTIFACT}.
   *
   * @param androidModel the Android model to check
   * @param artifact     the artifact
   * @return {@code true} if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOn(@NonNull AndroidGradleModel androidModel, @NonNull String artifact) {
    Dependencies dependencies = androidModel.getMainArtifact().getDependencies();
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
    VirtualFile realJarFile = findFileByIoFile(libraryFilePath, true);

    if (realJarFile == null) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    VirtualFile parent = realJarFile.getParent();
    String name = getNameWithoutExtension(libraryFilePath);
    String sourceFileName = name + SOURCES_JAR_NAME_SUFFIX;
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

    // Try IDEA's own cache.
    File librarySourceDirPath = InternetAttachSourceProvider.getLibrarySourceDir();
    File sourceJar = new File(librarySourceDirPath, sourceFileName);
    return findFileByIoFile(sourceJar, true);
  }
}
