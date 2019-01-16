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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import icons.StudioIcons;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class BaseLineToggleViewAction extends ToggleViewAction {
  public BaseLineToggleViewAction() {
    super(StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT,
          StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED,
          "Show Baseline", "Hide Baseline");
  }

  @Override
  public boolean isSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren) {
    if (selectedChildren.size() != 1) {
      // The BaseLineToggle can only toggle one component at time
      return false;
    }

    NlComponent selectedComponent = selectedChildren.get(0);
    SceneComponent c = editor.getScene().getSceneComponent(selectedComponent);
    return c != null && c.canShowBaseline();
  }

  @Override
  public void setSelected(@NotNull ViewEditor editor,
                          @NotNull ViewHandler handler,
                          @NotNull NlComponent parent,
                          @NotNull List<NlComponent> selectedChildren,
                          boolean selected) {
    if (selectedChildren.size() == 1) {
      NlComponent selectedComponent = selectedChildren.get(0);
      SceneComponent c = editor.getScene().getSceneComponent(selectedComponent);
      if (c != null) {
        c.setShowBaseline(selected);
        // Update scene since the visibilities of vertical and baseline Targets are changed.
        c.getScene().getSceneManager().update();
      }
    }
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 int modifiers,
                                 boolean selected) {
    super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiers, selected);

    boolean visible = false;
    if (selectedChildren.size() == 1) {
      NlComponent selectedComponent = selectedChildren.get(0);
      int baseline = NlComponentHelperKt.getBaseline(selectedComponent);
      ViewInfo info = NlComponentHelperKt.getViewInfo(selectedComponent);
      if (baseline <= 0 && info != null) {
        baseline = info.getBaseLine();
      }

      visible = baseline >= 0;
    }

    presentation.setVisible(visible);
  }
}
