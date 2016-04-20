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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A sub menu for a group of actions
 */
public final class ViewActionMenu extends ViewAction {
  private final List<ViewAction> myActions;

  /**
   * Creates a new view action.
   *
   * @param rank     the sorting order of this action
   * @param menuName the menu label
   * @param actions  the set of actions in this menu
   */
  public ViewActionMenu(int rank, @NotNull String menuName, @NotNull List<ViewAction> actions) {
    super(rank, null, menuName);
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
                                 @NotNull List<NlComponent> selectedChildren) {
    presentation.setLabel(myLabel);
  }
}
