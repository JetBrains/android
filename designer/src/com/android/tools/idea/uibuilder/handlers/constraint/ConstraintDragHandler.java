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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.List;

/**
 * Handles drag'n drop on a ConstraintLayout
 */
public class ConstraintDragHandler extends DragHandler {

  public ConstraintDragHandler(@NotNull ViewEditor editor,
                               @NotNull ConstraintLayoutHandler constraintLayoutHandler,
                               @NotNull NlComponent layout,
                               @NotNull List<NlComponent> components, DragType type) {
    super(editor, constraintLayoutHandler, layout, components, type);
    ConstraintModel.useNewModel(editor.getModel());
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    if (this.components.size() == 1) {
      NlComponent component = this.components.get(0);

      int ax = ConstraintModel.getModel().pxToDp(x - this.layout.x - this.layout.getPadding().left - component.w / 2);
      int ay = ConstraintModel.getModel().pxToDp(y - this.layout.y - this.layout.getPadding().top - component.h / 2);
      component.x = x;
      component.y = y;
      NlComponent root = component.getRoot();
      root.ensureNamespace(SdkConstants.SHERPA_PREFIX, SdkConstants.AUTO_URI);
      ConstraintUtilities.setEditorPosition(component, ax, ay);
    }
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    // do nothing for now
  }
}
