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
import com.android.tools.idea.gradle.dependency.Dependency;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.gradle.service.notification.SearchInBuildFilesHyperlink;
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
import com.intellij.openapi.externalSystem.model.Key;
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
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static com.android.tools.idea.gradle.service.ProjectImportEventMessageDataService.RECOMMENDED_ACTIONS_CATEGORY;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_MINIMUM_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_PLUGIN_MINIMUM_VERSION;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {

  private static final String UNSUPPORTED_MODEL_VERSION_ERROR = String.format(
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
    return nextResolver.createModule(gradleModule, projectData);
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
    // create Android Facet (for android JPS builder) only if there is at least one Android module exist in the project
    if (!resolverCtx.findModulesWithModel(AndroidProject.class).isEmpty()) {
      IdeaGradleProject ideaGradleProject =
        new IdeaGradleProject(gradleModule.getName(), buildFile, gradleModule.getGradleProject().getPath());
      ideModule.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, ideaGradleProject);
    }
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

  /**
   * This method is meant to be used just to return any extra JVM args we'd like to pass to Gradle. Given that we need to do some checks
   * (e.g. ensure that we only use one Android SDK) before the actual project import, and the IDEA's Gradle 'project import' framework does
   * not currently provide a place for such checks, we need to perform them here.
   * This method has a side effect: it checks that Android Studio and the project (local.properties) point to the same Android SDK home. If
   * they are not the same, it asks the user to choose one and updates either the IDE's default SDK or project's SDK based on the user's
   * choice.
   * TODO: Ask JetBrains to provide a method in GradleProjectResolverExtension where we can perform this check.
   */
  @NotNull
  @Override
  public List<KeyValue<String, String>> getExtraJvmArgs() {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      List<KeyValue<String, String>> args = Lists.newArrayList();

      File projectDir = new File(FileUtil.toSystemDependentName(resolverCtx.getProjectPath()));
      LocalProperties localProperties = getLocalProperties(projectDir);

      if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
        SdkSync.syncIdeAndProjectAndroidHomes(localProperties);
      }
      else if (localProperties.getAndroidSdkPath() == null) {
        File androidHomePath = DefaultSdks.getDefaultAndroidHome();
        // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
        if (androidHomePath != null) {
          args.add(KeyValue.create(AndroidGradleSettings.ANDROID_HOME_JVM_ARG, androidHomePath.getPath()));
        }
      }

      args.add(KeyValue.create(AndroidProject.BUILD_MODEL_ONLY_SYSTEM_PROPERTY, String.valueOf(this.resolverCtx.isPreviewMode())));
      return args;
    }
    return Collections.emptyList();
  }

  @NotNull
  private static LocalProperties getLocalProperties(@NotNull File projectDir) {
    try {
      return new LocalProperties(projectDir);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file in project '%1$s'", projectDir.getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored") // Studio complains that the exceptions in line 256 and 257 are never thrown.
  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    // Check if the import error is due to an unsupported version of Gradle. If that is the case, the error received does not give users
    // any hint of the real issue. Here we check the version of Gradle and show an user-friendly error message.
    BuildEnvironment buildEnvironment = getBuildEnvironment();
    if (buildEnvironment != null) {
      GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
      GradleVersion minimumGradleVersion = GradleVersion.version(GRADLE_MINIMUM_VERSION);
      if (gradleVersion.compareTo(minimumGradleVersion) != 0) {
        // For now we have to use an exact version of Gradle.
        String msg = String
          .format("You are using Gradle version %1$s, which is not supported. Please use version %2$s.", gradleVersion.getVersion(),
                  GRADLE_MINIMUM_VERSION);
        msg += ('\n' + AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION);
        return new ExternalSystemException(msg);
      }
    }
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
    return userFriendlyError != null ? userFriendlyError : nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @Nullable
  private BuildEnvironment getBuildEnvironment() {
    try {
      return resolverCtx.getConnection().getModel(BuildEnvironment.class);
    }
    catch (Exception e) {
      return null;
    }
  }

  @NotNull
  private static Variant getFirstVariant(@NotNull AndroidProject androidProject) {
    Collection<Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      Variant variant = ContainerUtil.getFirstItem(variants);
      assert variant != null;
      return variant;
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

  private static void populateUnresolvedDependencies(@NotNull DataNode<ProjectData> projectInfo,
                                                     @NotNull Set<String> unresolvedDependencies) {
    boolean promptToInstallSupportRepository = false;
    for (String dep : unresolvedDependencies) {
      if (dep.startsWith("com.android.support:")) {
        promptToInstallSupportRepository = true;
      }
      NotificationHyperlink hyperlink = createSearchInBuildFileHyperlink(dep);
      ProjectImportEventMessage msg = new ProjectImportEventMessage("Unresolved dependencies:", dep, hyperlink);
      projectInfo.createChild(AndroidProjectKeys.IMPORT_EVENT_MSG, msg);
    }
    if (promptToInstallSupportRepository) {
      NotificationHyperlink hyperlink = new OpenAndroidSdkManagerHyperlink();
      ProjectImportEventMessage msg =
        new ProjectImportEventMessage(RECOMMENDED_ACTIONS_CATEGORY, "Install the Android Support Repository.", hyperlink);
      projectInfo.createChild(AndroidProjectKeys.IMPORT_EVENT_MSG, msg);
    }
  }

  @NotNull
  private static NotificationHyperlink createSearchInBuildFileHyperlink(@NotNull String dependency) {
    String url = "search:" + dependency;
    return new SearchInBuildFilesHyperlink(url, "Search", dependency);
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
