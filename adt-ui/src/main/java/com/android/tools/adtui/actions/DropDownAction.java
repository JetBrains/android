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

import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Button Action with a dropdown popup and a text.
 *
 * <p> It extend {@link DefaultActionGroup} so action can be added to the popup using the {{@link #add(AnAction)}} method.
 * <p> If a class needs to update the popup actions dynamically, it can extend this class and override the {@link #updateActions} method.
 * This method will be called before opening the popup menu
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

  public DropDownAction(@Nullable String title, @Nullable String description, @Nullable Icon icon) {
    // TODO(b/215726556) Use the super constructor that takes title, description, icon after the merge of IJ2022.1.1
    super(title, true);
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription(description);
    if (icon != null) {
      presentation.setIcon(icon);
    }
    else {
      presentation.setIcon(BLANK_ICON);
      presentation.setDisabledIcon(BLANK_ICON);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent eve) {
    ActionButton button = getActionButton(eve);
    updateActions(eve.getDataContext());
    if (getChildrenCount() == 0) {
      return;
    }
    if (button == null) {
      if (!isPerformableWithoutActionButton()) {
        return;
      }
      // This means this action is not performed by the action button. For example, it is performed by shortcut key or IJ's search bar.
      // In this case, we show a JBPopup.
      updateActions(eve.getDataContext());
      JPanel componentPopup = createCustomComponentPopup();
      JBPopup popup;
      if (componentPopup != null) {
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(componentPopup, componentPopup).createPopup();
      }
      else {
        popup = JBPopupFactory.getInstance()
          .createActionGroupPopup(eve.getPresentation().getText(), this, eve.getDataContext(), false, null, 10);
      }
      Project project = eve.getProject();
      if (project != null) {
        popup.showCenteredInCurrentWindow(project);
      }
      else {
        popup.showInBestPositionFor(eve.getDataContext());
      }
    }
    else {
      JPanel componentPopup = createCustomComponentPopup();
      if (componentPopup == null) {
        showPopupMenu(eve, button);
      }
      else {
        JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(componentPopup, componentPopup).createPopup();
        Component owner = eve.getInputEvent().getComponent();
        Point location = owner.getLocationOnScreen();
        location.translate(0, owner.getHeight());
        popup.showInScreenCoordinates(owner, location);
        popup.moveToFitScreen();
      }
    }
  }

  /**
   * Return if this dropdown action can be performed without using {@link ActionButton}. For example, this action is registered to and
   * performed by action system.<br>
   * If it is performable, the content of this dropdown wil be shown as a popup dialog.
   */
  protected boolean isPerformableWithoutActionButton() {
    return false;
  }

  private void showPopupMenu(@NotNull AnActionEvent eve, @NotNull ActionButton button) {
    ActionManagerImpl am = (ActionManagerImpl)ActionManager.getInstance();
    JPopupMenu component = am.createActionPopupMenu(eve.getPlace(), this).getComponent();
    JBPopupMenu.showBelow(button, component);
  }

  private static ActionButton getActionButton(@NotNull AnActionEvent eve) {
    return (ActionButton)eve.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
  }

  @Override
  @NotNull
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    if (displayTextInToolbar()) {
      return new ActionButtonWithText(this, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    }
    else {
      return new ActionButton(this, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        protected void updateToolTipText() {
          // Copied from ActionButtonWithText to get the same tooltip behaviour for both types of buttons
          String description = myPresentation.getDescription();
          if (Registry.is("ide.helptooltip.enabled")) {
            HelpTooltip.dispose(this);
            if (StringUtil.isNotEmpty(description)) {
              new HelpTooltip().setDescription(description).installOn(this);
            }
          }
          else {
            setToolTipText(description);
          }
        }
      };
    }
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
  protected boolean updateActions(@NotNull DataContext context) {
    return false;
  }

  @Override
  public boolean canBePerformed(@NotNull DataContext context) {
    return true;
  }

  /**
   * Create the {@link DefaultActionGroup} used by this dropdown action. This group can be used when all the {@link AnAction}s in the
   * sub-menu are {@link com.intellij.openapi.actionSystem.Toggleable} to prevent dropdown menu not disappear issue.
   *
   * @see com.intellij.openapi.actionSystem.impl.Utils#isMultiChoiceGroup
   */
  @NotNull
  public static DefaultActionGroup createSubMenuGroup(@NotNull Supplier<@NlsActions.ActionText String> shortName) {
    return new SubMenuGroup(shortName);
  }

  /**
   * The action group for creating the sub menu in this dropdown action.
   * If all the actions in a popup {@link DefaultActionGroup} are {@link com.intellij.openapi.actionSystem.Toggleable}, the created sub-menu
   * would be flagged as multi choice. When it happens, clicking the action in the submenu would not hide this dropdown the menu.
   * If clicking {@link com.intellij.openapi.actionSystem.Toggleable} {@link AnAction} in sub-menu should hide this dropdown, use this class
   * to store the actions.
   *
   * @see com.intellij.openapi.actionSystem.impl.Utils#isMultiChoiceGroup
   */
  private static class SubMenuGroup extends DefaultActionGroup {

    public SubMenuGroup(@NotNull Supplier<@NlsActions.ActionText String> shortName) {
      super(shortName, true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
