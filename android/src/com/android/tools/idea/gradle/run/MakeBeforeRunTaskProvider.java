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
package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.invoker.GradleInvoker.TestCompileType;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import icons.AndroidIcons;
import org.jetbrains.android.run.AndroidRunConfigurationBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides the "Gradle-aware Make" task for Run Configurations, which
 * <ul>
 *   <li>is only available in Android Studio</li>
 *   <li>delegates to the regular "Make" if the project is not an Android Gradle project</li>
 *   <li>otherwise, invokes Gradle directly, to build the project</li>
 * </ul>
 */
public class MakeBeforeRunTaskProvider extends BeforeRunTaskProvider<MakeBeforeRunTask> {
  @NotNull public static final Key<MakeBeforeRunTask> ID = Key.create("Android.Gradle.BeforeRunTask");

  private static final Logger LOG = Logger.getInstance(MakeBeforeRunTask.class);
  private static final String TASK_NAME = "Gradle-aware Make";

  @NotNull private final Project myProject;

  public MakeBeforeRunTaskProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(MakeBeforeRunTask task) {
    return AndroidIcons.Android;
  }

  @Override
  public String getName() {
    return TASK_NAME;
  }

  @Override
  public String getDescription(MakeBeforeRunTask task) {
    String goal = task.getGoal();
    return StringUtil.isEmpty(goal) ? TASK_NAME : "gradle " + goal;
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Nullable
  @Override
  public MakeBeforeRunTask createTask(RunConfiguration runConfiguration) {
    if (// Enable "Gradle-aware Make" only for android configurations...
        (runConfiguration instanceof AndroidRunConfigurationBase  ||
            // ...and JUnit configurations if unit-testing support is enabled.
            (GradleExperimentalSettings.getInstance().ENABLE_UNIT_TESTING_SUPPORT && runConfiguration instanceof JUnitConfiguration))) {
      return new MakeBeforeRunTask();
    } else {
      return null;
    }
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, MakeBeforeRunTask task) {
    GradleEditTaskDialog dialog = new GradleEditTaskDialog(myProject);
    dialog.setGoal(task.getGoal());
    dialog.setAvailableGoals(createAvailableTasks());
    if (!dialog.showAndGet()) {
      // since we allow tasks without any arguments (assumed to be equivalent to assembling the app),
      // we need a way to specify that a task is not valid. This is because of the current restriction
      // of this API, where the return value from configureTask is ignored.
      task.setInvalid();
      return false;
    }

    task.setGoal(dialog.getGoal());
    return true;
  }

  private List<String> createAvailableTasks() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> gradleTasks = Lists.newArrayList();
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      IdeaGradleProject gradleProject = facet.getGradleProject();
      if (gradleProject == null) {
        continue;
      }

      gradleTasks.addAll(gradleProject.getTaskNames());
    }

    return gradleTasks;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, MakeBeforeRunTask task) {
    return task.isValid();
  }

  @Override
  public boolean executeTask(final DataContext context,
                             final RunConfiguration configuration,
                             ExecutionEnvironment env,
                             final MakeBeforeRunTask task) {
    if (!Projects.isGradleProject(myProject)) {
      return regularMake(context, configuration, env);
    }

    final AtomicBoolean success = new AtomicBoolean();
    try {
      final Semaphore done = new Semaphore();
      done.down();

      final AtomicReference<String> errorMsgRef = new AtomicReference<String>();

      // If the model needs a sync, we need to sync "synchronously" before running.
      // See: https://code.google.com/p/android/issues/detail?id=70718
      GradleSyncState syncState = GradleSyncState.getInstance(myProject);
      if (syncState.isSyncNeeded() != ThreeState.NO) {
        GradleProjectImporter.getInstance().syncProjectSynchronously(myProject, false, new GradleSyncListener.Adapter() {
          @Override
          public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
            errorMsgRef.set(errorMessage);
          }
        });
      }

      String errorMsg = errorMsgRef.get();
      if (errorMsg != null) {
        // Sync failed. There is no point on continuing, because most likely the model is either not there, or has stale information,
        // including the path of the APK.
        LOG.info("Unable to launch '" + TASK_NAME + "' task. Project sync failed with message: " + errorMsg);
        return false;
      }

      if (myProject.isDisposed()) {
        done.up();
      }
      else {
        final Module[] modules;
        if (configuration instanceof ModuleBasedConfiguration) {
          // ModuleBasedConfiguration includes Android and JUnit run configurations.
          modules = ((ModuleBasedConfiguration)configuration).getModules();
        }
        else {
          modules = Projects.getModulesToBuildFromSelection(myProject, context);
        }
        final TestCompileType testCompileType = getTestCompileType(configuration);
        final String goal = task.getGoal();

        if (Projects.isDirectGradleInvocationEnabled(myProject)) {
          final GradleInvoker gradleInvoker = GradleInvoker.getInstance(myProject);
          final GradleInvoker.AfterGradleInvocationTask afterTask = new GradleInvoker.AfterGradleInvocationTask() {
            @Override
            public void execute(@NotNull GradleInvocationResult result) {
              success.set(result.isBuildSuccessful());
              gradleInvoker.removeAfterGradleInvocationTask(this);
              done.up();
            }
          };
          // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use SwingUtilities.invokeAndWait. I tried
          // using Application.invokeAndWait but it never worked. IDEA also uses SwingUtilities in this scenario (see CompileStepBeforeRun.)
          SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
              gradleInvoker.addAfterGradleInvocationTask(afterTask);
              if (StringUtil.isEmpty(goal)) {
                gradleInvoker.assemble(modules, testCompileType);
              }
              else {
                gradleInvoker.executeTasks(Lists.newArrayList(goal));
              }
            }
          });
        }
        else {
          ExternalSystemTaskExecutionSettings executionSettings = createExternalSystemTaskExecutionSettings(modules, goal, testCompileType);
          if (executionSettings == null) {
            done.up();
            return regularMake(context, configuration, env);
          }

          ExternalSystemUtil.runTask(
            executionSettings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID, new TaskCallback() {
              @Override
              public void onSuccess() {
                success.set(true);
                done.up();
              }

              @Override
              public void onFailure() {
                success.set(false);
                done.up();
              }
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC
          );
        }
        done.waitFor();
      }
    }
    catch (Throwable t) {
      LOG.info("Unable to launch '" + TASK_NAME + "' task", t);
      return false;
    }
    return success.get();
  }

  private boolean regularMake(DataContext context, RunConfiguration configuration, ExecutionEnvironment env) {
    CompileStepBeforeRun regularMake = new CompileStepBeforeRun(myProject);
    return regularMake.executeTask(context, configuration, env, new CompileStepBeforeRun.MakeBeforeRunTask());
  }

  @NotNull
  private static TestCompileType getTestCompileType(@Nullable RunConfiguration runConfiguration) {
    String id = runConfiguration != null ? runConfiguration.getType().getId() : null;
    return GradleInvoker.getTestCompileType(id);
  }

  @Nullable
  private static ExternalSystemTaskExecutionSettings createExternalSystemTaskExecutionSettings(@NotNull Module[] modules,
                                                                                               @Nullable String goal,
                                                                                               @NotNull TestCompileType testCompileType) {
    final Set<String> gradleProjectRootPaths = ContainerUtil.map2SetNotNull(ContainerUtil.newArrayList(modules), new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return ExternalSystemApiUtil.getExternalRootProjectPath(module);
      }
    });

    if (gradleProjectRootPaths.isEmpty()) return null;
    if (gradleProjectRootPaths.size() > 1) {
      LOG.warn("Modules from different linked gradle projects found");
      return null;
    }

    final String rootProjectPath = ContainerUtil.getFirstItem(gradleProjectRootPaths);
    ExternalSystemTaskExecutionSettings executionSettings = new ExternalSystemTaskExecutionSettings();
    executionSettings.setExternalProjectPath(rootProjectPath);
    executionSettings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
    if (StringUtil.isEmpty(goal)) {
      List<String> tasks = GradleInvoker.findTasksToExecute(modules, BuildMode.ASSEMBLE, testCompileType);
      executionSettings.getTaskNames().addAll(tasks);
    }
    else {
      executionSettings.getTaskNames().add(goal);
    }
    return executionSettings;
  }
}
