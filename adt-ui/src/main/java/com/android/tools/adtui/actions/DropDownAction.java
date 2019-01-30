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

  private static final Icon BLANK_ICON = new Icon() {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

    }

    @Override
    public int getIconWidth() {
      return 0;
    }

    @Override
    public int getIconHeight() {
      return 0;
    }
  };

  @Nullable private JBPopup myCurrentPopup = null;
  /**
   * True if the actions have been initialized so {@link #hasDropDownArrow()} does not need to call it again before deciding its return
   * value.
   */
  private boolean myActionsInitialized = false;

  public DropDownAction(@Nullable String title, @Nullable String description, @Nullable Icon icon) {
    super(title, true);
    Presentation presentation = getTemplatePresentation();
    presentation.setText(title);
    presentation.setDescription(description);
    if (icon != null) {
      presentation.setIcon(icon);
    }
    else {
      presentation.setIcon(BLANK_ICON);
      presentation.setDisabledIcon(BLANK_ICON);
    }
  }

  private void updateActionsInternal() {
    myActionsInitialized = true;
    updateActions();
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
    updateActionsInternal();
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
    JPopupMenu component = am.createActionPopupMenu(eve.getPlace(), this).getComponent();
    component.addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        button.setSelected(true);
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        button.setSelected(false);
      }
    });
    component.show(button, 0, button.getHeight());
  }

  private void showJBPopup(@NotNull AnActionEvent eve, @NotNull DropDownActionButton button, @NotNull JPanel componentPopup) {
    JBPopup popup = createJBPopup(componentPopup);
    Component owner = eve.getInputEvent().getComponent();
    Point location = owner.getLocationOnScreen();
    location.translate(0, owner.getHeight());
    popup.showInScreenCoordinates(owner, location);
    ((DropDownAction)button.getAction()).myCurrentPopup = popup;
  }

  private static DropDownActionButton getActionButton(@NotNull AnActionEvent eve) {
    return (DropDownActionButton)eve.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
  }

  @Override
  @NotNull
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    return new DropDownActionButton(this, presentation, ActionPlaces.TOOLBAR);
  }

  @NotNull
  private JBPopup createJBPopup(@NotNull JPanel content) {
    JBPopup popupMenu;
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    popupMenu = popupFactory.createComponentPopupBuilder(content, content).createPopup();
    popupMenu.addListener(new JBPopupAdapter() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        super.beforeShown(event);
        DropDownActionButton button = (DropDownActionButton)event.asPopup().getOwner();
        button.setSelected(true);
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        super.onClosed(event);
        DropDownActionButton button = (DropDownActionButton)event.asPopup().getOwner();
        button.setSelected(false);
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
  public boolean canBePerformed(@NotNull DataContext context) {
    return true;
  }

  /**
   * This is used by the {@link DropDownActionButton} to decide when to show the drop down arrow. This is usually decided by looking if the
   * number of actions in this group is more than 1. If calculating the actions is expensive, overriding this method can avoid running
   * updateActions just to decide the drop down arrow state.
   */
  protected boolean hasDropDownArrow() {
    if (!myActionsInitialized) {
      updateActionsInternal();
    }
    return getChildrenCount() > 1;
  }

  public void closePopup() {
    if (myCurrentPopup != null) {
      myCurrentPopup.closeOk(null);
      myCurrentPopup = null;
    }
  }
}
