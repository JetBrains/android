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
package com.android.tools.idea.designer;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.designSurface.graphics.DirectionResizePoint;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.RectangleFeedback;
import com.intellij.android.designer.designSurface.graphics.ResizeSelectionDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.LineMarginBorder;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.MAX_MATCH_DISTANCE;

public class ResizeOperation implements EditOperation {
  public static final String TYPE = "resize_children";
  private static final String LABEL_CHANGE_BOTH = "Change layout:width x layout:height";
  private static final String LABEL_CHANGE_WIDTH = "Change layout:width";
  private static final String LABEL_CHANGE_HEIGHT = "Change layout:height";

  protected ResizeContext myResizeContext;
  protected final OperationContext myContext;
  protected RadViewComponent myComponent;
  protected TextFeedback myTextFeedback;
  private RectangleFeedback myWrapFeedback;
  private RectangleFeedback myFillFeedback;
  private RectangleFeedback myFeedback;

  public ResizeOperation(OperationContext context) {
    myContext = context;
  }

  /**
   * For the new mouse position, compute the resized bounds (the bounding rectangle that
   * the view should be resized to). This is not just a width or height, since in some
   * cases resizing will change the x/y position of the view as well (for example, in
   * RelativeLayout or in AbsoluteLayout).
   */
  private Rectangle getResizedBounds() {
    // Similar to myContext.getTransformedRectangle(myComponent.getBounds()), but handles
    // aspect-preserving resizing etc
    Rectangle b = myComponent.getBounds();
    Dimension sizeDelta = myContext.getSizeDelta();
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    sizeDelta = myComponent.toModel(layer, sizeDelta);
    int direction = myContext.getResizeDirection();
    int x = b.x;
    int y = b.y;
    int w = b.width;
    int h = b.height;
    int newW = b.width + sizeDelta.width;
    int newH = b.height + sizeDelta.height;

    ResizePolicy resizePolicy = ResizePolicy.getResizePolicy(myComponent);
    if (resizePolicy.isAspectPreserving() && w != 0 && h != 0 && (myResizeContext.modifierMask & InputEvent.SHIFT_MASK) == 0) {
      double aspectRatio = w / (double) h;
      double newAspectRatio = newW / (double) newH;
      if (newH == 0 || newAspectRatio > aspectRatio) {
        newH = (int)(newW / aspectRatio);
      } else {
        newW = (int)(newH * aspectRatio);
      }
      switch (direction) {
        case Position.SOUTH: direction = Position.SOUTH_EAST; myResizeContext.verticalEdgeType = SegmentType.RIGHT; break;
        case Position.NORTH: direction = Position.NORTH_EAST; myResizeContext.verticalEdgeType = SegmentType.RIGHT; break;
        case Position.EAST: direction = Position.SOUTH_EAST; myResizeContext.horizontalEdgeType = SegmentType.BOTTOM; break;
        case Position.WEST: direction = Position.SOUTH_WEST; myResizeContext.horizontalEdgeType = SegmentType.BOTTOM; break;
      }
    }

    if (isLeft(direction)) {
      // The user is dragging the left edge, so the position is anchored on the right.
      int x2 = b.x + b.width;
      w = newW;
      x = x2 - newW;
    } else if (isRight(direction)) {
      // The user is dragging the right edge, so the position is anchored on the left.
      w = newW;
    } else {
      assert direction == Position.SOUTH || direction == Position.NORTH : direction;
    }

    if (isTop(direction)) {
      // The user is dragging the top edge, so the position is anchored on the bottom.
      int y2 = b.y + b.height;
      h = newH;
      y = y2 - newH;
    } else if (isBottom(direction)) {
      // The user is dragging the bottom edge, so the position is anchored on the top.
      h = newH;
    } else {
      assert direction == Position.WEST || direction == Position.EAST : direction;
    }

    return new Rectangle(x, y, Math.max(w, 0), Math.max(h, 0));
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
    init();
  }

  private void init() {
    assert myComponent != null;
    RadViewComponent layout = (RadViewComponent)myComponent.getParent();
    int direction1 = myContext.getResizeDirection();
    Object layoutView = layout.getViewInfo() != null ? layout.getViewInfo().getViewObject() : null;
    myResizeContext = createResizeContext(layout, layoutView, myComponent);
    myResizeContext.bounds = myComponent.getBounds();
    myResizeContext.horizontalEdgeType = SegmentType.getHorizontalResizeEdge(direction1);
    myResizeContext.verticalEdgeType = SegmentType.getVerticalResizeEdge(direction1);

    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    Rectangle bounds = myComponent.getBounds(layer);
    Dimension fillSize = myComponent.fromModel(layer, myResizeContext.fillSize);
    Dimension wrapSize = myComponent.fromModel(layer, myResizeContext.wrapSize);

    int direction = myContext.getResizeDirection();
    int wrapX = bounds.x;
    int wrapY = bounds.y;
    int fillWidth = fillSize.width;
    int fillHeight = fillSize.height;

    if (isLeft(direction)) {
      // The user is dragging the left edge, so the position is anchored on the
      // right.
      wrapX = bounds.x + bounds.width - wrapSize.width;
    } else if (isRight(direction)) {
      // The user is dragging the right edge, so the position is anchored on the
      // left.
      wrapX = bounds.x;
    } else {
      assert direction == Position.SOUTH || direction == Position.NORTH : direction;
      fillWidth = bounds.width;
    }

    if (isTop(direction)) {
      // The user is dragging the top edge, so the position is anchored on the
      // bottom.
      wrapY = bounds.y + bounds.height - wrapSize.height;
    } else if (isBottom(direction)) {
      // The user is dragging the bottom edge, so the position is anchored on the
      // top.
      wrapY = bounds.y;
    } else {
      assert direction == Position.WEST || direction == Position.EAST : direction;
      fillHeight = bounds.height;
    }

    Rectangle wrapBounds = new Rectangle(wrapX, wrapY, wrapSize.width, wrapSize.height);
    Rectangle fillBounds = new Rectangle(bounds.x, bounds.y, fillWidth, fillHeight);

    // Measure actual fill bounds
    RenderTask task = AndroidDesignerUtils.createRenderTask(myContext.getArea());
    if (task != null) {
      final XmlTag tag = myComponent.getTag();
      ViewInfo viewInfo = task.measureChild(tag, new RenderTask.AttributeFilter() {
        @Override
        public String getAttribute(@NotNull XmlTag n, @Nullable String namespace, @NotNull String name) {
          // Clear out layout weights; we need to measure the unweighted sizes
          // of the children
          if (n == tag && (ATTR_LAYOUT_WIDTH.equals(name) || ATTR_LAYOUT_HEIGHT.equals(name)) && ANDROID_URI.equals(namespace)) {
            return VALUE_FILL_PARENT;
          }

          return null;
        }
      });
      if (viewInfo != null) {
        viewInfo = RenderService.getSafeBounds(viewInfo);
        int left = viewInfo.getLeft();
        int top = viewInfo.getTop();
        fillBounds = new Rectangle(left, top, viewInfo.getRight() - left, viewInfo.getBottom() - top);
        // Translate from Android model coordinates to designer UI coordinates
        fillBounds = myComponent.fromModel(layer, fillBounds);
      }
    }

    myWrapFeedback = new RectangleFeedback(DrawingStyle.RESIZE_WRAP);
    myWrapFeedback.setBounds(wrapBounds);
    myFillFeedback = new RectangleFeedback(DrawingStyle.GUIDELINE_DASHED);
    myFillFeedback.setBounds(fillBounds);
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
      onResizeBegin();
    }
  }

  /** Is the direction somewhere on the left edge? */
  private static boolean isLeft(int direction) {
    return direction == Position.NORTH_WEST || direction == Position.WEST || direction == Position.SOUTH_WEST;
  }

  /** Is the direction somewhere on the right edge? */
  private static boolean isRight(int direction) {
    return direction == Position.NORTH_EAST || direction == Position.EAST || direction == Position.SOUTH_EAST;
  }

  /** Is the direction somewhere on the top edge? */
  private static boolean isTop(int direction) {
    return direction == Position.NORTH_WEST || direction == Position.NORTH || direction == Position.NORTH_EAST;
  }

  /** Is the direction somewhere on the bottom edge? */
  private static boolean isBottom(int direction) {
    return direction == Position.SOUTH_WEST || direction == Position.SOUTH || direction == Position.SOUTH_EAST;
  }

  /** Creates a new {@link ResizeContext} object to track resize state */
  protected ResizeContext createResizeContext(RadViewComponent layout, @Nullable Object layoutView, RadViewComponent node) {
    return new ResizeContext(myContext.getArea(), layout, layoutView, node);
  }

  /**
   * Performs the edit on the node to complete a resizing operation. The actual edit
   * part is pulled out such that subclasses can change/add to the edits and be part of
   * the same undo event
   *
   * @param resizeContext the current resize state
   * @param node the child node being resized
   * @param layout the parent of the resized node
   * @param newBounds the new bounds to resize the child to, in pixels
   * @param horizontalEdge the horizontal edge being resized
   * @param verticalEdge the vertical edge being resized
   */
  protected void setNewSizeBounds(ResizeContext resizeContext, RadViewComponent node, RadViewComponent layout,
                                  Rectangle oldBounds, Rectangle newBounds, SegmentType horizontalEdge, SegmentType verticalEdge) {
    if (verticalEdge != null
        && (newBounds.width != oldBounds.width || resizeContext.wrapWidth || resizeContext.fillWidth)) {
      node.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, resizeContext.getWidthAttribute());
    }
    if (horizontalEdge != null
        && (newBounds.height != oldBounds.height || resizeContext.wrapHeight || resizeContext.fillHeight)) {
      node.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, resizeContext.getHeightAttribute());
    }
  }

  /**
   * Returns the message to display to the user during the resize operation
   *
   * @param resizeContext the current resize state
   * @param child the child node being resized
   * @param parent the parent of the resized node
   * @param newBounds the new bounds to resize the child to, in pixels
   * @param horizontalEdge the horizontal edge being resized
   * @param verticalEdge the vertical edge being resized
   * @return the message to display for the current resize bounds
   */
  protected String getResizeUpdateMessage(ResizeContext resizeContext, RadViewComponent child, RadViewComponent parent,
                                          Rectangle newBounds, SegmentType horizontalEdge, SegmentType verticalEdge) {
    String width = resizeContext.getWidthAttribute();
    String height = resizeContext.getHeightAttribute();

    if (horizontalEdge == null) {
      return width;
    } else if (verticalEdge == null) {
      return height;
    } else {
      // U+00D7: Unicode for multiplication sign
      return String.format("%s \u00D7 %s", width, height);
    }
  }

  public void onResizeBegin() {
  }

  public void onResizeUpdate(@NotNull RadViewComponent parent, @NotNull Rectangle newBounds, int modifierMask) {
    myResizeContext.bounds = newBounds;
    myResizeContext.modifierMask = modifierMask;

    // Match on wrap bounds
    myResizeContext.wrapWidth = myResizeContext.wrapHeight = false;
    if (myResizeContext.wrapSize != null) {
      Dimension b = myResizeContext.wrapSize;
      if (myResizeContext.horizontalEdgeType != null) {
        if (Math.abs(newBounds.height - b.height) < MAX_MATCH_DISTANCE) {
          myResizeContext.wrapHeight = true;
          if (myResizeContext.horizontalEdgeType == SegmentType.TOP) {
            newBounds.y += newBounds.height - b.height;
          }
          newBounds.height = b.height;
        }
      }
      if (myResizeContext.verticalEdgeType != null) {
        if (Math.abs(newBounds.width - b.width) < MAX_MATCH_DISTANCE) {
          myResizeContext.wrapWidth = true;
          if (myResizeContext.verticalEdgeType == SegmentType.LEFT) {
            newBounds.x += newBounds.width - b.width;
          }
          newBounds.width = b.width;
        }
      }
    }

    // Match on fill bounds
    myResizeContext.horizontalFillSegment = null;
    myResizeContext.fillHeight = false;
    if (myResizeContext.horizontalEdgeType == SegmentType.BOTTOM && !myResizeContext.wrapHeight) {
      Rectangle parentBounds = parent.getBounds();
      myResizeContext.horizontalFillSegment = new Segment(parentBounds.y + parentBounds.height, newBounds.x, newBounds.x + newBounds.width,
                                                        null /*node*/, null /*id*/, SegmentType.BOTTOM, MarginType.NO_MARGIN);
      if (Math.abs(newBounds.y + newBounds.height - (parentBounds.y + parentBounds.height)) < MAX_MATCH_DISTANCE) {
        myResizeContext.fillHeight = true;
        newBounds.height = parentBounds.y + parentBounds.height - newBounds.y;
      }
    }
    myResizeContext.verticalFillSegment = null;
    myResizeContext.fillWidth = false;
    if (myResizeContext.verticalEdgeType == SegmentType.RIGHT && !myResizeContext.wrapWidth) {
      Rectangle parentBounds = parent.getBounds();
      myResizeContext.verticalFillSegment = new Segment(parentBounds.x + parentBounds.width, newBounds.y, newBounds.y + newBounds.height,
                                                      null /*node*/, null /*id*/, SegmentType.RIGHT, MarginType.NO_MARGIN);
      if (Math.abs(newBounds.x + newBounds.width - (parentBounds.x + parentBounds.width)) < MAX_MATCH_DISTANCE) {
        myResizeContext.fillWidth = true;
        newBounds.width = parentBounds.x + parentBounds.width - newBounds.x;
      }
    }
  }

  protected void updateResizeMessage() {
    RadViewComponent layout = (RadViewComponent)myComponent.getParent();
    String message = getResizeUpdateMessage(myResizeContext, myComponent, layout,
                                            myResizeContext.bounds, myResizeContext.horizontalEdgeType, myResizeContext.verticalEdgeType);
    myTextFeedback.append(message);
    myTextFeedback.setSize(myTextFeedback.getPreferredSize());
    myTextFeedback.locationTo(myContext.getLocation(), 15);
  }

  @Override
  public void showFeedback() {
    createFeedback();
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    Rectangle modelBounds = getResizedBounds();
    RadViewComponent layout = (RadViewComponent)myComponent.getParent();
    onResizeUpdate(layout, modelBounds, myContext.getModifiers());
    Rectangle viewBounds = myComponent.fromModel(layer, myResizeContext.bounds);
    myFeedback.setBounds(viewBounds);
    myTextFeedback.clear();
    updateResizeMessage();
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
        RadViewComponent layout = (RadViewComponent)myComponent.getParent();
        Rectangle oldBounds = myComponent.getBounds();
        Rectangle newBounds = getResizedBounds();
        setNewSizeBounds(myResizeContext, myComponent, layout, oldBounds, newBounds,
                         myResizeContext.horizontalEdgeType, myResizeContext.verticalEdgeType);
      }
    });
  }

  public static void addResizePoints(ResizeSelectionDecorator decorator) {
    addResizePoints(decorator, ResizePolicy.full());
  }

  public static void addResizePoints(ResizeSelectionDecorator decorator, @NotNull RadViewComponent component) {
    addResizePoints(decorator, ResizePolicy.getResizePolicy(component));
  }

  public static void addResizePoints(ResizeSelectionDecorator decorator, @NotNull ResizePolicy policy) {
    if (policy.leftAllowed()) {
      decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.WEST, TYPE, LABEL_CHANGE_WIDTH));
      if (policy.topAllowed()) {
        decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.NORTH_WEST, TYPE, LABEL_CHANGE_BOTH));
      }
      if (policy.bottomAllowed()) {
        decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH_WEST, TYPE, LABEL_CHANGE_BOTH));
      }
    }
    if (policy.rightAllowed()) {
      decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.EAST, TYPE, LABEL_CHANGE_WIDTH));
      if (policy.topAllowed()) {
        decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.NORTH_EAST, TYPE, LABEL_CHANGE_BOTH));
      }
      if (policy.bottomAllowed()) {
        decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH_EAST, TYPE, LABEL_CHANGE_BOTH));
      }
    }
    if (policy.topAllowed()) {
      decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.NORTH, TYPE, LABEL_CHANGE_HEIGHT));
    }
    if (policy.bottomAllowed()) {
      decorator.addPoint(new DirectionResizePoint(DrawingStyle.SELECTION, Position.SOUTH, TYPE, LABEL_CHANGE_HEIGHT));
    }
  }
}
