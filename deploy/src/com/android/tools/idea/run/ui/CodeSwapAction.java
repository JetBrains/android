/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.ui;

import static icons.StudioIcons.Shell.Toolbar.APPLY_CODE_SWAP;

import com.android.tools.idea.run.util.SwapInfo;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.annotations.NotNull;

public class CodeSwapAction extends BaseAction {

  public static final String ID = "android.deploy.CodeSwap";

  public static final String DISPLAY_NAME = "Apply Code Changes";

  // The '&' is IJ markup to indicate the subsequent letter is the accelerator key.
  public static final String ACCELERATOR_NAME = "Apply Cod&e Changes";

  private static final String DESC = "Attempt to apply only code changes without restarting anything.";

  public CodeSwapAction() {
    super(DISPLAY_NAME, ACCELERATOR_NAME, SwapInfo.SwapType.APPLY_CODE_CHANGES, APPLY_CODE_SWAP, DESC);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    if (project == null) {
      return;
    }

    // Disable the button for any test project that is not an Instrumented Test.
    RunManager runManager = RunManager.getInstanceIfCreated(project);
    if (runManager == null) {
      return;
    }

    RunnerAndConfigurationSettings runConfig = runManager.getSelectedConfiguration();
    if (runConfig != null) {
      ConfigurationType type = runConfig.getType();
      String id = type.getId();
      if (AndroidBuildCommonUtils.isTestConfiguration(id)) {
        disableAction(e.getPresentation(), new DisableMessage(DisableMessage.DisableMode.DISABLED, "test project",
                                                              "the selected configuration is a test configuration"));
      }
    }
  }
}

