/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.network.details;

import static com.google.common.truth.Truth.assertThat;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.Test;

public class CompressedVerticalLayoutTest {
  @Test
  public void testCompressedVerticalLayout() {
    JPanel container = new JPanel(new HttpDataComponentFactory.CompressedVerticalLayout());
    container.setBounds(0, 0, 40, Short.MAX_VALUE);

    CustomVerticalComponent component = new CustomVerticalComponent(60, 90, 20, 30);
    container.add(new FixedComponent());
    container.add(component);
    container.add(new FixedComponent());
    container.doLayout();

    assertThat(component.getBounds()).isEqualTo(new Rectangle(0, 0, 20, 30));
  }

  private static class FixedComponent extends JComponent {
    @Override
    public Dimension getMaximumSize() {
      return new Dimension(30, 10);
    }

    @Override
    public boolean isMaximumSizeSet() {
      return true;
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(30, 10);
    }

    @Override
    public boolean isPreferredSizeSet() {
      return true;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      assertThat(x).isEqualTo(0);
      assertThat(y).isEqualTo(0);
      assertThat(width).isEqualTo(30);
      assertThat(height).isEqualTo(10);
      super.setBounds(x, y, width, height);
    }
  }

  private static class CustomVerticalComponent extends JComponent {
    private int myMaxWidth;
    private int myMaxHeight;
    private int myPreferredWidth;
    private int myPreferredHeight;
    private int myResizeCalls = 0;

    public CustomVerticalComponent(int maxWidth, int maxHeight, int preferredWidth, int preferredHeight) {
      setMaxWidth(maxWidth);
      setMaxHeight(maxHeight);
      setPreferredWidth(preferredWidth);
      setPreferredHeight(preferredHeight);
    }

    public void setMaxWidth(int maxWidth) {
      myMaxWidth = maxWidth;
    }

    public void setMaxHeight(int maxHeight) {
      myMaxHeight = maxHeight;
    }

    public void setPreferredWidth(int preferredWidth) {
      myPreferredWidth = preferredWidth;
      assertThat(myMaxWidth).isAtLeast(myPreferredWidth); // Just making sure callers are using this class correctly.
    }

    public void setPreferredHeight(int preferredHeight) {
      myPreferredHeight = preferredHeight;
      assertThat(myMaxHeight).isAtLeast(myPreferredHeight);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(myPreferredWidth, myPreferredHeight);
    }

    @Override
    public boolean isPreferredSizeSet() {
      return true;
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(myMaxWidth, myMaxHeight);
    }

    @Override
    public boolean isMaximumSizeSet() {
      return true;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      assertThat(x).isEqualTo(0);
      assertThat(y).isEqualTo(0);

      if (myResizeCalls % 2 == 0) {
        assertThat(width).isAtMost(myMaxWidth);
        assertThat(height).isEqualTo(myMaxHeight);
      }
      else {
        assertThat(width).isEqualTo(myPreferredWidth);
        assertThat(height).isEqualTo(myPreferredHeight);
      }

      myResizeCalls++;

      super.setBounds(x, y, width, height);
    }
  }
}
