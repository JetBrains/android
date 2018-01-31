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
package com.android.tools.adtui.stdui.menu;

import com.android.tools.adtui.model.stdui.CommonAction;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Used for creating our own {@link CommonPopupMenu} for showing sub-menus. The base class implementation creates a {@link JPopupMenu} and
 * does not expose it for styling. Here we override methods that reference the popup menu so we can point to our own {@link CommonPopupMenu}
 * instead.
 */
public class CommonMenu extends JMenu implements PropertyChangeListener {

  private JPopupMenu myPopupMenu;
  private Point myCustomMenuLocation = null;
  /**
   * Swing uses {@link JMenu#isSelected()} for highlight states, so here we use an extra boolean to indicate if the action is selected.
   */
  private boolean myActionSelected;

  public CommonMenu(CommonAction action) {
    super(action);
    myActionSelected = action.isSelected();
    action.addPropertyChangeListener(this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    switch (event.getPropertyName()) {
      case CommonAction.SELECTED_CHANGED:
        assert event.getNewValue() instanceof Boolean;
        myActionSelected = (Boolean)event.getNewValue();
        repaint();
        break;
      default:
        break;
    }
  }

  public boolean isActionSelected() {
    return myActionSelected;
  }

  @Override
  public void updateUI() {
    super.updateUI();

    setUI(new CommonMenuUI());
    if (myPopupMenu != null) {
      myPopupMenu.setUI(new CommonPopupMenuUI());
    }
  }

  @Override
  public boolean isPopupMenuVisible() {
    ensurePopupMenuCreated();
    return myPopupMenu.isVisible();
  }

  @Override
  public void setPopupMenuVisible(boolean b) {
    boolean isVisible = isPopupMenuVisible();
    if (b != isVisible && (isEnabled() || !b)) {
      ensurePopupMenuCreated();
      if ((b == true) && isShowing()) {
        // Set location of myPopupMenu (pulldown or pullright)
        // STUDIO customization
        // Point p = getCustomMenuLocation();
        Point p = myCustomMenuLocation;
        if (p == null) {
          p = getPopupMenuOrigin();
        }
        getPopupMenu().show(this, p.x, p.y);
      }
      else {
        getPopupMenu().setVisible(false);
      }
    }
  }

  @Override
  protected Point getPopupMenuOrigin() {
    int x;
    int y;
    JPopupMenu pm = getPopupMenu();
    // Figure out the sizes needed to calculate the menu position
    Dimension s = getSize();
    Dimension pmSize = pm.getSize();
    // For the first time the menu is popped up,
    // the size has not yet been initiated
    if (pmSize.width == 0) {
      pmSize = pm.getPreferredSize();
    }
    Point position = getLocationOnScreen();
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    GraphicsConfiguration gc = getGraphicsConfiguration();
    Rectangle screenBounds = new Rectangle(toolkit.getScreenSize());
    GraphicsEnvironment ge =
      GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gd = ge.getScreenDevices();
    for (int i = 0; i < gd.length; i++) {
      if (gd[i].getType() == GraphicsDevice.TYPE_RASTER_SCREEN) {
        GraphicsConfiguration dgc =
          gd[i].getDefaultConfiguration();
        if (dgc.getBounds().contains(position)) {
          gc = dgc;
          break;
        }
      }
    }


    if (gc != null) {
      screenBounds = gc.getBounds();
      // take screen insets (e.g. taskbar) into account
      Insets screenInsets = toolkit.getScreenInsets(gc);

      screenBounds.width -=
        Math.abs(screenInsets.left + screenInsets.right);
      screenBounds.height -=
        Math.abs(screenInsets.top + screenInsets.bottom);
      position.x -= Math.abs(screenInsets.left);
      position.y -= Math.abs(screenInsets.top);
    }

    Container parent = getParent();
    if (parent instanceof JPopupMenu) {
      // We are a submenu (pull-right)
      int xOffset = 0; // STUDIO customization - UIManager.getInt("Menu.submenuPopupOffsetX");
      int yOffset = 0; // STUDIO customization - UIManager.getInt("Menu.submenuPopupOffsetY");

      // STUDIO customization
      // if( SwingUtilities.isLeftToRight(this) ) {
      if (getComponentOrientation().isLeftToRight()) {
        // First determine x:
        x = s.width + xOffset;   // Prefer placement to the right
        if (position.x + x + pmSize.width >= screenBounds.width
                                             + screenBounds.x &&
            // popup doesn't fit - place it wherever there's more room
            screenBounds.width - s.width < 2 * (position.x
                                                - screenBounds.x)) {

          x = 0 - xOffset - pmSize.width;
        }
      }
      else {
        // First determine x:
        x = 0 - xOffset - pmSize.width; // Prefer placement to the left
        if (position.x + x < screenBounds.x &&
            // popup doesn't fit - place it wherever there's more room
            screenBounds.width - s.width > 2 * (position.x -
                                                screenBounds.x)) {

          x = s.width + xOffset;
        }
      }
      // Then the y:
      y = yOffset;                     // Prefer dropping down
      if (position.y + y + pmSize.height >= screenBounds.height
                                            + screenBounds.y &&
          // popup doesn't fit - place it wherever there's more room
          screenBounds.height - s.height < 2 * (position.y
                                                - screenBounds.y)) {

        y = s.height - yOffset - pmSize.height;
      }
    }
    else {
      // We are a toplevel menu (pull-down)
      int xOffset = 0; // STUDIO customization - UIManager.getInt("Menu.submenuPopupOffsetX");
      int yOffset = 0; // STUDIO customization - UIManager.getInt("Menu.submenuPopupOffsetY");

      // STUDIO customization
      // if( SwingUtilities.isLeftToRight(this) ) {
      if (getComponentOrientation().isLeftToRight()) {
        // First determine the x:
        x = xOffset;                   // Extend to the right
        if (position.x + x + pmSize.width >= screenBounds.width
                                             + screenBounds.x &&
            // popup doesn't fit - place it wherever there's more room
            screenBounds.width - s.width < 2 * (position.x
                                                - screenBounds.x)) {

          x = s.width - xOffset - pmSize.width;
        }
      }
      else {
        // First determine the x:
        x = s.width - xOffset - pmSize.width; // Extend to the left
        if (position.x + x < screenBounds.x &&
            // popup doesn't fit - place it wherever there's more room
            screenBounds.width - s.width > 2 * (position.x
                                                - screenBounds.x)) {

          x = xOffset;
        }
      }
      // Then the y:
      y = s.height + yOffset;    // Prefer dropping down
      if (position.y + y + pmSize.height >= screenBounds.height
                                            + screenBounds.y &&
          // popup doesn't fit - place it wherever there's more room
          screenBounds.height - s.height < 2 * (position.y
                                                - screenBounds.y)) {

        y = 0 - yOffset - pmSize.height;   // Otherwise drop 'up'
      }
    }
    return new Point(x, y);
  }

  private void ensurePopupMenuCreated() {
    if (myPopupMenu == null) {
      // STUDIO customization
      // this.myPopupMenu = new JPopupMenu();
      this.myPopupMenu = new CommonPopupMenu();
      myPopupMenu.setInvoker(this);
      popupListener = createWinListener(myPopupMenu);
    }
  }

  @Override
  public void setMenuLocation(int x, int y) {
    myCustomMenuLocation = new Point(x, y);
    if (myPopupMenu != null) {
      myPopupMenu.setLocation(x, y);
    }
  }

  @Override
  public JMenuItem add(JMenuItem menuItem) {
    ensurePopupMenuCreated();
    return myPopupMenu.add(menuItem);
  }

  @Override
  public Component add(Component c) {
    ensurePopupMenuCreated();
    myPopupMenu.add(c);
    return c;
  }

  @Override
  public Component add(Component c, int index) {
    ensurePopupMenuCreated();
    myPopupMenu.add(c, index);
    return c;
  }

  @Override
  public void addSeparator() {
    ensurePopupMenuCreated();
    myPopupMenu.addSeparator();
  }

  @Override
  public void insert(String s, int pos) {
    if (pos < 0) {
      throw new IllegalArgumentException("index less than zero.");
    }

    ensurePopupMenuCreated();
    myPopupMenu.insert(new JMenuItem(s), pos);
  }

  @Override
  public JMenuItem insert(JMenuItem mi, int pos) {
    if (pos < 0) {
      throw new IllegalArgumentException("index less than zero.");
    }
    ensurePopupMenuCreated();
    myPopupMenu.insert(mi, pos);
    return mi;
  }

  @Override
  public JMenuItem insert(Action a, int pos) {
    if (pos < 0) {
      throw new IllegalArgumentException("index less than zero.");
    }

    ensurePopupMenuCreated();
    JMenuItem mi = new JMenuItem(a);
    mi.setHorizontalTextPosition(JButton.TRAILING);
    mi.setVerticalTextPosition(JButton.CENTER);
    myPopupMenu.insert(mi, pos);
    return mi;
  }

  @Override
  public void insertSeparator(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index less than zero.");
    }

    ensurePopupMenuCreated();
    myPopupMenu.insert(new JPopupMenu.Separator(), index);
  }

  @Override
  public void remove(JMenuItem item) {
    if (myPopupMenu != null) {
      myPopupMenu.remove(item);
    }
  }

  @Override
  public void remove(int pos) {
    if (pos < 0) {
      throw new IllegalArgumentException("index less than zero.");
    }
    if (pos > getItemCount()) {
      throw new IllegalArgumentException("index greater than the number of items.");
    }
    if (myPopupMenu != null) {
      myPopupMenu.remove(pos);
    }
  }

  @Override
  public void remove(Component c) {
    if (myPopupMenu != null) {
      myPopupMenu.remove(c);
    }
  }

  @Override
  public void removeAll() {
    if (myPopupMenu != null) {
      myPopupMenu.removeAll();
    }
  }

  @Override
  public int getMenuComponentCount() {
    int componentCount = 0;
    if (myPopupMenu != null) {
      componentCount = myPopupMenu.getComponentCount();
    }
    return componentCount;
  }

  @Override
  public Component getMenuComponent(int n) {
    if (myPopupMenu != null) {
      return myPopupMenu.getComponent(n);
    }

    return null;
  }

  @Override
  public Component[] getMenuComponents() {
    if (myPopupMenu != null) {
      return myPopupMenu.getComponents();
    }

    return new Component[0];
  }

  @Override
  public JPopupMenu getPopupMenu() {
    ensurePopupMenuCreated();
    return myPopupMenu;
  }

  @Override
  public MenuElement[] getSubElements() {
    if (myPopupMenu == null) {
      return new MenuElement[0];
    }
    else {
      MenuElement result[] = new MenuElement[1];
      result[0] = myPopupMenu;
      return result;
    }
  }

  @Override
  public void applyComponentOrientation(ComponentOrientation o) {
    super.applyComponentOrientation(o);

    if (myPopupMenu != null) {
      int ncomponents = getMenuComponentCount();
      for (int i = 0; i < ncomponents; ++i) {
        getMenuComponent(i).applyComponentOrientation(o);
      }
      myPopupMenu.setComponentOrientation(o);
    }
  }

  @Override
  public void setComponentOrientation(ComponentOrientation o) {
    super.setComponentOrientation(o);
    if (myPopupMenu != null) {
      myPopupMenu.setComponentOrientation(o);
    }
  }
}
