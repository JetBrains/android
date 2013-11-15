package com.intellij.android.designer.designSurface.layout.grid;

import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.ResizeSelectionDecorator;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class GridSelectionDecorator extends ResizeSelectionDecorator {
  public GridSelectionDecorator(DrawingStyle style) {
    super(style);
  }

  @Override
  protected Rectangle getBounds(DecorationLayer layer, RadComponent component) {
    return getCellBounds(layer, component);
  }

  public abstract Rectangle getCellBounds(Component layer, RadComponent component);

  public static Rectangle calculateBounds(Component layer,
                                          GridInfo gridInfo,
                                          RadComponent parent,
                                          RadComponent component,
                                          int row,
                                          int column,
                                          int rowSpan,
                                          int columnSpan) {
    Rectangle bounds = parent.getBounds(layer);

    Point topLeft = gridInfo.getCellPosition(layer, row, column);
    Point bottomRight = gridInfo.getCellPosition(layer, row + rowSpan, column + columnSpan);
    bounds.x += topLeft.x;
    bounds.width = bottomRight.x - topLeft.x;
    bounds.y += topLeft.y;
    bounds.height = bottomRight.y - topLeft.y;

    Rectangle componentBounds = component.getBounds(layer);
    if (!bounds.contains(componentBounds.x, componentBounds.y)) {
      return componentBounds;
    }

    return bounds;
  }
}
