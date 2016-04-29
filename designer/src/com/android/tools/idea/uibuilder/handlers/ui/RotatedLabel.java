/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.ui;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.LabelUI;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * RotatedLabel is a {@link JLabel} that draws its text sideways.
 * Useful for tight places in a Swing UI.
 */
public class RotatedLabel extends JLabel {
  private boolean myPainting;

  @Override
  public void setUI(LabelUI newUI) {
    super.setUI(new RotatedLabelUI(newUI));
  }

  @Override
  public int getWidth() {
    if (myPainting && getIcon() == null) {
      // While painting the LabelUI may use the width of the component to determine if the
      // text should be truncated. We want it to believe we have space enough to use the entire
      // vertical height of the component for drawing the text.

      // Unfortunately if there is an icon we should continue to return the actual width.
      // Otherwise the LabelUI will scale to fit the larger height (unwanted).
      return super.getHeight();
    }
    return super.getWidth();
  }

  private class RotatedLabelUI extends LabelUI {
    private final ComponentUI myUI;

    public RotatedLabelUI(ComponentUI delegate) {
      myUI = delegate;
    }

    @Override
    public void installUI(JComponent component) {
      myUI.installUI(component);
    }

    @Override
    public void uninstallUI(JComponent component) {
      myUI.uninstallUI(component);
    }

    @Override
    public void paint(Graphics g, JComponent component) {
      prepareForDrawing(g, component);
      try {
        myPainting = true;
        myUI.paint(g, component);
      }
      finally {
        myPainting = false;
      }
    }

    @Override
    public void update(Graphics g, JComponent component) {
      prepareForDrawing(g, component);
      try {
        myPainting = true;
        myUI.update(g, component);
      }
      finally {
        myPainting = false;
      }
    }

    private void prepareForDrawing(Graphics g, JComponent component) {
      Graphics2D g2d = (Graphics2D)g;
      Dimension text = component.getPreferredSize();
      Dimension box = component.getSize();
      g2d.translate((text.width - box.height) / 2, (text.height + box.height) / 2);
      g2d.transform(AffineTransform.getQuadrantRotateInstance(-1));
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
      Dimension size = myUI.getPreferredSize(c);
      //noinspection SuspiciousNameCombination
      return new Dimension(size.height, size.width);
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
      Dimension size = myUI.getMinimumSize(c);
      //noinspection SuspiciousNameCombination
      return new Dimension(size.height, size.width);
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
      Dimension size = myUI.getMaximumSize(c);
      //noinspection SuspiciousNameCombination
      return new Dimension(size.height, size.width);
    }

    @Override
    public boolean contains(JComponent c, int x, int y) {
      return myUI.contains(c, x, y);
    }

    @Override
    public int getBaseline(JComponent c, int width, int height) {
      return myUI.getBaseline(c, width, height);
    }

    @Override
    public Component.BaselineResizeBehavior getBaselineResizeBehavior(JComponent c) {
      return myUI.getBaselineResizeBehavior(c);
    }

    @Override
    public int getAccessibleChildrenCount(JComponent c) {
      return myUI.getAccessibleChildrenCount(c);
    }

    @Override
    public Accessible getAccessibleChild(JComponent c, int i) {
      return myUI.getAccessibleChild(c, i);
    }
  }
}
