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

public class FlavorsTabFixture extends ProjectStructureDialogFixture {

  FlavorsTabFixture(JDialog dialog, IdeFrameFixture ideFrameFixture) {
    super(dialog, ideFrameFixture);
  }

  public FlavorsTabFixture clickAddButton() {
    clickAddButtonImpl();
    return this;
  }

  public FlavorsTabFixture setFlavorName(final String name) {
    setTextField("Name:", name);
    return this;
  }

  public FlavorsTabFixture setVersionName(String versionName) {
    setTextField("Version Name", versionName);
    return this;
  }

  public FlavorsTabFixture setVersionCode(String versionCode) {
    setTextField("Version Code", versionCode);
    return this;
  }

  public FlavorsTabFixture setMinSdkVersion(String sdk) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Min Sdk Version", JComboBox.class, true)).selectItem(sdk);
    return this;
  }

  public FlavorsTabFixture setTargetSdkVersion(String sdk) {
    new JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Target Sdk Version", JComboBox.class, true)).selectItem(sdk);
    return this;
  }

  @NotNull
  public FlavorsTabFixture selectFlavor(@NotNull String item) {
    // Find a JBList inside a NamedObjectPanel, so we get the list that's inside the tab-area.
    NamedObjectPanel namedObjectPanel = robot().finder().findByType(target(), NamedObjectPanel.class, true);

    JBList list = robot().finder().findByType(namedObjectPanel, JBList.class);
    JListFixture jListFixture = new JListFixture(robot(), list);
    jListFixture.clickItem(item);
    return this;
  }

  private void setTextField(String label, String text) {
    JTextField textField = robot().finder().findByLabel(target(), label, JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(text);
  }
}