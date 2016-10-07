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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.annotations.Nullable;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.google.common.truth.Truth;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.assertions.Assertions;
import org.fest.swing.core.*;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.google.common.truth.Truth.assertThat;

/**
 * Fixture wrapping the component inspector
 */
public class NlPropertyInspectorFixture extends ComponentFixture<NlPropertyInspectorFixture, Component> {
  private final NlPropertiesPanel myPanel;

  public NlPropertyInspectorFixture(@NotNull Robot robot, @NotNull IdeFrameFixture frame, @NotNull NlPropertiesPanel panel) {
    super(NlPropertyInspectorFixture.class, robot, panel);
    myPanel = panel;
  }

  public static NlPropertiesPanel create(@NotNull Robot robot) {
    return waitUntilFound(robot, null, new GenericTypeMatcher<NlPropertiesPanel>(NlPropertiesPanel.class) {
      @Override
      protected boolean isMatching(@NotNull NlPropertiesPanel list) {
        return true;
      }
    });
  }

  @Nullable
  public NlPropertyFixture findProperty(final String name) {
    JBLabel label = waitUntilFound(robot(), myPanel, new GenericTypeMatcher<JBLabel>(JBLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JBLabel label) {
        return name.equals(label.getText()) && label.getIcon() == null;
      }
    });

    Container parent = label.getParent();
    Component[] components = parent.getComponents();
    for (int i = 0; i < components.length; i++) {
      if (label == components[i]) {
        return new NlPropertyFixture(robot(), (JPanel)components[i + 1]);
      }
    }
    return null;
  }
}