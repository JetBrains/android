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

import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.swing.ui.ClickableLabel;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.junit.Assert.assertNotNull;

public class ResourceComponentFixture extends JPanelFixture {

  public ResourceComponentFixture(@NotNull Robot robot, @NotNull ResourceComponent target) {
    super(robot, target);
  }

  @NotNull
  private JLabelFixture getLabel() {
    return new JLabelFixture(robot(), robot().finder().findByName(target(), ResourceComponent.NAME_LABEL, JLabel.class));
  }

  @NotNull
  public JButtonFixture getSwatchButton() {
    return new JButtonFixture(robot(), robot().finder().findByType(target(), ClickableLabel.class));
  }

  @NotNull
  public EditorTextFieldFixture getTextField() {
    return EditorTextFieldFixture.find(robot(), target());
  }

  @NotNull
  private SwatchComponentFixture getValueComponent() {
    return SwatchComponentFixture.find(robot(), target());
  }

  @NotNull
  public String getLabelText() {
    String labelValue = getLabel().text();
    assertNotNull(labelValue);
    return labelValue;
  }

  @Nullable
  public String getValueString() {
    return getValueComponent().getText();
  }

  public boolean hasWarningIcon() {
    return getValueComponent().hasWarningIcon();
  }
}
