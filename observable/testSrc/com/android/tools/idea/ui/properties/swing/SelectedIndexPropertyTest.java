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

import static com.google.common.truth.Truth.assertThat;

public final class SelectedIndexPropertyTest {
  @Test
  public void testSelectedIndexProperty() {
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement("A");
    model.addElement("B");
    model.addElement("C");

    JComboBox comboBox = new JComboBox(model);
    SelectedIndexProperty selectedIndexProperty = new SelectedIndexProperty(comboBox);
    CountListener listener = new CountListener();
    selectedIndexProperty.addListener(listener);

    assertThat(selectedIndexProperty.get()).isEqualTo(0);
    assertThat(listener.getCount()).isEqualTo(0);

    comboBox.setSelectedIndex(2);
    assertThat(selectedIndexProperty.get()).isEqualTo(2);
    assertThat(listener.getCount()).isEqualTo(1);

    selectedIndexProperty.set(1);
    assertThat(comboBox.getSelectedIndex()).isEqualTo(1);
    assertThat(listener.getCount()).isEqualTo(2);
  }
}