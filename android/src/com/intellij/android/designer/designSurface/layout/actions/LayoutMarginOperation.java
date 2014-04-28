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
import com.android.tools.idea.designer.Insets;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.designSurface.graphics.*;
import com.intellij.android.designer.model.RadComponentOperations;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.designSurface.selection.EmptyPoint;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.VALUE_N_DP;

/**
 * @author Alexander Lobas
 */
public class LayoutMarginOperation implements EditOperation {
  public static final String TYPE = "layout_margin";

  protected final OperationContext myContext;
  protected RadViewComponent myComponent;
  protected RectangleFeedback myFeedback;
  protected TextFeedback myTextFeedback;
  private Rectangle myBounds; // in screen coordinates
  protected Insets myMargins; // in model coordinates

  public LayoutMarginOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    myBounds = myComponent.getBounds(myContext.getArea().getFeedbackLayer());
    myMargins = myComponent.getMargins();
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
    if (myFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(new LineMarginBorder(0, 5, 3, 0));
      layer.add(myTextFeedback);

      myFeedback = new RectangleFeedback(DrawingStyle.MARGIN_BOUNDS);
      layer.add(myFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    Rectangle bounds = myContext.getTransformedRectangle(myBounds);
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    myComponent.getMargins(layer).subtractFrom(bounds);
    myFeedback.setBounds(bounds);

    myTextFeedback.clear();
    fillTextFeedback();
    myTextFeedback.locationTo(myContext.getLocation(), 15);
  }

  protected void fillTextFeedback() {
    EditableArea area = myContext.getArea();
    FeedbackLayer layer = area.getFeedbackLayer();
    Dimension moveDelta = myComponent.toModel(layer, new Dimension(myContext.getMoveDelta().x, myContext.getMoveDelta().y));
    Dimension sizeDelta = myComponent.toModel(layer, myContext.getSizeDelta());
    int direction = myContext.getResizeDirection();

    if (direction == Position.WEST) { // left
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.left - moveDelta.width));
    }
    else if (direction == Position.EAST) { // right
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.right + sizeDelta.width));
    }
    else if (direction == Position.NORTH) { // top
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.top - moveDelta.height));
    }
    else if (direction == Position.SOUTH) { // bottom
      myTextFeedback.append(AndroidDesignerUtils.pxToDpWithUnits(area, myMargins.bottom + sizeDelta.height));
    }
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myTextFeedback);
      layer.remove(myFeedback);
      layer.repaint();
      myTextFeedback = null;
      myFeedback = null;
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
        XmlTag tag = myComponent.getTag();

        XmlAttribute margin = tag.getAttribute("layout_margin", SdkConstants.NS_RESOURCES);
        if (margin != null) {
          String value = margin.getValue();
          margin.delete();

          if (!StringUtil.isEmpty(value)) {
            tag.setAttribute("layout_marginLeft", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginRight", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginTop", SdkConstants.NS_RESOURCES, value);
            tag.setAttribute("layout_marginBottom", SdkConstants.NS_RESOURCES, value);
          }
        }

        FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
        Dimension moveDelta = myComponent.toModel(layer, new Dimension(myContext.getMoveDelta().x, myContext.getMoveDelta().y));
        Dimension sizeDelta = myComponent.toModel(layer, myContext.getSizeDelta());
        int direction = myContext.getResizeDirection();

        if (direction == Position.WEST) { // left
          setValue(tag, "layout_marginLeft", myMargins.left - moveDelta.width);
        }
        else if (direction == Position.EAST) { // right
          setValue(tag, "layout_marginRight", myMargins.right + sizeDelta.width);
        }
        else if (direction == Position.NORTH) { // top
          setValue(tag, "layout_marginTop", myMargins.top - moveDelta.height);
        }
        else if (direction == Position.SOUTH) { // bottom
          setValue(tag, "layout_marginBottom", myMargins.bottom + sizeDelta.height);
        }
      }
    });
  }

  private void setValue(XmlTag tag, String name, int pxValue) {
    int value = AndroidDesignerUtils.pxToDp(myContext.getArea(), pxValue);
    if (value == 0) {
      RadComponentOperations.deleteAttribute(tag, name);
    }
    else {
      tag.setAttribute(name, SdkConstants.NS_RESOURCES, String.format(Locale.US, VALUE_N_DP, value));
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    pointFeedback(decorator);

    decorator.addPoint(new DirectionResizePoint(DrawingStyle.MARGIN_HANDLE, Position.WEST, TYPE, "Change layout:margin.left") { // left
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginLeft = ((RadViewComponent)component).getMargins(layer).left;
        location.x -= marginLeft;
        return location;
      }
    });

    pointRight(decorator, DrawingStyle.MARGIN_HANDLE, 0.25, TYPE, "Change layout:margin.right");

    decorator.addPoint(new DirectionResizePoint(DrawingStyle.MARGIN_HANDLE, Position.NORTH, TYPE, "Change layout:margin.top") { // top
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginTop = ((RadViewComponent)component).getMargins(layer).top;
        location.y -= marginTop;
        return location;
      }
    });

    pointBottom(decorator, DrawingStyle.MARGIN_HANDLE, 0.25, TYPE, "Change layout:margin.bottom");
  }

  protected static void pointFeedback(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new EmptyPoint() {
      @Override
      protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
        Rectangle bounds = component.getBounds(layer);
        Insets margins = ((RadViewComponent)component).getMargins(layer);
        if (!margins.isEmpty()) {
          margins.subtractFrom(bounds);
          DesignerGraphics.drawRect(DrawingStyle.MARGIN_BOUNDS, g, bounds.x, bounds.y, bounds.width, bounds.height);
        }
      }
    });
  }

  protected static void pointRight(ResizeSelectionDecorator decorator,
                                   DrawingStyle style,
                                   double ySeparator,
                                   Object type,
                                   @Nullable String description) {
    decorator.addPoint(new DirectionResizePoint(style, Position.EAST, type, description) {
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginRight = ((RadViewComponent)component).getMargins(layer).right;
        location.x += marginRight;
        return location;
      }
    }.move(1, ySeparator));
  }

  protected static void pointBottom(ResizeSelectionDecorator decorator,
                                    DrawingStyle style,
                                    double xSeparator,
                                    Object type,
                                    @Nullable String description) {
    decorator.addPoint(new DirectionResizePoint(style, Position.SOUTH, type, description) {
      @Override
      protected Point getLocation(DecorationLayer layer, RadComponent component) {
        Point location = super.getLocation(layer, component);
        int marginBottom = ((RadViewComponent)component).getMargins(layer).bottom;
        location.y += marginBottom;
        return location;
      }
    }.move(xSeparator, 1));
  }
}
