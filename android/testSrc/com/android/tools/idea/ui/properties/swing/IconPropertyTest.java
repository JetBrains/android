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
import java.awt.*;

import static org.fest.assertions.Assertions.assertThat;

public final class IconPropertyTest {
  @Test
  public void iconPropertyCanWrapLabel() throws Exception {
    JLabel label = new JLabel();
    Icon dummyIcon = new DummyIcon();

    IconProperty iconProperty = new IconProperty(label);
    CountListener listener = new CountListener();
    iconProperty.addListener(listener);

    assertThat(iconProperty.get().isPresent()).isFalse();
    assertThat(listener.getCount()).isEqualTo(0);

    label.setIcon(dummyIcon);
    assertThat(iconProperty.get().isPresent()).isTrue();
    assertThat(iconProperty.getValue()).isEqualTo(dummyIcon);
    assertThat(listener.getCount()).isEqualTo(1);

    label.setIcon(null);
    assertThat(iconProperty.get().isPresent()).isFalse();
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void iconPropertyCanWrapButton() throws Exception {
    JButton button = new JButton();
    Icon dummyIcon = new DummyIcon();

    IconProperty iconProperty = new IconProperty(button);
    CountListener listener = new CountListener();
    iconProperty.addListener(listener);

    assertThat(iconProperty.get().isPresent()).isFalse();
    assertThat(listener.getCount()).isEqualTo(0);

    button.setIcon(dummyIcon);
    assertThat(iconProperty.get().isPresent()).isTrue();
    assertThat(iconProperty.getValue()).isEqualTo(dummyIcon);
    assertThat(listener.getCount()).isEqualTo(1);

    button.setIcon(null);
    assertThat(iconProperty.get().isPresent()).isFalse();
    assertThat(listener.getCount()).isEqualTo(2);
  }

  private static class DummyIcon implements Icon {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

    }

    @Override
    public int getIconWidth() {
      return 0;
    }

    @Override
    public int getIconHeight() {
      return 0;
    }
  }
}