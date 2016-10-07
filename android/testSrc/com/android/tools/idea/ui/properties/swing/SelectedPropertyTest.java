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

public final class SelectedPropertyTest {
  @Test
  public void testSelectedProperty() {
    JCheckBox checkbox = new JCheckBox();
    checkbox.setSelected(true);

    SelectedProperty selectedProperty = new SelectedProperty(checkbox);
    CountListener listener = new CountListener();
    selectedProperty.addListener(listener);

    assertThat(selectedProperty.get()).isTrue();
    assertThat(listener.getCount()).isEqualTo(0);

    checkbox.setSelected(false);
    assertThat(selectedProperty.get()).isFalse();
    assertThat(listener.getCount()).isEqualTo(1);

    selectedProperty.set(true);
    assertThat(checkbox.isSelected()).isTrue();
    assertThat(listener.getCount()).isEqualTo(2);
  }
}