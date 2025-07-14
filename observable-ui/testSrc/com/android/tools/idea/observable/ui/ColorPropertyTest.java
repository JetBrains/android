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

import com.android.tools.idea.observable.CountListener;
import com.intellij.ui.ColorPanel;
import java.awt.Color;
import org.junit.Test;

public class ColorPropertyTest {
  @Test
  public void testColorProperty() {
    ColorPanel colorPanel = new ColorPanel();
    ColorProperty color = new ColorProperty(colorPanel);
    CountListener listener = new CountListener();
    color.addListener(listener);

    assertThat(color.get().isPresent()).isFalse();
    assertThat(listener.getCount()).isEqualTo(0);

    colorPanel.setSelectedColor(Color.RED);
    assertThat(color.get().isPresent()).isTrue();
    assertThat(color.getValue()).isEqualTo(Color.RED);
    // ColorPanel only fires its listener when the button is clicked, not when color is set
    // programmatically. Otherwise, this should have been true:
    // assertThat(listener.getCount()).isEqualTo(1);

    color.setValue(Color.BLUE);
    assertThat(colorPanel.getSelectedColor()).isEqualTo(Color.BLUE);
    assertThat(listener.getCount()).isEqualTo(1);

    color.clear();
    assertThat(colorPanel.getSelectedColor()).isNull();
    assertThat(listener.getCount()).isEqualTo(2);
  }
}