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
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.intellij.ui.PopupMenuListenerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
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
          populateAndShowPopup();
        }
      }
    });

    addPropertyChangeListenerRecursive(myAction);
  }

  @Override
  @NotNull
  public CommonAction getAction() {
    return myAction;
  }

  private void populateAndShowPopup() {
    myPopup.removeAll();
    List<CommonAction> actions = myAction.getChildrenActions();
    for (CommonAction action : actions) {
      JMenuItem menu;
      if (action.getChildrenActionCount() == 0) {
        menu = new CommonMenuItem(action);
      }
      else {
        menu = new CommonMenu(action);
        populateMenuRecursive(menu, action.getChildrenActions());
      }
      menu.setFont(getFont());
      myPopup.add(menu);
    }

    myPopup.show(this, 0, this.getHeight());
  }

  private void populateMenuRecursive(JMenuItem parent, List<CommonAction> actions) {
    for (CommonAction action : actions) {
      JMenuItem menu;
      if (action.getChildrenActionCount() == 0) {
        menu = new CommonMenuItem(action);
      }
      else {
        menu = new CommonMenu(action);
        populateMenuRecursive(menu, action.getChildrenActions());
      }
      menu.setFont(getFont());
      parent.add(menu);

      // Close and repopulate the dropdown if any of the descendant actions have changed.
      action.addPropertyChangeListener(this);
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
        // First hide the popup if it is showing.
        if (myPopup.isShowing()) {
          myPopup.setVisible(false);
        }

        // Unregister the listeners on the old list of actions
        List<CommonAction> oldValues = (List<CommonAction>)event.getOldValue();
        oldValues.forEach(action -> removePropertyChangeListenerRecursive(action));

        // Register the new listeners
        List<CommonAction> newValues = (List<CommonAction>)event.getNewValue();
        newValues.forEach(action -> addPropertyChangeListenerRecursive(action));

        // TODO restore popup if it is showing.
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

  private void addPropertyChangeListenerRecursive(@NotNull CommonAction action) {
    action.addPropertyChangeListener(this);
    action.getChildrenActions().forEach(child -> addPropertyChangeListenerRecursive(child));
  }

  private void removePropertyChangeListenerRecursive(@NotNull CommonAction action) {
    action.removePropertyChangeListener(this);
    action.getChildrenActions().forEach(child -> removePropertyChangeListenerRecursive(child));
  }
}
