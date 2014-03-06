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
import com.android.tools.idea.gradle.ProjectImportEventMessage;
import com.android.tools.idea.gradle.facet.JavaModel;
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
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.model.ProjectDependenciesModel;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.gradle.service.ProjectImportEventMessageDataService.RECOMMENDED_ACTIONS_CATEGORY;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_MINIMUM_VERSION;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  public static final String UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX =
    "The project is using an unsupported version of the Android Gradle plug-in";

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
      String msg = getUnsupportedModelVersionErrorMsg(GradleModelVersionCheck.getModelVersion(androidProject));
      throw new IllegalStateException(msg);
    }
    return nextResolver.createModule(gradleModule, projectData);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    GradleScript buildScript = gradleModule.getGradleProject().getBuildScript();
    if (buildScript == null || buildScript.getSourceFile() == null || !isAndroidGradleProject()) {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    File buildFile = buildScript.getSourceFile();
    File moduleRootDir = buildFile.getParentFile();

    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);

    if (androidProject != null) {
      Variant selectedVariant = getVariantToSelect(androidProject);
      IdeaAndroidProject ideaAndroidProject =
        new IdeaAndroidProject(gradleModule.getName(), moduleRootDir, androidProject, selectedVariant.getName());
      ideModule.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    }

    File gradleSettingsFile = new File(moduleRootDir, SdkConstants.FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      return;
    }

    IdeaGradleProject gradleProject = new IdeaGradleProject(gradleModule.getName(), buildFile, gradleModule.getGradleProject().getPath());
    ideModule.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);

    if (androidProject == null) {
      // This is a Java lib module.
      JavaModel javaModel = createJavaModelFrom(gradleModule);
      gradleProject.setJavaModel(javaModel);
    }
  }

  @NotNull
  private JavaModel createJavaModelFrom(@NotNull IdeaModule module) {
    Collection<? extends IdeaContentRoot> contentRoots = getContentRootsFrom(module);
    List<? extends IdeaDependency> dependencies = getDependenciesFrom(module);
    return new JavaModel(contentRoots, dependencies);
  }

  @NotNull
  private Collection<? extends IdeaContentRoot> getContentRootsFrom(@NotNull IdeaModule module) {
    ModuleExtendedModel model = resolverCtx.getExtraProject(module, ModuleExtendedModel.class);
    Collection<? extends IdeaContentRoot> contentRoots = model != null ? model.getContentRoots() : module.getContentRoots();
    if (contentRoots != null) {
      return contentRoots;
    }
    return Collections.emptyList();
  }

  @NotNull
  private List<? extends IdeaDependency> getDependenciesFrom(@NotNull IdeaModule module) {
    ProjectDependenciesModel model = resolverCtx.getExtraProject(module, ProjectDependenciesModel.class);
    List<? extends IdeaDependency> dependencies = model != null ? model.getDependencies() : module.getDependencies().getAll();
    if (dependencies != null) {
      return dependencies;
    }
    return Collections.emptyList();
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    if (!isAndroidGradleProject()) {
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
  private boolean isAndroidGradleProject() {
    return !resolverCtx.findModulesWithModel(AndroidProject.class).isEmpty();
  }

  @NotNull
  @Override
  public Set<Class> getExtraProjectModelClasses() {
    return Collections.<Class>singleton(AndroidProject.class);
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

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored") // Studio complains that the exceptions created by this method are never thrown.
  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    // Check if the import error is due to an unsupported version of Gradle. If that is the case, the error received does not give users
    // any hint of the real issue. Here we check the version of Gradle and show an user-friendly error message.
    String msg = error.getMessage();
    if (msg != null && !msg.contains(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
      Throwable rootCause = ExceptionUtil.getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // We may get mismatched Gradle and plug-in versions. The problem is that we don't know the version of the plug-in we are using,
        // so here is our best guess.
        if ("org.gradle.api.artifacts.result.ResolvedComponentResult".equals(msg)) {
          // We got plug-in 0.8.+ with Gradle 1.9 or older.
          GradleVersion gradleVersion = getGradleVersion();
          if (gradleVersion != null) {
            GradleVersion supportedGradleVersion = getGradleMaximumSupportedVersion();
            if (gradleVersion.compareTo(supportedGradleVersion) != 0) {
              return new ExternalSystemException(getUnsupportedGradleVersionErrorMsg(gradleVersion, supportedGradleVersion));
            }
          }
        }
        else if ("org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(msg)) {
          // We got plug-in 0.7.+ with Gradle 1.10.
          GradleVersion gradleVersion = getGradleVersion();
          if (gradleVersion != null) {
            GradleVersion supportedGradleVersion = getGradleMinimumSupportedVersion();
            if (gradleVersion.compareTo(supportedGradleVersion) != 0) {
              return new ExternalSystemException(getUnsupportedGradleVersionErrorMsg(gradleVersion, supportedGradleVersion));
            }
          }
        }
      }
      // (rootCause instanceof BuildException) does not work, it may be because of the Groovy magic used to keep different versions of the
      // Tooling API being compatible with each other.
      if (rootCause.getClass().getName().equals("org.gradle.tooling.BuildException")) {
        msg = rootCause.getMessage();
        // This error happens when using an unsupported version of the plug-in with a recent version of Gradle. The error message comes
        // from the plug-in itself.
        // For example, using plug-in 0.6.+ with Gradle 1.9.
        if (msg != null && msg.startsWith("Gradle version") && msg.contains("is required")) {
          // We can assume we need to fix the plug-in version in build.gradle files.
          return new ExternalSystemException(getUnsupportedModelVersionErrorMsg(null));
        }
      }
    }
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
    return userFriendlyError != null ? userFriendlyError : nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @Nullable
  private GradleVersion getGradleVersion() {
    BuildEnvironment buildEnvironment = getBuildEnvironment();
    if (buildEnvironment != null) {
      return GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    }
    return null;
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
  private static GradleVersion getGradleMinimumSupportedVersion() {
    return GradleVersion.version(GRADLE_MINIMUM_VERSION);
  }

  @NotNull
  private static GradleVersion getGradleMaximumSupportedVersion() {
    return GradleVersion.version(GRADLE_LATEST_VERSION);
  }

  @NotNull
  private static String getUnsupportedModelVersionErrorMsg(@Nullable FullRevision modelVersion) {
    StringBuilder builder = new StringBuilder();
    builder.append(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX);
    if (modelVersion != null) {
      builder.append(String.format(" (%1$s)", modelVersion.toString()));
    }
    builder.append(".");
    return builder.toString();
  }

  @NotNull
  private static String getUnsupportedGradleVersionErrorMsg(@NotNull GradleVersion currentVersion,
                                                            @NotNull GradleVersion supportedVersion) {
    String msg = String.format(
      "You are using Gradle version %1$s, which is not supported by the version of the Android Gradle plug-in the project is using. " +
      "Please use version %2$s.", currentVersion.getVersion(), supportedVersion.getVersion());
    msg += ('\n' + AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION);
    return msg;
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
