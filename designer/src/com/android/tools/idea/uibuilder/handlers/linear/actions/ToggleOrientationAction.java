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

import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import icons.StudioIcons;
import java.util.ArrayList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Changes orientation from horizontal to vertical and vice versa. The direction
 * of the change is based on the state of the first selected component.
 */
public class ToggleOrientationAction extends LinearLayoutAction {

  @Override
  protected void perform(@NotNull ViewEditor editor,
                         @NotNull LinearLayoutHandler handler,
                         @NotNull NlComponent component,
                         @NotNull List<NlComponent> selectedChildren,
                         @JdkConstants.InputEventMask int modifiers) {
    List<NlComponent> targetLinearLayouts = getTargetLinearLayouts(component, selectedChildren);
    NlWriteCommandActionUtil.run(targetLinearLayouts, "Change LinearLayout orientation", () -> {
      for (NlComponent child : targetLinearLayouts) {
        boolean isVertical = handler.isVertical(child);
        String value = isVertical ? VALUE_HORIZONTAL : VALUE_VERTICAL;
        child.setAttribute(ANDROID_URI, ATTR_ORIENTATION, value);
      }
    });
  }

  @Override
  protected void updatePresentation(@NotNull ViewActionPresentation presentation,
                                    @NotNull ViewEditor editor,
                                    @NotNull LinearLayoutHandler handler,
                                    @NotNull NlComponent component,
                                    @NotNull List<NlComponent> selectedChildren,
                                    int modifiers) {
    List<NlComponent> targetLinearLayouts = getTargetLinearLayouts(component, selectedChildren);
    // It is possible that part of them are vertical and others are horizontal.
    // Because this is a toggle action, using primary one for displaying icon is fine.
    NlComponent primary = targetLinearLayouts.get(0);
    boolean vertical = handler.isVertical(primary);
    presentation.setLabel("Convert orientation to " + (vertical ? VALUE_HORIZONTAL : VALUE_VERTICAL));
    // If current orientation is vertical, then the icon should be horizontal, as so on.
    Icon icon = vertical ? StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ : StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT;
    presentation.setIcon(icon);
  }

  private static List<NlComponent> getTargetLinearLayouts(@NotNull NlComponent parent, @NotNull List<NlComponent> selectedChildren) {
    // Check if selecting any linear layout
    List<NlComponent> targetLinearLayouts = new ArrayList<>();
    for (NlComponent child : selectedChildren) {
      if (NlComponentHelperKt.isOrHasSuperclass(child, LINEAR_LAYOUT)) {
        targetLinearLayouts.add(child);
      }
    }

    if (targetLinearLayouts.isEmpty()) {
      // No selected linear layout, toggle parent instead.
      targetLinearLayouts.add(parent);
    }
    return targetLinearLayouts;
  }
}

