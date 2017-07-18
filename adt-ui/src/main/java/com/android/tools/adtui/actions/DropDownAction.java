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
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Button Action with a drop down popup and a text.
 *
 * <p> It extend {@link DefaultActionGroup} so action can be added to the popup using the {{@link #add(AnAction)}} method.
 * <p> If a class needs to update the popup actions dynamically, it can subclass this call and override the {@link #updateActions()}
 * method. This method will be called before opening the popup menu
 */
public class DropDownAction extends DefaultActionGroup implements CustomComponentAction {

  @Nullable private JBPopup myCurrentPopup = null;

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

  @Override
  public void actionPerformed(@NotNull AnActionEvent eve) {
    DropDownActionButton button = getActionButton(eve);
    if (button == null) {
      return;
    }
    updateActions();
    JPanel componentPopup = createCustomComponentPopup();
    if (componentPopup == null) {
      showPopupMenu(eve, button);
    }
    else {
      showJBPopup(eve, button, componentPopup);
    }
  }

  private void showPopupMenu(@NotNull AnActionEvent eve, @NotNull DropDownActionButton button) {
    ActionManagerImpl am = (ActionManagerImpl)ActionManager.getInstance();
    am.createActionPopupMenu(eve.getPlace(), this).getComponent().show(button, 0, button.getHeight());
  }

  private void showJBPopup(@NotNull AnActionEvent eve, @NotNull DropDownActionButton button, @NotNull JPanel componentPopup) {
    JBPopup popup = createJBPopup(componentPopup);
    Component owner = eve.getInputEvent().getComponent();
    Point location = owner.getLocationOnScreen();
    location.translate(0, owner.getHeight());
    popup.showInScreenCoordinates(owner, location);
    button.setSelected(true);
    ((DropDownAction)button.getAction()).myCurrentPopup = popup;
  }

  private static DropDownActionButton getActionButton(@NotNull AnActionEvent eve) {
    return (DropDownActionButton)eve.getPresentation().getClientProperty(CustomComponentAction.CUSTOM_COMPONENT_PROPERTY);
  }

  @Override
  @NotNull
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    DropDownActionButton button = new DropDownActionButton(this, presentation, ActionPlaces.TOOLBAR);
    updateActions();
    return button;
  }

  @NotNull
  private JBPopup createJBPopup(@NotNull JPanel content) {
    JBPopup popupMenu;
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    popupMenu = popupFactory.createComponentPopupBuilder(content, content).createPopup();
    popupMenu.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        super.onClosed(event);
        DropDownActionButton button = (DropDownActionButton)event.asPopup().getOwner();
        button.setSelected(false);
        button.revalidate();
        button.repaint();
        event.asPopup().removeListener(this);
        ((DropDownAction)button.getAction()).myCurrentPopup = null;
      }
    });
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
      myCurrentPopup.closeOk(null);
      myCurrentPopup = null;
    }
  }
}
