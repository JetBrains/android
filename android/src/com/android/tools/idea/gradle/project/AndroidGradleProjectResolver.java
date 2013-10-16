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
import com.android.tools.idea.gradle.service.notification.SearchInBuildFilesHyperlink;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.net.HttpConfigurable;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.net.URL;
import java.util.*;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_PLUGIN_MINIMUM_VERSION;

/**
 * Imports Android-Gradle projects into IDEA.
 */
public class AndroidGradleProjectResolver implements GradleProjectResolverExtension {
  @NonNls private static final String COMPILE_JAVA_TASK_NAME = "compileJava";
  @NonNls private static final String CLASSES_TASK_NAME = "classes";
  @NonNls private static final String JAR_TASK_NAME = "jar";

  @NonNls private static final String UNSUPPORTED_MODEL_VERSION_ERROR = String.format(
    "Project is using an old version of the Android Gradle plug-in. The minimum supported version is %1$s.\n\n" +
    "Please update the version of the dependency 'com.android.tools.build:gradle' in your build.gradle files.",
    GRADLE_PLUGIN_MINIMUM_VERSION);

  @NotNull private final GradleExecutionHelper myHelper;
  @NotNull private final ProjectImportErrorHandler myErrorHandler;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'projectResolve'.
  @SuppressWarnings("UnusedDeclaration")
  public AndroidGradleProjectResolver() {
    //noinspection TestOnlyProblems
    this(new GradleExecutionHelper(), new ProjectImportErrorHandler());
  }

  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull GradleExecutionHelper helper, @NotNull ProjectImportErrorHandler errorHandler) {
    myHelper = helper;
    myErrorHandler = errorHandler;
  }

  /**
   * Imports an Android-Gradle project into IDEA.
   * <p/>
   * </p>Two types of projects are supported:
   * <ol>
   * <li>A single {@link AndroidProject}</li>
   * <li>A multi-project has at least one {@link AndroidProject} child</li>
   * </ol>
   *
   * @param id                id of the current 'resolve project info' task.
   * @param projectPath       absolute path of the parent folder of the build.gradle file.
   * @param isPreviewMode     Indicates, that an implementation can not provide/resolve any external dependencies.
   *                          Only project dependencies and local file dependencies may included on the modules' classpath.
   *                          And should not include any 'heavy' tasks like not trivial code generations.
   *                          It is supposed to be fast.
   * @param settings          settings to use for the project resolving; {@code null} as indication that no specific settings are required.
   * @param listener          callback to be notified about the execution
   * @return the imported project, or {@code null} if the project to import is not supported.
   */
  @Nullable
  @Override
  public DataNode<ProjectData> resolveProjectInfo(@NotNull final ExternalSystemTaskId id,
                                                  @NotNull final String projectPath,
                                                  final boolean isPreviewMode,
                                                  @Nullable final GradleExecutionSettings settings,
                                                  @NotNull final ExternalSystemTaskNotificationListener listener) {
    String systemDependentProjectPath = FileUtil.toSystemDependentName(projectPath);
    final File projectDir = new File(systemDependentProjectPath);
    if (!isPreviewMode) {
      // We ignore the second pass of the import process. We got everything we needed from the first one.
      return createProjectInfo(projectDir);
    }
    return myHelper.execute(systemDependentProjectPath, settings, new Function<ProjectConnection, DataNode<ProjectData>>() {
      @Nullable
      @Override
      public DataNode<ProjectData> fun(ProjectConnection connection) {
        try {
          List<String> extraJvmArgs = getExtraJvmArgs(projectDir);
          BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = connection.action(new ProjectImportAction());
          GradleExecutionHelper.prepare(buildActionExecutor, id, settings, listener, extraJvmArgs, connection);

          //noinspection TestOnlyProblems
          return resolveProjectInfo(projectDir, buildActionExecutor);
        }
        catch (RuntimeException e) {
          Projects.applyToCurrentGradleProject(new AsyncResult.Handler<Project>() {
            @Override
            public void run(Project project) {
              Projects.notifyProjectSyncCompleted(project, false);
            }
          });
          throw myErrorHandler.getUserFriendlyError(e, projectDir, null);
        }
      }
    });
  }

  @NotNull
  private static List<String> getExtraJvmArgs(@NotNull File projectDir) {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      List<String> args = Lists.newArrayList();
      if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(projectDir)) {
        String androidHome = getAndroidSdkPathFromIde();
        if (androidHome != null) {
          String arg = AndroidGradleSettings.createAndroidHomeJvmArg(androidHome);
          args.add(arg);
        }
      }
      List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
      for (KeyValue<String, String> proxyProperty : proxyProperties) {
        String arg = AndroidGradleSettings.createJvmArg(proxyProperty.getKey(), proxyProperty.getValue());
        args.add(arg);
      }
      String arg = AndroidGradleSettings.createJvmArg(AndroidProject.BUILD_MODEL_ONLY_SYSTEM_PROPERTY, "true");
      args.add(arg);
      return args;
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  @Nullable
  DataNode<ProjectData> resolveProjectInfo(@NotNull File projectDir,
                                           @NotNull BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor) {
    ProjectImportAction.AllModels allModels = buildActionExecutor.run();
    if (allModels == null) {
      return null;
    }
    IdeaProject ideaProject = allModels.getIdeaProject();
    DataNode<ProjectData> projectInfo = createProjectInfo(projectDir);

    Set<String> unresolvedDependencies = Sets.newHashSet();

    for (IdeaModule module : ideaProject.getModules()) {
      GradleScript buildScript = module.getGradleProject().getBuildScript();
      if (buildScript == null || buildScript.getSourceFile() == null) {
        continue;
      }
      File buildFile = buildScript.getSourceFile();
      File moduleDir = buildFile.getParentFile();
      String moduleDirPath = moduleDir.getPath();

      IdeaGradleProject gradleProject = new IdeaGradleProject(module.getName(), module.getGradleProject().getPath());

      AndroidProject androidProject = allModels.getAndroidProject(module);
      if (androidProject != null) {
        if (!GradleModelVersionCheck.isSupportedVersion(androidProject)) {
          throw new IllegalStateException(UNSUPPORTED_MODEL_VERSION_ERROR);
        }
        createModuleInfo(module, androidProject, projectInfo, moduleDirPath, gradleProject);
        unresolvedDependencies.addAll(androidProject.getUnresolvedDependencies());

      } else if (isJavaLibrary(module.getGradleProject())) {
        createModuleInfo(module, projectInfo, moduleDirPath, gradleProject);
      }

      else {
        File gradleSettingsFile = new File(moduleDir, SdkConstants.FN_SETTINGS_GRADLE);
        if (gradleSettingsFile.isFile()) {
          // This is just a root folder for a group of Gradle projects. Set the Gradle project to null so the JPS builder won't try to
          // compile it using Gradle. We still need to create the module to display files inside it.
          createModuleInfo(module, projectInfo, moduleDirPath, null);
        }
      }
    }
    populateDependencies(projectInfo);
    populateUnresolvedDependencies(projectInfo, unresolvedDependencies);
    return projectInfo;
  }

  @Nullable
  private static String getAndroidSdkPathFromIde() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      AndroidPlatform androidPlatform = AndroidPlatform.parse(sdk);
      String sdkHomePath = sdk.getHomePath();
      if (androidPlatform != null && sdkHomePath != null) {
        return sdkHomePath;
      }
    }
    return null;
  }


  @NotNull
  private static DataNode<ProjectData> createProjectInfo(@NotNull File projectDir) {
    String name = projectDir.getName();

    String projectDirPath = projectDir.getPath();
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath, projectDirPath);
    projectData.setName(name);

    DataNode<ProjectData> projectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    projectInfo.createChild(JavaProjectData.KEY, javaProjectData);

    return projectInfo;
  }

  private static boolean isJavaLibrary(@NotNull GradleProject gradleProject) {
    // A Gradle project is a Java library if it has the tasks 'compileJava', 'classes' and 'jar'.
    List<String> javaTasks = Lists.newArrayList(COMPILE_JAVA_TASK_NAME, CLASSES_TASK_NAME, JAR_TASK_NAME);
    for (GradleTask task : gradleProject.getTasks()) {
      String taskName = task.getName();
      if (taskName != null && javaTasks.remove(taskName) && javaTasks.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static DataNode<ModuleData> createModuleInfo(@NotNull IdeaModule module,
                                                       @NotNull AndroidProject androidProject,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @NotNull String moduleDirPath,
                                                       @NotNull IdeaGradleProject gradleProject) {
    String moduleName = module.getName();
    ModuleData moduleData = createModuleData(module, projectInfo, moduleName, moduleDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    Variant selectedVariant = getFirstVariant(androidProject);
    IdeaAndroidProject ideaAndroidProject = new IdeaAndroidProject(moduleName, moduleDirPath, androidProject, selectedVariant.getName());
    addContentRoot(ideaAndroidProject, moduleInfo, moduleDirPath);

    moduleInfo.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    moduleInfo.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);

    addModuleTasks(moduleInfo, module, projectInfo);
    return moduleInfo;
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
                                     @NotNull String moduleDirPath) {
    final ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, moduleDirPath);
    AndroidContentRoot.ContentRootStorage storage = new AndroidContentRoot.ContentRootStorage() {
      @Override
      @NotNull
      public String getRootDirPath() {
        return contentRootData.getRootPath();
      }

      @Override
      public void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File directory) {
        contentRootData.storePath(sourceType, directory.getAbsolutePath());
      }
    };
    AndroidContentRoot.storePaths(androidProject, storage);
    moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
  }

  @NotNull
  private static DataNode<ModuleData> createModuleInfo(@NotNull IdeaModule module,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @NotNull String moduleDirPath,
                                                       @Nullable IdeaGradleProject gradleProject) {
    ModuleData moduleData = createModuleData(module, projectInfo, module.getName(), moduleDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    // Populate content roots.
    for (IdeaContentRoot from : module.getContentRoots()) {
      if (from == null || from.getRootDirectory() == null) {
        continue;
      }
      ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, from.getRootDirectory().getAbsolutePath());
      GradleContentRoot.storePaths(from, contentRootData);
      moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
    }

    moduleInfo.createChild(AndroidProjectKeys.IDEA_MODULE, module);
    if (gradleProject != null) {
      moduleInfo.createChild(AndroidProjectKeys.IDE_GRADLE_PROJECT, gradleProject);
    }

    addModuleTasks(moduleInfo, module, projectInfo);
    return moduleInfo;
  }

  private static ModuleData createModuleData(@NotNull IdeaModule module,
                                             @NotNull DataNode<ProjectData> projectInfo,
                                             @NotNull String name,
                                             @NotNull String dirPath) {
    String moduleConfigPath = GradleUtil.getConfigPath(module.getGradleProject(), projectInfo.getData().getLinkedExternalProjectPath());
    return new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, dirPath, moduleConfigPath);
  }

  private static void populateDependencies(@NotNull DataNode<ProjectData> projectInfo) {
    Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE);
    ImportedDependencyUpdater importer = new ImportedDependencyUpdater(projectInfo);
    for (DataNode<ModuleData> moduleInfo : modules) {
      IdeaAndroidProject androidProject = getIdeaAndroidProject(moduleInfo);
      Collection<Dependency> dependencies = Collections.emptyList();
      if (androidProject != null) {
        dependencies = Dependency.extractFrom(androidProject);
      }
      else {
        IdeaModule module = extractIdeaModule(moduleInfo);
        if (module != null) {
          dependencies = Dependency.extractFrom(module);
        }
      }
      if (!dependencies.isEmpty()) {
        importer.updateDependencies(moduleInfo, dependencies);
      }
    }
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

  private static void addModuleTasks(@NotNull DataNode<ModuleData> moduleInfo,
                                     @NotNull IdeaModule module,
                                     @NotNull DataNode<ProjectData> projectInfo) {
    String rootProjectPath = projectInfo.getData().getLinkedExternalProjectPath();
    String moduleConfigPath = GradleUtil.getConfigPath(module.getGradleProject(), rootProjectPath);

    DataNode<?> target = moduleConfigPath.equals(rootProjectPath) ? projectInfo : moduleInfo;

    for (GradleTask task : module.getGradleProject().getTasks()) {
      String taskName = task.getName();
      //noinspection TestOnlyProblems
      if (taskName == null || taskName.trim().isEmpty() || isIdeaTask(taskName)) {
        continue;
      }
      TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, taskName, moduleConfigPath, task.getDescription());
      target.createChild(ProjectKeys.TASK, taskData);
    }
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
