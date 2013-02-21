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
package com.intellij.android.designer.designSurface.layout;

import com.android.SdkConstants;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.layout.actions.ResizeOperation;
import com.intellij.android.designer.designSurface.layout.flow.FlowBaseOperation;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.SHOW_STATIC_GRID;

/**
 * @author Alexander Lobas
 */
public class LinearLayoutOperation extends FlowBaseOperation {
  private GravityFeedback myFeedback;
  private TextFeedback myTextFeedback;
  private FlowPositionFeedback myFlowFeedback;
  private Gravity myExclude;
  private Gravity myGravity;
  private boolean mySetGravity;

  public LinearLayoutOperation(RadComponent container, OperationContext context, boolean horizontal) {
    super(container, context, horizontal);

    if (context.isMove() && context.getComponents().size() == 1) {
      myExclude = getGravity(myHorizontal, context.getComponents().get(0));
    }
  }

  private final static int MIN_MARGIN_DIMENSIONS = 100;

  @Override
  protected void createFeedback() {
    super.createFeedback();

    if (myTextFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      if (myHorizontal && myBounds.height > MIN_MARGIN_DIMENSIONS
            || !myHorizontal && myBounds.width > MIN_MARGIN_DIMENSIONS) {
        mySetGravity = true;

        myFeedback = new GravityFeedback();
        if (myContainer.getChildren().isEmpty()) {
          myFeedback.setBounds(myBounds);
        }
        layer.add(myFeedback, 0);
      }

      //noinspection ConstantConditions
      if (!SHOW_STATIC_GRID) {
        myFlowFeedback = new FlowPositionFeedback();
        myFlowFeedback.setBounds(myBounds);
        layer.add(myFlowFeedback, 0);
      }

      myTextFeedback = new TextFeedback();
      myTextFeedback.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 2, 0));
      layer.add(myTextFeedback);

      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    super.showFeedback();

    if (mySetGravity) {
      Point location = myContext.getLocation();
      Gravity gravity = myHorizontal ? calculateVertical(myBounds, location) : calculateHorizontal(myBounds, location);

      if (!myContainer.getChildren().isEmpty()) {
        myFeedback.setBounds(myInsertFeedback.getBounds());
      }

      myFeedback.setGravity(gravity);

      myTextFeedback.clear();
      myTextFeedback.bold(gravity == null ? "fill_parent" : gravity.name());
      myTextFeedback.centerTop(myBounds);

      // TODO: Only do this if large enough!
      myGravity = gravity;
    }

    //noinspection ConstantConditions
    if (!SHOW_STATIC_GRID) {
      myFlowFeedback.repaint();
    }
  }

  @Override
  public void eraseFeedback() {
    super.eraseFeedback();
    if (myTextFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      if (mySetGravity) {
        layer.remove(myFeedback);
      }
      layer.remove(myTextFeedback);

      //noinspection ConstantConditions
      if (!SHOW_STATIC_GRID) {
        layer.remove(myFlowFeedback);
      }

      layer.repaint();
      myFeedback = null;
      myTextFeedback = null;
    }
  }

  @Nullable
  private static Gravity calculateHorizontal(Rectangle bounds, Point location) {
    Gravity horizontal = Gravity.right;
    double width = bounds.width / 4.0;
    double left = bounds.x + width;
    double center = bounds.x + 2 * width;
    double fill = bounds.x + 3 * width;

    if (location.x < left) {
      horizontal = Gravity.left;
    }
    else if (left < location.x && location.x < center) {
      horizontal = Gravity.center;
    }
    else if (center < location.x && location.x < fill) {
      horizontal = null;
    }

    return horizontal;
  }

  @Nullable
  private static Gravity calculateVertical(Rectangle bounds, Point location) {
    Gravity vertical = Gravity.bottom;
    double height = bounds.height / 4.0;
    double top = bounds.y + height;
    double center = bounds.y + 2 * height;
    double fill = bounds.y + 3 * height;

    if (location.y < top) {
      vertical = Gravity.top;
    }
    else if (top < location.y && location.y < center) {
      vertical = Gravity.center;
    }
    else if (center < location.y && location.y < fill) {
      vertical = null;
    }

    return vertical;
  }

  @Override
  public boolean canExecute() {
    return super.canExecute() || (myComponents.size() == 1 && myGravity != myExclude);
  }

  @Override
  public void execute() throws Exception {
    if (super.canExecute()) {
      super.execute();
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        execute(myHorizontal, myGravity, RadViewComponent.getViewComponents(myComponents));
      }
    });
  }

  public static void execute(boolean horizontal, Gravity gravity, List<? extends RadViewComponent> components) {
    if (gravity == null) {
      for (RadViewComponent component : components) {
        XmlTag tag = component.getTag();
        ModelParser.deleteAttribute(tag, "layout_gravity");
        tag.setAttribute(horizontal ? "layout_height" : "layout_width", SdkConstants.NS_RESOURCES, "fill_parent");
      }
    }
    else {
      String gravityValue = horizontal ? Gravity.getValue(Gravity.center, gravity) : Gravity.getValue(gravity, Gravity.center);

      for (RadViewComponent component : components) {
        XmlTag tag = component.getTag();

        XmlAttribute attribute = tag.getAttribute(horizontal ? "layout_height" : "layout_width", SdkConstants.NS_RESOURCES);
        if (attribute != null && ("match_parent".equals(attribute.getValue()) || "fill_parent".equals(attribute.getValue()))) {
          attribute.setValue("wrap_content");
        }

        tag.setAttribute("layout_gravity", SdkConstants.NS_RESOURCES, gravityValue);
      }
    }
  }

  @Nullable
  public static Gravity getGravity(boolean horizontal, RadComponent component) {
    XmlTag tag = ((RadViewComponent)component).getTag();
    String length = tag.getAttributeValue(horizontal ? "layout_height" : "layout_width", SdkConstants.NS_RESOURCES);

    if (!ResizeOperation.isFill(length)) {
      Pair<Gravity, Gravity> gravity = Gravity.getSides(component);
      return horizontal ? gravity.second : gravity.first;
    }

    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Feedback
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private class FlowPositionFeedback extends JComponent {
    @Override
    public void paint(Graphics graphics) {
      super.paint(graphics);
    }


    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      Rectangle bounds = myContainer.getBounds(this);
      RadComponent component = myContainer;
      DesignerGraphics.drawRect(DrawingStyle.DROP_RECIPIENT, g, bounds.x, bounds.y, bounds.width, bounds.height);

      if (myHorizontal) {
        for (RadComponent child : component.getChildren()) {
          Rectangle childBounds = child.getBounds(this);
          int marginRight = ((RadViewComponent)child).getMargins(this).right;
          int x = childBounds.x + childBounds.width + marginRight;
          DesignerGraphics.drawLine(DrawingStyle.DROP_ZONE, g, x, bounds.y, x, bounds.y + bounds.height);
        }
      }
      else {
        for (RadComponent child : component.getChildren()) {
          Rectangle childBounds = child.getBounds(this);
          int marginBottom = ((RadViewComponent)child).getMargins(this).bottom;
          int y = childBounds.y + childBounds.height + marginBottom;
          DesignerGraphics.drawLine(DrawingStyle.DROP_ZONE, g, bounds.x, y, bounds.x + bounds.width, y);
        }
      }
    }
  }

  private class GravityFeedback extends JComponent {
    private Gravity myGravity;

    public void setGravity(Gravity gravity) {
      myGravity = gravity;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      // Paint outline of inserted component, if there's just one:
      if (myComponents.size() == 1) {
        RadComponent first = myComponents.get(0);
        Rectangle b = first.getBounds(this);
        if (b.isEmpty()) {
          b.width = 100;
          b.height = 30;
        }
        Rectangle bounds;
        if (myHorizontal) {
          bounds = computeHorizontalPreviewBounds(b);
        }
        else {
          bounds = computeVerticalPreviewBounds(b);
        }

        Shape clip = g.getClip();
        g.setClip(bounds);
        DesignerGraphics.drawRect(DrawingStyle.DROP_PREVIEW, g, bounds.x, bounds.y, bounds.width, bounds.height);
        g.setClip(clip);
      } else {
        // Multiple components: Just show insert line
        if (myHorizontal) {
          paintHorizontalCell(g);
        }
        else {
          paintVerticalCell(g);
        }
      }
    }

    private Rectangle computeVerticalPreviewBounds(Rectangle b) {
      int x = 0;
      int y = 0;
      if (!myContainer.getChildren().isEmpty()) {
        y -= b.height / 2;
      }

      if (myGravity == Gravity.center) {
        x = getWidth() / 2 - b.width / 2;
      }
      else if (myGravity == null) {
        x = 0;
        b.width = getWidth();
      }
      else if (myGravity == Gravity.right) {
        x = getWidth() - b.width;
      }

      int hSpace = Math.min(5, Math.max(1, getWidth() / 30));
      if (hSpace > 1) {
        x += hSpace;
      }
      return new Rectangle(x, y, b.width, b.height);
    }

    private Rectangle computeHorizontalPreviewBounds(Rectangle b) {
      int x = 0;
      int y = 0;
      if (!myContainer.getChildren().isEmpty()) {
        x -= b.width / 2;
      }

      if (myGravity == Gravity.center) {
        y = getHeight() / 2 - b.height / 2;
      }
      else if (myGravity == null) {
        y = 0;
        b.height = getHeight();
      }
      else if (myGravity == Gravity.bottom) {
        y = getHeight() - b.height;
      }

      int vSpace = Math.min(5, Math.max(1, getHeight() / 30));
      if (vSpace > 1) {
        y += vSpace;
      }
      return new Rectangle(x, y, b.width, b.height);
    }

    private void paintHorizontalCell(Graphics g) {
      int y = 0;
      int height = (getHeight() - 3) / 4;
      if (myGravity == Gravity.center) {
        y = height + 1;
      }
      else if (myGravity == null) {
        y = 2 * height + 2;
      }
      else if (myGravity == Gravity.bottom) {
        y = getHeight() - height;
      }

      int vSpace = Math.min(5, Math.max(1, getHeight() / 30));
      if (vSpace > 1) {
        y += vSpace;
        height -= 2 * vSpace;
      }

      int thickness = DrawingStyle.GRAVITY.getLineWidth();
      if (myContainer.getChildren().isEmpty()) {
        DesignerGraphics.drawFilledRect(DrawingStyle.GRAVITY, g, 0, y, thickness, height);
        DesignerGraphics.drawFilledRect(DrawingStyle.GRAVITY, g, myBounds.width - thickness, y, thickness, height);
      }
      else {
        DesignerGraphics.drawLine(DrawingStyle.GRAVITY, g, thickness / 2, y, thickness / 2, y + height);
      }
    }

    private void paintVerticalCell(Graphics g) {
      int x = 0;
      int width = (getWidth() - 3) / 4;
      if (myGravity == Gravity.center) {
        x = width + 1;
      }
      else if (myGravity == null) {
        x = 2 * width + 2;
      }
      else if (myGravity == Gravity.right) {
        x = getWidth() - width;
      }

      int hSpace = Math.min(5, Math.max(1, getWidth() / 30));
      if (hSpace > 1) {
        x += hSpace;
        width -= 2 * hSpace;
      }

      int thickness = DrawingStyle.GRAVITY.getLineWidth();
      if (myContainer.getChildren().isEmpty()) {
        DesignerGraphics.drawFilledRect(DrawingStyle.GRAVITY, g, x, 0, width, thickness);
        DesignerGraphics.drawFilledRect(DrawingStyle.GRAVITY, g, x, myBounds.height - thickness, width, thickness);

      }
      else {
        DesignerGraphics.drawLine(DrawingStyle.GRAVITY, g, x, thickness / 2, x + width, thickness / 2);
      }
    }
  }
}
