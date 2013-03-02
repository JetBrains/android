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
package com.intellij.android.designer.designSurface.layout.actions;

import com.android.SdkConstants;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.designSurface.graphics.DirectionResizePoint;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.RectangleFeedback;
import com.intellij.android.designer.designSurface.graphics.ResizeSelectionDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;

import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.MAX_MATCH_DISTANCE;

/**
 * @author Alexander Lobas
 */
public class ResizeOperation implements EditOperation {
  public static final String TYPE = "resize_children";

  private final OperationContext myContext;
  private RadViewComponent myComponent;

  private RectangleFeedback myWrapFeedback;
  private RectangleFeedback myFillFeedback;
  private RectangleFeedback myFeedback;
  private TextFeedback myTextFeedback;

  private String myStaticWidth;
  private String myStaticHeight;

  /** Wrap size, in screen pixels */
  private Dimension myWrapSize;

  /** Wrap size, in model pixels (not dp) */
  private Dimension myModelWrapSize;

  /** Fill size, in screen pixels */
  private Dimension myFillSize;

  /** Fill size, in model pixels (not dp) */
  private Dimension myModelFillSize;

  /** Proposed new size, in screen (zoomed) coordinates */
  private Rectangle myBounds;

  /** Proposed new size, in model */
  private Rectangle myModelBounds;

  public ResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;

    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    Rectangle bounds = myComponent.getBounds(layer);
    Rectangle modelBounds = myComponent.getBounds();

    String width = myComponent.getTag().getAttributeValue("layout_width", SdkConstants.NS_RESOURCES);
    String height = myComponent.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES);

    Pair<Integer, Integer> widthInfo = getDefaultSize(width, modelBounds.width);
    Pair<Integer, Integer> heightInfo = getDefaultSize(height, modelBounds.height);

    myModelWrapSize = new Dimension(widthInfo.first, heightInfo.first);
    myComponent.calculateWrapSize(myModelWrapSize, modelBounds);

    myModelFillSize = new Dimension(widthInfo.second, heightInfo.second);
    calculateFillParentSize(modelBounds);

    myFillSize = myComponent.fromModel(layer, myModelFillSize);
    myWrapSize = myComponent.fromModel(layer, myModelWrapSize);

    createStaticFeedback(bounds, width, height);
  }

  private void calculateFillParentSize(Rectangle bounds) {
    if (myModelFillSize.width == -1 || myModelFillSize.height == -1) {
      Rectangle parentBounds = myComponent.getParent().getBounds();

      if (myModelFillSize.width == -1) {
        myModelFillSize.width = parentBounds.x + parentBounds.width - bounds.x;
      }
      if (myModelFillSize.height == -1) {
        myModelFillSize.height = parentBounds.y + parentBounds.height - bounds.y;
      }
    }
    if (myModelWrapSize.width == myModelFillSize.width) {
      myModelFillSize.width += 10; // TODO: ??? This doesn't look right.
    }
    if (myModelWrapSize.height == myModelFillSize.height) {
      myModelFillSize.height += 10;
    }
  }

  public static Pair<Integer, Integer> getDefaultSize(String value, int size) {
    int wrap = -1;
    int fill = -1;

    if ("wrap_content".equals(value)) {
      wrap = size;
    }
    else if (isFill(value)) {
      fill = size;
    }

    return new Pair<Integer, Integer>(wrap, fill);
  }

  public static boolean isFill(String value) {
    return "fill_parent".equals(value) || "match_parent".equals(value);
  }

  private void createStaticFeedback(Rectangle bounds, String width, String height) {
    int direction = myContext.getResizeDirection();

    Rectangle wrapBounds;
    Rectangle fillBounds;
    if (direction == Position.EAST) {
      myStaticHeight = height;
      wrapBounds = new Rectangle(bounds.x, bounds.y, myWrapSize.width, bounds.height);
      fillBounds = new Rectangle(bounds.x, bounds.y, myFillSize.width, bounds.height);
    }
    else if (direction == Position.SOUTH) {
      myStaticWidth = width;
      wrapBounds = new Rectangle(bounds.x, bounds.y, bounds.width, myWrapSize.height);
      fillBounds = new Rectangle(bounds.x, bounds.y, bounds.width, myFillSize.height);
    }
    else {
      wrapBounds = new Rectangle(bounds.getLocation(), myWrapSize);
      fillBounds = new Rectangle(bounds.getLocation(), myFillSize);
    }

    myWrapFeedback = new RectangleFeedback(DrawingStyle.RESIZE_WRAP);
    myWrapFeedback.setBounds(wrapBounds);

    myFillFeedback = new RectangleFeedback(DrawingStyle.GUIDELINE);
    myFillFeedback.setBounds(fillBounds);
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myFeedback = new RectangleFeedback(DrawingStyle.RESIZE_PREVIEW);
      layer.add(myFeedback);

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(new LineMarginBorder(0, 5, 3, 0));
      layer.add(myTextFeedback);
      layer.add(myWrapFeedback);
      layer.add(myFillFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    myBounds = myContext.getTransformedRectangle(myComponent.getBounds(layer));
    myBounds.width = Math.max(myBounds.width, 0);
    myBounds.height = Math.max(myBounds.height, 0);

    // Snap, unless Shift key is pressed
    if ((myContext.getModifiers() & InputEvent.SHIFT_MASK) == 0) {
      int direction = myContext.getResizeDirection();
      if ((direction & Position.EAST) != 0) {
        if (!snapToWidth(myBounds, myWrapSize)) {
          snapToWidth(myBounds, myFillSize);
        }
      }
      if ((direction & Position.SOUTH) != 0) {
        if (!snapToHeight(myBounds, myWrapSize)) {
          snapToHeight(myBounds, myFillSize);
        }
      }
    }

    myFeedback.setBounds(myBounds);

    myTextFeedback.clear();

    myModelBounds = myComponent.toModel(layer, myBounds);
    addTextSize(myStaticWidth, myModelBounds.width, myModelWrapSize.width, myModelFillSize.width);
    myTextFeedback.append(" x ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    addTextSize(myStaticHeight, myModelBounds.height, myModelWrapSize.height, myModelFillSize.height);

    myTextFeedback.locationTo(myContext.getLocation(), 15);
  }

  private void addTextSize(String staticText, int size, int wrap, int fill) {
    if (staticText == null) {
      if (size == wrap) {
        myTextFeedback.snap("wrap_content");
      }
      else if (size == fill) {
        myTextFeedback.snap("match_parent");
      }
      else {
        myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(myContext.getArea(), size));
      }
    }
    else if (staticText.length() > 3 && staticText.endsWith("dip")) {
      myTextFeedback.append(staticText.substring(0, staticText.length() - 3));
      myTextFeedback.dimension("dip");
    }
    else if (staticText.length() > 2) {
      int index = staticText.length() - 2;
      String dimension = staticText.substring(index);
      if (ArrayUtil.indexOf(ResourceRenderer.DIMENSIONS, dimension) != -1) {
        myTextFeedback.append(staticText.substring(0, index));
        myTextFeedback.dimension(dimension);
      }
      else {
        myTextFeedback.append(staticText);
      }
    }
    else {
      myTextFeedback.append(staticText);
    }
  }

  private static boolean snapToWidth(Rectangle bounds, Dimension size) {
    if (Math.abs(bounds.width - size.width) < MAX_MATCH_DISTANCE) {
      bounds.width = size.width;
      return true;
    }
    return false;
  }

  private static boolean snapToHeight(Rectangle bounds, Dimension size) {
    if (Math.abs(bounds.height - size.height) < MAX_MATCH_DISTANCE) {
      bounds.height = size.height;
      return true;
    }
    return false;
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.remove(myTextFeedback);
      layer.remove(myWrapFeedback);
      layer.remove(myFillFeedback);
      layer.repaint();
      myFeedback = null;
      myTextFeedback = null;
      myWrapFeedback = null;
      myFillFeedback = null;
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
        int direction = myContext.getResizeDirection();

        if ((direction & Position.EAST) != 0) {
          String size = getSize(myModelBounds.width, myModelWrapSize.width, myModelFillSize.width);
          myComponent.getTag().setAttribute("layout_width", SdkConstants.NS_RESOURCES, size);
        }
        if ((direction & Position.SOUTH) != 0) {
          String size = getSize(myModelBounds.height, myModelWrapSize.height, myModelFillSize.height);
          myComponent.getTag().setAttribute("layout_height", SdkConstants.NS_RESOURCES, size);
        }
      }
    });
  }

  private String getSize(int size, int wrap, int fill) {
    if (size == wrap) {
      return "wrap_content";
    }
    if (size == fill) {
      return "fill_parent";
    }
    if (Math.abs(size - wrap) <= 3) { // Rounding when views are zoomed out a lot
      return "wrap_content";
    }
    if (Math.abs(size - fill) <= 3) { // Rounding when views are zoomed out a lot
      return "fill_parent";
    }
    return AndroidDesignerUtils.pxToDpWithUnits(myContext.getArea(), size);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    width(decorator);
    height(decorator);
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH_EAST, TYPE, "Change layout:width x layout:height"));
  }

  public static void width(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.EAST, TYPE, "Change layout:width"));
  }

  public static void height(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH, TYPE, "Change layout:height"));
  }
}
