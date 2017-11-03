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

import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.preview.ThemePreviewComponent;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;

import static org.junit.Assert.assertEquals;

public class ThemePreviewComponentFixture extends JPanelFixture {
  public ThemePreviewComponentFixture(@NotNull Robot robot,
                                      @NotNull ThemePreviewComponent target) {
    super(robot, target);
  }

  @NotNull
  public AndroidThemePreviewPanelFixture getThemePreviewPanel() {
    return new AndroidThemePreviewPanelFixture(robot(), robot().finder()
      .findByType(target(), AndroidThemePreviewPanel.class));
  }

  @NotNull
  public ThemePreviewComponentFixture requireApi(int apiLevel) {
    assertEquals(apiLevel, getThemePreviewPanel().target().getConfiguration().getTarget().getVersion().getApiLevel());
    return this;
  }
}
