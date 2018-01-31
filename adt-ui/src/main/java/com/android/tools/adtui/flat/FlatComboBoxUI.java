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
package com.android.tools.adtui.flat;

import com.android.tools.adtui.stdui.GraphicsUtilKt;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * TODO deprecate in preference of {@link com.android.tools.adtui.stdui.CommonComboBoxUI}
 */
class FlatComboBoxUI extends BasicComboBoxUI {
  private final MouseAdapter myHoverAdapter;
  private boolean myHover;

  public FlatComboBoxUI() {

    myHoverAdapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myHover = true;
        comboBox.repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHover = false;
        comboBox.repaint();
      }
    };
  }

  @Override
  protected void installListeners() {
    super.installListeners();
    comboBox.addMouseListener(myHoverAdapter);
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    comboBox.removeMouseListener(myHoverAdapter);
  }

  @Override
  protected ComboPopup createPopup() {
    return new BasicComboPopup(comboBox) {
      @Override
      protected void configurePopup() {
        super.configurePopup();
        setBorderPainted(false);
        setBorder(JBUI.Borders.empty());
      }

      @Override
      protected void firePopupMenuWillBecomeInvisible() {
        super.firePopupMenuWillBecomeInvisible();
        comboBox.repaint();
      }
    };
  }

  @Override
  protected JButton createArrowButton() {
    FlatArrowButton button = new FlatArrowButton();
    button.addMouseListener(myHoverAdapter);
    return button;
  }

  @Override
  protected void installDefaults() {
    padding = JBUI.insets(0, 0, 0, 2);
    squareButton = false;
  }


  @Override
  public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
    // Stop super from paiting this
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension size = super.getMinimumSize(c);
    return new Dimension(size.width, Math.max(size.height, JBUI.scale(25)));
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    // TODO: Create a unique style for showing focus, for now use the hover state visuals.
    if (myHover || isPopupVisible(comboBox) || c.isFocusOwner())  {
      GraphicsUtilKt.paintBackground(g, c);
    }
    super.paint(g, c);
  }

  @Override
  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    ListCellRenderer renderer = comboBox.getRenderer();
    Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
    c.setFont(comboBox.getFont());
    c.setBackground(UIUtil.TRANSPARENT_COLOR);

    boolean shouldValidate = false;
    // Fix for 4238829: should lay out the JPanel. See BasicComboBoxUI.paintCurrentValue
    if (c instanceof JPanel) {
      shouldValidate = true;
    }

    int x = bounds.x, y = bounds.y, w = bounds.width, h = bounds.height;
    if (padding != null) {
      x = bounds.x + padding.left;
      y = bounds.y + padding.top;
      w = bounds.width - (padding.left + padding.right);
      h = bounds.height - (padding.top + padding.bottom);
    }

    currentValuePane.paintComponent(g, c, comboBox, x, y, w, h, shouldValidate);
  }

  private static class FlatArrowButton extends JButton {
    public FlatArrowButton() {
      setUI(null);
    }

    @Override
    public boolean isFocusable() {
      return false;
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return new JBDimension(15, 25);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Dimension size = getSize();
      AllIcons.General.ComboArrowDown.paintIcon(this, g, 0, size.height / 2 - 2);
    }
  }
}
