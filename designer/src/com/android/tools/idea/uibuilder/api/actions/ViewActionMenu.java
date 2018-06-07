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
import com.android.tools.idea.common.model.NlComponent;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * A sub menu for a group of actions
 */
public class ViewActionMenu extends ViewAction {
  protected final List<ViewAction> myActions;

  /**
   * Creates a new view action.
   *
   * @param menuName the menu label
   * @param icon     the (optional) icon
   * @param actions  the set of actions in this menu
   */
  public ViewActionMenu(@NotNull String menuName, @Nullable Icon icon, @NotNull List<ViewAction> actions) {
    this(-1, menuName, icon, actions);
  }

  /**
   * Creates a new view action.
   *
   * @param rank     the sorting order of this action
   * @param menuName the menu label
   * @param icon     the (optional) icon
   * @param actions  the set of actions in this menu
   */
  public ViewActionMenu(int rank, @NotNull String menuName, @Nullable Icon icon, @NotNull List<ViewAction> actions) {
    super(rank, icon, menuName);
    myActions = actions;
  }

  /**
   * Returns the list of actions in this menu
   *
   * @return the list of actions in this menu
   */
  @NotNull
  public List<ViewAction> getActions() {
    return myActions;
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @InputEventMask int modifiers) {
    presentation.setLabel(myLabel);
  }
}
