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
package com.android.tools.idea.uibuilder.handlers.linear.actions;

import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.handlers.DelegatingViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import icons.AndroidDesignerIcons;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Changes orientation from horizontal to vertical and vice versa. The direction
 * of the change is based on the state of the first selected component.
 */
public class ToggleOrientationAction extends DirectViewAction {
  @Override
  public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                      @NotNull List<NlComponent> selectedChildren,
                      @JdkConstants.InputEventMask int modifiers) {
    if (handler instanceof DelegatingViewGroupHandler) {
      handler = ((DelegatingViewGroupHandler)handler).getDelegateHandler();
    }

    if (handler instanceof LinearLayoutHandler) {
      if (!selectedChildren.isEmpty()) {
        LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
        boolean isHorizontal = !linearLayoutHandler.isVertical(selectedChildren.get(0));
        for (NlComponent selected : selectedChildren) {
          String value = isHorizontal ? VALUE_VERTICAL : null; // null: horizontal is the default
          selected.setAttribute(ANDROID_URI, ATTR_ORIENTATION, value);
        }
      }
    }
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @JdkConstants.InputEventMask int modifiers) {
    if (handler instanceof DelegatingViewGroupHandler) {
      handler = ((DelegatingViewGroupHandler)handler).getDelegateHandler();
    }

    if (handler instanceof LinearLayoutHandler) {
      presentation.setVisible(true);
      LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
      boolean vertical = linearLayoutHandler.isVertical(selectedChildren.get(0));

      presentation.setLabel("Convert orientation to " + (!vertical ? VALUE_VERTICAL : VALUE_HORIZONTAL));
      Icon icon = vertical ? AndroidDesignerIcons.SwitchVerticalLinear : AndroidDesignerIcons.SwitchHorizontalLinear;
      presentation.setIcon(icon);
    }
    else {
      presentation.setVisible(false);
    }
  }
}
