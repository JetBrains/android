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
package com.android.tools.idea.explorer.ui;

import com.intellij.ide.actions.NonEmptyActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Utility class for building and installing a popup menu for a given {@link JComponent}.
 */
public class ComponentPopupMenu {
  @NotNull private JComponent myComponent;
  @NotNull private DefaultActionGroup myGroup;

  public ComponentPopupMenu(@NotNull JComponent component) {
    myComponent = component;
    myGroup = new DefaultActionGroup();
  }

  ComponentPopupMenu(@NotNull JComponent component, @NotNull DefaultActionGroup group) {
    myComponent = component;
    myGroup = group;
  }

  @NotNull
  public ActionGroup getActionGroup() {
    return myGroup;
  }

  public void install() {
    PopupHandler.installUnknownPopupHandler(myComponent, myGroup, ActionManager.getInstance());
  }

  public void addSeparator() {
    myGroup.addSeparator();
  }

  public ComponentPopupMenu addPopup(@SuppressWarnings("SameParameterValue") @NotNull String name) {
    ComponentPopupMenu newMenu = new ComponentPopupMenu(myComponent, new NonEmptyActionGroup());
    ActionGroup subGroup = newMenu.getActionGroup();
    subGroup.setPopup(true);
    subGroup.getTemplatePresentation().setText(name);
    myGroup.add(subGroup);
    return newMenu;
  }

  public void addItem(@NotNull PopupMenuItem popupMenuItem) {

    AnAction action = new AnAction(null, null, popupMenuItem.getIcon()) {
      @Override
      public void update(AnActionEvent e) {
        super.update(e);

        Presentation presentation = e.getPresentation();
        presentation.setText(popupMenuItem.getText());
        presentation.setEnabled(popupMenuItem.isEnabled());
        presentation.setVisible(popupMenuItem.isVisible());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        popupMenuItem.run();
      }
    };

    String shortcutId = popupMenuItem.getShortcutId();
    if (!StringUtil.isEmpty(shortcutId)) {
      Keymap active = KeymapManager.getInstance().getActiveKeymap();
      if (active != null) {
        Shortcut[] shortcuts = active.getShortcuts(shortcutId);
        action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myComponent);
      }
    }

    Shortcut[] shortcuts = popupMenuItem.getShortcuts();
    if (shortcuts != null && shortcuts.length > 0) {
      action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myComponent);
    }

    myGroup.add(action);
  }
}
