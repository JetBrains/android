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
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


/**
 * This is a copy of {@link com.intellij.openapi.actionSystem.ex.ComboBoxAction}, with a couple of crucial changes for ADT:
 * <ul>
 * <li>
 * The visual appearance has changed: there is no background gradient, and no border, on the button
 * until the mouse is actually over the button. This makes the button "flatter" and more suitable
 * for use as a toolbar button.
 * </li>
 * <li>
 * The action has a {@link #handleIconClicked} method which
 * an action subclass can override to handle a click on the icon. Some actions will use this to
 * handle the action directly and not pop open the action menu. Clicking on the optional text or
 * arrow will always open up the menu.
 * </li>
 * <li>
 * The arrow icons have been changed to be less visually prominent (smaller, lighter)
 * </li>
 * <li>
 * The smallVariant flag from ComboBoxAction was removed; this action only supports smallVariant.
 * </li>
 * <p/>
 * </ul>
 * Originally, I had this just as a subclass of ComboBoxAction, only overriding the
 * paint method, but that became uglier and uglier as I had to counteract padding etc
 * done in the parent class by overriding and subtracting in getPreferredSize, getInsets, etc.
 * I also had to register my own mouse listener to find out whether the mouse is hovering
 * since myMouseInside was private.
 * <p/>
 * And it became impossible when I tried to override the click behavior for clicking on icons;
 * I simply did not have the hooks to prevent the menu from popping up.
 * <p/>
 * So, I forked.
 * <p/>
 * I at first tried to keep the original code with small deltas, but the deltas got uglier
 * and uglier so I finally went and cleaned up the code, removed a lot of dead code, etc.
 */
public abstract class FlatComboAction extends AnAction implements CustomComponentAction {
  private static final Icon ARROW_DOWN = AndroidIcons.ArrowDown;
  private static final Icon DISABLED_ARROW_ICON = IconLoader.getDisabledIcon(ARROW_DOWN);

  private DataContext myDataContext;

  protected FlatComboAction() {
  }

  /**
   * Invoked if the user clicks on the icon, not the text or arrow part. Subclasses of the action
   * can use this to for example perform immediate actions.
   *
   * @return true if the action has been handled; this will cancel opening the menu, and
   *         otherwise return false to have the default handling (which pops open a menu)
   */
  protected boolean handleIconClicked() {
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return createComboBoxButton(presentation);
  }

  protected FlatComboButton createComboBoxButton(Presentation presentation) {
    return new FlatComboButton(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    myDataContext = e.getDataContext();
  }

  @NotNull
  protected abstract DefaultActionGroup createPopupActionGroup();

  protected int getMaxRows() {
    return 30;
  }

  protected int getMinHeight() {
    return 1;
  }

  protected int getMinWidth() {
    return 1;
  }

  protected JBPopup createPopup(Runnable onDispose, DataContext context) {
    DefaultActionGroup group = createPopupActionGroup();
    JBPopupFactory factory = JBPopupFactory.getInstance();
    ListPopup popup = factory.createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, onDispose,
                                                     getMaxRows());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
  }

  protected class FlatComboButton extends JButton {
    private final Presentation myPresentation;
    private boolean myForcePressed = false;
    private PropertyChangeListener myButtonSynchronizer;
    private boolean myMouseInside = false;
    private JBPopup myPopup;

    public FlatComboButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      setHorizontalAlignment(LEFT);
      setFocusable(ScreenReader.isActive());
      Insets margins = getMargin();
      setMargin(new Insets(margins.top, 2, margins.bottom, 2));
      setBorder(IdeBorderFactory.createEmptyBorder(0, 2, 0, 2));
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      }
      addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          if (!myForcePressed) {
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
              @Override
              public void run() {
                final Icon icon = getIcon();
                if (icon != null && isShowing()) {
                  Point location = MouseInfo.getPointerInfo().getLocation();
                  Point current = getLocationOnScreen();
                  int x = location.x - current.x;
                  // 3 is from painting code. I need to clean this crap up !
                  if (x < icon.getIconWidth() + 3) {
                    if (handleIconClicked()) {
                      return;
                    }
                  }
                }

                showPopup();
              }
            });
          }
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
          dispatchEventToPopup(e);
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
          dispatchEventToPopup(e);
        }
      });
    }

    // Event forwarding. We need it if user does press-and-drag gesture for opening popup and
    // choosing item there.
    // It works in JComboBox, here we provide the same behavior
    private void dispatchEventToPopup(MouseEvent e) {
      if (myPopup != null && myPopup.isVisible()) {
        JComponent content = myPopup.getContent();
        Rectangle rectangle = content.getBounds();
        Point location = rectangle.getLocation();
        SwingUtilities.convertPointToScreen(location, content);
        Point eventPoint = e.getLocationOnScreen();
        rectangle.setLocation(location);
        if (rectangle.contains(eventPoint)) {
          MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, myPopup.getContent());
          Component component = SwingUtilities.getDeepestComponentAt(content, event.getX(), event.getY());
          if (component != null) component.dispatchEvent(event);
        }
      }
    }

    public void showPopup() {
      myForcePressed = true;
      repaint();

      Runnable onDispose = new Runnable() {
        @Override
        public void run() {
          // give button chance to handle action listener
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              myForcePressed = false;
              myPopup = null;
            }
          });
          repaint();
        }
      };

      myPopup = createPopup(onDispose);
      myPopup.show(new RelativePoint(this, new Point(0, this.getHeight() - 1)));
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed ? null : super.getToolTipText();
    }

    protected JBPopup createPopup(Runnable onDispose) {
      DataContext context = getDataContext();
      myDataContext = null;
      return FlatComboAction.this.createPopup(onDispose, context);
    }

    protected DataContext getDataContext() {
      return myDataContext == null || PlatformDataKeys.CONTEXT_COMPONENT.getData(myDataContext) == null
             ? DataManager.getInstance().getDataContext(this) : myDataContext;
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
      String tooltip = KeymapUtil.createTooltipText(description, FlatComboAction.this);
      setToolTipText(!tooltip.isEmpty() ? tooltip : null);
    }

    protected class MyButtonModel extends DefaultButtonModel {
      @Override
      public boolean isPressed() {
        return myForcePressed || super.isPressed();
      }

      @Override
      public boolean isArmed() {
        return myForcePressed || super.isArmed();
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
      return new Insets(insets.top, insets.left, insets.bottom, insets.right + ARROW_DOWN.getIconWidth());
    }

    @Override
    public Insets getInsets(Insets insets) {
      final Insets result = super.getInsets(insets);
      result.right += ARROW_DOWN.getIconWidth();
      return result;
    }

    @Override
    public boolean isOpaque() {
      return false;
    }

    @Override
    public Dimension getPreferredSize() {
      final boolean isEmpty = getIcon() == null && StringUtil.isEmpty(getText());
      int width = isEmpty ? 10 + ARROW_DOWN.getIconWidth() : super.getPreferredSize().width;
      // See ActionToolBarImpl: For a horizontal toolbar, the preferred height is 24
      return new Dimension(width, JBUI.scale(24));
    }

    @Override
    public void paint(Graphics g) {
      GraphicsUtil.setupAntialiasing(g);

      boolean textEmpty = StringUtil.isEmpty(getText());
      final boolean isEmpty = getIcon() == null && textEmpty;
      final Dimension size = getSize();
      {
        final Graphics2D g2 = (Graphics2D)g;
        Color controlColor = getParent().getBackground();
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
      final Insets insets = super.getInsets();

      final Icon icon;
      if (isEnabled()) {
        icon = ARROW_DOWN;
      }
      else {
        icon = DISABLED_ARROW_ICON;
      }

      final int x;
      if (isEmpty) {
        x = (size.width - icon.getIconWidth()) / 2;
      }
      else {
        x = size.width - icon.getIconWidth() - insets.right - (textEmpty ? 1 : 4);
      }

      icon.paintIcon(null, g, x, (size.height - icon.getIconHeight()) / 2);
      g.setPaintMode();
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
    }
  }
}
