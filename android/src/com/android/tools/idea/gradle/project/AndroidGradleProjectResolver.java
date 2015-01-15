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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.*;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.service.notification.errors.UnsupportedModelVersionErrorHandler;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData.ClasspathEntry;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.util.EmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPath;
import static com.android.tools.idea.gradle.util.GradleBuilds.BUILD_SRC_FOLDER_NAME;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  /** Default test artifact selected when importing a project. */
  private static final String DEFAULT_TEST_ARTIFACT = AndroidProject.ARTIFACT_ANDROID_TEST;

  @NotNull private final ProjectImportErrorHandler myErrorHandler;

  public AndroidGradleProjectResolver() {
    this(new ProjectImportErrorHandler());
  }

  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull ProjectImportErrorHandler errorHandler) {
    myErrorHandler = errorHandler;
  }

  @NotNull
  @Override
  public ModuleData createModule(@NotNull IdeaModule gradleModule, @NotNull ProjectData projectData) {
    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    if (androidProject != null && !GradleModelVersionCheck.isSupportedVersion(androidProject)) {
      String msg = getUnsupportedModelVersionErrorMsg(GradleModelVersionCheck.getModelVersion(androidProject));
      throw new IllegalStateException(msg);
    }
    return nextResolver.createModule(gradleModule, projectData);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    ImportedModule importedModule = new ImportedModule(gradleModule);
    ideModule.createChild(AndroidProjectKeys.IMPORTED_MODULE, importedModule);

    GradleScript buildScript = null;
    try {
      buildScript = gradleModule.getGradleProject().getBuildScript();
    } catch (UnsupportedOperationException ignore) {}

    if (buildScript == null || !inAndroidGradleProject(gradleModule)) {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    File moduleFilePath = new File(FileUtil.toSystemDependentName(ideModule.getData().getModuleFilePath()));
    File moduleRootDirPath = moduleFilePath.getParentFile();

    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);

    if (androidProject != null) {
      Variant selectedVariant = getVariantToSelect(androidProject);
      IdeaAndroidProject ideaAndroidProject =
        new IdeaAndroidProject(GradleConstants.SYSTEM_ID,
                               gradleModule.getName(),
                               moduleRootDirPath,
                               androidProject,
                               selectedVariant.getName(),
                               DEFAULT_TEST_ARTIFACT);
      ideModule.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    }

    File gradleSettingsFile = new File(moduleRootDirPath, SdkConstants.FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      createJavaProject(gradleModule, ideModule);
      return;
    }

    File buildFilePath = buildScript.getSourceFile();
    IdeaGradleProject gradleProject = IdeaGradleProject.newIdeaGradleProject(gradleModule.getName(),
                                                                             gradleModule.getGradleProject(), buildFilePath);
    ideModule.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);

    if (androidProject == null && isJavaProject(gradleModule)) {
      // This is a Java lib module.
      createJavaProject(gradleModule, ideModule);
    }
  }

  private static boolean isJavaProject(@NotNull IdeaModule gradleModule) {
    for (GradleTask task : gradleModule.getGradleProject().getTasks()) {
      if (JavaGradleFacet.COMPILE_JAVA_TASK_NAME.equals(task.getName())) {
        return true;
      }
    }
    return false;
  }

  private void createJavaProject(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    ModuleExtendedModel model = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    IdeaJavaProject javaProject = new IdeaJavaProject(gradleModule, model);
    ideModule.createChild(AndroidProjectKeys.IDE_JAVA_PROJECT, javaProject);
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    if (!inAndroidGradleProject(gradleModule)) {
      nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
    }
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    if (!inAndroidGradleProject(gradleModule)) {
      // For plain Java projects (non-Gradle) we let the framework populate dependencies
      nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    }
  }

  @Override
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    nextResolver.populateModuleExtraModels(gradleModule, ideModule);

    if (inAndroidGradleProject(gradleModule) && AndroidStudioSpecificInitializer.isAndroidStudio()) {
      // We add the Android Gradle plugin jars (from the embedded Maven repo) to the classpath to make the build.gradle file editor resolve
      // methods from the plugin.
      Set<String> classes = Sets.newHashSet();
      Set<String> sources = Sets.newHashSet();
      for (File jarPath : findAndroidGradlePluginJarPaths()) {
        String jarName = jarPath.getName();
        if (jarName.endsWith("sources.jar")) {
          sources.add(jarPath.getPath());
        }
        else if (jarName.endsWith(SdkConstants.DOT_JAR)) {
          classes.add(jarPath.getPath());
        }
      }
      if (!classes.isEmpty()) {
        ClasspathEntry classpathEntry = new ClasspathEntry(classes, sources, Collections.<String>emptySet());
        List<ClasspathEntry> classpathEntries = Collections.singletonList(classpathEntry);
        BuildScriptClasspathData buildScriptClasspathData = new BuildScriptClasspathData(GradleConstants.SYSTEM_ID, classpathEntries);
        ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);
      }
    }
  }

  @NotNull
  private static List<File> findAndroidGradlePluginJarPaths() {
    File repoPath = findAndroidStudioLocalMavenRepoPath();
    if (repoPath != null) {
      File parentPath = new File(repoPath, FileUtil.join("com", "android", "tools", "build"));
      if (parentPath.isDirectory()) {
        List<File> paths = Lists.newArrayList();
        paths.addAll(findAndroidGradlePluginJarPaths(parentPath, "builder"));
        paths.addAll(findAndroidGradlePluginJarPaths(parentPath, "builder-model"));
        paths.addAll(findAndroidGradlePluginJarPaths(parentPath, "gradle"));
        return paths;
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<File> findAndroidGradlePluginJarPaths(@NotNull File parentPath, @NotNull String artifactId) {
    File artifactPath = new File(parentPath, artifactId);
    if (artifactPath.isDirectory()) {
      for (File versionDirPath : FileUtil.notNullize(artifactPath.listFiles())) {
        File[] files = FileUtil.notNullize(versionDirPath.listFiles());
        if (files.length > 0) {
          return Arrays.asList(files);
        }
      }
    }
    return Collections.emptyList();
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject.
  private boolean inAndroidGradleProject(@NotNull IdeaModule gradleModule) {
    if (!resolverCtx.findModulesWithModel(AndroidProject.class).isEmpty()) {
      return true;
    }
    if (BUILD_SRC_FOLDER_NAME.equals(gradleModule.getGradleProject().getName()) && AndroidStudioSpecificInitializer.isAndroidStudio()) {
      // For now, we will "buildSrc" to be considered part of an Android project. We need changes in IDEA to make this distinction better.
      // Currently, when processing "buildSrc" we don't have access to the rest of modules in the project, making it impossible to tell
      // if the project has at least one Android module.
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  public Set<Class> getExtraProjectModelClasses() {
    return Sets.<Class>newHashSet(AndroidProject.class);
  }

  @Override
  public void preImportCheck() {
    if (AndroidPlugin.isGuiTestingMode()) {
      // We use this task in GUI tests to simulate errors coming from Gradle project sync.
      Application application = ApplicationManager.getApplication();
      Runnable task = application.getUserData(AndroidPlugin.EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY);
      if (task != null) {
        application.putUserData(AndroidPlugin.EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY, null);
        task.run();
      }
    }
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      LocalProperties localProperties = getLocalProperties();
      // Ensure that Android Studio and the project (local.properties) point to the same Android SDK home. If they are not the same, we'll
      // ask the user to choose one and updates either the IDE's default SDK or project's SDK based on the user's choice.
      SdkSync.syncIdeAndProjectAndroidHomes(localProperties);
    }
  }

  @Override
  @NotNull
  public List<KeyValue<String, String>> getExtraJvmArgs() {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      List<KeyValue<String, String>> args = Lists.newArrayList();

      if (!AndroidStudioSpecificInitializer.isAndroidStudio()) {
        LocalProperties localProperties = getLocalProperties();
        if (localProperties.getAndroidSdkPath() == null) {
          File androidHomePath = DefaultSdks.getDefaultAndroidHome();
          // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
          if (androidHomePath != null) {
            args.add(KeyValue.create(AndroidGradleSettings.ANDROID_HOME_JVM_ARG, androidHomePath.getPath()));
          }
        }
      }
      return args;
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<String> getExtraCommandLineArgs() {
    List<String> args = Lists.newArrayList();

    Project project = findProject();
    if (project != null) {
      String[] commandLineOptions = project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY);
      if (commandLineOptions != null) {
        project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, null);
        Collections.addAll(args, commandLineOptions);
      }
    }

    args.add(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_BUILD_MODEL_ONLY, true));
    args.add(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, true));

    if (AndroidPlugin.isGuiTestingMode()) {
      // We store the command line args, the GUI test will later on verify that the correct values were passed to the sync process.
      ApplicationManager.getApplication().putUserData(AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY, ArrayUtil.toStringArray(args));
    }

    GradleUtil.addLocalMavenRepoInitScriptCommandLineOption(args);

    return args;
  }

  @Nullable
  private Project findProject() {
    String projectDir = resolverCtx.getProjectPath();
    if (StringUtil.isNotEmpty(projectDir)) {
      File projectDirPath = new File(FileUtil.toSystemDependentName(projectDir));
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project : projects) {
        File currentPath = new File(project.getBasePath());
        if (FileUtil.filesEqual(projectDirPath, currentPath)) {
          return project;
        }
      }
    }
    return null;
  }

  @NotNull
  private LocalProperties getLocalProperties() {
    File projectDir = new File(FileUtil.toSystemDependentName(resolverCtx.getProjectPath()));
    try {
      return new LocalProperties(projectDir);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file in project '%1$s'", projectDir.getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored") // Studio complains that the exceptions created by this method are never thrown.
  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    String msg = error.getMessage();
    if (msg != null && !msg.contains(UnsupportedModelVersionErrorHandler.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
      Throwable rootCause = ExceptionUtil.getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // Project is using an old version of Gradle (and most likely an old version of the plug-in.)
        if ("org.gradle.api.artifacts.result.ResolvedComponentResult".equals(msg) ||
            "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(msg)) {
          return new ExternalSystemException("The project is using an unsupported version of Gradle.");
        }
      }
    }
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
    return userFriendlyError != null ? userFriendlyError : nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @NotNull
  private static String getUnsupportedModelVersionErrorMsg(@Nullable FullRevision modelVersion) {
    StringBuilder builder = new StringBuilder();
    builder.append(UnsupportedModelVersionErrorHandler.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX);
    String recommendedVersion = String.format("The recommended version is %1$s.", SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION);
    if (modelVersion != null) {
      builder.append(String.format(" (%1$s).", modelVersion.toString()))
             .append(" ")
             .append(recommendedVersion);
      if (modelVersion.getMajor() == 0 && modelVersion.getMinor() <= 8) {
        builder.append("\n\nStarting with version 0.9.0 incompatible changes were introduced in the build language.\n")
               .append(UnsupportedModelVersionErrorHandler.READ_MIGRATION_GUIDE_MSG)
               .append(" to learn how to update your project.");
      }
    }
    else {
      builder.append(". ")
             .append(recommendedVersion);
    }
    return builder.toString();
  }

  @NotNull
  private static Variant getVariantToSelect(@NotNull AndroidProject androidProject) {
    Collection<Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      Variant variant = ContainerUtil.getFirstItem(variants);
      assert variant != null;
      return variant;
    }
    // look for "debug" variant. This is just a little convenience for the user that has not created any additional flavors/build types.
    // trying to match something else may add more complexity for little gain.
    for (Variant variant : variants) {
      if ("debug".equals(variant.getName())) {
        return variant;
      }
    }
    List<Variant> sortedVariants = Lists.newArrayList(variants);
    Collections.sort(sortedVariants, new Comparator<Variant>() {
      @Override
      public int compare(Variant o1, Variant o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return sortedVariants.get(0);
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    final List<String> classPath = ContainerUtilRt.newArrayList();
    // Android module jars
    ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(getClass()), classPath);
    // Android sdklib jar
    ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(FullRevision.class), classPath);
    // Android common jar
    ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(AndroidGradleSettings.class), classPath);
    // Android gradle model jar
    ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(AndroidProject.class), classPath);
    parameters.getClassPath().addAll(classPath);
  }
}
