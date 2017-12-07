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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/** A separator between actions */
public final class ViewActionSeparator extends ViewAction {
  private List<ViewAction> myFollowingActions = new ArrayList<>();
  /**
   * Creates a separator
   */
  public ViewActionSeparator() {
    this(-1);
  }

  /**
   * Creates a separator
   *
   * @param rank the sorting order of this separator
   */
  public ViewActionSeparator(int rank) {
    super(rank, null, "");
  }

  /**
   * Setup {@link ViewActionSeparator} with a list of actions following the separator.
   * Use this method to omit separators when all the following actions are invisible.
   * There is no need to call this method if actions are never invisible.
   *
   * @param actions the actions for e.g. a toolbar that have separators between actions
   *                that may be invisible.
   */
  public static void setupFollowingActions(@NotNull List<ViewAction> actions) {
    ViewActionSeparator separator = null;
    for (ViewAction action : actions) {
      if (action instanceof ViewActionSeparator) {
        separator = (ViewActionSeparator)action;
      }
      else if (separator != null) {
        separator.addFollowingAction(action);
      }
      if (action instanceof NestedViewActionMenu) {
        List<ViewAction> nested = new ArrayList<>();
        ((NestedViewActionMenu)action).getActions().forEach(list -> nested.addAll(list));
        setupFollowingActions(nested);
      }
    }
  }

  /**
   * Register action following this separator.
   */
  private void addFollowingAction(@NotNull ViewAction action) {
    myFollowingActions.add(action);
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @InputEventMask int modifiers) {
  }

  public boolean isVisible(@NotNull ViewEditor editor,
                           @NotNull ViewHandler handler,
                           @NotNull NlComponent component,
                           @NotNull List<NlComponent> selectedChildren) {
    // If there are no following actions: assume that setupFollowingActions
    // was never called, and thus we should not hide separators.
    if (myFollowingActions.isEmpty()) {
      return true;
    }

    // The separator is visible if at least one of the following actions are visible:
    for (ViewAction action : myFollowingActions) {
      SeparatorPresentation following = new SeparatorPresentation();
      action.updatePresentation(following, editor, handler, component, selectedChildren, 0);
      if (following.isVisible()) {
        return true;
      }
    }

    // All following actions are invisible: Don't show the separator either.
    return false;
  }

  private static class SeparatorPresentation implements ViewActionPresentation {
    private boolean myVisible = true;

    @Override
    public void setLabel(@NotNull String label) {}

    @Override
    public void setEnabled(boolean enabled) {}

    @Override
    public void setIcon(@Nullable Icon icon) {}

    @Override
    public void setVisible(boolean visible) {
      myVisible = visible;
    }

    public boolean isVisible() {
      return myVisible;
    }
  }
}
