/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.wizard.dynamic;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* DynamicWizardHost for displaying a single stepped wizard in a dialog.
 * Only includes ok/cancel buttons, not next/previous.
*/
public class SingleStepDialogWrapperHost extends DialogWrapperHost {
  public SingleStepDialogWrapperHost(@Nullable Project project) {
    super(project);
  }

  public SingleStepDialogWrapperHost(@Nullable Project project, IdeModalityType modalityType) {
    super(project, modalityType);
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    myFinishAction.putValue(Action.NAME, IdeBundle.message("button.ok"));
    return new Action[] {myCancelAction, myFinishAction};
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getFinishButton();
  }
}
