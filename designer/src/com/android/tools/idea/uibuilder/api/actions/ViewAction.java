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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * An abstract view action; there are many specific sub types
 */
public abstract class ViewAction implements Comparable<ViewAction> {
  protected String myLabel;
  protected Icon myIcon;
  private int myRank = -1;

  /**
   * Creates a new view action.
   */
  public ViewAction() {
    this(-1, null, "");
  }

  /**
   * Creates a new view action with a given icon and label.
   *
   * @param rank the relative sorting order of this action; see {@link #getRank()}
   *             for details
   * @param icon  the icon to be shown if in the toolbar
   * @param label the menu label (if in a context menu) or the tooltip (if in a toolbar)
   */
  public ViewAction(int rank, @Nullable Icon icon, @NotNull String label) {
    myRank = rank;
    myIcon = icon;
    myLabel = label;
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
   */
  public abstract void updatePresentation(@NotNull ViewActionPresentation presentation,
                                          @NotNull ViewEditor editor,
                                          @NotNull ViewHandler handler,
                                          @NotNull NlComponent component,
                                          @NotNull List<NlComponent> selectedChildren);

  /**
   * The relative sorting order of this action. Should be unique for all actions
   * that are shown together. By convention these typically increment by 20 to allow
   * other actions from other sources to insert themselves within the hierarchy. For
   * similar reasons, avoid changing these values later.
   *
   * @return a rank
   */
  public final int getRank() {
    return myRank;
  }

  /**
   * Sets the relative sorting order of this action.
   *
   * @param rank
   * @return this for constructor chaining
   */
  public ViewAction setRank(int rank) {
    myRank = rank;
    return this;
  }

  @Override
  public final int compareTo(ViewAction other) {
    return getRank() - other.getRank();
  }

  /**
   * Returns the default label or tooltip
   *
   * @return the default label or tooltip
   */
  @NotNull
  public String getLabel() {
    return myLabel;
  }

  /**
   * Sets the default label or tooltip
   *
   * @param label the label
   * @return this for constructor chaining
   */
  public ViewAction setLabel(@NotNull String label) {
    myLabel = label;
    return this;
  }

  /**
   * Returns the default icon, if any
   *
   * @return the icon
   */
  public Icon getDefaultIcon() {
    return myIcon;
  }

  /**
   * Sets the default icon, if any
   *
   * @param icon the icon
   * @return this for constructor chaining
   */
  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  /**
   * Returns true if this action affects undo. Actions that update the user's project
   * and should be undoable should return true from this method. It is an error to attempt
   * to update the model from a non-undoable action.
   * <p>
   * Actions that only affect temporary IDE state (such as actions for toggline view overlays
   * etc) should return false here.
   *
   * @return true if this action affects Undo/Redo
   */
  public boolean affectsUndo() {
    return true;
  }
}
