/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface.layout.flow;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class FlowBaseOperation extends com.intellij.designer.designSurface.FlowBaseOperation {
  public FlowBaseOperation(RadComponent container, OperationContext context, boolean horizontal) {
    super(container, context, horizontal);
  }

  @Override
  protected Rectangle getBounds(RadComponent component, FeedbackLayer layer) {
    Rectangle bounds = component.getBounds(layer);

    Rectangle margins = ((RadViewComponent)component).getMargins();
    if (margins.x == 0 && margins.y == 0 && margins.width == 0 && margins.height == 0) {
      return bounds;
    }

    // Margin x and y are not actually x and y coordinates; they are
    // dimensions on the left and top sides. Therefore, we should NOT
    // use Rectangle bounds conversion operations, since they will
    // shift coordinate systems
    Dimension topLeft = component.fromModel(layer, new Dimension(margins.x, margins.y));
    Dimension bottomRight = component.fromModel(layer, new Dimension(margins.width, margins.height));

    bounds.x -= topLeft.width;
    bounds.width += topLeft.width;

    bounds.y -= topLeft.height;
    bounds.height += topLeft.height;

    bounds.width += bottomRight.width;
    bounds.height += bottomRight.height;

    return bounds;
  }

  @Override
  protected void execute(@Nullable RadComponent insertBefore) throws Exception {
    AbstractEditOperation.execute(myContext, (RadViewComponent)myContainer, myComponents, (RadViewComponent)insertBefore);
  }
}