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

import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.google.common.collect.Lists;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Semaphore;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
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
  public static final String TASK_NAME = "Gradle-aware Make";

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
    // "Gradle-aware Make" is only available in Android Studio.
    if (AndroidStudioInitializer.isAndroidStudio() && configurationTypeIsSupported(runConfiguration)) {
      MakeBeforeRunTask task = new MakeBeforeRunTask();
      if (runConfiguration instanceof AndroidRunConfigurationBase) {
        // For Android configurations, we want to replace the default make, so this new task needs to be enabled.
        // In AndroidRunConfigurationType#configureBeforeTaskDefaults we disable the default make, which is
        // enabled by default. For other configurations we leave it disabled, so we don't end up with two different
        // make steps executed by default. If the task is added to the run configuration manually, it will be
        // enabled by the UI layer later.
        task.setEnabled(true);
      }
      return task;
    } else {
      return null;
    }
  }

  private static boolean configurationTypeIsSupported(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof AndroidRunConfigurationBase || isUnitTestConfiguration(runConfiguration);
  }

  private static boolean isUnitTestConfiguration(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof JUnitConfiguration ||
           // Avoid direct dependency on the TestNG plugin:
           runConfiguration.getClass().getSimpleName().equals("TestNGConfiguration");
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

      GradleModel gradleModel = facet.getGradleModel();
      if (gradleModel == null) {
        continue;
      }

      gradleTasks.addAll(gradleModel.getTaskNames());
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
    if (!Projects.requiresAndroidModel(myProject) || !Projects.isDirectGradleInvocationEnabled(myProject)) {
      CompileStepBeforeRun regularMake = new CompileStepBeforeRun(myProject);
      return regularMake.executeTask(context, configuration, env, new CompileStepBeforeRun.MakeBeforeRunTask());
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
        return false;
      }

      final GradleInvoker gradleInvoker = GradleInvoker.getInstance(myProject);

      final GradleInvoker.AfterGradleInvocationTask afterTask = new GradleInvoker.AfterGradleInvocationTask() {
        @Override
        public void execute(@NotNull GradleInvocationResult result) {
          success.set(result.isBuildSuccessful());
          gradleInvoker.removeAfterGradleInvocationTask(this);
          done.up();
        }
      };

      final GradleInvokerOptions options = GradleInvokerOptions.create(myProject, context, configuration, env, task.getGoal());
      if (options.tasks.isEmpty()) {
        // should not happen, but if it does happen, then GradleInvoker with an empty list of tasks seems to hang forever
        // So we error out earlier.
        LOG.error("Unable to determine gradle tasks to execute for run configuration: " + configuration.getName());
        return false;
      }

      // To ensure that the "Run Configuration" waits for the Gradle tasks to be executed, we use SwingUtilities.invokeAndWait. I tried
      // using Application.invokeAndWait but it never worked. IDEA also uses SwingUtilities in this scenario (see CompileStepBeforeRun.)
      SwingUtilities.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          gradleInvoker.addAfterGradleInvocationTask(afterTask);
          gradleInvoker.executeTasks(options.tasks, options.buildMode, options.commandLineArguments);
        }
      });

      done.waitFor();
    }
    catch (Throwable t) {
      LOG.info("Unable to launch '" + TASK_NAME + "' task", t);
      return false;
    }
    LOG.info("Gradle invocation complete, success = " + success.get());
    return success.get();
  }
}
