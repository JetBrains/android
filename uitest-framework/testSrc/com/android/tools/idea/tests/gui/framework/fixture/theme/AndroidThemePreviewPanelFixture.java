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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Fixture wrapping the {@link AndroidThemePreviewPanel}
 */
public class AndroidThemePreviewPanelFixture extends ComponentFixture<AndroidThemePreviewPanelFixture, AndroidThemePreviewPanel> {
  public AndroidThemePreviewPanelFixture(@NotNull Robot robot, @NotNull AndroidThemePreviewPanel target) {
    super(AndroidThemePreviewPanelFixture.class, robot, target);
  }

  @NotNull
  public AndroidPreviewPanelFixture getPreviewPanel() {
    return new AndroidPreviewPanelFixture(robot(), robot().finder().findByType(target(), AndroidPreviewPanel.class));
  }

  /**
   * Checks if the preview panel in the theme editor actually shows a theme preview.
   * Throws an {@link AssertionError} if it does not.
   */
  public void requirePreviewPanel() {
    try {
      robot().finder().findByType(target(), AndroidPreviewPanel.class);
    }
    catch (ComponentLookupException e) {
      throw new AssertionError("The theme editor preview is not showing");
    }
  }

  /**
   * Checks if the preview panel in the theme editor displays an error message.
   * Throws an {@link AssertionError} if it does not.
   */
  public void requireErrorPanel() {
    try {
      robot().finder().findByType(target(), JTextPane.class);
    }
    catch (ComponentLookupException e) {
      throw new AssertionError("The theme editor preview is not displaying an error message");
    }
  }
}
