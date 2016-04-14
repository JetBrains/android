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

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.tools.idea.gradle.*;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
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
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.KeyValue;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.AndroidProjectKeys.*;
import static com.android.tools.idea.gradle.project.GradleModelVersionCheck.getModelVersion;
import static com.android.tools.idea.gradle.project.GradleModelVersionCheck.isSupportedVersion;
import static com.android.tools.idea.gradle.project.ProjectImportErrorHandler.trackSyncError;
import static com.android.tools.idea.gradle.service.notification.errors.UnsupportedModelVersionErrorHandler.READ_MIGRATION_GUIDE_MSG;
import static com.android.tools.idea.gradle.service.notification.errors.UnsupportedModelVersionErrorHandler.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX;
import static com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleBuilds.BUILD_SRC_FOLDER_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.addLocalMavenRepoInitScriptCommandLineOption;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.android.tools.idea.stats.UsageTracker.*;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isInProcessMode;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;
import static java.util.Collections.sort;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  /** Default test artifact selected when importing a project. */
  private static final String DEFAULT_TEST_ARTIFACT = ARTIFACT_ANDROID_TEST;

  @NotNull private final ProjectImportErrorHandler myErrorHandler;

  @SuppressWarnings("UnusedDeclaration")
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
    if (androidProject != null && !isSupportedVersion(androidProject)) {
      trackSyncError(ACTION_GRADLE_SYNC_UNSUPPORTED_ANDROID_MODEL_VERSION, androidProject.getModelVersion());

      String msg = getUnsupportedModelVersionErrorMsg(getModelVersion(androidProject));
      throw new IllegalStateException(msg);
    }
    return nextResolver.createModule(gradleModule, projectData);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    ImportedModule importedModule = new ImportedModule(gradleModule);
    ideModule.createChild(IMPORTED_MODULE, importedModule);

    GradleProject gradleProject = gradleModule.getGradleProject();
    GradleScript buildScript = null;
    try {
      buildScript = gradleProject.getBuildScript();
    } catch (UnsupportedOperationException ignore) {}

    if (buildScript == null || !isAndroidGradleProject(gradleModule)) {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    // do not derive module root dir based on *.iml file location
    File moduleRootDirPath = new File(toSystemDependentName(ideModule.getData().getLinkedExternalProjectPath()));

    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);

    boolean androidProjectWithoutVariants = false;
    if (androidProject != null) {
      Variant selectedVariant = getVariantToSelect(androidProject);
      if (selectedVariant == null) {
        // If an Android project does not have variants, it would be impossible to build. This is a possible but invalid use case.
        // For now we are going to treat this case as a Java library module, because everywhere in the IDE (e.g. run configurations,
        // editors, test support, variants tool window, project building, etc.) we have the assumption that there is at least one variant
        // per Android project, and changing that in the code base is too risky, for very little benefit.
        // See https://code.google.com/p/android/issues/detail?id=170722
        androidProjectWithoutVariants = true;
      }
      else {
        AndroidGradleModel androidModel = new AndroidGradleModel(GRADLE_SYSTEM_ID, gradleModule.getName(), moduleRootDirPath,
                                                                 androidProject, selectedVariant.getName(), DEFAULT_TEST_ARTIFACT);
        ideModule.createChild(ANDROID_MODEL, androidModel);
      }
    }

    NativeAndroidProject nativeAndroidProject = resolverCtx.getExtraProject(gradleModule, NativeAndroidProject.class);
    if (androidProject == null && nativeAndroidProject != null) {
      // For a hybrid app which contains both java and native code, the information about the native code is present in both AndroidProject
      // and NativeAndroidProject. Until we figure out the correct way to handle that, using the NativeAndroidProject only for the pure
      // native modules and will be falling back to the information in AndroidProject model for hybrid modules.
      ideModule.createChild(NATIVE_ANDROID_MODEL, new NativeAndroidGradleModel(GRADLE_SYSTEM_ID, gradleModule.getName(), moduleRootDirPath,
                                                                               nativeAndroidProject));
    }

    File gradleSettingsFile = new File(moduleRootDirPath, FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null && nativeAndroidProject == null) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      createJavaProject(gradleModule, ideModule, false);
      return;
    }

    BuildScriptClasspathModel buildScriptModel = resolverCtx.getExtraProject(BuildScriptClasspathModel.class);
    String gradleVersion = buildScriptModel != null ? buildScriptModel.getGradleVersion() : null;

    File buildFilePath = buildScript.getSourceFile();
    GradleModel gradleModel = GradleModel.create(gradleModule.getName(), gradleProject, buildFilePath, gradleVersion);
    ideModule.createChild(GRADLE_MODEL, gradleModel);

    if (nativeAndroidProject == null && (androidProject == null || androidProjectWithoutVariants)) {
      // This is a Java lib module.
      createJavaProject(gradleModule, ideModule, androidProjectWithoutVariants);
    }
  }

  private void createJavaProject(@NotNull IdeaModule gradleModule,
                                 @NotNull DataNode<ModuleData> ideModule,
                                 boolean androidProjectWithoutVariants) {
    ModuleExtendedModel model = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    JavaProject javaProject = JavaProject.create(gradleModule, model, androidProjectWithoutVariants);
    ideModule.createChild(JAVA_PROJECT, javaProject);
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    if (!isAndroidGradleProject(gradleModule)) {
      nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
    }
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    if (!isAndroidGradleProject(gradleModule)) {
      // For plain Java projects (non-Gradle) we let the framework populate dependencies
      nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    }
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject or NativeAndroidProjct.
  private boolean isAndroidGradleProject(@NotNull IdeaModule gradleModule) {
    if (!resolverCtx.findModulesWithModel(AndroidProject.class).isEmpty() ||
        !resolverCtx.findModulesWithModel(NativeAndroidProject.class).isEmpty()) {
      return true;
    }
    if (BUILD_SRC_FOLDER_NAME.equals(gradleModule.getGradleProject().getName()) && isAndroidStudio()) {
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
    return Sets.<Class>newHashSet(AndroidProject.class, NativeAndroidProject.class);
  }

  @Override
  public void preImportCheck() {
    if (isGuiTestingMode()) {
      // We use this task in GUI tests to simulate errors coming from Gradle project sync.
      Application application = ApplicationManager.getApplication();
      Runnable task = application.getUserData(AndroidPlugin.EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY);
      if (task != null) {
        application.putUserData(AndroidPlugin.EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY, null);
        task.run();
      }
    }
  }

  @Override
  @NotNull
  public List<KeyValue<String, String>> getExtraJvmArgs() {
    if (isInProcessMode(GRADLE_SYSTEM_ID)) {
      List<KeyValue<String, String>> args = Lists.newArrayList();

      if (!isAndroidStudio()) {
        LocalProperties localProperties = getLocalProperties();
        if (localProperties.getAndroidSdkPath() == null) {
          File androidHomePath = IdeSdks.getAndroidSdkPath();
          // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
          if (androidHomePath != null) {
            args.add(KeyValue.create(ANDROID_HOME_JVM_ARG, androidHomePath.getPath()));
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

    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY, true));
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_ADVANCED, true));
    args.add(createProjectProperty(PROPERTY_INVOKED_FROM_IDE, true));

    if (isGuiTestingMode()) {
      // We store the command line args, the GUI test will later on verify that the correct values were passed to the sync process.
      ApplicationManager.getApplication().putUserData(AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY, toStringArray(args));
    }

    addLocalMavenRepoInitScriptCommandLineOption(args);

    return args;
  }

  @Nullable
  private Project findProject() {
    String projectDir = resolverCtx.getProjectPath();
    if (isNotEmpty(projectDir)) {
      File projectDirPath = new File(toSystemDependentName(projectDir));
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project : projects) {
        String basePath = project.getBasePath();
        if (basePath != null) {
          File currentPath = new File(basePath);
          if (filesEqual(projectDirPath, currentPath)) {
            return project;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private LocalProperties getLocalProperties() {
    File projectDir = new File(toSystemDependentName(resolverCtx.getProjectPath()));
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
    if (msg != null && !msg.contains(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
      Throwable rootCause = getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // Project is using an old version of Gradle (and most likely an old version of the plug-in.)
        if ("org.gradle.api.artifacts.result.ResolvedComponentResult".equals(msg) ||
            "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(msg)) {

          trackSyncError(CATEGORY_GRADLE_SYNC_FAILURE, ACTION_GRADLE_SYNC_UNSUPPORTED_GRADLE_VERSION);
          return new ExternalSystemException("The project is using an unsupported version of Gradle.");
        }
      }
    }
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);

    if (userFriendlyError == null) {
      trackSyncError(error);
      return nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
    }

    return userFriendlyError;
  }

  @NotNull
  private static String getUnsupportedModelVersionErrorMsg(@Nullable GradleVersion modelVersion) {
    StringBuilder builder = new StringBuilder();
    builder.append(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX);
    String recommendedVersion = String.format("The recommended version is %1$s.", GRADLE_PLUGIN_RECOMMENDED_VERSION);
    if (modelVersion != null) {
      builder.append(String.format(" (%1$s).", modelVersion.toString()))
             .append(" ")
             .append(recommendedVersion);
      if (modelVersion.getMajor() == 0 && modelVersion.getMinor() <= 8) {
        builder.append("\n\nStarting with version 0.9.0 incompatible changes were introduced in the build language.\n")
               .append(READ_MIGRATION_GUIDE_MSG)
               .append(" to learn how to update your project.");
      }
    }
    else {
      builder.append(". ")
             .append(recommendedVersion);
    }
    return builder.toString();
  }

  @Nullable
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
    sort(sortedVariants, new Comparator<Variant>() {
      @Override
      public int compare(Variant o1, Variant o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return sortedVariants.isEmpty() ? null : sortedVariants.get(0);
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    List<String> classPath = Lists.newArrayList();
    // Android module jars
    addIfNotNull(getJarPathForClass(getClass()), classPath);
    // Android sdklib jar
    addIfNotNull(getJarPathForClass(Revision.class), classPath);
    // Android common jar
    addIfNotNull(getJarPathForClass(AndroidGradleSettings.class), classPath);
    // Android gradle model jar
    addIfNotNull(getJarPathForClass(AndroidProject.class), classPath);
    parameters.getClassPath().addAll(classPath);
  }
}
