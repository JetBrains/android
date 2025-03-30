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
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@linkplain DirectViewAction} is an action related to one or more views.
 * Examples include layout parameter actions provided for children, such
 * as LinearLayout's view handler providing actions to distribute the weights
 * evenly, or actions intended for the view itself, such as an action to toggle
 * orientation.
 * <p>
 * These actions are typically shown in the layout actions toolbar but can also
 * be included in context menus for actions.
 */
public abstract class DirectViewAction extends AbstractViewAction {

  /**
   * Returns an optional confirmation message to be displayed to the user before performing the action.
   */
  @Nullable
  public String getConfirmationMessage() {
    return null;
  }

  /**
   * Creates a new view action. If you use this method you must also override
   * {@link ViewAction#updatePresentation(ViewActionPresentation, ViewEditor, ViewHandler, NlComponent, List, int)}
   * to set an icon or label just-in-time.
   */
  public DirectViewAction() {
    this(null, "");
  }

  /**
   * Creates a new view action with a given icon and label.
   *
   * @param icon        the icon to be shown if in the toolbar
   * @param label the menu label (if in a context menu) or the tooltip (if in a toolbar)
   */
  public DirectViewAction(@Nullable Icon icon, @NotNull String label) {
    super(icon, label);
  }
}
