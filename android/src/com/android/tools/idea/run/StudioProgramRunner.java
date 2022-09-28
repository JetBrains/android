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
package com.android.tools.idea.run;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentDescriptorReusePolicy;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.util.ThreeState;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base {@link com.intellij.execution.runners.ProgramRunner} for all Android Studio (not ASWB) program runners.
 * It provides the necessary support and management for working with hot swap (Apply (Code) Changes).
 */
public abstract class StudioProgramRunner extends AndroidProgramRunner {
  @NotNull
  private final Function<Project, GradleSyncState> mySyncStateGetter;

  public StudioProgramRunner() {
    mySyncStateGetter = project -> GradleSyncState.getInstance(project);
  }

  // @VisibleForTesting
  StudioProgramRunner(@NotNull Function<Project, GradleSyncState> syncStateGetter,
                      @NotNull BiFunction<@NotNull Project, @NotNull RunConfiguration, @NotNull AndroidExecutionTarget> executionTargetGetter) {
    super(executionTargetGetter);
    mySyncStateGetter = syncStateGetter;
  }

  @Override
  public boolean canRun(@NotNull String executorId,
                        @NotNull RunProfile profile) {
    if (!super.canRun(executorId, profile) || !(profile instanceof AndroidRunConfigurationBase)) {
      return false;
    }
    AndroidRunConfigurationBase config = (AndroidRunConfigurationBase)profile;
    if (config.canRunWithoutSync()) {
      return true;
    }
    if (config instanceof AndroidRunConfiguration && AndroidConfigurationProgramRunner.Companion.getUseNewExecutionForActivities()) {
      // In this case [AndroidConfigurationProgramRunner] is going to be used.
      return false;
    }
    GradleSyncState syncState = mySyncStateGetter.apply(config.getProject());
    return !syncState.isSyncInProgress() && syncState.isSyncNeeded().equals(ThreeState.NO);
  }

  @Override
  @Nullable
  protected RunContentDescriptor doExecute(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment env)
    throws ExecutionException {

    Project project = env.getProject();
    Executor executor = env.getExecutor();
    String executorId = executor.getId();

    boolean isTestConfig = env.getRunProfile() instanceof AndroidTestRunConfiguration;
    RunnerAndConfigurationSettings settings = env.getRunnerAndConfigurationSettings();

    SwapInfo swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY);

    if (settings != null && swapInfo == null) {
      settings.setActivateToolWindowBeforeRun(isTestConfig || settings.isActivateToolWindowBeforeRun());
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    ExecutionResult result = state.execute(executor, this);

    RunContentDescriptor descriptor = null;
    if (swapInfo != null && result != null) {
      // If we're hotswapping, we want to use the currently-running ContentDescriptor,
      // instead of making a new one (which "show"RunContent actually does).
      RunContentManager manager = RunContentManager.getInstance(project);
      // Note we may still end up with a null descriptor since the user could close the tool tab after starting a hotswap.
      descriptor = manager.findContentDescriptor(executor, result.getProcessHandler());
    }

    if (descriptor == null || descriptor.getAttachedContent() == null) {
      descriptor = DefaultProgramRunnerKt.showRunContent(result, env);
    }
    else if (!(descriptor instanceof HiddenRunContentDescriptor)) {
      // Since we got to this branch, it implies we're swapping for the first time. In this case,
      // create a wrapper descriptor so that the ExecutionManager doesn't try to "show" it
      // (which actually creates a new descriptor and wipes out the old one).
      Content content = descriptor.getAttachedContent();
      descriptor = new HiddenRunContentDescriptor(descriptor);
      content.putUserData(RunContentDescriptor.DESCRIPTOR_KEY, descriptor);
    }

    if (descriptor != null) {
      if (swapInfo != null) {
        // Don't show the tool window when we're applying (code) changes.
        descriptor.setActivateToolWindowWhenAdded(false);
      }

      ProcessHandler processHandler = descriptor.getProcessHandler();
      assert processHandler != null;

      RunProfile runProfile = env.getRunProfile();
      RunConfiguration runConfiguration = runProfile instanceof RunConfiguration ? (RunConfiguration)runProfile : null;
      AndroidSessionInfo.create(processHandler, runConfiguration, executorId, env.getExecutionTarget());
    }

    return descriptor;
  }


  @VisibleForTesting
  static class HiddenRunContentDescriptor extends RunContentDescriptor {
    @NotNull
    private final RunContentDescriptor myDelegate;

    private HiddenRunContentDescriptor(@NotNull RunContentDescriptor delegate) {
      super(null, null, new JLabel(), "hidden", null, null, null);
      myDelegate = delegate;
      Disposer.register(this, myDelegate);
    }

    @Override
    public Runnable getActivationCallback() {
      return myDelegate.getActivationCallback();
    }

    @NotNull
    @Override
    public AnAction[] getRestartActions() {
      return myDelegate.getRestartActions();
    }

    @Override
    public ExecutionConsole getExecutionConsole() {
      return myDelegate.getExecutionConsole();
    }

    @Override
    public void dispose() {
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myDelegate.getIcon();
    }

    @Nullable
    @Override
    public ProcessHandler getProcessHandler() {
      return myDelegate.getProcessHandler();
    }

    @Override
    public void setProcessHandler(ProcessHandler processHandler) {
      myDelegate.setProcessHandler(processHandler);
    }

    @Override
    public boolean isContentReuseProhibited() {
      return myDelegate.isContentReuseProhibited();
    }

    @Override
    public JComponent getComponent() {
      return myDelegate.getComponent();
    }

    @Override
    public String getDisplayName() {
      return myDelegate.getDisplayName();
    }

    @Override
    public String getHelpId() {
      return myDelegate.getHelpId();
    }

    @Nullable
    @Override
    public Content getAttachedContent() {
      return myDelegate.getAttachedContent();
    }

    @Override
    public void setAttachedContent(@NotNull Content content) {
      myDelegate.setAttachedContent(content);
    }

    @Nullable
    @Override
    public String getContentToolWindowId() {
      return myDelegate.getContentToolWindowId();
    }

    @Override
    public void setContentToolWindowId(@Nullable String contentToolWindowId) {
      myDelegate.setContentToolWindowId(contentToolWindowId);
    }

    @Override
    public boolean isActivateToolWindowWhenAdded() {
      return myDelegate.isActivateToolWindowWhenAdded();
    }

    @Override
    public void setActivateToolWindowWhenAdded(boolean activateToolWindowWhenAdded) {
      myDelegate.setActivateToolWindowWhenAdded(activateToolWindowWhenAdded);
    }

    @Override
    public boolean isSelectContentWhenAdded() {
      return myDelegate.isSelectContentWhenAdded();
    }

    @Override
    public void setSelectContentWhenAdded(boolean selectContentWhenAdded) {
      myDelegate.setSelectContentWhenAdded(selectContentWhenAdded);
    }

    @Override
    public boolean isReuseToolWindowActivation() {
      return myDelegate.isReuseToolWindowActivation();
    }

    @Override
    public void setReuseToolWindowActivation(boolean reuseToolWindowActivation) {
      myDelegate.setReuseToolWindowActivation(reuseToolWindowActivation);
    }

    @Override
    public long getExecutionId() {
      return myDelegate.getExecutionId();
    }

    @Override
    public void setExecutionId(long executionId) {
      myDelegate.setExecutionId(executionId);
    }

    @Override
    public String toString() {
      return myDelegate.toString();
    }

    @Override
    public Computable<JComponent> getPreferredFocusComputable() {
      return myDelegate.getPreferredFocusComputable();
    }

    @Override
    public void setFocusComputable(Computable<JComponent> focusComputable) {
      myDelegate.setFocusComputable(focusComputable);
    }

    @Override
    public boolean isAutoFocusContent() {
      return myDelegate.isAutoFocusContent();
    }

    @Override
    public void setAutoFocusContent(boolean autoFocusContent) {
      myDelegate.setAutoFocusContent(autoFocusContent);
    }

    @Nullable
    @Override
    public RunnerLayoutUi getRunnerLayoutUi() {
      return myDelegate.getRunnerLayoutUi();
    }

    @Override
    public void setRunnerLayoutUi(@Nullable RunnerLayoutUi runnerLayoutUi) {
      myDelegate.setRunnerLayoutUi(runnerLayoutUi);
    }

    @Override
    public boolean isHiddenContent() {
      return true;
    }

    @NotNull
    @Override
    public RunContentDescriptorReusePolicy getReusePolicy() {
      return myDelegate.getReusePolicy();
    }

    @Override
    public void setReusePolicy(@NotNull RunContentDescriptorReusePolicy reusePolicy) {
      myDelegate.setReusePolicy(reusePolicy);
    }
  }
}
