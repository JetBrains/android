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
package com.android.tools.idea.avdmanager;

import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.NONE;

/**
 * This {@link EditorNotificationPanel} will display problems described by a {@link AccelerationErrorCode}.
 * Some of these problems have an action the user can invoke to fix the problem, and other errors just
 * have text for the solution (solution == NONE).<br/>
 * We show the {@link AccelerationErrorCode#getSolutionMessage} as tooltip if there is an action for
 * fixing the problem, and as a popup dialog if (solution ==NONE).
 */
public class AccelerationErrorNotificationPanel extends EditorNotificationPanel {

  public AccelerationErrorNotificationPanel(@NotNull AccelerationErrorCode error, @NotNull Project project, @Nullable Runnable refresh) {
    setText(error.getProblem());
    Runnable action = AccelerationErrorSolution.getActionForFix(error, project, refresh, null);
    HyperlinkLabel link = createActionLabel(error.getSolution().getDescription(), action);
    link.setToolTipText(error.getSolution() != NONE ? error.getSolutionMessage() : null);
  }
}
