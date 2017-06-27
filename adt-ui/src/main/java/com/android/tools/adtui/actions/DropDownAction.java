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
package com.android.tools.adtui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.ui.PopupMenuListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;

/**
 * Button Action with a drop down popup and a text
 */
public class DropDownAction extends DefaultActionGroup implements CustomComponentAction {

  private Icon myIcon;
  private String myDescription;

  public DropDownAction(@Nullable String title, @Nullable String description, @Nullable Icon icon) {
    super(title, true);
    myIcon = icon;
    myDescription = description;
  }

  public DropDownAction(@Nullable String title,
                        @Nullable String description,
                        @Nullable Icon icon,
                        @NotNull AnAction... actions) {
    this(title, description, icon);
    addAll(actions);
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  private static void showPopup(@NotNull Component invoker, @NotNull DropDownActionButton button) {
    button.setSelected(true);
    button.getComponentPopupMenu().show(invoker, 0, invoker.getHeight());
  }

  @Override
  public void actionPerformed(AnActionEvent eve) {
    DropDownActionButton button = (DropDownActionButton) eve.getPresentation().getClientProperty(CustomComponentAction.CUSTOM_COMPONENT_PROPERTY);
    if(button == null) {
      return;
    }
    if(updateActions()) {
      button.setComponentPopupMenu(createPopupMenu(button));
    }
    showPopup(eve.getInputEvent().getComponent(), button);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    DropDownActionButton button = new DropDownActionButton(this, presentation, ActionPlaces.TOOLBAR);
    presentation.setIcon(myIcon);
    presentation.setEnabled(true);
    presentation.setDescription(myDescription);
    button.setComponentPopupMenu(createPopupMenu(button));
    return button;
  }

  @NotNull
  private JPopupMenu createPopupMenu(@NotNull DropDownActionButton button) {
    JPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLBAR, this).getComponent();
    PopupMenuListenerAdapter listener = new PopupMenuListenerAdapter() {

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        button.setSelected(false);
      }
    };
    popupMenu.addPopupMenuListener(listener);
    return popupMenu;
  }

  protected boolean updateActions() {
    return false;
  }

  @Override
  public boolean canBePerformed(DataContext context) {
    return true;
  }
}
