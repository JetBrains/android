/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import one.util.streamex.StreamEx;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.gradle.util.GradleBuilds.*;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class GradleTaskFinder {
  @NotNull
  public static GradleTaskFinder getInstance() {
    return ServiceManager.getService(GradleTaskFinder.class);
  }

  @NotNull
  public ListMultimap<Path, String> findTasksToExecuteForTest(@NotNull Module[] testedModules,
                                                              @NotNull Module[] modules,
                                                              @NotNull BuildMode buildMode,
                                                              @NotNull TestCompileType testCompileType) {
    ListMultimap<Path, String> allTasks = findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    ListMultimap<Path, String> testedModulesTasks = findTasksToExecute(testedModules, buildMode, testCompileType);

    // Add testedModulesTasks to allTasks without duplicate
    testedModulesTasks.forEach((path, task) -> {
      if (!allTasks.get(path).contains(task)) {
        allTasks.put(path, task);
      }
    });
    return allTasks;
  }

  @NotNull
  public ListMultimap<Path, String> findTasksToExecute(@NotNull Module[] modules,
                                                       @NotNull BuildMode buildMode,
                                                       @NotNull TestCompileType testCompileType) {
    LinkedHashMultimap<Path, String> tasks = LinkedHashMultimap.create();
    if (ASSEMBLE == buildMode) {
      Project project = modules[0].getProject();
      if (GradleSyncState.getInstance(project).lastSyncFailed()) {
        // If last Gradle sync failed, just call "assemble" at the top-level. Without a model there are no other tasks we can call.
        StreamEx.of(modules)
          .map(module -> {
            String projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
            if (projectPath == null) {
              projectPath = project.getBasePath();
            }
            return projectPath;
          })
          .nonNull()
          .distinct()
          .map(path -> Paths.get(path))
          .forEach(path -> tasks.put(path, DEFAULT_ASSEMBLE_TASK_NAME));
        return ArrayListMultimap.create(tasks);
      }
    }

    for (Module module : modules) {
      if (BUILD_SRC_FOLDER_NAME.equals(module.getName())) {
        // "buildSrc" is a special case handled automatically by Gradle.
        continue;
      }
      String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (isEmpty(rootProjectPath)) {
        continue;
      }

      List<String> moduleTasks = new ArrayList<>();
      findAndAddGradleBuildTasks(module, buildMode, moduleTasks, testCompileType);

      if (buildMode == REBUILD && (!tasks.isEmpty() || !moduleTasks.isEmpty())) {
        tasks.put(Paths.get(rootProjectPath), CLEAN_TASK_NAME);
      }

      tasks.putAll(Paths.get(rootProjectPath), moduleTasks);
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      getLogger().info(String.format(format, modules[0].getProject().getName(), buildMode.name()));
    }
    return ArrayListMultimap.create(tasks);
  }

  private void findAndAddGradleBuildTasks(@NotNull Module module,
                                          @NotNull BuildMode buildMode,
                                          @NotNull List<String> tasks,
                                          @NotNull TestCompileType testCompileType) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return;
    }

    String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (isEmpty(gradlePath)) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String msg = String.format("Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.",
                                 module.getName());
      getLogger().info(msg);
      return;
    }

    if (isCompositeBuild(module)) {
      // Sikp gradle tasks for composite build.
      String msg = String.format("Module '%1$s' comes from composite build, skip gradle tasks.", module.getName());
      getLogger().info(msg);
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();

      AndroidModuleModel androidModel = AndroidModuleModel.get(module);

      switch (buildMode) {
        case CLEAN: // Intentional fall-through.
        case SOURCE_GEN:
          addAfterSyncTasks(tasks, gradlePath, properties);
          if (androidModel != null) {
            addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);
          }
          break;
        case ASSEMBLE:
        case REBUILD:
          addTaskIfSpecified(tasks, gradlePath, properties.ASSEMBLE_TASK_NAME);

          // Add assemble tasks for tests.
          if (testCompileType != TestCompileType.ALL) {
            if (androidModel != null) {
              for (BaseArtifact artifact : testCompileType.getArtifacts(androidModel.getSelectedVariant())) {
                addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
              }
            }
          }

          // Add assemble tasks for tested variants in test-only modules
          addAssembleTasksForTargetVariants(tasks, module);

          break;
        default:
          addAfterSyncTasks(tasks, gradlePath, properties);
          if (androidModel != null) {
            addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);
            for (BaseArtifact artifact : testCompileType.getArtifacts(androidModel.getSelectedVariant())) {
              addTaskIfSpecified(tasks, gradlePath, artifact.getCompileTaskName());
            }
          }
          // When compiling for unit tests, run only COMPILE_JAVA_TEST_TASK_NAME, which will run javac over main and test code. If the
          // Jack compiler is enabled in Gradle, COMPILE_JAVA_TASK_NAME will end up running e.g. compileDebugJavaWithJack, which produces
          // no *.class files and would be just a waste of time.
          if (testCompileType != TestCompileType.UNIT_TESTS) {
            addTaskIfSpecified(tasks, gradlePath, properties.COMPILE_JAVA_TASK_NAME);
          }

          // Add compile tasks for tests.
          break;
      }
    }
    else {
      JavaFacet javaFacet = JavaFacet.getInstance(module);
      if (javaFacet != null && javaFacet.getConfiguration().BUILDABLE) {
        String gradleTaskName = javaFacet.getGradleTaskName(buildMode);
        if (gradleTaskName != null) {
          tasks.add(createBuildTask(gradlePath, gradleTaskName));
        }
        if (TestCompileType.UNIT_TESTS.equals(testCompileType)) {
          tasks.add(createBuildTask(gradlePath, JavaFacet.TEST_CLASSES_TASK_NAME));
        }
      }
    }
  }

  /**
   * @return {@code true} if current module comes from composite build.
   */
  @VisibleForTesting
  static boolean isCompositeBuild(@NotNull Module module) {
    String projectPath = module.getProject().getBasePath();
    GradleProjectSettings projectSettings =
      GradleSettings.getInstance(module.getProject()).getLinkedProjectSettings(projectPath);
    if (projectSettings == null) {
      return false;
    }
    CompositeBuild compositeBuild = projectSettings.getCompositeBuild();
    if (compositeBuild == null) {
      return false;
    }
    VirtualFile moduleFile = module.getModuleFile();
    if (moduleFile == null) {
      return false;
    }
    String modulePath = moduleFile.getParent().getPath();
    for (BuildParticipant participant : compositeBuild.getCompositeParticipants()) {
      if (participant.getProjects().contains(modulePath)) {
        return true;
      }
    }
    return false;
  }

  private void addAssembleTasksForTargetVariants(@NotNull List<String> tasks, @NotNull Module testOnlyModule) {
    AndroidModuleModel testAndroidModel = AndroidModuleModel.get(testOnlyModule);

    if (testAndroidModel == null ||
        !testAndroidModel.getFeatures().isTestedTargetVariantsSupported() ||
        testAndroidModel.getAndroidProject().getProjectType() != AndroidProject.PROJECT_TYPE_TEST) {
      // If we don't have the target module and variant to be tested, no task should be added.
      return;
    }

    for (TestedTargetVariant testedTargetVariant : testAndroidModel.getSelectedVariant().getTestedTargetVariants()) {
      String targetProjectGradlePath = testedTargetVariant.getTargetProjectPath();
      Module targetModule = findModuleByGradlePath(testOnlyModule.getProject(), targetProjectGradlePath);

      // Adds the assemble task for the tested variants
      if (targetModule != null) {
        AndroidModuleModel targetAndroidModel = AndroidModuleModel.get(targetModule);

        if (targetAndroidModel != null) {
          String targetVariantName = testedTargetVariant.getTargetVariant();
          Variant targetVariant = targetAndroidModel.findVariantByName(targetVariantName);

          if (targetVariant != null) {
            addTaskIfSpecified(tasks, targetProjectGradlePath, targetVariant.getMainArtifact().getAssembleTaskName());
          }
        }
      }
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleTaskFinder.class);
  }

  private void addAfterSyncTasksForTestArtifacts(@NotNull List<String> tasks,
                                                 @NotNull String gradlePath,
                                                 @NotNull TestCompileType testCompileType,
                                                 @NotNull AndroidModuleModel androidModel) {
    IdeVariant variant = androidModel.getSelectedVariant();
    Collection<IdeBaseArtifact> testArtifacts = testCompileType.getArtifacts(variant);
    for (IdeBaseArtifact artifact : testArtifacts) {
      for (String taskName : artifact.getIdeSetupTaskNames()) {
        addTaskIfSpecified(tasks, gradlePath, taskName);
      }
    }
  }

  private void addAfterSyncTasks(@NotNull List<String> tasks,
                                 @NotNull String gradlePath,
                                 @NotNull JpsAndroidModuleProperties properties) {
    // Make sure all the generated sources, unpacked aars and mockable jars are in place. They are usually up to date, since we
    // generate them at sync time, so Gradle will just skip those tasks. The generated files can be missing if this is a "Rebuild
    // Project" run or if the user cleaned the project from the command line. The mockable jar is necessary to run unit tests, but the
    // compilation tasks don't depend on it, so we have to call it explicitly.
    for (String taskName : properties.AFTER_SYNC_TASK_NAMES) {
      addTaskIfSpecified(tasks, gradlePath, taskName);
    }
  }

  private void addTaskIfSpecified(@NotNull List<String> tasks, @NotNull String gradlePath, @Nullable String gradleTaskName) {
    if (isNotEmpty(gradleTaskName)) {
      String buildTask = createBuildTask(gradlePath, gradleTaskName);
      if (!tasks.contains(buildTask)) {
        tasks.add(buildTask);
      }
    }
  }

  @NotNull
  public String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    if (gradleProjectPath.equals(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      // Prevent double colon when dealing with root module (e.g. "::assemble");
      return gradleProjectPath + taskName;
    }
    return gradleProjectPath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }
}
