/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model;

import com.intellij.android.designer.model.layout.actions.ToggleRenderModeAction;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;

import java.util.List;

public class RadScrollViewLayout extends RadSingleChildrenViewLayout {
  @Override
  public void addContainerSelectionActions(DesignerEditorPanel designer,
                                           DefaultActionGroup actionGroup,
                                           List<? extends RadViewComponent> selection) {

    // Add render mode action
    if (myContainer != null && (myContainer.isBackground() || myContainer.getParent() != null && myContainer.getParent().isBackground())) {
      actionGroup.add(new ToggleRenderModeAction(designer));
      actionGroup.add(new Separator());
    }

    super.addContainerSelectionActions(designer, actionGroup, selection);
  }
}