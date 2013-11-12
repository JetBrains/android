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
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.*;
import com.android.tools.idea.gradle.dependency.Dependency;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.SearchInBuildFilesHyperlink;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.net.URL;
import java.util.*;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_PLUGIN_MINIMUM_VERSION;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {

  @NonNls private static final String UNSUPPORTED_MODEL_VERSION_ERROR = String.format(
    "Project is using an old version of the Android Gradle plug-in. The minimum supported version is %1$s.\n\n" +
    "Please update the version of the dependency 'com.android.tools.build:gradle' in your build.gradle files.",
    GRADLE_PLUGIN_MINIMUM_VERSION);

  @NotNull private final ProjectImportErrorHandler myErrorHandler;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'projectResolve'.
  @SuppressWarnings("UnusedDeclaration")
  public AndroidGradleProjectResolver() {
    //noinspection TestOnlyProblems
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
      throw new IllegalStateException(UNSUPPORTED_MODEL_VERSION_ERROR);
    }
    String moduleName = gradleModule.getName();
    if (moduleName == null) {
      throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
    }
    String moduleConfigPath = GradleUtil.getConfigPath(gradleModule.getGradleProject(), projectData.getLinkedExternalProjectPath());
    return new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), moduleName, moduleConfigPath, moduleConfigPath);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    GradleScript buildScript = gradleModule.getGradleProject().getBuildScript();
    if (buildScript == null || buildScript.getSourceFile() == null) {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }
    File buildFile = buildScript.getSourceFile();
    File moduleRootDir = buildFile.getParentFile();
    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    if (androidProject != null) {
      Variant selectedVariant = getFirstVariant(androidProject);
      IdeaAndroidProject ideaAndroidProject =
        new IdeaAndroidProject(gradleModule.getName(), moduleRootDir, androidProject, selectedVariant.getName());
      addContentRoot(ideaAndroidProject, ideModule, moduleRootDir);

      ideModule.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    }
    else {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
    }
    File gradleSettingsFile = new File(moduleRootDir, SdkConstants.FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      return;
    }
    IdeaGradleProject ideaGradleProject = new IdeaGradleProject(gradleModule.getName(), buildFile, gradleModule.getGradleProject().getPath());
    ideModule.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, ideaGradleProject);
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    // TODO check if an Android module should contain specific compiler settings
    // AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    // if (androidProject == null) {
    //   nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
    // }
    nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    if (androidProject != null) {
      // TODO check if this dependency import differs from base impl, see org.jetbrains.plugins.gradle.service.project.BaseGradleProjectResolverExtension#populateModuleDependencies
      IdeaAndroidProject ideAndroidProject = getIdeaAndroidProject(ideModule);
      Collection<Dependency> dependencies = Collections.emptyList();
      if (ideAndroidProject != null) {
        dependencies = Dependency.extractFrom(ideAndroidProject);
      }
      else {
        IdeaModule module = extractIdeaModule(ideModule);
        if (module != null) {
          dependencies = Dependency.extractFrom(module);
        }
      }
      if (!dependencies.isEmpty()) {
        ImportedDependencyUpdater importer = new ImportedDependencyUpdater(ideProject);
        importer.updateDependencies(ideModule, dependencies);
      }

      Collection<String> unresolvedDependencies = androidProject.getUnresolvedDependencies();
      populateUnresolvedDependencies(ideProject, Sets.newHashSet(unresolvedDependencies));
    }
    else {
      nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    }
  }

  @NotNull
  @Override
  public Set<Class> getExtraProjectModelClasses() {
    return ContainerUtil.<Class>set(AndroidProject.class);
  }

  @NotNull
  @Override
  public List<KeyValue<String, String>> getExtraJvmArgs() {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      List<KeyValue<String, String>> args = Lists.newArrayList();
      File projectDir = new File(FileUtil.toSystemDependentName(resolverCtx.getProjectPath()));
      if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(projectDir)) {
        SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkManager != null) {
          String androidHome = FileUtil.toSystemDependentName(sdkManager.getLocation());
          args.add(KeyValue.create(AndroidGradleSettings.ANDROID_HOME_JVM_ARG, androidHome));
        }
      }

      args.add(KeyValue.create(AndroidProject.BUILD_MODEL_ONLY_SYSTEM_PROPERTY, String.valueOf(this.resolverCtx.isPreviewMode())));
      return args;
    }
    return Collections.emptyList();
  }

  // this exception will be thrown by org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
    return userFriendlyError != null ? userFriendlyError : nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @NotNull
  private static Variant getFirstVariant(@NotNull AndroidProject androidProject) {
    Map<String, Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      Variant variant = ContainerUtil.getFirstItem(variants.values());
      assert variant != null;
      return variant;
    }
    List<String> variantNames = Lists.newArrayList(variants.keySet());
    Collections.sort(variantNames);
    return variants.get(variantNames.get(0));
  }

  private static void addContentRoot(@NotNull IdeaAndroidProject androidProject,
                                     @NotNull DataNode<ModuleData> moduleInfo,
                                     @NotNull File moduleDir) {
    final ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, moduleDir.getPath());
    AndroidContentRoot.ContentRootStorage storage = new AndroidContentRoot.ContentRootStorage() {
      @Override
      @NotNull
      public String getRootDirPath() {
        return contentRootData.getRootPath();
      }

      @Override
      public void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File directory) {
        contentRootData.storePath(sourceType, directory.getPath());
      }
    };
    AndroidContentRoot.storePaths(androidProject, storage);
    moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
  }

  @Nullable
  private static IdeaAndroidProject getIdeaAndroidProject(@NotNull DataNode<ModuleData> moduleInfo) {
    return getFirstNodeData(moduleInfo, AndroidProjectKeys.IDE_ANDROID_PROJECT);
  }

  @Nullable
  private static <T> T getFirstNodeData(@NotNull DataNode<ModuleData> moduleInfo, @NotNull Key<T> key) {
    Collection<DataNode<T>> children = ExternalSystemApiUtil.getChildren(moduleInfo, key);
    return getFirstNodeData(children);
  }

  @Nullable
  private static IdeaModule extractIdeaModule(@NotNull DataNode<ModuleData> moduleInfo) {
    Collection<DataNode<IdeaModule>> modules = ExternalSystemApiUtil.getChildren(moduleInfo, AndroidProjectKeys.IDEA_MODULE);
    // it is safe to remove this node. We only needed it to resolve dependencies.
    moduleInfo.getChildren().removeAll(modules);
    return getFirstNodeData(modules);
  }

  @Nullable
  private static <T> T getFirstNodeData(Collection<DataNode<T>> nodes) {
    DataNode<T> node = ContainerUtil.getFirstItem(nodes);
    return node != null ? node.getData() : null;
  }

  @VisibleForTesting
  static boolean isIdeaTask(@NotNull String taskName) {
    return taskName.equals("idea") ||
           (taskName.startsWith("idea") && taskName.length() > 5 && Character.isUpperCase(taskName.charAt(4))) ||
           taskName.endsWith("Idea") ||
           taskName.endsWith("IdeaModule") ||
           taskName.endsWith("IdeaProject") ||
           taskName.endsWith("IdeaWorkspace");
  }

  private static void populateUnresolvedDependencies(@NotNull DataNode<ProjectData> projectInfo,
                                                     @NotNull Set<String> unresolvedDependencies) {
    String category = "Unresolved dependencies:";
    for (String dep : unresolvedDependencies) {
      String url = "search:" + dep;
      NotificationHyperlink hyperlink = new SearchInBuildFilesHyperlink(url, "Search", dep);
      projectInfo.createChild(AndroidProjectKeys.IMPORT_EVENT_MSG, new ProjectImportEventMessage(category, dep, hyperlink));
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
    parameters.getClassPath().addAll(classPath);
  }

  @Override
  public void enhanceLocalProcessing(@NotNull List<URL> urls) {
    GradleImportNotificationListener.attachToManager();
  }
}
