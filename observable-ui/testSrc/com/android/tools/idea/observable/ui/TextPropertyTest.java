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
package com.android.tools.idea.observable.ui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.idea.observable.CountListener;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.EditorTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Unit tests for {@link TextProperty}.
 *
 * Note: This class skips testing {@link EditorComboBox} and {@link EditorTextField}. Those
 * components are IntelliJ Swing Components with too many assumptions about IntelliJ being up
 * and running in the background.
 *
 * There is a TextProperty that wraps one of these in our new project creation flow, so it's
 * common enough that we can rely on the regular product to catch an issue if one ever arises
 * (which is so unlikely as the logic that we'd put under test is so trivial that it probably
 * won't break).
 */
public final class TextPropertyTest {

  @ClassRule
  public static ApplicationRule appRule = new ApplicationRule();

  @Test
  public void textPropertyCanWrapLabel() {
    JLabel label = new JLabel("New Label");
    TextProperty textProperty = new TextProperty(label);
    CountListener listener = new CountListener();
    textProperty.addListener(listener);

    assertThat(textProperty.get()).isEqualTo("New Label");
    assertThat(listener.getCount()).isEqualTo(0);

    label.setText("Label text updated directly");
    assertThat(textProperty.get()).isEqualTo("Label text updated directly");
    assertThat(listener.getCount()).isEqualTo(1);

    textProperty.set("Label text updated via property");
    assertThat(label.getText()).isEqualTo("Label text updated via property");
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void textPropertyCanWrapButton() {
    JButton button = new JButton("New Button");
    TextProperty textProperty = new TextProperty(button);
    CountListener listener = new CountListener();
    textProperty.addListener(listener);

    assertThat(textProperty.get()).isEqualTo("New Button");
    assertThat(listener.getCount()).isEqualTo(0);

    button.setText("Button text updated directly");
    assertThat(textProperty.get()).isEqualTo("Button text updated directly");
    assertThat(listener.getCount()).isEqualTo(1);

    textProperty.set("Button text updated via property");
    assertThat(button.getText()).isEqualTo("Button text updated via property");
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void textPropertyCanWrapTextField() {
    JTextField field = new JTextField("New Field");
    TextProperty textProperty = new TextProperty(field);
    CountListener listener = new CountListener();
    textProperty.addListener(listener);

    assertThat(textProperty.get()).isEqualTo("New Field");
    assertThat(listener.getCount()).isEqualTo(0);

    field.setText("Field text updated directly");
    assertThat(textProperty.get()).isEqualTo("Field text updated directly");
    assertThat(listener.getCount()).isEqualTo(2); // +2 here: TextField fires two events when setText is called directly (remove and insert)

    textProperty.set("Field text updated via property");
    assertThat(field.getText()).isEqualTo("Field text updated via property");
    assertThat(listener.getCount()).isEqualTo(3); // Only +1 here: Property.set hides extra validation calls
  }

  @Test
  public void textPropertyCanWrapLabelWithEditLink() {
    LabelWithEditButton editLabel = new LabelWithEditButton();
    TextProperty textProperty = new TextProperty(editLabel);
    CountListener listener = new CountListener();
    textProperty.addListener(listener);

    assertThat(textProperty.get()).isEqualTo("");
    assertThat(listener.getCount()).isEqualTo(0);

    editLabel.setText("Edit label set directly");
    assertThat(textProperty.get()).isEqualTo("Edit label set directly");
    assertThat(listener.getCount()).isEqualTo(1);

    textProperty.set("Edit label updated via property");
    assertThat(editLabel.getText()).isEqualTo("Edit label updated via property");
    assertThat(listener.getCount()).isEqualTo(2);
  }
}