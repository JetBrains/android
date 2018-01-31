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
package com.android.tools.adtui.model.stdui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * An action that supports nested children actions.
 *
 * TODO include mnemonic + accelator keys info.
 * TODO include enable/disable listeners.
 */
public class CommonAction extends AbstractAction {
  public static final String CHILDREN_ACTION_CHANGED = "childrenActions";
  public static final String SHOW_EXPAND_ARROW_CHANGED = "showExpandArrow";
  public static final String SELECTED_CHANGED = "isSelected";

  @NotNull private final List<CommonAction> myChildrenActions;
  @Nullable private Runnable myAction;
  private boolean myShowExpandArrow;
  private boolean myIsSelected;

  public CommonAction(@NotNull String text, @Nullable Icon icon) {
    this(text, icon, null);
  }

  public CommonAction(@NotNull String text, @Nullable Icon icon, @Nullable Runnable action) {
    super(text, icon);
    myAction = action;
    myChildrenActions = new ArrayList<>();
  }

  @NotNull
  public String getText() {
    return (String)getValue(Action.NAME);
  }

  @Nullable
  public Icon getIcon() {
    return (Icon)getValue(Action.SMALL_ICON);
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent e) {
    if (myAction != null) {
      myAction.run();
    }
  }

  public int getChildrenActionCount() {
    return myChildrenActions.size();
  }

  @NotNull
  public List<CommonAction> getChildrenActions() {
    return myChildrenActions;
  }

  public void setAction(@NotNull Runnable action) {
    myAction = action;
  }

  public void setSelected(boolean value) {
    if (value == myIsSelected) {
      return;
    }

    myIsSelected = value;
    firePropertyChange(SELECTED_CHANGED, !myIsSelected, myIsSelected);
  }

  public boolean isSelected() {
    return myIsSelected;
  }

  public void setShowExpandArrow(boolean value) {
    if (value == myShowExpandArrow) {
      return;
    }

    myShowExpandArrow = value;
    firePropertyChange(SHOW_EXPAND_ARROW_CHANGED, !myShowExpandArrow, myShowExpandArrow);
  }

  public boolean getShowExpandArrow() {
    return myShowExpandArrow;
  }

  public void addChildrenActions(@NotNull CommonAction... actions) {
    List<CommonAction> oldActions = new ArrayList<>(myChildrenActions);
    for (CommonAction action : actions) {
      myChildrenActions.add(action);
    }
    firePropertyChange(CHILDREN_ACTION_CHANGED, oldActions, myChildrenActions);
  }

  public void clear() {
    List<CommonAction> oldActions = new ArrayList<>(myChildrenActions);
    myChildrenActions.clear();
    firePropertyChange(CHILDREN_ACTION_CHANGED, oldActions, myChildrenActions);
  }
}
