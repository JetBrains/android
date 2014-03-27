/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.android.designer.model.layout.relative;

import com.android.tools.idea.designer.SegmentType;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.designSurface.graphics.DirectionResizePoint;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.ResizeSelectionDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;

import java.awt.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class RelativeLayoutResizeOperation implements EditOperation {
  public static final String TYPE = "relative_resize";

  private final OperationContext myContext;
  private RadViewComponent myComponent;
  private RadViewComponent myContainer;
  private GuidelinePainter myFeedback;
  private ResizeHandler myResizeHandler;
  private MultiLineTooltipManager myTooltip;

  public RelativeLayoutResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myContainer = (RadViewComponent)myComponent.getParent();
  }

  @Override
  public void setComponents(List<RadComponent> components) {
    assert components.size() == 1 : components;
    myComponent = (RadViewComponent)components.get(0);
    myContainer = (RadViewComponent)myComponent.getParent();
  }

  @Override
  public void showFeedback() {
    if (myFeedback == null) {
      SegmentType horizontalEdgeType = null;
      SegmentType verticalEdgeType = null;
      int direction = myContext.getResizeDirection();
      if ((direction & Position.NORTH) != 0) {
        horizontalEdgeType = SegmentType.TOP;
      }
      if ((direction & Position.SOUTH) != 0) {
        horizontalEdgeType = SegmentType.BOTTOM;
      }
      if ((direction & Position.WEST) != 0) {
        verticalEdgeType = SegmentType.LEFT;
      }
      if ((direction & Position.EAST) != 0) {
        verticalEdgeType = SegmentType.RIGHT;
      }
      myResizeHandler = new ResizeHandler(myContainer, myComponent, myContext, horizontalEdgeType, verticalEdgeType);
      myFeedback = new GuidelinePainter(myResizeHandler);
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.add(myFeedback);
      myFeedback.setBounds(0, 0, layer.getWidth(), layer.getHeight());
      myTooltip = new MultiLineTooltipManager(layer, 4);
    }

    Rectangle bounds = myContext.getTransformedRectangle(myComponent.getBounds(myContext.getArea().getFeedbackLayer()));
    bounds.width = Math.max(bounds.width, 0);
    bounds.height = Math.max(bounds.height, 0);

    myResizeHandler.updateResize(myComponent, bounds, myContext.getModifiers());
    myFeedback.repaint();

    // Update the text
    describeMatch(myResizeHandler.getCurrentLeftMatch(), 0, myResizeHandler.getLeftMarginDp(), ATTR_LAYOUT_MARGIN_LEFT);
    describeMatch(myResizeHandler.getCurrentRightMatch(), 1, myResizeHandler.getRightMarginDp(), ATTR_LAYOUT_MARGIN_RIGHT);
    describeMatch(myResizeHandler.getCurrentTopMatch(), 2, myResizeHandler.getTopMarginDp() , ATTR_LAYOUT_MARGIN_TOP);
    describeMatch(myResizeHandler.getCurrentBottomMatch(), 3, myResizeHandler.getBottomMarginDp(), ATTR_LAYOUT_MARGIN_BOTTOM);

    // Position the tooltip
    Point location = myContext.getLocation();
    myTooltip.update(myContainer, location);
  }

  private void describeMatch(Match m, int line, int margin, String marginAttribute) {
    if (m == null) {
      myTooltip.setVisible(line, false);
      return;
    }

    myTooltip.setVisible(line, true);
    TextFeedback feedback = myTooltip.getFeedback(line);
    m.describe(feedback, margin, marginAttribute);
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      myTooltip.dispose();
      myFeedback = null;
      myTooltip = null;
      layer.repaint();
    }
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myResizeHandler.removeCycles();
        myResizeHandler.applyConstraints(myComponent);
      }
    });
  }

  public static void addResizePoints(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.NORTH_WEST, TYPE,
                                                "Change layout:width x layout:height, top x left alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.NORTH, TYPE, "Change layout:height, top alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.NORTH_EAST, TYPE,
                                                "Change layout:width x layout:height, top x right alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.EAST, TYPE, "Change layout:width, right alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH_EAST, TYPE,
                                                "Change layout:width x layout:height, bottom x right alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH, TYPE,
                                                "Change layout:height, bottom alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH_WEST, TYPE,
                                                "Change layout:width x layout:height, bottom x left alignment"));
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.WEST, TYPE, "Change layout:width, left alignment"));
  }
}
