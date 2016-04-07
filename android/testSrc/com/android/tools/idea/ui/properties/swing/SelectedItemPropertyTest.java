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

public final class SelectedItemPropertyTest {
  @Test
  public void testSelectedItemProperty() {
    TestString a = new TestString("A");
    TestString b = new TestString("B");

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement(a);
    model.addElement(b);

    JComboBox comboBox = new JComboBox(model);
    SelectedItemProperty<TestString> selectedItemProperty = new SelectedItemProperty<>(comboBox);
    CountListener listener = new CountListener();
    selectedItemProperty.addListener(listener);

    assertThat(selectedItemProperty.getValue()).isEqualTo(a);
    assertThat(listener.getCount()).isEqualTo(0);

    selectedItemProperty.setValue(b);
    assertThat(comboBox.getSelectedItem()).isEqualTo(b);
    assertThat(listener.getCount()).isEqualTo(1);

    comboBox.setSelectedIndex(0);
    assertThat(comboBox.getSelectedItem()).isEqualTo(a);
    assertThat(listener.getCount()).isEqualTo(2);

    comboBox.setSelectedItem(null);
    assertThat(selectedItemProperty.get().isPresent()).isEqualTo(false);
    assertThat(listener.getCount()).isEqualTo(3);
  }

  private static class TestString {
    public String myString;

    public TestString(String string) {
      myString = string;
    }
  }
}