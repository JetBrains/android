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
package com.intellij.android.designer.designSurface.layout.grid;

import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.IGridProvider;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.SHOW_STATIC_GRID;

/**
 * @author Alexander Lobas
 */
public class GridDecorator extends StaticDecorator {
  public GridDecorator(RadComponent component) {
    super(component);
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    //noinspection ConstantConditions
    if (SHOW_STATIC_GRID) {
      DesignerGraphics.useStroke(DrawingStyle.GRID, g);

      Rectangle bounds = component.getBounds(layer);
      GridInfo gridInfo = ((IGridProvider)component).getGridInfo();
      Dimension gridSize = gridInfo.getSize(layer);

      for (int column = 0; column < gridInfo.vLines.length; column++) {
        int x = gridInfo.getCellPosition(layer, 0, column).x;
        g.drawLine(bounds.x + x, bounds.y, bounds.x + x, bounds.y + gridSize.height);
      }
      for (int row = 0; row < gridInfo.hLines.length; row++) {
        int y = gridInfo.getCellPosition(layer, row, 0).y;
        g.drawLine(bounds.x, bounds.y + y, bounds.x + gridSize.width, bounds.y + y);
      }
    }
  }
}