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

import java.util.List;

/**
 * A group of mutually exclusive toggle actions. When one is invoked, the others
 * are cleared.
 */
public class ToggleViewActionGroup extends ViewAction {
  private final List<ToggleViewAction> myActions;

  /**
   * Creates a new view action.
   *
   * @param actions  the set of toggle actions in this group
   */
  public ToggleViewActionGroup(@NotNull List<ToggleViewAction> actions) {
    myActions = actions;
  }

  /**
   * Returns the list of toggle actions in this group
   *
   * @return the list of toggle actions in this group
   */
  @NotNull
  public List<ToggleViewAction> getActions() {
    return myActions;
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @InputEventMask int modifiers) {
  }
}

