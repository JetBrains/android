/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.editors.theme.preview.ThemePreviewComponent;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.intellij.openapi.project.Project;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ThemePreviewFixture extends ToolWindowFixture {
  public ThemePreviewFixture(@NotNull Robot robot, @NotNull Project project) {
    super("Theme Preview", project, robot);
  }

  /**
   * Returns the root component for the preview tab
   */
  @NotNull
  private JComponent getRootComponent() {
    return myToolWindow.getContentManager().getSelectedContent().getComponent();
  }

  @NotNull
  public ThemePreviewComponentFixture getPreviewComponent() {
    return new ThemePreviewComponentFixture(myRobot, myRobot.finder()
      .findByType(getRootComponent(), ThemePreviewComponent.class));
  }
}
