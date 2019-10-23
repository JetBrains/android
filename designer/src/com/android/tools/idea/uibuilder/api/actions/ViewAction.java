/*
 * Copyright (C) 2018 The Android Open Source Project
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
import java.util.List;
import javax.swing.Icon;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for actions that can operate on {@code View}s
 */
public interface ViewAction extends Comparable<ViewAction> {
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
   */
  void updatePresentation(@NotNull ViewActionPresentation presentation,
                          @NotNull ViewEditor editor,
                          @NotNull ViewHandler handler,
                          @NotNull NlComponent component,
                          @NotNull List<NlComponent> selectedChildren,
                          @JdkConstants.InputEventMask int modifiers);

  /**
   * Method invoked by the system when this action is performed.
   *
   * @param editor           the associated IDE editor
   * @param handler          the view handler
   * @param component        the component this action is associated with
   * @param selectedChildren any selected children of the component
   * @param modifiers        modifiers currently in effect
   */
  void perform(@NotNull ViewEditor editor,
               @NotNull ViewHandler handler,
               @NotNull NlComponent component,
               @NotNull List<NlComponent> selectedChildren,
               @JdkConstants.InputEventMask int modifiers);

  /**
   * The relative sorting order of this action. Should be unique for all actions
   * that are shown together. By convention these typically increment by 20 to allow
   * other actions from other sources to insert themselves within the hierarchy. For
   * similar reasons, avoid changing these values later.
   * @deprecated Do not use ranks. Ordering should be based on list ordering.
   */
  int getRank();

  @Override
  default int compareTo(ViewAction other) {
    return getRank() - other.getRank();
  }

  /**
   * Returns the default label or tooltip
   */
  @NotNull
  String getLabel();

  /**
   * Returns the default icon, if any
   */
  @Nullable
  Icon getIcon();

  /**
   * Returns true if this action affects undo. Actions that update the user's project
   * and should be undoable should return true from this method. It is an error to attempt
   * to update the model from a non-undoable action.
   * <p>
   * Actions that only affect temporary IDE state (such as actions for toggline view overlays
   * etc) should return false here.
   */
  boolean affectsUndo();
}
