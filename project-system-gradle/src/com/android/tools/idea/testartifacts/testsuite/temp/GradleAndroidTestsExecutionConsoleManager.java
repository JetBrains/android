/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite.temp;

import static com.android.tools.idea.flags.StudioFlags.ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS;

import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView;
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension;
import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.StartEvent;
import com.intellij.build.events.impl.ProgressBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationViewManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.filters.ReRunTaskFilter;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * This class is copied and modified from
 * {@link org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsoleManager}
 * to replace GradleTestsExecutionConsole with AndroidTestSuiteView.
 * <a href="https://youtrack.jetbrains.com/issue/IDEA-368796">IDEA-368796</a>
 */
public final class GradleAndroidTestsExecutionConsoleManager
  implements ExternalSystemExecutionConsoleManager<AndroidTestSuiteView, ProcessHandler> {

  @Override
  public @NotNull ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  public @Nullable AndroidTestSuiteView attachExecutionConsole(@NotNull Project project,
                                                               @NotNull ExternalSystemTask task,
                                                               @Nullable ExecutionEnvironment env,
                                                               @Nullable ProcessHandler processHandler) {
    if (env == null) return null;
    RunConfiguration configuration;
    RunnerAndConfigurationSettings settings = env.getRunnerAndConfigurationSettings();
    if (settings == null) {
      RunProfile runProfile = env.getRunProfile();
      if (runProfile instanceof AbstractImportTestsAction.ImportRunProfile) {
        configuration = ((AbstractImportTestsAction.ImportRunProfile)runProfile).getInitialConfiguration();
      }
      else {
        return null;
      }
    }
    else {
      configuration = settings.getConfiguration();
    }
    if (!(configuration instanceof ExternalSystemRunConfiguration)) return null;

    AndroidTestSuiteView consoleView = new AndroidTestSuiteView(project, project, null);

    if (task instanceof ExternalSystemExecuteTaskTask) {
      consoleView.addMessageFilter(new ReRunTaskFilter((ExternalSystemExecuteTaskTask)task, env));
    }

    Disposable disposable = Disposer.newDisposable(consoleView, "Gradle test runner build event listener disposable");
    BuildViewManager buildViewManager = project.getService(BuildViewManager.class);
    project.getService(ExternalSystemRunConfigurationViewManager.class).addListener(new BuildProgressListener() {
      @Override
      public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        if (buildId != task.getId()) return;

        if (event instanceof FinishBuildEvent) {
          Disposer.dispose(disposable);
        }
        else if (event instanceof StartBuildEvent) {
          // override start build event to use different execution console, toolbar actions etc.
          BuildDescriptor buildDescriptor = ((StartBuildEvent)event).getBuildDescriptor();
          DefaultBuildDescriptor defaultBuildDescriptor =
            new DefaultBuildDescriptor(buildDescriptor.getId(), buildDescriptor.getTitle(),
                                       buildDescriptor.getWorkingDir(), buildDescriptor.getStartTime());

          // do not open Build tw for any error messages as it can be tests failure events
          defaultBuildDescriptor.setActivateToolWindowWhenFailed(false);
          event = new StartBuildEventImpl(defaultBuildDescriptor, event.getMessage());
        }
        buildViewManager.onEvent(buildId, event);

        if (event instanceof StartEvent) {
          ProgressBuildEventImpl progressBuildEvent =
            new ProgressBuildEventImpl(event.getId(), event.getParentId(), event.getEventTime(), event.getMessage(), -1, -1, "");
          progressBuildEvent.setHint("- " + GradleBundle.message("gradle.test.runner.build.tw.link.title"));
          buildViewManager.onEvent(buildId, progressBuildEvent);
        }
      }
    }, disposable);
    return consoleView;
  }

  @Override
  public void onOutput(@NotNull AndroidTestSuiteView executionConsole,
                       @NotNull ProcessHandler processHandler,
                       @NotNull String text,
                       @NotNull Key processOutputType) {
    GradleAndroidTestsExecutionConsoleOutputProcessor.onOutput(executionConsole, text, processOutputType);
  }

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    if (!ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.get()) {
      return false;
    }

    if (task instanceof ExternalSystemExecuteTaskTask taskTask) {
      if (StringUtil.equals(taskTask.getExternalSystemId().getId(), GradleConstants.SYSTEM_ID.getId())) {
        var showResultInAndroidTestSuiteView = taskTask.getUserData(
          GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.getUserDataKey());
        return ObjectUtils.chooseNotNull(showResultInAndroidTestSuiteView, false);
      }
    }

    return false;
  }

  @Override
  public AnAction[] getRestartActions(final @NotNull AndroidTestSuiteView consoleView) {
    // RerunFailedTests action is not supported by AndroidTestSuiteView yet.
    return AnAction.EMPTY_ARRAY;
  }
}