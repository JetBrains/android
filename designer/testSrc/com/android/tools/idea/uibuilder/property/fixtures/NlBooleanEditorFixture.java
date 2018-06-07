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
package com.android.tools.idea.uibuilder.property.fixtures;

import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.fixtures.EditorFixtureBase;
import com.android.tools.idea.uibuilder.property.editors.NlBooleanEditor;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;

public class NlBooleanEditorFixture extends EditorFixtureBase {
  private final NlBooleanEditor myComponentEditor;
  private final ThreeStateCheckBox myCheckBox;

  protected NlBooleanEditorFixture(@NotNull NlBooleanEditor editor) {
    super(editor);
    myComponentEditor = editor;
    myCheckBox = (ThreeStateCheckBox)editor.getComponent().getComponent(0);
  }

  public static NlBooleanEditorFixture createForInspector() {
    NlBooleanEditor editor = NlBooleanEditor.createForInspector(createListener());
    return new NlBooleanEditorFixture(editor);
  }

  public NlBooleanEditorFixture setProperty(@NotNull NlProperty property) {
    myComponentEditor.setProperty(property);
    myComponentEditor.getComponent().doLayout();
    return this;
  }

  public NlBooleanEditorFixture expectValue(@Nullable Boolean expectedValue) {
    String expectedString = expectedValue != null ? expectedValue.toString() : null;
    assertThat(myComponentEditor.getProperty().getValue()).isEqualTo(expectedString);
    return this;
  }

  public NlBooleanEditorFixture expectCheckboxTipText(@NotNull String expectedTipText) {
    String actual = myCheckBox.getToolTipText();
    assertEquals(expectedTipText, actual);
    return this;
  }

  public NlBooleanEditorFixture click() {
    myCheckBox.doClick();
    return this;
  }
}
