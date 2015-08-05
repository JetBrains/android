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
package com.android.tools.idea.editors.theme;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Helper class to implement ActionListeners that behave differently in dumb mode
 */
public abstract class DumbAwareActionListener implements ActionListener {
  protected final @NotNull Project myProject;

  public DumbAwareActionListener(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Action to perform in dumb mode
   */
  public abstract void dumbActionPerformed(ActionEvent e);

  /**
   * Action to perform in "smart" mode (when indices are available)
   */
  public abstract void smartActionPerformed(ActionEvent e);

  @Override
  public final void actionPerformed(ActionEvent e) {
    if (DumbService.isDumb(myProject)) {
      dumbActionPerformed(e);
    }
    else {
      smartActionPerformed(e);
    }
  }
}
