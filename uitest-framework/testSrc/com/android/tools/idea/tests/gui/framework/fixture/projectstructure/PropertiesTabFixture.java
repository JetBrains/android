/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PropertiesTabFixture  extends ProjectStructureDialogFixture {

  PropertiesTabFixture(JDialog dialog, IdeFrameFixture ideFrameFixture) {
    super(dialog, ideFrameFixture);
  }

  @NotNull
  public PropertiesTabFixture setCompileSdkVersion(@NotNull String value) {
    new JComboBoxFixture(
      robot(), robot().finder().findByLabel(target(), "Compile Sdk Version", JComboBox.class, true)).selectItem(value);
    return this;
  }

  @NotNull
  public PropertiesTabFixture setBuildToolsVersion(@NotNull String value) {
    new JComboBoxFixture(
      robot(), robot().finder().findByLabel(target(), "Build Tools Version", JComboBox.class, true)).selectItem(value);
    return this;
  }

  @NotNull
  public PropertiesTabFixture setLibraryRepository(@NotNull String value) {
    setTextField("Library Repository", value);
    return this;
  }

  @NotNull
  public PropertiesTabFixture setIgnoreAssetsPattern(@NotNull String value) {
    setTextField("Ignore Assets Pattern", value);
    return this;
  }

  @NotNull
  public PropertiesTabFixture setIncrementalDex(boolean value) {
    String stringValue = value ? "true" : "false";
    new JComboBoxFixture(
      robot(), robot().finder().findByLabel(target(), "Incremental Dex", JComboBox.class, true)).selectItem(stringValue);
    return this;
  }

  @NotNull
  public PropertiesTabFixture setSourceCompatibility(@NotNull String value) {
    new JComboBoxFixture(
      robot(), robot().finder().findByLabel(target(), "Source Compatibility", JComboBox.class, true)).selectItem(value);
    return this;
  }

  @NotNull
  public PropertiesTabFixture setTargetCompatibility(@NotNull String value) {
    new JComboBoxFixture(
      robot(), robot().finder().findByLabel(target(), "Target Compatibility", JComboBox.class, true)).selectItem(value);
    return this;
  }

  private void setTextField(@NotNull String label, @NotNull String text) {
    JTextField textField = robot().finder().findByLabel(target(), label, JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(text);
  }
}