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
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.facet.JavaModel;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.navigatable.SearchInBuildFilesNavigatable;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNRESOLVED_ANDROID_DEPENDENCIES;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNRESOLVED_DEPENDENCIES;
import static com.android.tools.idea.gradle.util.GradleBuilds.BUILD_SRC_FOLDER_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_MINIMUM_VERSION;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  @NotNull public static final String UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX =
    "The project is using an unsupported version of the Android Gradle plug-in";

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

  /**
   * Returns the physical path of the module's root directory (the path in the file system.)
   * <p>
   * It is important to note that Gradle has its own "logical" path that may or may not be equal to the physical path of a Gradle project.
   * For example, the sub-project at ${projectRootDir}/apps/app will have the Gradle path :apps:app. Gradle also allows mapping physical
   * paths to a different logical path. For example, in settings.gradle:
   * <pre>
   *   include ':app'
   *   project(':app').projectDir = new File(rootDir, 'apps/app')
   * </pre>
   * In this example, sub-project at ${projectRootDir}/apps/app will have the Gradle path :app.
   * </p>
   *
   * @param build contains information about the root Gradle project and its sub-projects. Such information includes the physical path of
   *              the root Gradle project and its sub-projects.
   * @param path  the Gradle "logical" path. This path uses colon as separator, and may or may not be equal to the physical path of a
   *              Gradle project.
   * @return the physical path of the module's root directory.
   */
  @VisibleForTesting
  @Nullable
  static File getModuleDirPath(@NotNull GradleBuild build, @NotNull String path) {
    for (BasicGradleProject project : build.getProjects()) {
      if (project.getPath().equals(path)) {
        return project.getProjectDirectory();
      }
    }
    return null;
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
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
        new IdeaAndroidProject(gradleModule.getName(), moduleRootDirPath, androidProject, selectedVariant.getName());
      ideModule.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    }

    File gradleSettingsFile = new File(moduleRootDirPath, SdkConstants.FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      populateContentRootsForProjectModule(gradleModule, ideModule);
      return;
    }

    File buildFilePath = buildScript.getSourceFile();
    IdeaGradleProject gradleProject = new IdeaGradleProject(gradleModule.getName(), gradleModule.getGradleProject(), buildFilePath);
    ideModule.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);

    if (androidProject == null) {
      // This is a Java lib module.
      ModuleExtendedModel model = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
      assert model != null;
      JavaModel javaModel = JavaModel.newJavaModel(gradleModule, model);
      gradleProject.setJavaModel(javaModel);

      List<String> unresolved = javaModel.getUnresolvedDependencyNames();
      populateUnresolvedDependencies(ideModule, unresolved);
    }
  }

  private void populateContentRootsForProjectModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    nextResolver.populateModuleContentRoots(gradleModule, ideModule);

    ModuleExtendedModel model = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    assert model != null;

    String buildFolderPath = model.getBuildDir().getPath();

    ContentRootData.SourceRoot buildFolderRoot = null;

    for (DataNode<ContentRootData> contentRootNode : ExternalSystemApiUtil.getChildren(ideModule, ProjectKeys.CONTENT_ROOT)) {
      ContentRootData contentRoot = contentRootNode.getData();
      Collection<ContentRootData.SourceRoot> excludedRoots = contentRoot.getRoots(ExternalSystemSourceType.EXCLUDED);
      for (ContentRootData.SourceRoot root : excludedRoots) {
        if (FileUtil.pathsEqual(buildFolderPath, root.getPath())) {
          buildFolderRoot = root;
          break;
        }
      }
      if (buildFolderRoot != null) {
        excludedRoots.remove(buildFolderRoot);
        break;
      }
    }
  }

  private static void populateUnresolvedDependencies(@NotNull DataNode<ModuleData> ideModule, @NotNull List<String> unresolved) {
    if (unresolved.isEmpty() || ideModule.getParent() == null) {
      return;
    }
    DataNode<?> parent = ideModule.getParent();
    Object data = parent.getData();
    // the following is always going to be true.
    if (data instanceof ProjectData) {
      //noinspection unchecked
      populateUnresolvedDependencies((DataNode<ProjectData>)parent, unresolved);
    }
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
      return;
    }
    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    if (androidProject != null) {
      Collection<String> unresolvedDependencies = androidProject.getUnresolvedDependencies();
      populateUnresolvedDependencies(ideProject, Sets.newHashSet(unresolvedDependencies));
    }
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

      args.add(KeyValue.create(AndroidProject.BUILD_MODEL_ONLY_SYSTEM_PROPERTY, "true"));
      return args;
    }
    return Collections.emptyList();
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
    if (msg != null && !msg.contains(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
      Throwable rootCause = ExceptionUtil.getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // Project is using an old version of Gradle (and most likely an old version of the plug-in.)
        if ("org.gradle.api.artifacts.result.ResolvedComponentResult".equals(msg) ||
            "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(msg)) {
          GradleVersion supported = getGradleSupportedVersion();
          return new ExternalSystemException(getUnsupportedGradleVersionErrorMsg(supported));
        }
      }
    }
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
    return userFriendlyError != null ? userFriendlyError : nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @NotNull
  private static GradleVersion getGradleSupportedVersion() {
    return GradleVersion.version(GRADLE_MINIMUM_VERSION);
  }

  @NotNull
  private static String getUnsupportedModelVersionErrorMsg(@Nullable FullRevision modelVersion) {
    StringBuilder builder = new StringBuilder();
    builder.append(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX);
    if (modelVersion != null) {
      builder.append(String.format(" (%1$s)", modelVersion.toString()));
    }
    builder.append(".\n\nVersion 0.9.0 introduced incompatible changes in the build language.\n")
      .append("Please read the migration guide to learn how to update your project.");
    return builder.toString();
  }

  @NotNull
  private static String getUnsupportedGradleVersionErrorMsg(@NotNull GradleVersion supportedVersion) {
    String version = supportedVersion.getVersion();
    return String.format("The project is using an unsupported version of Gradle. Please use version %1$s.\n", version) +
           AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION;
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

  private static void populateUnresolvedDependencies(@NotNull DataNode<ProjectData> projectInfo,
                                                     @NotNull Collection<String> unresolvedDependencies) {
    for (String dep : unresolvedDependencies) {
      String group = dep.startsWith("com.android.support:") ? UNRESOLVED_ANDROID_DEPENDENCIES : UNRESOLVED_DEPENDENCIES;
      String text = dep + " (double-click here to find usages.)";
      Message msg = new Message(group, Message.Type.ERROR, new SearchInBuildFilesNavigatable(dep), text);
      projectInfo.createChild(AndroidProjectKeys.IMPORT_EVENT_MSG, msg);
    }
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
