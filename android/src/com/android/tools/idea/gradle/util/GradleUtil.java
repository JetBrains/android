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
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import icons.AndroidIcons;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.startup.AndroidStudioSpecificInitializer.GRADLE_DAEMON_TIMEOUT_MS;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";

  /** The name of the gradle wrapper executable associated with the current OS. */
  @NonNls public static final String GRADLE_WRAPPER_EXECUTABLE_NAME =
    SystemInfo.isWindows ? SdkConstants.FN_GRADLE_WRAPPER_WIN : SdkConstants.FN_GRADLE_WRAPPER_UNIX;

  @NonNls public static final String GRADLEW_PROPERTIES_PATH =
    FileUtil.join(SdkConstants.FD_GRADLE_WRAPPER, SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES);

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);
  private static final Pattern GRADLE_JAR_NAME_PATTERN = Pattern.compile("gradle-(.*)-(.*)\\.jar");
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  /**
   * Finds characters that shouldn't be used in the Gradle path.
   *
   * I was unable to find any specification for Gradle paths. In my
   * experiments, Gradle only failed with slashes. This list may grow if
   * we find any other unsupported characters.
   */
  private static final CharMatcher ILLEGAL_GRADLE_PATH_CHARS_MATCHER = CharMatcher.anyOf("\\/");
  private static final Pattern GRADLE_DISTRIBUTION_URL_PATTERN = Pattern.compile(".*-([^-]+)-([^.]+).zip");

  private GradleUtil() {
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
    AndroidArtifactOutput output = ContainerUtil.getFirstItem(outputs);
    assert output != null;
    return output;
  }

  @NotNull
  public static Icon getModuleIcon(@NotNull Module module) {
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject != null) {
      return androidProject.isLibrary() ? AndroidIcons.LibraryModule : AndroidIcons.AppModule;
    }
    return Projects.isGradleProject(module.getProject()) ? AllIcons.Nodes.PpJdk : AllIcons.Nodes.Module;
  }

  @Nullable
  public static AndroidProject getAndroidProject(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      if (androidProject != null) {
        return androidProject.getDelegate();
      }
    }
    return null;
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
   *
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
   * Returns the library dependencies in the given variant. This method checks dependencies in the "main" and "instrumentation tests"
   * artifacts. The dependency lookup is not transitive (only direct dependencies are returned.)
   *
   * @param variant the given variant.
   * @return the library dependencies in the given variant.
   */
  @NotNull
  public static List<AndroidLibrary> getDirectLibraryDependencies(@NotNull Variant variant) {
    List<AndroidLibrary> libraries = Lists.newArrayList();
    libraries.addAll(variant.getMainArtifact().getDependencies().getLibraries());
    AndroidArtifact testArtifact = IdeaAndroidProject.findInstrumentationTestArtifact(variant);
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
    return Lists.newArrayList(Splitter.on(SdkConstants.GRADLE_PATH_SEPARATOR).omitEmptyStrings().split(gradlePath));
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null && gradleFacet.getGradleProject() != null) {
      return gradleFacet.getGradleProject().getBuildFile();
    }
    // At the time we're called, module.getModuleFile() may be null, but getModuleFilePath returns the path where it will be created.
    File moduleFilePath = new File(module.getModuleFilePath());
    return getGradleBuildFile(moduleFilePath.getParentFile());
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File rootDir) {
    File gradleBuildFilePath = getGradleBuildFilePath(rootDir);
    return VfsUtil.findFileByIoFile(gradleBuildFilePath, true);
  }

  @NotNull
  public static File getGradleBuildFilePath(@NotNull File rootDir) {
    return new File(rootDir, SdkConstants.FN_BUILD_GRADLE);
  }

  @Nullable
  public static VirtualFile getGradleSettingsFile(@NotNull File rootDir) {
    File gradleSettingsFilePath = getGradleSettingsFilePath(rootDir);
    return VfsUtil.findFileByIoFile(gradleSettingsFilePath, true);
  }

  @NotNull
  public static File getGradleSettingsFilePath(@NotNull File rootDir) {
    return new File(rootDir, SdkConstants.FN_SETTINGS_GRADLE);
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
    Properties properties = PropertiesUtil.getProperties(propertiesFile);
    String gradleDistributionUrl = getGradleDistributionUrl(gradleVersion, false);
    String property = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (property != null && (property.equals(gradleDistributionUrl) || property.equals(getGradleDistributionUrl(gradleVersion, true)))) {
      return false;
    }
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, gradleDistributionUrl);
    PropertiesUtil.savePropertiesToFile(properties, propertiesFile, null);
    return true;
  }

  @Nullable
  public static String getGradleWrapperVersion(@NotNull File propertiesFile) throws IOException {
    Properties properties = PropertiesUtil.getProperties(propertiesFile);
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
      String format = "Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'";
      String msg = String.format(format, project.getName(), FileUtil.toSystemDependentName(project.getBasePath()));
      LOG.info(msg);
      return null;
    }
    try {
      GradleExecutionSettings settings =
        ExternalSystemApiUtil.getExecutionSettings(project, projectSettings.getExternalProjectPath(), SYSTEM_ID);
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
    File baseDir = new File(project.getBasePath());
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(baseDir);
    return wrapperPropertiesFile.isFile() ? wrapperPropertiesFile : null;
  }

  @Nullable
  public static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    GradleSettings settings = (GradleSettings)ExternalSystemApiUtil.getSettings(project, SYSTEM_ID);

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

  @NotNull
  public static List<String> getGradleInvocationJvmArgs(@NotNull File projectDir, @Nullable BuildMode buildMode) {
    if (ExternalSystemApiUtil.isInProcessMode(SYSTEM_ID)) {
      List<String> args = Lists.newArrayList();
      if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(projectDir)) {
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData != null) {
          String arg = AndroidGradleSettings.createAndroidHomeJvmArg(sdkData.getLocation().getPath());
          args.add(arg);
        }
      }
      List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
      for (KeyValue<String, String> proxyProperty : proxyProperties) {
        String arg = AndroidGradleSettings.createJvmArg(proxyProperty.getKey(), proxyProperty.getValue());
        args.add(arg);
      }
      String arg = getGradleInvocationJvmArg(buildMode);
      if (arg != null) {
        args.add(arg);
      }
      return args;
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  @Nullable
  static String getGradleInvocationJvmArg(@Nullable BuildMode buildMode) {
    if (BuildMode.ASSEMBLE_TRANSLATE == buildMode) {
      return AndroidGradleSettings.createJvmArg(GradleBuilds.ENABLE_TRANSLATION_JVM_ARG, true);
    }
    return null;
  }

  public static void stopAllGradleDaemons() {
    DefaultGradleConnector.close();
  }

  /**
   * Convert a Gradle project name into a system dependent path relative to root project. Please note this is the default mapping from a
   * Gradle "logical" path to a physical path. Users can override this mapping in settings.gradle and this mapping may not always be
   * accurate.
   * <p/>
   * E.g. ":module" becomes "module" and ":directory:module" is converted to "directory/module"
   */
  @NotNull
  public static String getDefaultPhysicalPathFromGradlePath(@NotNull String name) {
    List<String> segments = getPathSegments(name);
    return FileUtil.join(ArrayUtil.toStringArray(segments));
  }

  /**
   * Obtain default path for the Gradle subproject with the given name in the project.
   */
  @NotNull
  public static File getDefaultSubprojectLocation(@NotNull VirtualFile project, @NotNull String gradlePath) {
    assert gradlePath.length() > 0;
    String relativePath = getDefaultPhysicalPathFromGradlePath(gradlePath);
    return new File(VfsUtilCore.virtualToIoFile(project), relativePath);
  }

  /**
   * Prefixes string with colon if there isn't one already there.
   */
  @Nullable
  @Contract("null -> null;!null -> !null")
  public static String makeAbsolute(String string) {
    if (string == null) {
      return null;
    }
    else if (string.trim().length() == 0) {
      return ":";
    }
    else if (!string.startsWith(":")) {
      return ":" + string.trim();
    }
    else {
      return string.trim();
    }
  }

  /**
   * Tests if the Gradle path is valid and return index of the offending
   * character or -1 if none.
   * <p/>
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
      File location = getDefaultSubprojectLocation(project.getBaseDir(), gradlePath);
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

  /**
   * Attempts to figure out the Gradle version of the given distribution.
   *
   * @param gradleHomePath the path of the directory containing the Gradle distribution.
   * @return the Gradle version of the given distribution, or {@code null} if it was not possible to obtain the version.
   */
  @Nullable
  public static FullRevision getGradleVersion(@NotNull File gradleHomePath) {
    File libDirPath = new File(gradleHomePath, "lib");

    for (File child : FileUtil.notNullize(libDirPath.listFiles())) {
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
        return FullRevision.parseRevision(version);
      }
      catch (NumberFormatException e) {
        LOG.warn(String.format("Unable to parse version '%1$s' (obtained from file '%2$s')", version, fileName));
      }
    }
    return null;
  }

  /**
   * Creates the Gradle wrapper, using the latest supported version of Gradle, in the project at the given directory.
   *
   * @param projectDirPath the project's root directory.
   * @return {@code true} if the project already has the wrapper or the wrapper was successfully created; {@code false} if the wrapper was
   * not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   *
   * @see com.android.SdkConstants#GRADLE_LATEST_VERSION
   */
  public static boolean createGradleWrapper(@NotNull File projectDirPath) throws IOException {
    return createGradleWrapper(projectDirPath, SdkConstants.GRADLE_LATEST_VERSION);
  }

  /**
   * Creates the Gradle wrapper in the project at the given directory.
   *
   * @param projectDirPath the project's root directory.
   * @param gradleVersion the version of Gradle to use.
   * @return {@code true} if the project already has the wrapper or the wrapper was successfully created; {@code false} if the wrapper was
   * not created (e.g. the template files for the wrapper were not found.)
   * @throws IOException any unexpected I/O error.
   *
   * @see com.android.SdkConstants#GRADLE_LATEST_VERSION
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
      FileUtil.copyDirContent(wrapperSrcDirPath, projectDirPath);
    }
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(projectDirPath);
    updateGradleDistributionUrl(gradleVersion, wrapperPropertiesFile);
    return true;
  }

  /**
   * Finds the version of the Android Gradle plug-in being used in the given project.
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
   */
  @Nullable
  public static FullRevision getResolvedAndroidGradleModelVersion(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      // This is default project.
      return null;
    }
    final Ref<FullRevision> modelVersionRef = new Ref<FullRevision>();
    VfsUtil.processFileRecursivelyWithoutIgnored(baseDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (SdkConstants.FN_BUILD_GRADLE.equals(virtualFile.getName())) {
          File fileToCheck = VfsUtilCore.virtualToIoFile(virtualFile);
          try {
            String contents = FileUtil.loadFile(fileToCheck);
            FullRevision version = getResolvedAndroidGradleModelVersion(contents);
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
  static FullRevision getResolvedAndroidGradleModelVersion(@NotNull String fileContents) {
    GradleCoordinate found = null;
    String pluginDefinitionString = getPluginDefinitionString(fileContents, SdkConstants.GRADLE_PLUGIN_NAME);
    if (pluginDefinitionString != null) {
      found = GradleCoordinate.parseCoordinateString(pluginDefinitionString);
    }

    if (found != null) {
      String revision = getAndroidGradleModelVersion(found);
      if (StringUtil.isNotEmpty(revision)) {
        try {
          return FullRevision.parseRevision(revision);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    return null;
  }

  /**
   * Delegates to the {@link #forPluginDefinition(String, String, Function)} and just returns target plugin's definition string (unquoted).
   *
   * @param fileContents  target gradle config text
   * @param pluginName    target plugin's name in a form <code>'group-id:artifact-id:'</code>
   * @return              target plugin's definition string if found (unquoted); <code>null</code> otherwise
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
   * @param fileContents  target gradle config text
   * @param pluginName    target plugin's name in a form <code>'group-id:artifact-id:'</code>
   * @param consumer      a callback to be notified for the target plugin's definition string
   * @param <T>           given callback's return type
   * @return              given callback's call result if target plugin definition is found; <code>null</code> otherwise
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
        String text = StringUtil.unquoteString(lexer.getTokenText());
        if (text.startsWith(pluginName)) {
          return consumer.fun(Pair.create(text, lexer));
        }
      }
      lexer.advance();
    }
    return null;
  }

  @Nullable
  private static String getAndroidGradleModelVersion(@NotNull GradleCoordinate coordinate) {
    String revision = coordinate.getFullRevision();
    if (StringUtil.isNotEmpty(revision)) {
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
    GradleCoordinate latest = findLatestVersionInGradleCache(coordinate, null);
    return latest != null ? latest.getFullRevision() : null;
  }

  @Nullable
  public static GradleCoordinate findLatestVersionInGradleCache(@NotNull GradleCoordinate original, @Nullable String filter) {
    List<GradleCoordinate> coordinates = Lists.newArrayList();
    File gradleCache = new File(SystemProperties.getUserHome(), FileUtil.join(DOT_GRADLE, "caches"));
    if (gradleCache.exists()) {
      String groupId = original.getGroupId();
      String artifactId = original.getArtifactId();
      for (File moduleDir : FileUtil.notNullize(gradleCache.listFiles())) {
        if (!moduleDir.getName().startsWith("modules-") || !moduleDir.isDirectory()) {
          continue;
        }
        for (File metadataDir : FileUtil.notNullize(moduleDir.listFiles())) {
          if (!metadataDir.getName().startsWith("metadata-") || !metadataDir.isDirectory()) {
            continue;
          }
          File versionDir = new File(metadataDir, FileUtil.join("descriptors", groupId, artifactId));
          if (!versionDir.isDirectory()) {
            continue;
          }
          for (File version : FileUtil.notNullize(versionDir.listFiles())) {
            String name = version.getName();
            if ((filter == null || name.startsWith(filter)) && !name.isEmpty() && Character.isDigit(name.charAt(0))) {
              GradleCoordinate found = GradleCoordinate.parseCoordinateString(groupId + ":" + artifactId + ":" + name);
              if (found != null) {
                coordinates.add(found);
              }
            }
          }
        }
      }
      if (!coordinates.isEmpty()) {
        Collections.sort(coordinates, GradleCoordinate.COMPARE_PLUS_LOWER);
        return coordinates.get(coordinates.size() - 1);
      }
    }
    return null;
  }

  public static void addLocalMavenRepoInitScriptCommandLineOption(@NotNull List<String> args) {
    if (AndroidStudioSpecificInitializer.isAndroidStudio() || ApplicationManager.getApplication().isUnitTestMode()) {
      File repoPath = getAndroidStudioLocalMavenRepoPath();
      if (repoPath != null && repoPath.isDirectory()) {
        addLocalMavenRepoInitScriptCommandLineOption(args, repoPath);
      }
    }
  }

  @Nullable
  public static File getAndroidStudioLocalMavenRepoPath() {
    File repoPath = new File(getEmbeddedGradleArtifactsDirPath(), "m2repository");
    LOG.info("Looking for embedded Maven repo at '" + repoPath.getPath() + "'");
    return repoPath.isDirectory() ? repoPath : null;
  }

  @VisibleForTesting
  @Nullable
  static File addLocalMavenRepoInitScriptCommandLineOption(@NotNull List<String> args, @NotNull File repoPath) {
    try {
      File file = FileUtil.createTempFile("asLocalRepo", SdkConstants.DOT_GRADLE);
      file.deleteOnExit();

      String contents ="allprojects {\n" +
                       "  buildscript {\n" +
                       "    repositories {\n" +
                       "      maven { url '" + GradleImport.escapeGroovyStringLiteral(repoPath.getPath()) + "'}\n" +
                       "    }\n" +
                       "  }\n" +
                       "}\n";
      FileUtil.writeToFile(file, contents);
      ContainerUtil.addAll(args, GradleConstants.INIT_SCRIPT_CMD_OPTION, file.getAbsolutePath());

      return file;
    }
    catch (IOException e) {
      LOG.warn("Failed to set up 'local repo' Gradle init script", e);
    }
    return null;
  }

  public static void attemptToUseEmbeddedGradle(@NotNull Project project) {
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      File wrapperPropertiesFile = findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile != null) {
        String gradleVersion = null;
        try {
          gradleVersion = getGradleWrapperVersion(wrapperPropertiesFile);
        }
        catch (IOException e) {
          LOG.warn("Failed to read file " + wrapperPropertiesFile.getPath());
        }
        if (gradleVersion != null && isCompatibleWithEmbeddedGradleVersion(gradleVersion)) {
          File embeddedPath = new File(getEmbeddedGradleArtifactsDirPath(), "gradle-" + SdkConstants.GRADLE_LATEST_VERSION);
          LOG.info("Looking for embedded Gradle distribution at '" + embeddedPath.getPath() + "'");
          if (embeddedPath.isDirectory()) {
            GradleProjectSettings gradleSettings = getGradleProjectSettings(project);
            if (gradleSettings != null) {
              gradleSettings.setDistributionType(DistributionType.LOCAL);
              gradleSettings.setGradleHome(embeddedPath.getPath());
            }
          }
        }
      }
    }
  }

  // Currently, the latest Gradle version is 2.2.1, and we consider 2.2 and 2.2.1 as compatible.
  private static boolean isCompatibleWithEmbeddedGradleVersion(@NotNull String gradleVersion) {
    return gradleVersion.equals(SdkConstants.GRADLE_MINIMUM_VERSION) || gradleVersion.equals(SdkConstants.GRADLE_LATEST_VERSION);
  }

  @NotNull
  private static File getEmbeddedGradleArtifactsDirPath() {
    String homePath = PathManager.getHomePath();
    return new File(homePath, "gradle");
  }

  /**
   * Returns true if the given project depends on the given artifact, which consists of
   * a group id and an artifact id, such as {@link com.android.SdkConstants#APPCOMPAT_LIB_ARTIFACT}
   *
   * @param project the Gradle project to check
   * @param artifact the artifact
   * @return true if the project depends on the given artifact (including transitively)
   */
  public static boolean dependsOn(@NonNull IdeaAndroidProject project, @NonNull String artifact) {
    Dependencies dependencies = project.getSelectedVariant().getMainArtifact().getDependencies();
    return dependsOn(dependencies, artifact);

  }

  /**
   * Returns true if the given dependencies include the given artifact, which consists of
   * a group id and an artifact id, such as {@link com.android.SdkConstants#APPCOMPAT_LIB_ARTIFACT}
   *
   * @param dependencies the Gradle dependencies object to check
   * @param artifact the artifact
   * @return true if the dependencies include the given artifact (including transitively)
   */
  public static boolean dependsOn(@NonNull Dependencies dependencies, @NonNull String artifact) {
    for (AndroidLibrary library : dependencies.getLibraries()) {
      if (dependsOn(library, artifact, true)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the given library depends on the given artifact, which consists of
   * a group id and an artifact id, such as {@link com.android.SdkConstants#APPCOMPAT_LIB_ARTIFACT}
   *
   * @param library the Gradle library to check
   * @param artifact the artifact
   * @param transitively if false, checks only direct dependencies, otherwise checks transitively
   * @return true if the project depends on the given artifact
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
}
