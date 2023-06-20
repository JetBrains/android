/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui;

import com.android.tools.idea.device.explorer.files.ui.menu.item.PopupMenuItem;
import com.intellij.ide.actions.NonEmptyActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PopupHandler;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for building and installing a popup menu for a given {@link JComponent}.
 */
public class ComponentPopupMenu {
  private final @NotNull JComponent myComponent;
  private final @NotNull DefaultActionGroup myGroup;

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
    PopupHandler.installPopupMenu(myComponent, myGroup, ActionPlaces.UNKNOWN);
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

    AnAction action = new AnAction(popupMenuItem.getIcon()) {
      @Override
      public void update(@NotNull AnActionEvent e) {

        Presentation presentation = e.getPresentation();
        presentation.setText(popupMenuItem.getText());
        presentation.setEnabled(popupMenuItem.isEnabled());
        presentation.setVisible(popupMenuItem.isVisible());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        popupMenuItem.run();
      }
    };

    String shortcutId = popupMenuItem.getShortcutId();
    if (!StringUtil.isEmpty(shortcutId)) {
      Keymap active = KeymapManager.getInstance().getActiveKeymap();
      Shortcut[] shortcuts = active.getShortcuts(shortcutId);
      action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myComponent);
    }

    Shortcut[] shortcuts = popupMenuItem.getShortcuts();
    if (shortcuts != null && shortcuts.length > 0) {
      action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myComponent);
    }

    myGroup.add(action);
  }
}
