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
package com.android.tools.idea.ui.properties.swing;

import com.android.tools.idea.ui.properties.CountListener;
import org.junit.Test;

import javax.swing.*;

import static org.fest.assertions.Assertions.assertThat;

public final class TextPropertyTest {

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
    assertThat(listener.getCount()).isEqualTo(2); // +2 here: TextField fires two events when setText is called direclty (remove and insert)

    textProperty.set("Field text updated via property");
    assertThat(field.getText()).isEqualTo("Field text updated via property");
    assertThat(listener.getCount()).isEqualTo(3); // Only +1 here: Property.set hides extra validation calls
  }
}