/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure;

import com.intellij.openapi.roots.ui.configuration.ConfigurationError;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProjectConfigurationError extends ConfigurationError {
  @Nullable private final JComponent myNavigationTarget;

  private Runnable myNavigationTask;
  private Runnable myQuickFix;

  public ProjectConfigurationError(@NotNull String description, @Nullable JComponent navigationTarget) {
    super(description, description);
    myNavigationTarget = navigationTarget;
  }

  public ProjectConfigurationError(@NotNull String description, @Nullable JComponent navigationTarget, boolean ignored) {
    super(description, description, ignored);
    myNavigationTarget = navigationTarget;
  }

  public void setNavigationTask(@NotNull Runnable navigationTask) {
    myNavigationTask = navigationTask;
  }

  @Override
  public void navigate() {
    if (myNavigationTask != null) {
      myNavigationTask.run();
      if (myNavigationTarget != null) {
        myNavigationTarget.requestFocusInWindow();
      }
    }
  }

  public void setQuickFix(@NotNull Runnable quickFix) {
    myQuickFix = quickFix;
  }

  @Override
  public boolean canBeFixed() {
    return myQuickFix != null;
  }

  @Override
  public void fix(JComponent contextComponent, RelativePoint relativePoint) {
    if (canBeFixed()) {
      myQuickFix.run();
    }
  }
}
