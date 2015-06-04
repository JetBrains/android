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

import com.android.tools.idea.editors.theme.ThemeEditor;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.editors.theme.ThemeEditorTable;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.google.common.collect.ImmutableList;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComboBox;
import java.util.List;

public class ThemeEditorFixture extends ComponentFixture<ThemeEditorFixture, ThemeEditorComponent> {
  public ThemeEditorFixture(@NotNull Robot robot, @NotNull ThemeEditor themeEditor) {
    super(ThemeEditorFixture.class, robot, (ThemeEditorComponent)themeEditor.getComponent());
  }

  @NotNull
  public JComboBoxFixture getThemesComboBox() {
    return new JComboBoxFixture(robot(), robot().finder().findByType(this.target().getSecondComponent(), JComboBox.class));
  }

  @NotNull
  public JTableFixture getPropertiesTable() {
    return new JTableFixture(robot(), robot().finder().findByType(this.target().getSecondComponent(), ThemeEditorTable.class));
  }

  @NotNull
  public AndroidThemePreviewPanelFixture getThemePreviewPanel() {
    return new AndroidThemePreviewPanelFixture(robot(), robot().finder()
      .findByType(this.target().getFirstComponent(), AndroidThemePreviewPanel.class));
  }

  @NotNull
  public List<String> getThemesList() {
    JComboBoxFixture themesCombo = getThemesComboBox();

    return ImmutableList.copyOf(themesCombo.contents());
  }

  @NotNull
  public JTableFixture getThemeEditorTable() {
    return new JTableFixture(robot(), robot().finder().findByType(this.target().getSecondComponent(), ThemeEditorTable.class));
  }
}
