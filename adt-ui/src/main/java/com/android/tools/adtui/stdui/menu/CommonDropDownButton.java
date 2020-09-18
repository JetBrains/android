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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.PopupMenuListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * This control takes a {@link CommonAction} as the model and when clicked, populates and shows a dropdown menu that mirrors the
 * descendant hierarchy of the {@link CommonAction} model.
 */
public class CommonDropDownButton extends CommonToggleButton implements PropertyChangeListener {
  @NotNull private final CommonPopupMenu myPopup;
  @NotNull private final CommonAction myAction;

  public CommonDropDownButton(@NotNull CommonAction action) {
    super(action.getText(), action.getIcon());
    myAction = action;
    myPopup = new CommonPopupMenu();

    // Toggle the button's state based on whether the popup menu is visible.
    myPopup.addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeInvisible(@NotNull PopupMenuEvent event) {
        setSelected(false);
      }

      @Override
      public void popupMenuCanceled(@NotNull PopupMenuEvent event) {
        setSelected(false);
      }
    });

    // Expand the menu based on button click.
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent event) {
        CommonDropDownButton menu = CommonDropDownButton.this;
        if (menu.isSelected()) {
          myAction.actionPerformed(event);
          populatePopup();
          JBPopupMenu.showBelow(menu, myPopup);
        }
      }
    });

    addPropertyChangeListenerRecursive(myAction);
  }

  @VisibleForTesting
  @NotNull
  JPopupMenu getPopup() {
    return myPopup;
  }

  @Override
  @NotNull
  public CommonAction getAction() {
    return myAction;
  }

  private void populatePopup() {
    myPopup.removeAll();
    List<CommonAction> actions = myAction.getChildrenActions();
    for (CommonAction action : actions) {
      if (action instanceof CommonAction.SeparatorAction) {
        myPopup.addSeparator();
      }
      else {
        JMenuItem menu;
        if (action.getChildrenActionCount() == 0) {
          menu = new CommonMenuItem(action);
        }
        else {
          menu = new CommonMenu(action);
          populateMenuRecursive((CommonMenu)menu, action.getChildrenActions());
        }
        menu.setFont(getFont());
        myPopup.add(menu);
      }
    }

    myPopup.pack();
  }

  private void populateMenuRecursive(@NotNull JMenu parent, @NotNull List<CommonAction> actions) {
    parent.removeAll();
    for (CommonAction action : actions) {
      if (action instanceof CommonAction.SeparatorAction) {
        parent.addSeparator();
      }
      else {
        JMenuItem menu;
        if (action.getChildrenActionCount() == 0) {
          menu = new CommonMenuItem(action);
        }
        else {
          menu = new CommonMenu(action);
          populateMenuRecursive((CommonMenu)menu, action.getChildrenActions());
        }
        menu.setFont(getFont());
        parent.add(menu);
      }
    }
  }

  @Override
  public void updateUI() {
    setUI(new CommonDropDownButtonUI());
    revalidate();
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent event) {
    switch (event.getPropertyName()) {
      case CommonAction.CHILDREN_ACTION_CHANGED:
        // Unregister the listeners on the old list of actions
        List<CommonAction> oldValues = (List<CommonAction>)event.getOldValue();
        oldValues.forEach(action -> removePropertyChangeListenerRecursive(action));

        // Register the new listeners
        List<CommonAction> newValues = (List<CommonAction>)event.getNewValue();
        newValues.forEach(action -> addPropertyChangeListenerRecursive(action));

        // Refresh the popup menu with the new menu item collections.
        if (event.getSource() instanceof CommonAction) {
          CommonAction sourceAction = (CommonAction)event.getSource();
          // Special case for changes in the top-level popup.
          if (sourceAction == myAction) {
            populatePopup();
          }
          else {
            // Find the popup menu that needs to be refresh based on the current event.
            JMenuItem sourceMenuItem = findMenuRecursive(myPopup, sourceAction);
            // TODO - currently this does not handle changing menu type. e.g. A leaf menu turning into a menu with children, or vice versa.
            if (sourceMenuItem instanceof JMenu) {
              JMenu sourceMenu = (JMenu)sourceMenuItem;
              populateMenuRecursive(sourceMenu, newValues);
              JPopupMenu sourcePopup = sourceMenu.getPopupMenu();
              if (sourcePopup.isShowing()) {
                // This currently causes a flicker. It might be worth investigating if there is a better way.
                sourcePopup.pack();
              }
            }
          }
        }
        break;
      case Action.NAME:
      case Action.SMALL_ICON:
      case CommonAction.SHOW_EXPAND_ARROW_CHANGED:
      case CommonAction.SELECTED_CHANGED:
        // TODO handle these changes.
        break;
      default:
        break;
    }
  }

  /**
   * @return The {@link CommonMenu} within the popup menu hierarchy that matches |targetAction|. Null if it is not found.
   */
  @VisibleForTesting
  @Nullable
  JMenuItem findMenuRecursive(@NotNull JPopupMenu popup, @NotNull CommonAction targetAction) {
    for (Component component : popup.getComponents()) {
      if (component instanceof JMenuItem) {
        JMenuItem jmenuItem = (JMenuItem)component;
        if (jmenuItem.getAction() == targetAction) {
          return jmenuItem;
        }

        if (jmenuItem instanceof JMenu) {
          JMenuItem matchedChildMenuItem = findMenuRecursive(((JMenu)jmenuItem).getPopupMenu(), targetAction);
          if (matchedChildMenuItem != null) {
            return matchedChildMenuItem;
          }
        }
      }
    }

    return null;
  }

  private void addPropertyChangeListenerRecursive(@NotNull CommonAction action) {
    action.addPropertyChangeListener(this);
    action.getChildrenActions().forEach(child -> addPropertyChangeListenerRecursive(child));
  }

  private void removePropertyChangeListenerRecursive(@NotNull CommonAction action) {
    action.removePropertyChangeListener(this);
    action.getChildrenActions().forEach(child -> removePropertyChangeListenerRecursive(child));
  }
}
