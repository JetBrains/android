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
package com.intellij.android.designer.model.layout.actions;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DominateWeightsAction extends LayoutAction {
  private final RadViewComponent myLayout;
  private final List<? extends RadViewComponent> mySelectedChildren;

  public DominateWeightsAction(@NotNull DesignerEditorPanel designer,
                               @NotNull RadViewComponent layout,
                               @NotNull List<? extends RadViewComponent> selectedChildren) {
    super(designer, "Assign All Weight", null, AndroidDesignerIcons.DominateWeight);
    myLayout = layout;
    mySelectedChildren = selectedChildren;
  }

  @Override
  protected void performWriteAction() {
    ClearWeightsAction.clearWeights(myLayout, RadViewComponent.getViewComponents(myLayout.getChildren()));
    DistributeWeightsAction.distributeWeights(myLayout, mySelectedChildren);
  }
}
