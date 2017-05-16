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
package com.android.tools.idea.tests.gui.framework.fixture.projectstructure;

import com.android.tools.idea.gradle.structure.editors.NamedObjectPanel;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.ui.components.JBList;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BuildTypesTabFixture extends ProjectStructureDialogFixture {
  BuildTypesTabFixture(@NotNull JDialog dialog, @NotNull IdeFrameFixture ideFrameFixture) {
    super(dialog, ideFrameFixture);
  }

  @NotNull
  public BuildTypesTabFixture setName(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "Name:", JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(name);
    return this;
  }

  @NotNull
  public BuildTypesTabFixture setDebuggable(@NotNull String debuggable) {
    JComboBox comboBox = robot().finder().findByLabel(target(), "Debuggable", JComboBox.class, true);
    new JComboBoxFixture(robot(), comboBox).selectItem(debuggable);
    return this;
  }

  @NotNull
  public BuildTypesTabFixture setVersionNameSuffix(@NotNull String versionNameSuffix) {
    JTextField textField = robot().finder().findByLabel(target(), "Version Name Suffix", JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(versionNameSuffix);
    return this;
  }


  @NotNull
  public BuildTypesTabFixture selectBuildType(@NotNull String item) {
    // Find a JBList inside a NamedObjectPanel, so we get the list that's inside the tab-area.
    NamedObjectPanel namedObjectPanel = robot().finder().findByType(target(), NamedObjectPanel.class, true);

    JBList list = robot().finder().findByType(namedObjectPanel, JBList.class);
    JListFixture jListFixture = new JListFixture(robot(), list);
    jListFixture.clickItem(item);
    return this;
  }
}
