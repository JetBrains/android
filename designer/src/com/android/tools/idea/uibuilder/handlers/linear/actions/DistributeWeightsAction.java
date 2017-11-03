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
import icons.AndroidDesignerIcons;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DistributeWeightsAction extends LinearLayoutAction {
  public DistributeWeightsAction() {
    super(AndroidDesignerIcons.DistributeWeights, "Distribute Weights Evenly");
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull LinearLayoutHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @JdkConstants.InputEventMask int modifiers) {
    presentation.setVisible(selectedChildren.size() > 1);
  }

  @Override
  protected void perform(@NotNull ViewEditor editor,
                         @NotNull LinearLayoutHandler handler,
                         @NotNull NlComponent component,
                         @NotNull List<NlComponent> selectedChildren,
                         int modifiers) {
    handler.distributeWeights(component, selectedChildren);
  }
}
