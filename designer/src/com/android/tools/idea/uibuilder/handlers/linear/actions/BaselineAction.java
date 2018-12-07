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
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.common.model.NlComponent;
import icons.StudioIcons;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Action to set the {@linkplain ATTR_BASELINE_ALIGNED} value.
 */
public class BaselineAction extends LinearLayoutAction {

  @Override
  public void perform(@NotNull ViewEditor editor, @NotNull LinearLayoutHandler handler, @NotNull NlComponent component,
                      @NotNull List<NlComponent> selectedChildren, @JdkConstants.InputEventMask int modifiers) {
    if (!selectedChildren.isEmpty()) {
      boolean align = !isBaselineAligned(selectedChildren.get(0));
      for (NlComponent selected : selectedChildren) {
        selected.setAttribute(ANDROID_URI, ATTR_BASELINE_ALIGNED, align ? null : VALUE_FALSE);
      }
    }
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull LinearLayoutHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @JdkConstants.InputEventMask int modifiers) {
    if (selectedChildren.isEmpty()) {
      presentation.setVisible(false);
    }
    else {
      presentation.setVisible(true);
      boolean align = !isBaselineAligned(selectedChildren.get(0));
      presentation.setIcon(align ? StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED : StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_OFF);
      presentation.setLabel(align ? "Align with the baseline" : "Do not align with the baseline");
    }
  }

  private static boolean isBaselineAligned(NlComponent component) {
    String value = component.getAttribute(ANDROID_URI, ATTR_BASELINE_ALIGNED);
    return value == null || Boolean.parseBoolean(value);
  }
}
