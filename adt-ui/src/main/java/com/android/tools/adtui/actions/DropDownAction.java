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
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;

/**
 * Button Action with a drop down popup and a text.
 *
 * <p> It extend {@link DefaultActionGroup} so action can be added to the popup using the {{@link #add(AnAction)}} method.
 * <p> If a class needs to update the popup actions dynamically, it can subclass this call and override the {@link #updateActions()}
 * method. This method will be called before opening the popup menu
 */
public class DropDownAction extends DefaultActionGroup implements CustomComponentAction {

  @Nullable private JPopupMenu myCurrentPopup = null;

  public DropDownAction(@Nullable String title, @Nullable String description, @Nullable Icon icon) {
    super(title, true);
    Presentation presentation = getTemplatePresentation();
    presentation.setText(title);
    presentation.setDescription(description);
    presentation.setIcon(icon != null ? icon : EmptyIcon.ICON_0);
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  private static void showPopup(@NotNull Component invoker, @NotNull DropDownActionButton button) {
    button.setSelected(true);
    JPopupMenu menu = button.getComponentPopupMenu();
    PopupMenuListenerAdapter listener = new PopupMenuListenerAdapter() {

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        button.setSelected(false);
        button.revalidate();
        button.repaint();
        menu.removePopupMenuListener(this);
        ((DropDownAction)button.getAction()).myCurrentPopup = null;
      }
    };
    menu.addPopupMenuListener(listener);
    menu.show(invoker, 0, invoker.getHeight());
    ((DropDownAction)button.getAction()).myCurrentPopup = menu;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent eve) {
    DropDownActionButton button =
      (DropDownActionButton)eve.getPresentation().getClientProperty(CustomComponentAction.CUSTOM_COMPONENT_PROPERTY);
    if (button == null) {
      return;
    }
    if (updateActions()) {
      button.setComponentPopupMenu(createPopupMenu());
    }
    showPopup(eve.getInputEvent().getComponent(), button);
  }

  @Override
  @NotNull
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    DropDownActionButton button = new DropDownActionButton(this, presentation, ActionPlaces.TOOLBAR);
    updateActions();
    button.setComponentPopupMenu(createPopupMenu());
    return button;
  }

  @NotNull
  private JPopupMenu createPopupMenu() {
    JPanel customPanel = createCustomComponentPopup();
    JPopupMenu popupMenu;
    if (customPanel != null) {
      popupMenu = new JBPopupMenu();
      popupMenu.add(customPanel);
    }
    else {
      popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLBAR, this).getComponent();
    }
    return popupMenu;
  }

  /**
   * Subclass should override this method to display a custom popup content.
   * The returned JPanel will be used as the content of the popup and no other actions will be added.
   * <p> The implementing class can have access to the action using the {@link #getChildren(AnActionEvent)} method
   *
   * @return The custom panel to use or null to use the default one
   */
  @Nullable
  protected JPanel createCustomComponentPopup() {
    return null;
  }

  /**
   * If a subclass needs to update the popup menu actions dynamically, it should override this class.
   *
   * @return true id the actions were updated, false otherwise.
   * <p>Returning false allows the popup previous popup instance to be reused
   */
  protected boolean updateActions() {
    return false;
  }

  @Override
  public boolean canBePerformed(@Nullable DataContext context) {
    return true;
  }

  public void closePopup() {
    if (myCurrentPopup != null) {
      myCurrentPopup.setVisible(false);
      myCurrentPopup = null;
    }
  }
}
