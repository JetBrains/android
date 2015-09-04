/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.CENTER;


/**
 * Simplified version of {@link FlatComboAction}, which works as a push button
 * rather than a combo button. This is needed such that this button looks and
 * behaves similar to the {@link FlatComboAction} buttons in the same toolbar;
 * in particular, same text font (which is not the case for the default IntelliJ
 * toolbar action ({@link com.intellij.openapi.actionSystem.impl.ActionButtonWithText})
 * when you show both icons and text), same roll over gradient, same border,
 * same baseline alignment, etc.
 */
public abstract class FlatAction extends AnAction implements CustomComponentAction {
  private DataContext myDataContext;

  protected FlatAction() {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new GridBagLayout());
    FlatButton button = createComboBoxButton(presentation);
    panel.add(button, new GridBagConstraints(0, 0, 1, 1, 1, 1, CENTER, BOTH, new Insets(0, 0, 0, 0), 0, 0));
    return panel;
  }

  protected FlatButton createComboBoxButton(Presentation presentation) {
    return new FlatButton(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    myDataContext = e.getDataContext();
  }

  protected class FlatButton extends JButton {
    private final Presentation myPresentation;
    private PropertyChangeListener myButtonSynchronizer;
    private boolean myMouseInside = false;

    public FlatButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new DefaultButtonModel());
      setHorizontalAlignment(LEFT);
      setFocusable(false);
      Insets margins = getMargin();
      setMargin(JBUI.insets(margins.top, 2, margins.bottom, 2));
      setBorder(IdeBorderFactory.createEmptyBorder(0, 2, 0, 2));
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        setFont(UIUtil.getLabelFont().deriveFont(JBUI.scale(11.0f)));
      }
      addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
            @Override
            public void run() {
              AnActionEvent event = AnActionEvent.createFromInputEvent(FlatAction.this, null, ActionPlaces.EDITOR_TOOLBAR);
              FlatAction.this.actionPerformed(event);
            }
          });
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          myMouseInside = true;
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myMouseInside = false;
          repaint();
        }

        @Override
        public void mousePressed(final MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume();
            doClick();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
        }
      });
      addMouseMotionListener(new MouseMotionListener() {
        @Override
        public void mouseDragged(MouseEvent e) {
          mouseMoved(
            new MouseEvent(e.getComponent(), MouseEvent.MOUSE_MOVED, e.getWhen(), e.getModifiers(), e.getX(), e.getY(), e.getClickCount(),
                           e.isPopupTrigger(), e.getButton()));
        }

        @Override
        public void mouseMoved(MouseEvent e) {
        }
      });
    }

    protected DataContext getDataContext() {
      return myDataContext == null ? DataManager.getInstance().getDataContext(this) : myDataContext;
    }

    @Override
    public void removeNotify() {
      if (myButtonSynchronizer != null) {
        myPresentation.removePropertyChangeListener(myButtonSynchronizer);
        myButtonSynchronizer = null;
      }
      super.removeNotify();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (myButtonSynchronizer == null) {
        myButtonSynchronizer = new MyButtonSynchronizer();
        myPresentation.addPropertyChangeListener(myButtonSynchronizer);
      }
      initButton();
    }

    private void initButton() {
      setIcon(myPresentation.getIcon());
      setEnabled(myPresentation.isEnabled());
      setText(myPresentation.getText());
      updateTooltipText(myPresentation.getDescription());
      updateButtonSize();
    }

    private void updateTooltipText(String description) {
      String tooltip = KeymapUtil.createTooltipText(description, FlatAction.this);
      setToolTipText(!tooltip.isEmpty() ? tooltip : null);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        setBorder(UIUtil.getButtonBorder());
      }
    }

    private class MyButtonSynchronizer implements PropertyChangeListener {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (Presentation.PROP_TEXT.equals(propertyName)) {
          setText((String)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
          updateTooltipText((String)evt.getNewValue());
        }
        else if (Presentation.PROP_ICON.equals(propertyName)) {
          setIcon((Icon)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_ENABLED.equals(propertyName)) {
          setEnabled(((Boolean)evt.getNewValue()).booleanValue());
        }
      }
    }

    @Override
    public Insets getInsets() {
      final Insets insets = super.getInsets();
      return new Insets(insets.top, insets.left, insets.bottom, insets.right);
    }

    @Override
    public boolean isOpaque() {
      return false;
    }

    @Override
    public Dimension getPreferredSize() {
      final boolean isEmpty = getIcon() == null && StringUtil.isEmpty(getText());
      int width = isEmpty ? JBUI.scale(10) : super.getPreferredSize().width;
      // See ActionToolBarImpl: For a horizontal toolbar, the preferred height is 24
      return new Dimension(width, JBUI.scale(24));
    }

    @Override
    public void paint(Graphics g) {
      GraphicsUtil.setupAntialiasing(g);

      boolean textEmpty = StringUtil.isEmpty(getText());
      final Dimension size = getSize();
      {
        final Graphics2D g2 = (Graphics2D)g;
        Color controlColor = UIUtil.getControlColor();
        if (UIUtil.isUnderIntelliJLaF()) {
          controlColor = getParent().getBackground();
        }
        g2.setColor(controlColor);
        final int w = getWidth();
        final int h = getHeight();
        if (getModel().isArmed() && getModel().isPressed()) {
          g2.setPaint(new GradientPaint(0, 0, controlColor, 0, h, ColorUtil.shift(controlColor, 0.8)));
        }
        else {
          if (myMouseInside) {
            g2.setPaint(new GradientPaint(0, 0, ColorUtil.shift(controlColor, 1.1), 0, h,
                                          ColorUtil.shift(controlColor, 0.9)));
          }
        }
        g2.fillRect(1, 1, w - 2, h - 2);
        GraphicsUtil.setupAntialiasing(g2);
        if (myMouseInside) {
          if (!UIUtil.isUnderDarcula()) {
            g2.setPaint(new GradientPaint(0, 0, UIUtil.getBorderColor().darker(), 0, h, UIUtil.getBorderColor().darker().darker()));
          }
          else {
            g2.setPaint(new GradientPaint(0, 0, ColorUtil.shift(controlColor, 1.4), 0, h,
                                          ColorUtil.shift(controlColor, 1.5)));
          }

          g2.drawRect(0, 0, w - 1, h - 1);
        }

        final Icon icon = getIcon();
        int x = 2;
        if (icon != null) {
          icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2 - 1);
          x += icon.getIconWidth() + 1;
        }
        if (!textEmpty) {
          final Font font = getFont();
          g2.setFont(font);
          g2.setColor(UIManager.getColor("Panel.foreground"));
          g2.drawString(getText(), x, (size.height + font.getSize()) / 2 - 1);
        }
      }
      g.setPaintMode();
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
    }
  }
}
