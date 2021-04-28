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

import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.gradle.util.GradleBuilds.BUILD_SRC_FOLDER_NAME;
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeTestedTargetVariant;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleTaskFinder {
  private final GradleRootPathFinder myRootPathFinder;

  @NotNull
  public static GradleTaskFinder getInstance() {
    return ApplicationManager.getApplication().getService(GradleTaskFinder.class);
  }

  @SuppressWarnings("unused") // Invoked by IDEA.
  public GradleTaskFinder() {
    this(new GradleRootPathFinder());
  }

  @NonInjectable
  @VisibleForTesting
  GradleTaskFinder(GradleRootPathFinder rootPathFinder) {
    myRootPathFinder = rootPathFinder;
  }

  @NotNull
  public ListMultimap<Path, String> findTasksToExecuteForTest(@NotNull Module[] modules,
                                                              @NotNull Module[] testModules,
                                                              @NotNull BuildMode buildMode,
                                                              @NotNull TestCompileType testCompileType) {
    ListMultimap<Path, String> allTasks = findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    ListMultimap<Path, String> testedModulesTasks = findTasksToExecute(testModules, buildMode, testCompileType);

    // Add testedModulesTasks to allTasks without duplicate
    for (Map.Entry<Path, String> task : testedModulesTasks.entries()) {
      if (!allTasks.values().contains(task.getValue())) {
        allTasks.put(task.getKey(), task.getValue());
      }
    }
    return allTasks;
  }

  @NotNull
  public ListMultimap<Path, String> findTasksToExecute(@NotNull Module[] modules,
                                                       @NotNull BuildMode buildMode,
                                                       @NotNull TestCompileType testCompileType) {
    LinkedHashMultimap<Path, String> tasks = LinkedHashMultimap.create();
    if (ASSEMBLE == buildMode) {
      if (!canAssembleModules(modules)) {
        // Just call "assemble" at the top-level. Without a model there are no other tasks we can call.
        for (Module module : modules) {
          Path projectRootPath = myRootPathFinder.getProjectRootPath(module);
          tasks.put(projectRootPath, DEFAULT_ASSEMBLE_TASK_NAME);
        }
        return ArrayListMultimap.create(tasks);
      }
    }

    Set<Module> allModules = new LinkedHashSet<>();
    for (Module module : modules) {
      allModules.addAll(DynamicAppUtils.getModulesToBuild(module));
    }

    // Instrumented test support for Dynamic Features: base-app module should be added explicitly for gradle tasks
    if (testCompileType == TestCompileType.ANDROID_TESTS) {
      for (Module module : modules) {
        Module baseAppModule = DynamicAppUtils.getBaseFeature(module);
        if (baseAppModule != null) {
          allModules.add(baseAppModule);
        }
      }
    }

    for (Module module : allModules) {
      String modulePath = ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectId();
      if (modulePath != null && modulePath.endsWith(":" + BUILD_SRC_FOLDER_NAME)) {
        // "buildSrc" is a special case handled automatically by Gradle.
        continue;
      }

      Set<String> moduleTasks = new LinkedHashSet<>();
      findAndAddGradleBuildTasks(module, buildMode, moduleTasks, testCompileType);

      Path keyPath = myRootPathFinder.getProjectRootPath(module);
      // Remove duplicates.
      moduleTasks.addAll(tasks.get(keyPath));

      tasks.removeAll(keyPath);
      if (buildMode == REBUILD && !moduleTasks.isEmpty()) {
        // Clean only if other tasks are needed
        tasks.put(keyPath, CLEAN_TASK_NAME);
      }
      tasks.putAll(keyPath, moduleTasks);
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      getLogger().info(String.format(format, modules[0].getProject().getName(), buildMode.name()));
    }
    return ArrayListMultimap.create(tasks);
  }

  private static boolean canAssembleModules(@NotNull Module[] modules) {
    if (modules.length == 0) {
      return false;
    }
    Project project = modules[0].getProject();
    return !GradleSyncState.getInstance(project).lastSyncFailed();
  }

  private static void findAndAddGradleBuildTasks(@NotNull Module module,
                                                 @NotNull BuildMode buildMode,
                                                 @NotNull Set<String> tasks,
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

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      AndroidFacetProperties properties = androidFacet.getProperties();

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
              for (IdeBaseArtifact artifact : testCompileType.getArtifacts(androidModel.getSelectedVariant())) {
                addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
              }
            }
          }

          // Add assemble tasks for tested variants in test-only modules
          addAssembleTasksForTargetVariants(tasks, module);

          break;
        case BUNDLE:
          // The "Bundle" task is only valid for base (app) module, not for features, libraries, etc.
          if (androidModel != null && androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
            String taskName = androidModel.getSelectedVariant().getMainArtifact().getBuildInformation().getBundleTaskName();
            addTaskIfSpecified(tasks, gradlePath, taskName);
          }
          break;
        case APK_FROM_BUNDLE:
          // The "ApkFromBundle" task is only valid for base (app) module, and for features if it's for instrumented tests
          if (androidModel != null && androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
            String taskName = androidModel.getSelectedVariant().getMainArtifact().getBuildInformation().getApkFromBundleTaskName();
            addTaskIfSpecified(tasks, gradlePath, taskName);
          }
          else if (androidModel != null &&
                   androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
            // Instrumented test support for Dynamic Features: Add assembleDebugAndroidTest tasks
            if (testCompileType == TestCompileType.ANDROID_TESTS) {
              for (IdeBaseArtifact artifact : testCompileType.getArtifacts(androidModel.getSelectedVariant())) {
                addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
              }
            }
          }
          break;
        default:
          addAfterSyncTasks(tasks, gradlePath, properties);
          if (androidModel != null) {
            addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);
            for (IdeBaseArtifact artifact : testCompileType.getArtifacts(androidModel.getSelectedVariant())) {
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
          tasks.add(GradleUtil.createFullTaskName(gradlePath, gradleTaskName));
        }
        if (TestCompileType.UNIT_TESTS.equals(testCompileType) || TestCompileType.ALL.equals(testCompileType)) {
          tasks.add(GradleUtil.createFullTaskName(gradlePath, JavaFacet.TEST_CLASSES_TASK_NAME));
        }
      }
    }
  }

  private static void addAssembleTasksForTargetVariants(@NotNull Set<String> tasks, @NotNull Module testOnlyModule) {
    AndroidModuleModel testAndroidModel = AndroidModuleModel.get(testOnlyModule);

    if (testAndroidModel == null ||
        !testAndroidModel.getFeatures().isTestedTargetVariantsSupported() ||
        testAndroidModel.getAndroidProject().getProjectType() != IdeAndroidProjectType.PROJECT_TYPE_TEST) {
      // If we don't have the target module and variant to be tested, no task should be added.
      return;
    }

    for (IdeTestedTargetVariant testedTargetVariant : testAndroidModel.getSelectedVariant().getTestedTargetVariants()) {
      String targetProjectGradlePath = testedTargetVariant.getTargetProjectPath();
      Module targetModule = findModuleByGradlePath(testOnlyModule.getProject(), targetProjectGradlePath);

      // Adds the assemble task for the tested variants
      if (targetModule != null) {
        AndroidModuleModel targetAndroidModel = AndroidModuleModel.get(targetModule);

        if (targetAndroidModel != null) {
          String targetVariantName = testedTargetVariant.getTargetVariant();
          IdeVariant targetVariant = targetAndroidModel.findVariantByName(targetVariantName);

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

  private static void addAfterSyncTasksForTestArtifacts(@NotNull Set<String> tasks,
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

  private static void addAfterSyncTasks(@NotNull Set<String> tasks,
                                        @NotNull String gradlePath,
                                        @NotNull AndroidFacetProperties properties) {
    // Make sure all the generated sources, unpacked aars and mockable jars are in place. They are usually up to date, since we
    // generate them at sync time, so Gradle will just skip those tasks. The generated files can be missing if this is a "Rebuild
    // Project" run or if the user cleaned the project from the command line. The mockable jar is necessary to run unit tests, but the
    // compilation tasks don't depend on it, so we have to call it explicitly.
    for (String taskName : properties.AFTER_SYNC_TASK_NAMES) {
      addTaskIfSpecified(tasks, gradlePath, taskName);
    }
  }

  private static void addTaskIfSpecified(@NotNull Set<String> tasks, @NotNull String gradlePath, @Nullable String gradleTaskName) {
    if (isNotEmpty(gradleTaskName)) {
      String buildTask = GradleUtil.createFullTaskName(gradlePath, gradleTaskName);
      tasks.add(buildTask);
    }
  }
}
