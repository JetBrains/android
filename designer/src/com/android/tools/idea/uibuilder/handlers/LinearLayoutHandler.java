/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.api.actions.ViewActionSeparator;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.psi.xml.XmlTag;
import icons.AndroidDesignerIcons;
import icons.AndroidIcons;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.model.Coordinates.getSwingX;
import static com.android.tools.idea.uibuilder.model.Coordinates.getSwingY;
import static com.android.utils.XmlUtils.formatFloatAttribute;

/**
 * Handler for the {@code <LinearLayout>} layout
 */
public class LinearLayoutHandler extends ViewGroupHandler {

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    if (!component.getTagName().equals(LINEAR_LAYOUT)) {
      return super.getTitleAttributes(component);
    }
    return isVertical(component) ? "(vertical)" : "(horizontal)";
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    if (!component.getTagName().equals(LINEAR_LAYOUT)) {
      return super.getIcon(component);
    }
    return isVertical(component) ? AndroidIcons.Views.VerticalLinearLayout : AndroidIcons.Views.LinearLayout;
  }

  @Override
  public boolean paintConstraints(@NotNull ScreenView screenView, @NotNull Graphics2D graphics, @NotNull NlComponent component) {
    NlComponent prev = null;
    boolean vertical = isVertical(component);
    for (NlComponent child : component.getChildren()) {
      if (prev != null) {
        if (vertical) {
          int middle = getSwingY(screenView, (prev.y + prev.h + child.y) / 2);
          NlGraphics.drawLine(NlDrawingStyle.GUIDELINE_DASHED, graphics, getSwingX(screenView, component.x), middle,
                              getSwingX(screenView, component.x + component.w), middle);
        } else {
          int middle = getSwingX(screenView, (prev.x + prev.w + child.x) / 2);
          NlGraphics.drawLine(NlDrawingStyle.GUIDELINE_DASHED, graphics, middle, getSwingY(screenView, component.y), middle,
                              getSwingY(screenView, component.y + component.h));
        }
      }
      prev = child;
    }
    return false;
  }

  /**
   * Returns true if the given node represents a vertical linear layout.
   * @param component the node to check layout orientation for
   * @return true if the layout is in vertical mode, otherwise false
   */
  protected boolean isVertical(@NotNull NlComponent component) {
    // Horizontal is the default, so if no value is specified it is horizontal.
    String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
    return VALUE_VERTICAL.equals(orientation);
  }

  /**
   * Returns the current orientation, regardless of whether it has been defined in XML
   *
   * @param component The LinearLayout to look up the orientation for
   * @return "horizontal" or "vertical" depending on the current orientation of the
   *         linear layout
   */
  private static String getCurrentOrientation(@NotNull final NlComponent component) {
    String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
    if (orientation == null || orientation.length() == 0) {
      orientation = VALUE_HORIZONTAL;
    }
    return orientation;
  }

  private void distributeWeights(NlComponent parentNode, NlComponent[] targets) {
    // Any XML to get weight sum?
    String weightSum = parentNode.getAttribute(ANDROID_URI, ATTR_WEIGHT_SUM);
    double sum = -1.0;
    if (weightSum != null) {
      // Distribute
      try {
        sum = Double.parseDouble(weightSum);
      } catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }
    int numTargets = targets.length;
    double share;
    if (sum <= 0.0) {
      // The sum will be computed from the children, so just
      // use arbitrary amount
      share = 1.0;
    } else {
      share = sum / numTargets;
    }
    String value = formatFloatAttribute((float)share);
    String sizeAttribute = isVertical(parentNode) ?
                           ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent target : targets) {
      target.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, value);
      // Also set the width/height to 0dp to ensure actual equal
      // size (without this, only the remaining space is
      // distributed)
      if (VALUE_WRAP_CONTENT.equals(target.getAttribute(ANDROID_URI, sizeAttribute))) {
        target.setAttribute(ANDROID_URI, sizeAttribute, VALUE_ZERO_DP);
      }
    }
  }

  private void clearWeights(NlComponent parentNode) {
    // Clear attributes
    String sizeAttribute = isVertical(parentNode)
                           ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent target : parentNode.getChildren()) {
      target.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, null);
      String size = target.getAttribute(ANDROID_URI, sizeAttribute);
      if (size != null && size.startsWith("0")) { //$NON-NLS-1$
        target.setAttribute(ANDROID_URI, sizeAttribute, VALUE_WRAP_CONTENT);
      }
    }
  }

  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull NlComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    if (layout.w == 0 || layout.h == 0) {
      return null;
    }
    return new LinearDragHandler(editor, layout, components, type);
  }

  private class LinearDragHandler extends DragHandler {
    /**
     * Vertical layout?
     */
    private final boolean myVertical;

    /**
     * Insert points (pixels + index)
     */
    private final List<MatchPos> myIndices;

    /**
     * Number of insert positions in the target node
     */
    private final int myNumPositions;

    /**
     * Current marker X position
     */
    private Integer myCurrX;

    /**
     * Current marker Y position
     */
    private Integer myCurrY;

    /**
     * Position of the dragged element in this layout (or
     * -1 if the dragged element is from elsewhere)
     */
    private int mySelfPos;

    /**
     * Current drop insert index (-1 for "at the end")
     */
    private int myInsertPos = -1;

    /**
     * width of match line if it's a horizontal one
     */
    private Integer myWidth;

    /**
     * height of match line if it's a vertical one
     */
    private Integer myHeight;


    public LinearDragHandler(@NotNull ViewEditor editor,
                             @NotNull NlComponent layout,
                             @NotNull List<NlComponent> components,
                             @NotNull DragType type) {
      super(editor, LinearLayoutHandler.this, layout, components, type);
      assert !components.isEmpty();

      myVertical = isVertical(layout);

      // Prepare a list of insertion points: X coordinates for horizontal, Y for
      // vertical.
      myIndices = new ArrayList<MatchPos>();

      int last = myVertical ? layout.y + layout.getPadding().top : layout.x + layout.getPadding().left;
      int pos = 0;
      boolean lastDragged = false;
      mySelfPos = -1;
      for (NlComponent it : layout.getChildren()) {
        if (it.w > 0 && it.h > 0) {
          boolean isDragged = components.contains(it);

          // We don't want to insert drag positions before or after the
          // element that is itself being dragged. However, we -do- want
          // to insert a match position here, at the center, such that
          // when you drag near its current position we show a match right
          // where it's already positioned.
          if (isDragged) {
            int v = myVertical ? it.y + (it.h / 2) : it.x + (it.w / 2);
            mySelfPos = pos;
            myIndices.add(new MatchPos(v, pos++));
          }
          else if (lastDragged) {
            // Even though we don't want to insert a match below, we
            // need to increment the index counter such that subsequent
            // lines know their correct index in the child list.
            pos++;
          }
          else {
            // Add an insertion point between the last point and the
            // start of this child
            int v = myVertical ? it.y : it.x;
            v = (last + v) / 2;
            myIndices.add(new MatchPos(v, pos++));
          }

          last = myVertical ? (it.y + it.h) : (it.x + it.w);
          lastDragged = isDragged;
        }
        else {
          // We still have to count this position even if it has no bounds, or
          // subsequent children will be inserted at the wrong place
          pos++;
        }
      }

      // Finally add an insert position after all the children - unless of
      // course we happened to be dragging the last element
      if (!lastDragged) {
        int v = last + 1;
        myIndices.add(new MatchPos(v, pos));
      }

      myNumPositions = layout.getChildCount() + 1;
    }

    @Nullable
    @Override
    public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, @InputEventMask int modifiers) {
      super.update(x, y, modifiers);

      boolean isVertical = myVertical;

      int bestDist = Integer.MAX_VALUE;
      int bestIndex = Integer.MIN_VALUE;
      Integer bestPos = null;

      for (MatchPos index : myIndices) {
        int i = index.getDistance();
        int pos = index.getPosition();
        int dist = (isVertical ? y : x) - i;
        if (dist < 0) {
          dist = -dist;
        }
        if (dist < bestDist) {
          bestDist = dist;
          bestIndex = i;
          bestPos = pos;
          if (bestDist <= 0) {
            break;
          }
        }
      }

      if (bestIndex != Integer.MIN_VALUE) {
        if (isVertical) {
          myCurrX = layout.x + layout.w / 2;
          myCurrY = bestIndex;
          myWidth = layout.w;
          myHeight = null;
        }
        else {
          myCurrX = bestIndex;
          myCurrY = layout.y + layout.h / 2;
          myWidth = null;
          myHeight = layout.h;
        }

        myInsertPos = bestPos;
      }

      return null;
    }

    @Override
    public void paint(@NotNull NlGraphics gc) {
      Insets padding = layout.getPadding();
      int layoutX = layout.x + padding.left;
      int layoutW = layout.w - padding.width();
      int layoutY = layout.y + padding.top;
      int layoutH = layout.h - padding.height();

      // Highlight the receiver
      gc.useStyle(NlDrawingStyle.DROP_RECIPIENT);
      gc.drawRect(layoutX, layoutY, layoutW, layoutH);

      gc.useStyle(NlDrawingStyle.DROP_ZONE);

      boolean isVertical = myVertical;
      int selfPos = mySelfPos;

      for (MatchPos it : myIndices) {
        int i = it.getDistance();
        int pos = it.getPosition();
        // Don't show insert drop zones for "self"-index since that one goes
        // right through the center of the widget rather than in a sibling
        // position
        if (pos != selfPos) {
          if (isVertical) {
            // draw horizontal lines
            gc.drawLine(layoutX, i, layoutW, i);
          }
          else {
            // draw vertical lines
            gc.drawLine(i, layoutY, i, layoutH);
          }
        }
      }

      Integer currX = myCurrX;
      Integer currY = myCurrY;

      if (currX != null && currY != null) {
        gc.useStyle(NlDrawingStyle.DROP_ZONE_ACTIVE);

        int x = currX;
        int y = currY;

        NlComponent be = components.get(0);

        // Draw a clear line at the closest drop zone (unless we're over the
        // dragged element itself)
        if (myInsertPos != selfPos || selfPos == -1) {
          gc.useStyle(NlDrawingStyle.DROP_PREVIEW);
          if (myWidth != null) {
            int width = myWidth;
            int fromX = x - width / 2;
            int toX = x + width / 2;
            gc.drawLine(fromX, y, toX, y);
          }
          else if (myHeight != null) {
            int height = myHeight;
            int fromY = y - height / 2;
            int toY = y + height / 2;
            gc.drawLine(x, fromY, x, toY);
          }
        }

        if (be.w > 0 && be.h > 0) {
          boolean isLast = myInsertPos == myNumPositions - 1;

          // At least the first element has a bound. Draw rectangles for
          // all dropped elements with valid bounds, offset at the drop
          // point.
          int offsetX;
          int offsetY;
          if (isVertical) {
            offsetX = layoutX - be.x;
            offsetY = currY - be.y - (isLast ? 0 : (be.h / 2));

          }
          else {
            offsetX = currX - be.x - (isLast ? 0 : (be.w / 2));
            offsetY = layoutY - be.y;
          }

          gc.useStyle(NlDrawingStyle.DROP_PREVIEW);
          for (NlComponent element : components) {
            if (element.w > 0 && element.h > 0 && (element.w > layoutW || element.h > layoutH) &&
                layout.getChildCount() == 0) {
              // The bounds of the child does not fully fit inside the target.
              // Limit the bounds to the layout bounds (but only when there
              // are no children, since otherwise positioning around the existing
              // children gets difficult)
              final int px, py, pw, ph;
              if (element.w > layoutW) {
                px = layoutX;
                pw = layoutW;
              }
              else {
                px = element.x + offsetX;
                pw = element.w;
              }
              if (element.h > layoutH) {
                py = layoutY;
                ph = layoutH;
              }
              else {
                py = element.y + offsetY;
                ph = element.h;
              }
              gc.drawRect(px, py, pw, ph);
            }
            else {
              drawElement(gc, element, offsetX, offsetY);
            }
          }
        }
      }
    }

    /**
     * Draws the bounds of the given elements and all its children elements in the canvas
     * with the specified offset.
     *
     * @param gc        the graphics context
     * @param component the element to be drawn
     * @param offsetX   a horizontal delta to add to the current bounds of the element when
     *                  drawing it
     * @param offsetY   a vertical delta to add to the current bounds of the element when
     *                  drawing it
     */
    public void drawElement(NlGraphics gc, NlComponent component, int offsetX, int offsetY) {
      if (component.w > 0 && component.h > 0) {
        gc.drawRect(component.x + offsetX, component.y + offsetY, component.w, component.h);
      }

      for (NlComponent inner : component.getChildren()) {
        drawElement(gc, inner, offsetX, offsetY);
      }
    }

    @Override
    public int getInsertIndex() {
      return myInsertPos;
    }

    @Override
    public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    }
  }

  /** A possible match position */
  private static class MatchPos {
    /** The pixel distance */
    private final int myDistance;
    /** The position among siblings */
    private final int myPosition;

    public MatchPos(int distance, int position) {
      myDistance = distance;
      myPosition = position;
    }

    private int getDistance() {
      return myDistance;
    }

    private int getPosition() {
      return myPosition;
    }
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout, @NotNull NlComponent newChild, @NotNull InsertType insertType) {
    if (insertType == InsertType.MOVE_WITHIN) {
      // Don't adjust widths/heights/weights when just moving within a single
      // LinearLayout
      return;
    }

    // Attempt to set fill-properties on newly added views such that for example,
    // in a vertical layout, a text field defaults to filling horizontally, but not
    // vertically.
    ViewHandler viewHandler = newChild.getViewHandler();
    if (viewHandler != null) {
      boolean vertical = isVertical(layout);
      FillPolicy fill = viewHandler.getFillPolicy();
      String fillParent = VALUE_MATCH_PARENT;
      if (fill.fillHorizontally(vertical)) {
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
      } else if (!vertical && fill == FillPolicy.WIDTH_IN_VERTICAL) {
        // In a horizontal layout, make views that would fill horizontally in a
        // vertical layout have a non-zero weight instead. This will make the item
        // fill but only enough to allow other views to be shown as well.
        // (However, for drags within the same layout we do not touch
        // the weight, since it might already have been tweaked to a particular
        // value)
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, VALUE_1);
      }
      if (fill.fillVertically(vertical)) {
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);
      }
    }

    // If you insert into a layout that already is using layout weights,
    // and all the layout weights are the same (nonzero) value, then use
    // the same weight for this new layout as well. Also duplicate the 0dip/0px/0dp
    // sizes, if used.
    boolean duplicateWeight = true;
    boolean duplicate0dip = true;
    String sameWeight = null;
    String sizeAttribute = isVertical(layout) ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent target : layout.getChildren()) {
      if (target == newChild) {
        continue;
      }
      String weight = target.getAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
      if (weight == null || weight.length() == 0) {
        duplicateWeight = false;
        break;
      } else if (sameWeight != null && !sameWeight.equals(weight)) {
        duplicateWeight = false;
      } else {
        sameWeight = weight;
      }
      String size = target.getAttribute(ANDROID_URI, sizeAttribute);
      if (size != null && !size.startsWith("0")) { //$NON-NLS-1$
        duplicate0dip = false;
        break;
      }
    }
    if (duplicateWeight && sameWeight != null) {
      newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, sameWeight);
      if (duplicate0dip) {
        newChild.setAttribute(ANDROID_URI, sizeAttribute, VALUE_ZERO_DP);
      }
    }
  }

  @Override
  @Nullable
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return new LinearResizeHandler(editor, this, component, horizontalEdgeType, verticalEdgeType);
  }

  private class LinearResizeHandler extends DefaultResizeHandler {
    /** Whether the node should be assigned a new weight */
    public boolean useWeight;
    /** Weight sum to be applied to the parent */
    private float mNewWeightSum;
    /** The weight to be set on the node (provided {@link #useWeight} is true) */
    private float mWeight;
    /** Map from nodes to preferred bounds of nodes where the weights have been cleared */
    public final Map<NlComponent, Dimension> unweightedSizes;
    /** Total required size required by the siblings <b>without</b> weights */
    public int totalLength;
    /** List of nodes which should have their weights cleared */
    public List<NlComponent> mClearWeights;

    public LinearResizeHandler(@NotNull ViewEditor editor,
                               @NotNull ViewGroupHandler handler,
                               @NotNull NlComponent component,
                               @Nullable SegmentType horizontalEdgeType,
                               @Nullable SegmentType verticalEdgeType) {
      super(editor, handler, component, horizontalEdgeType, verticalEdgeType);

      unweightedSizes = editor.measureChildren(layout, new RenderTask.AttributeFilter() {
                                                 @Override
                                                 public String getAttribute(@NotNull XmlTag n,
                                                                            @Nullable String namespace,
                                                                            @NotNull String localName) {
                                                   // Clear out layout weights; we need to measure the unweighted sizes
                                                   // of the children
                                                   if (ATTR_LAYOUT_WEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
                                                     return ""; //$NON-NLS-1$
                                                   }

                                                   return null;
                                                 }
                                               });
      // Compute total required size required by the siblings *without* weights
      totalLength = 0;
      final boolean isVertical = isVertical(layout);
      if (unweightedSizes != null) {
        for (Map.Entry<NlComponent, Dimension> entry : unweightedSizes.entrySet()) {
          Dimension preferredSize = entry.getValue();
          if (isVertical) {
            totalLength += preferredSize.height;
          }
          else {
            totalLength += preferredSize.width;
          }
        }
      }
    }

    /** Resets the computed state */
    void reset() {
      mNewWeightSum = -1;
      useWeight = false;
      mClearWeights = null;
    }

    /** Sets a weight to be applied to the node */
    void setWeight(float weight) {
      useWeight = true;
      mWeight = weight;
    }

    /** Sets a weight sum to be applied to the parent layout */
    void setWeightSum(float weightSum) {
      mNewWeightSum = weightSum;
    }

    /** Marks that the given node should be cleared when applying the new size */
    void clearWeight(NlComponent n) {
      if (mClearWeights == null) {
        mClearWeights = new ArrayList<NlComponent>();
      }
      mClearWeights.add(n);
    }

    /** Applies the state to the nodes */
    public void apply() {
      assert useWeight;

      String value = mWeight > 0 ? formatFloatAttribute(mWeight) : null;
      component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, value);

      if (mClearWeights != null) {
        for (NlComponent n : mClearWeights) {
          if (getWeight(n) > 0.0f) {
            n.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, null);
          }
        }
      }

      if (mNewWeightSum > 0.0) {
        layout.setAttribute(ANDROID_URI, ATTR_WEIGHT_SUM,
                            formatFloatAttribute(mNewWeightSum));
      }
    }

    protected void updateResizeState(final NlComponent component, NlComponent layout,
                                     Rectangle oldBounds, Rectangle newBounds, SegmentType horizontalEdge,
                                     SegmentType verticalEdge) {
      // Update the resize state.
      // This method attempts to compute a new layout weight to be used in the direction
      // of the linear layout. If the superclass has already determined that we can snap to
      // a wrap_content or match_parent boundary, we prefer that. Otherwise, we attempt to
      // compute a layout weight - which can fail if the size is too big (not enough room),
      // or if the size is too small (smaller than the natural width of the node), and so on.
      // In that case this method just aborts, which will leave the resize state object
      // in such a state that it will call the superclass to resize instead, which will fall
      // back to device independent pixel sizing.
      reset();

      if (oldBounds.equals(newBounds)) {
        return;
      }

      // If we're setting the width/height to wrap_content/match_parent in the dimension of the
      // linear layout, then just apply wrap_content and clear weights.
      boolean isVertical = isVertical(layout);
      if (!isVertical && verticalEdge != null) {
        if (wrapWidth || fillWidth) {
          clearWeight(component);
          return;
        }
        if (newBounds.width == oldBounds.width) {
          return;
        }
      }

      if (isVertical && horizontalEdge != null) {
        if (wrapHeight || fillHeight) {
          clearWeight(component);
          return;
        }
        if (newBounds.height == oldBounds.height) {
          return;
        }
      }

      // Compute weight sum
      float sum = getWeightSum(layout);
      if (sum <= 0.0f) {
        sum = 1.0f;
        setWeightSum(sum);
      }

      // If the new size of the node is smaller than its preferred/wrap_content size,
      // then we cannot use weights to size it; switch to pixel-based sizing instead
      Map<NlComponent, Dimension> sizes = unweightedSizes;
      Dimension nodePreferredSize = sizes != null ? sizes.get(component) : null;
      if (nodePreferredSize != null) {
        if (horizontalEdge != null && newBounds.height < nodePreferredSize.height ||
            verticalEdge != null && newBounds.width < nodePreferredSize.width) {
          return;
        }
      }

      Rectangle layoutBounds = new Rectangle(layout.x, layout.y, layout.w, layout.h);
      int remaining = (isVertical ? layoutBounds.height : layoutBounds.width) - totalLength;
      Dimension nodeBounds = sizes != null ? sizes.get(component) : null;
      if (nodeBounds == null) {
        return;
      }

      if (remaining > 0) {
        int missing = 0;
        if (isVertical) {
          if (newBounds.height > nodeBounds.height) {
            missing = newBounds.height - nodeBounds.height;
          } else if (wrapBounds != null && newBounds.height > wrapBounds.height) {
            // The weights concern how much space to ADD to the view.
            // What if we have resized it to a size *smaller* than its current
            // size without the weight delta? This can happen if you for example
            // have set a hardcoded size, such as 500dp, and then size it to some
            // smaller size.
            missing = newBounds.height - wrapBounds.height;
            remaining += nodeBounds.height - wrapBounds.height;
            wrapHeight = true;
          }
        } else {
          if (newBounds.width > nodeBounds.width) {
            missing = newBounds.width - nodeBounds.width;
          } else if (wrapBounds != null && newBounds.width > wrapBounds.width) {
            missing = newBounds.width - wrapBounds.width;
            remaining += nodeBounds.width - wrapBounds.width;
            wrapWidth = true;
          }
        }
        if (missing > 0) {
          // (weight / weightSum) * remaining = missing, so
          // weight = missing * weightSum / remaining
          float weight = missing * sum / remaining;
          setWeight(weight);
        }
      }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden in this layout in order to make resizing affect the layout_weight
     * attribute instead of the layout_width (for horizontal LinearLayouts) or
     * layout_height (for vertical LinearLayouts).
     */
    @Override
    protected void setNewSizeBounds(@NotNull NlComponent component,
                                    @NotNull NlComponent layout,
                                    @NotNull Rectangle oldBounds,
                                    @NotNull Rectangle newBounds,
                                    @Nullable SegmentType horizontalEdge,
                                    @Nullable SegmentType verticalEdge) {
      updateResizeState(component, layout, oldBounds, newBounds, horizontalEdge, verticalEdge);

      if (useWeight) {
        apply();

        // Handle resizing in the opposite dimension of the layout
        final boolean isVertical = isVertical(layout);
        if (!isVertical && horizontalEdge != null) {
          if (newBounds.height != oldBounds.height || wrapHeight || fillHeight) {
            component.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, getHeightAttribute());
          }
        }
        if (isVertical && verticalEdge != null) {
          if (newBounds.width != oldBounds.width || wrapWidth || fillWidth) {
            component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, getWidthAttribute());
          }
        }
      }
      else {
        component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, null);
        super.setNewSizeBounds(component, layout, oldBounds, newBounds, horizontalEdge, verticalEdge);
      }
    }

    @Override
    protected String getResizeUpdateMessage(@NotNull NlComponent child,
                                            @NotNull NlComponent parent,
                                            @NotNull Rectangle newBounds,
                                            @Nullable SegmentType horizontalEdge,
                                            @Nullable SegmentType verticalEdge) {
      updateResizeState(child, parent, newBounds, newBounds,
                        horizontalEdge, verticalEdge);

      if (useWeight) {
        String weight = formatFloatAttribute(mWeight);
        String dimension = String.format("weight %1$s", weight);

        String width;
        String height;
        if (isVertical(parent)) {
          width = getWidthAttribute();
          height = dimension;
        } else {
          width = dimension;
          height = getHeightAttribute();
        }

        if (horizontalEdge == null) {
          return width;
        } else if (verticalEdge == null) {
          return height;
        } else {
          // U+00D7: Unicode for multiplication sign
          return String.format("%s \u00D7 %s", width, height);
        }
      } else {
        return super.getResizeUpdateMessage(child, parent, newBounds,
                                            horizontalEdge, verticalEdge);
      }
    }
   }

  /**
   * Returns the layout weight of of the given child of a LinearLayout, or 0.0 if it
   * does not define a weight
   */
  private static float getWeight(@NotNull NlComponent linearLayoutChild) {
    String weight = linearLayoutChild.getAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
    if (weight != null && weight.length() > 0) {
      try {
        return Float.parseFloat(weight);
      } catch (NumberFormatException ignore) {
      }
    }

    return 0.0f;
  }

  /**
   * Returns the sum of all the layout weights of the children in the given LinearLayout
   *
   * @param linearLayout the layout to compute the total sum for
   * @return the total sum of all the layout weights in the given layout
   */
  private static float getWeightSum(@NotNull NlComponent linearLayout) {
    String weightSum = linearLayout.getAttribute(ANDROID_URI, ATTR_WEIGHT_SUM);
    float sum = -1.0f;
    if (weightSum != null) {
      // Distribute
      try {
        sum = Float.parseFloat(weightSum);
        return sum;
      } catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }

    return getSumOfWeights(linearLayout);
  }

  private static float getSumOfWeights(@NotNull NlComponent linearLayout) {
    float sum = 0.0f;
    for (NlComponent child : linearLayout.getChildren()) {
      sum += getWeight(child);
    }

    return sum;
  }

  private void clearWeights(@NotNull NlComponent component, @NotNull List<NlComponent> selectedChildren) {
    // Clear attributes
    String sizeAttribute = isVertical(component) ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent selected : selectedChildren) {
      selected.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, null);
      String size = selected.getAttribute(ANDROID_URI, sizeAttribute);
      if (size != null && size.startsWith("0")) {
        selected.setAttribute(ANDROID_URI, sizeAttribute, VALUE_WRAP_CONTENT);
      }
    }
  }

  private void distributeWeights(@NotNull NlComponent component, @NotNull List<NlComponent> selectedChildren) {
    // Any XML to get weight sum?
    String weightSum = component.getAttribute(ANDROID_URI, ATTR_WEIGHT_SUM);
    double sum = -1.0;
    if (weightSum != null && !weightSum.isEmpty()) {
      // Distribute
      try {
        sum = Double.parseDouble(weightSum);
      }
      catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }
    int numTargets = selectedChildren.size();
    double share;
    if (sum <= 0.0) {
      // The sum will be computed from the children, so just
      // use arbitrary amount
      share = 1.0;
    }
    else {
      share = sum / numTargets;
    }
    String value = formatFloatAttribute((float)share);
    String sizeAttribute = isVertical(component) ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent selected : selectedChildren) {
      selected.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, value);

      // Also set the width/height to 0dp to ensure actual equal
      // size (without this, only the remaining space is
      // distributed)
      if (VALUE_WRAP_CONTENT.equals(selected.getAttribute(ANDROID_URI, sizeAttribute))) {
        selected.setAttribute(ANDROID_URI, sizeAttribute, VALUE_ZERO_DP);
      }
    }
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    int rank = 0;
    actions.add(new ToggleOrientationAction().setRank(rank += 20));
    actions.add(new BaselineAction().setRank(rank += 20));
    actions.add(new DistributeWeightsAction().setRank(rank += 20));
    actions.add(new DominateWeightsAction().setRank(rank += 20));
    actions.add(new ClearWeightsAction().setRank(rank += 20));
    actions.add(new ViewActionSeparator().setRank(rank += 20));
    addDefaultViewActions(actions, rank);
  }

  @Override
  public void addPopupMenuActions(@NotNull List<ViewAction> actions) {
    addToolbarActionsToMenu("LinearLayout", actions);
  }

  private class ToggleOrientationAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      boolean isHorizontal = !isVertical(component);
      String value = isHorizontal ? VALUE_VERTICAL : null; // null: horizontal is the default
      component.setAttribute(ANDROID_URI, ATTR_ORIENTATION, value);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      boolean vertical = isVertical(component);

      presentation.setLabel("Convert orientation to " + (!vertical ? VALUE_VERTICAL : VALUE_HORIZONTAL));
      Icon icon = vertical ? AndroidDesignerIcons.SwitchVerticalLinear : AndroidDesignerIcons.SwitchHorizontalLinear;
      presentation.setIcon(icon);
    }
  }

  private class DistributeWeightsAction extends DirectViewAction {
    public DistributeWeightsAction() {
      super(AndroidDesignerIcons.DistributeWeights, "Distribute Weights Evenly");
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setVisible(selectedChildren.size() > 1);
    }

    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {

      distributeWeights(component, selectedChildren);
    }
  }

  private class DominateWeightsAction extends DirectViewAction {
    public DominateWeightsAction() {
      super(AndroidDesignerIcons.DominateWeight, "Assign All Weight");
    }

    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {
      distributeWeights(component, selectedChildren);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setVisible(selectedChildren.size() > 1);
    }
  }

  private class ClearWeightsAction extends DirectViewAction {
    public ClearWeightsAction() {
      super(AndroidDesignerIcons.ClearWeights, "Clear All Weights");
    }

    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {
      clearWeights(component, selectedChildren);
    }


    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setVisible(selectedChildren.size() > 1);
    }
  }

  private static class BaselineAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {
      boolean align = !isBaselineAligned(component);
      component.setAttribute(ANDROID_URI, ATTR_BASELINE_ALIGNED, align ? null : VALUE_FALSE);
    }


    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      boolean align = !isBaselineAligned(component);
      presentation.setIcon(align ? AndroidDesignerIcons.Baseline : AndroidDesignerIcons.NoBaseline);
      presentation.setLabel(align ? "Align with the baseline" : "Do not align with the baseline");
    }

    private static boolean isBaselineAligned(NlComponent component) {
      String value = component.getAttribute(ANDROID_URI, ATTR_BASELINE_ALIGNED);
      return value == null ? true : Boolean.valueOf(value);
    }
  }
}
