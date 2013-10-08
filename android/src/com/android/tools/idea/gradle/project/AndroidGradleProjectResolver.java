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
import com.android.tools.idea.gradle.*;
import com.android.tools.idea.gradle.dependency.Dependency;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.SearchInBuildFilesHyperlink;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
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
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.KeyValue;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.BasicIdeaProject;
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

/**
 * Imports Android-Gradle projects into IDEA.
 */
public class AndroidGradleProjectResolver implements GradleProjectResolverExtension {
  @NonNls private static final String ANDROID_TASK_NAME_PREFIX = "android";
  @NonNls private static final String COMPILE_JAVA_TASK_NAME = "compileJava";
  @NonNls private static final String CLASSES_TASK_NAME = "classes";
  @NonNls private static final String JAR_TASK_NAME = "jar";

  @NonNls private static final String UNSUPPORTED_MODEL_VERSION_ERROR = String.format(
    "Project is using an old version of the Android Gradle plug-in. The minimum supported version is %1$s.\n\n" +
    "Please update the version of the dependency 'com.android.tools.build:gradle' in your build.gradle files.",
    GradleModelVersionCheck.MINIMUM_SUPPORTED_VERSION.toString());

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
    // skip preview stage, the default implementation (GradleProjectResolver#) will be used
    // this can be changed if Android-specific issues should be taken into account during resolving of the 'preview' model
    // if(isPreviewMode) return null;

    // to skip any further resolve processing (even default one) throw com.intellij.openapi.externalSystem.service.ImportCanceledException
    // e.g.:
    //  if(isPreviewMode && isAndroidProject()) {
    //    throw new ImportCanceledException();
    //  }

    return myHelper.execute(projectPath, settings, new Function<ProjectConnection, DataNode<ProjectData>>() {
      @Nullable
      @Override
      public DataNode<ProjectData> fun(ProjectConnection connection) {
        try {
          List<String> extraJvmArgs = getExtraJvmArgs(projectPath, isPreviewMode);
          //noinspection TestOnlyProblems
          return resolveProjectInfo(id, projectPath, isPreviewMode, connection, listener, extraJvmArgs, settings);
        }
        catch (RuntimeException e) {
          throw myErrorHandler.getUserFriendlyError(e, projectPath, null);
        }
      }
    });
  }

  @NotNull
  private static List<String> getExtraJvmArgs(@NotNull String projectPath, boolean isPreviewMode) {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      List<String> args = Lists.newArrayList();
      if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(new File(projectPath))) {
        String androidHome = getAndroidSdkPathFromIde();
        if (androidHome != null) {
          String arg = AndroidGradleSettings.createAndroidHomeJvmArg(androidHome);
          args.add(arg);
        }
      }
      List<KeyValue<String,String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
      for (KeyValue<String, String> proxyProperty : proxyProperties) {
        String arg = AndroidGradleSettings.createJvmArg(proxyProperty.getKey(), proxyProperty.getValue());
        args.add(arg);
      }
      // "build model only" mode means that project import will not be aborted if any dependencies are not found.
      boolean buildModelOnly = isPreviewMode;
      String arg = AndroidGradleSettings.createJvmArg(AndroidProject.BUILD_MODEL_ONLY_SYSTEM_PROPERTY, String.valueOf(buildModelOnly));
      args.add(arg);
      return args;
    }
    return Collections.emptyList();
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

  /**
   * Imports multiple Android-Gradle projects. The set of projects to import may include regular Java projects as well.
   * <p/>
   * </p>Since the Android Gradle model does not support multiple projects, we query the Gradle Tooling API for a regular Java
   * multi-project. Then, for each of the modules in the imported project, we query for an (@link AndroidProject Android Gradle model.) If
   * we get one we create an IDE module from it, otherwise we just use the regular Java module. Unfortunately, this process requires
   * creation of multiple {@link ProjectConnection}s.
   *
   * @param id           id of the current 'resolve project info' task.
   * @param projectPath  absolute path of the parent folder of the build.gradle file.
   * @param connection   Gradle Tooling API connection to the project to import.
   * @param listener     callback to be notified about the execution.
   * @param extraJvmArgs extra JVM arguments to pass to Gradle tooling API.
   * @param settings     settings to use for the project resolving; {@code null} as indication that no specific settings are required.
   * @return the imported project, or {@link null} if the project to import is not an Android-Gradle project.
   */
  @VisibleForTesting
  @Nullable
  DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                           @NotNull String projectPath,
                                           final boolean isPreviewMode,
                                           @NotNull ProjectConnection connection,
                                           @NotNull ExternalSystemTaskNotificationListener listener,
                                           @NotNull List<String> extraJvmArgs,
                                           @Nullable GradleExecutionSettings settings) {
    ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(
      isPreviewMode ? BasicIdeaProject.class : IdeaProject.class, id, settings, connection, listener, extraJvmArgs);
    IdeaProject ideaProject = modelBuilder.get();
    if (ideaProject == null || ideaProject.getModules().isEmpty()) {
      return null;
    }

    String name = new File(projectPath).getName();
    DataNode<ProjectData> projectInfo = createProjectInfo(projectPath, name);

    AndroidProject first = null;

    Set<String> unresolvedDependencies = Sets.newHashSet();

    DomainObjectSet<? extends IdeaModule> modules = ideaProject.getModules();
    for (IdeaModule module : modules) {
      IdeaGradleProject gradleProject = new IdeaGradleProject(module.getName(), module.getGradleProject().getPath());
      String relativePath = getRelativePath(gradleProject);
      File moduleDir;
      if (relativePath.isEmpty()) {
        moduleDir = new File(projectPath);
      }
      else {
        moduleDir = new File(projectPath, relativePath);
      }
      File gradleBuildFile = new File(moduleDir, SdkConstants.FN_BUILD_GRADLE);
      if (!gradleBuildFile.isFile()) {
        continue;
      }
      String moduleDirPath = moduleDir.getPath();
      if (isAndroidProject(module.getGradleProject())) {
        AndroidProject androidProject = getAndroidProject(id, moduleDirPath, gradleBuildFile, listener, extraJvmArgs, settings);
        if (androidProject == null || !GradleModelVersionCheck.isSupportedVersion(androidProject)) {
          throw new IllegalStateException(UNSUPPORTED_MODEL_VERSION_ERROR);
        }
        unresolvedDependencies.addAll(getUnresolvedDependencies(androidProject));
        createModuleInfo(module, androidProject, projectInfo, moduleDirPath, gradleProject);
        if (first == null) {
          first = androidProject;
        }
      }
      else if (isJavaLibrary(module.getGradleProject())) {
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

    if (first == null) {
      // Don't import project if we don't have at least one AndroidProject.
      return null;
    }

    populateDependencies(projectInfo);
    populateUnresolvedDependencies(projectInfo, unresolvedDependencies);
    return projectInfo;
  }

  @NotNull
  private static Collection<String> getUnresolvedDependencies(@NotNull AndroidProject androidProject) {
    try {
      return androidProject.getUnresolvedDependencies();
    } catch (UnsupportedMethodException e) {
      // This happens when using an old but supported v0.5.+ plug-in. This code will be removed once the minimum supported version is 0.6.0.
      return Collections.emptySet();
    }
  }

  @NotNull
  private static DataNode<ProjectData> createProjectInfo(@NotNull String projectDirPath, @NotNull String name) {
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath, projectDirPath);
    projectData.setName(name);

    DataNode<ProjectData> projectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    projectInfo.createChild(JavaProjectData.KEY, javaProjectData);

    return projectInfo;
  }

  @NotNull
  private static String getRelativePath(@NotNull IdeaGradleProject gradleProject) {
    String separator = File.separator;
    if (separator.equals("\\")) {
      separator = "\\\\";
    }
    String gradleProjectPath = gradleProject.getGradleProjectPath();
    if (SdkConstants.GRADLE_PATH_SEPARATOR.equals(gradleProjectPath)) {
      return "";
    }
    return gradleProjectPath.replaceAll(SdkConstants.GRADLE_PATH_SEPARATOR, separator);
  }

  @Nullable
  private AndroidProject getAndroidProject(@NotNull final ExternalSystemTaskId id,
                                           @NotNull final String projectPath,
                                           @NotNull final File gradleBuildFile,
                                           @NotNull final ExternalSystemTaskNotificationListener listener,
                                           @NotNull final List<String> extraJvmArgs,
                                           @Nullable final GradleExecutionSettings settings) {
    return myHelper.execute(projectPath, settings, new Function<ProjectConnection, AndroidProject>() {
      @Nullable
      @Override
      public AndroidProject fun(ProjectConnection connection) {
        try {
          ModelBuilder<AndroidProject> modelBuilder =
            myHelper.getModelBuilder(AndroidProject.class, id, settings, connection, listener, extraJvmArgs);
          return modelBuilder.get();
        }
        catch (UnknownModelException e) {
          // Safe to ignore. It means that the Gradle project does not have an AndroidProject (e.g. a Java library project.)
          return null;
        }
        catch (RuntimeException e) {
          // This code should go away once we have one-pass project resolution in Gradle 1.8.
          // Once that version of Gradle is out, we don't need to pass the project path because we won't be iterating through each
          // sub-project looking for an AndroidProject. The current problem is: in this particular call to Gradle we don't get the location
          // of the build.gradle file that has a problem.
          throw myErrorHandler.getUserFriendlyError(e, projectPath, gradleBuildFile.getPath());
        }
      }
    });
  }

  private static boolean isAndroidProject(@NotNull GradleProject gradleProject) {
    // A Gradle project is an Android project is if has at least one task with name starting with 'android'.
    for (GradleTask task : gradleProject.getTasks()) {
      String taskName = task.getName();
      if (taskName != null && taskName.startsWith(ANDROID_TASK_NAME_PREFIX)) {
        return true;
      }
    }
    return false;
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
  }

  @Override
  public void enhanceLocalProcessing(@NotNull List<URL> urls) {
    GradleImportNotificationListener.attachToManager();
  }
}
