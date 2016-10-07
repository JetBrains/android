/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.api.actions;

import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * A {@link ViewAction} which toggles state
 */
public abstract class ToggleViewAction extends ViewAction {
  private final Icon mySelectedIcon;
  private final String mySelectedLabel;

  /**
   * Creates a new {@linkplain ToggleViewAction}
   *
   * @param unselectedIcon  the icon shown when the action is in the unselected state
   * @param selectedIcon    the icon shown when the action is in the selected state
   * @param unselectedLabel the label or tooltip to show when in the unselected state
   * @param selectedLabel   the label or tooltip to show when in the selected state (or if null, use the unselectedLabel in both cases)
   */
  public ToggleViewAction(@NotNull Icon unselectedIcon,
                          @NotNull Icon selectedIcon,
                          @NotNull String unselectedLabel,
                          @Nullable String selectedLabel) {
    super(-1, unselectedIcon, unselectedLabel);
    mySelectedIcon = selectedIcon;
    mySelectedLabel = selectedLabel;
  }

  /**
   * Gets the selection state of this action
   *
   * @param editor           the associated IDE editor
   * @param handler          the view handler
   * @param parent           the component this action is associated with
   * @param selectedChildren any selected children of the component
   */
  public abstract boolean isSelected(@NotNull ViewEditor editor,
                                     @NotNull ViewHandler handler,
                                     @NotNull NlComponent parent,
                                     @NotNull List<NlComponent> selectedChildren);

  /**
   * Sets the selection state of this action
   *
   * @param editor           the associated IDE editor
   * @param handler          the view handler
   * @param parent           the component this action is associated with
   * @param selectedChildren any selected children of the component
   */
  public abstract void setSelected(@NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent parent,
                                   @NotNull List<NlComponent> selectedChildren,
                                   boolean selected);

  /**
   * Returns the unselected icon
   */
  @NotNull
  public final Icon getUnselectedIcon() {
    return myIcon;
  }

  /**
   * Returns the selected icon
   */
  @NotNull
  public final Icon getSelectedIcon() {
    return mySelectedIcon;
  }

  @Override
  public final void updatePresentation(@NotNull ViewActionPresentation presentation,
                                       @NotNull ViewEditor editor,
                                       @NotNull ViewHandler handler,
                                       @NotNull NlComponent component,
                                       @NotNull List<NlComponent> selectedChildren,
                                       @InputEventMask int modifiers) {
  }

  /**
   * Method invoked by the system right before this action is about to be changed,
   * or if the action is already showing, when something relevant has changed
   * such as the set of selected children.
   *
   * @param presentation     the presentation to apply visual changes to
   * @param editor           the associated IDE editor
   * @param handler          the view handler
   * @param component        the component this action is associated with
   * @param selectedChildren any selected children of the component
   * @param modifiers        modifiers currently in effect
   * @param selected         whether the action is currently selected
   */
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @InputEventMask int modifiers,
                                 boolean selected) {
  }

  /**
   * Returns the unselected label
   */
  public final String getUnselectedLabel() {
    return myLabel;
  }

  /**
   * Returns the selected label
   */
  public final String getSelectedLabel() {
    return mySelectedLabel;
  }
}
