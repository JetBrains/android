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
import com.android.utils.XmlUtils;
import com.google.common.collect.Maps;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.MAX_MATCH_DISTANCE;

public class LinearLayoutResizeOperation extends ResizeOperation {

  public LinearLayoutResizeOperation(OperationContext context) {
    super(context);
  }

  /**
   * Returns true if the given node represents a vertical linear layout.
   * @param node the node to check layout orientation for
   * @return true if the layout is in vertical mode, otherwise false
   */
  protected static boolean isVertical(@NotNull RadViewComponent node) {
    // Horizontal is the default, so if no value is specified it is horizontal.
    @NotNull XmlTag tag = node.getTag();
    return VALUE_VERTICAL.equals(tag.getAttributeValue(ATTR_ORIENTATION, ANDROID_URI));
  }

  protected void updateResizeContext(LinearResizeContext resizeContext,
                                     final RadViewComponent node,
                                     RadViewComponent layout,
                                     Rectangle oldBounds,
                                     Rectangle newBounds,
                                     SegmentType horizontalEdge,
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
    resizeContext.reset();

    if (oldBounds.equals(newBounds)) {
      return;
    }

    // If we're setting the width/height to wrap_content/match_parent in the dimension of the
    // linear layout, then just apply wrap_content and clear weights.
    boolean isVertical = isVertical(layout);
    if (!isVertical && verticalEdge != null) {
      if (resizeContext.wrapWidth || resizeContext.fillWidth) {
        resizeContext.clearWeight(node);
        return;
      }
      if (newBounds.width == oldBounds.width) {
        return;
      }
    }

    if (isVertical && horizontalEdge != null) {
      if (resizeContext.wrapHeight || resizeContext.fillHeight) {
        resizeContext.clearWeight(node);
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
      resizeContext.setWeightSum(sum);
    }

    // If the new size of the node is smaller than its preferred/wrap_content size,
    // then we cannot use weights to size it; switch to pixel-based sizing instead
    Map<XmlTag, Dimension> sizes = resizeContext.myUnweightedSizes;
    Dimension nodePreferredSize = sizes.get(node.getTag());
    if (nodePreferredSize != null) {
      if (horizontalEdge != null && newBounds.height < nodePreferredSize.height ||
          verticalEdge != null && newBounds.width < nodePreferredSize.width) {
        return;
      }
    }

    Rectangle layoutBounds = layout.getBounds();
    int remaining = (isVertical ? layoutBounds.height : layoutBounds.width) - resizeContext.myTotalLength;
    Dimension nodeSize = sizes.get(node.getTag());
    if (nodeSize == null) {
      return;
    }

    if (remaining > 0) {
      int missing = 0;
      if (isVertical) {
        if (newBounds.height > nodeSize.height) {
          missing = newBounds.height - nodeSize.height;
        } else if (newBounds.height > resizeContext.wrapSize.height) {
          // The weights concern how much space to ADD to the view.
          // What if we have resized it to a size *smaller* than its current
          // size without the weight delta? This can happen if you for example
          // have set a hardcoded size, such as 500dp, and then size it to some
          // smaller size.
          missing = newBounds.height - resizeContext.wrapSize.height;
          remaining += nodeSize.height - resizeContext.wrapSize.height;
          resizeContext.wrapHeight = true;
        }
      } else {
        if (newBounds.width > nodeSize.width) {
          missing = newBounds.width - nodeSize.width;
        } else if (newBounds.width > resizeContext.wrapSize.width) {
          missing = newBounds.width - resizeContext.wrapSize.width;
          remaining += nodeSize.width - resizeContext.wrapSize.width;
          resizeContext.wrapWidth = true;
        }
      }
      if (missing > 0) {
        // (weight / weightSum) * remaining = missing, so
        // weight = missing * weightSum / remaining
        float weight = missing * sum / remaining;
        resizeContext.setWeight(weight);
      }
    }
  }

  @Override
  public void onResizeUpdate(@NotNull RadViewComponent parent, @NotNull Rectangle newBounds, int modifierMask) {
    myResizeContext.bounds = newBounds;
    myResizeContext.modifierMask = modifierMask;

    // Match on wrap bounds
    myResizeContext.wrapWidth = myResizeContext.wrapHeight = false;
    if (myResizeContext.wrapSize != null) {
      Dimension b = myResizeContext.wrapSize;
      int maxMatchDistance = MAX_MATCH_DISTANCE;
      if (myResizeContext.horizontalEdgeType != null) {
        if (Math.abs(newBounds.height - b.height) < maxMatchDistance) {
          myResizeContext.wrapHeight = true;
          if (myResizeContext.horizontalEdgeType == SegmentType.TOP) {
            newBounds.y += newBounds.height - b.height;
          }
          newBounds.height = b.height;
        }
      }
      if (myResizeContext.verticalEdgeType != null) {
        if (Math.abs(newBounds.width - b.width) < maxMatchDistance) {
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

  /**
   * {@inheritDoc}
   * <p>
   * Overridden in this layout in order to make resizing affect the layout_weight
   * attribute instead of the layout_width (for horizontal LinearLayouts) or
   * layout_height (for vertical LinearLayouts).
   */
  @Override
  protected void setNewSizeBounds(ResizeContext context, final RadViewComponent node, RadViewComponent layout,
                                  Rectangle oldBounds, Rectangle newBounds, SegmentType horizontalEdge,
                                  SegmentType verticalEdge) {
    LinearResizeContext resizeContext = (LinearResizeContext) context;
    updateResizeContext(resizeContext, node, layout, oldBounds, newBounds, horizontalEdge, verticalEdge);

    if (resizeContext.myUseWeight) {
      // Handle resizing in the opposite dimension of the layout
      final boolean isVertical = isVertical(layout);
      if (isVertical && horizontalEdge != null || !isVertical && verticalEdge != null) {
        resizeContext.apply();
      }

      if (!isVertical && horizontalEdge != null) {
        if (newBounds.height != oldBounds.height || resizeContext.wrapHeight || resizeContext.fillHeight) {
          node.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, resizeContext.getHeightAttribute());
        }
      }
      if (isVertical && verticalEdge != null) {
        if (newBounds.width != oldBounds.width || resizeContext.wrapWidth || resizeContext.fillWidth) {
          node.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, resizeContext.getWidthAttribute());
        }
      }
    } else {
      node.setAttribute(ATTR_LAYOUT_WEIGHT, ANDROID_URI, null);
      super.setNewSizeBounds(resizeContext, node, layout, oldBounds, newBounds, horizontalEdge, verticalEdge);
    }
  }

  @Override
  protected String getResizeUpdateMessage(ResizeContext context, RadViewComponent child, RadViewComponent parent,
                                          Rectangle newBounds, SegmentType horizontalEdge, SegmentType verticalEdge) {
    LinearResizeContext resizeContext = (LinearResizeContext) context;
    updateResizeContext(resizeContext, child, parent, child.getBounds(), newBounds, horizontalEdge, verticalEdge);

    if (resizeContext.myUseWeight) {
      String weight = XmlUtils.formatFloatAttribute(resizeContext.myWeight);
      String dimension = String.format("weight %1$s", weight);

      String width;
      String height;
      if (isVertical(parent)) {
        width = resizeContext.getWidthAttribute();
        height = dimension;
      } else {
        width = dimension;
        height = resizeContext.getHeightAttribute();
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
      return super.getResizeUpdateMessage(context, child, parent, newBounds, horizontalEdge, verticalEdge);
    }
  }

  /**
   * Returns the layout weight off the given child of a LinearLayout, or 0.0 if it
   * does not define a weight
   */
  private static float getWeight(RadViewComponent linearLayoutChild) {
    String weight = linearLayoutChild.getTag().getAttributeValue(ATTR_LAYOUT_WEIGHT, ANDROID_URI);
    if (weight != null && weight.length() > 0) {
      try {
        return Float.parseFloat(weight);
      } catch (NumberFormatException nfe) {
        Logger.getInstance(LinearLayoutResizeOperation.class).warn(String.format("Invalid weight %1$s", weight), nfe);
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
  private static float getWeightSum(RadViewComponent linearLayout) {
    String weightSum = linearLayout.getTag().getAttributeValue(ATTR_WEIGHT_SUM, ANDROID_URI);
    if (weightSum != null) {
      // Distribute
      try {
        return Float.parseFloat(weightSum);
      } catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }

    return getSumOfWeights(linearLayout);
  }

  private static float getSumOfWeights(RadViewComponent linearLayout) {
    float sum = 0.0f;
    for (RadComponent child : linearLayout.getChildren()) {
      sum += getWeight((RadViewComponent)child);
    }

    return sum;
  }

  @Override
  protected ResizeContext createResizeContext(RadViewComponent layout, @Nullable Object layoutView, RadViewComponent node) {
    return new LinearResizeContext(myContext.getArea(), layout, layoutView, node);
  }

  /** Custom resize state used during linear layout resizing */
  private class LinearResizeContext extends ResizeContext {
    /** Whether the node should be assigned a new weight */
    public boolean myUseWeight;
    /** Weight sum to be applied to the parent */
    private float myNewWeightSum;
    /** The weight to be set on the node (provided {@link #myUseWeight} is true) */
    private float myWeight;
    /** Map from nodes to preferred bounds of nodes where the weights have been cleared */
    public final Map<XmlTag, Dimension> myUnweightedSizes;
    /** Total required size required by the siblings <b>without</b> weights */
    public int myTotalLength;
    /** List of nodes which should have their weights cleared */
    public List<RadViewComponent> myClearWeights;

    private LinearResizeContext(EditableArea area, RadViewComponent layout, @Nullable Object layoutView, RadViewComponent node) {
      super(area, layout, layoutView, node);

      myUnweightedSizes = computeUnweightedSizes();
      if (myUnweightedSizes != null) {
        // Compute total required size required by the siblings *without* weights
        myTotalLength = 0;
        final boolean isVertical = isVertical(layout);
        for (Map.Entry<XmlTag, Dimension> entry : myUnweightedSizes.entrySet()) {
          Dimension preferredSize = entry.getValue();
          if (isVertical) {
            myTotalLength += preferredSize.height;
          } else {
            myTotalLength += preferredSize.width;
          }
        }
      }
    }

    @Nullable
    private Map<XmlTag, Dimension> computeUnweightedSizes() {
      Map<XmlTag, Dimension> unweightedSizes = Maps.newHashMap();
      RadComponent parent = myComponent.getParent();
      if (!(parent instanceof RadViewComponent)) {
        return null;
      }
      XmlTag parentTag = ((RadViewComponent)parent).getTag();
      if (parentTag != null) {
        RenderTask task = AndroidDesignerUtils.createRenderTask(myContext.getArea());
        if (task == null) {
          return null;
        }

        // Measure unweighted bounds
        Map<XmlTag, ViewInfo> map = task.measureChildren(parentTag, new RenderTask.AttributeFilter() {
          @Override
          public String getAttribute(@NotNull XmlTag n, @Nullable String namespace, @NotNull String localName) {
            // Clear out layout weights; we need to measure the unweighted sizes
            // of the children
            if (ATTR_LAYOUT_WEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
              return ""; //$NON-NLS-1$
            }

            return null;
          }
        });
        if (map != null) {
          for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
            ViewInfo viewInfo = entry.getValue();
            viewInfo = RenderService.getSafeBounds(viewInfo);
            Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
            unweightedSizes.put(entry.getKey(), size);
          }
        }
      }

      return unweightedSizes;
    }

    /** Resets the computed state */
    void reset() {
      myNewWeightSum = -1;
      myUseWeight = false;
      myClearWeights = null;
    }

    /** Sets a weight to be applied to the node */
    void setWeight(float weight) {
      myUseWeight = true;
      this.myWeight = weight;
    }

    /** Sets a weight sum to be applied to the parent layout */
    void setWeightSum(float weightSum) {
      myNewWeightSum = weightSum;
    }

    /** Marks that the given node should be cleared when applying the new size */
    void clearWeight(RadViewComponent n) {
      if (myClearWeights == null) {
        myClearWeights = new ArrayList<RadViewComponent>();
      }
      myClearWeights.add(n);
    }

    /** Applies the state to the nodes */
    public void apply() {
      assert myUseWeight;

      String value = myWeight > 0 ? XmlUtils.formatFloatAttribute(myWeight) : null;
      node.setAttribute(ATTR_LAYOUT_WEIGHT, ANDROID_URI, value);

      if (myClearWeights != null) {
        for (RadViewComponent n : myClearWeights) {
          if (getWeight(n) > 0.0f) {
            n.setAttribute(ATTR_LAYOUT_WEIGHT, ANDROID_URI, null);
          }
        }
      }

      if (myNewWeightSum > 0.0) {
        layout.setAttribute(ATTR_WEIGHT_SUM, ANDROID_URI, XmlUtils.formatFloatAttribute(myNewWeightSum));
      }
    }
  }
}
