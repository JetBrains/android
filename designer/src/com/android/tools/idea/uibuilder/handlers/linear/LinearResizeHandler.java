/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.linear;

import com.android.tools.idea.uibuilder.api.DefaultResizeHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.SegmentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.utils.XmlUtils.formatFloatAttribute;

/**
 * Handler to resize the LinearLayout's children
 */
class LinearResizeHandler extends DefaultResizeHandler {
  /**
   * Whether the node should be assigned a new weight
   */
  public boolean useWeight;
  /**
   * Weight sum to be applied to the parent
   */
  private float mNewWeightSum;
  /**
   * The weight to be set on the node (provided {@link #useWeight} is true)
   */
  private float mWeight;
  /**
   * Map from nodes to preferred bounds of nodes where the weights have been cleared
   */
  public final Map<NlComponent, Dimension> unweightedSizes;
  /**
   * Total required size required by the siblings <b>without</b> weights
   */
  public int totalLength;
  /**
   * List of nodes which should have their weights cleared
   */
  public List<NlComponent> mClearWeights;

  protected LinearLayoutHandler myHandler;

  public LinearResizeHandler(@NotNull ViewEditor editor,
                             @NotNull LinearLayoutHandler handler,
                             @NotNull NlComponent component,
                             @Nullable SegmentType horizontalEdgeType,
                             @Nullable SegmentType verticalEdgeType) {
    super(editor, handler, component, horizontalEdgeType, verticalEdgeType);
    myHandler = handler;

    unweightedSizes = editor.measureChildren(layout, (n, namespace, localName) -> {
      // Clear out layout weights; we need to measure the unweighted sizes
      // of the children
      if (ATTR_LAYOUT_WEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
        return ""; //$NON-NLS-1$
      }

      return null;
    });
    // Compute total required size required by the siblings *without* weights
    totalLength = 0;
    final boolean isVertical = handler.isVertical(layout);
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

  /**
   * Resets the computed state
   */
  void reset() {
    mNewWeightSum = -1;
    useWeight = false;
    mClearWeights = null;
  }

  /**
   * Sets a weight to be applied to the node
   */
  void setWeight(float weight) {
    useWeight = true;
    mWeight = weight;
  }

  /**
   * Sets a weight sum to be applied to the parent layout
   */
  void setWeightSum(float weightSum) {
    mNewWeightSum = weightSum;
  }

  /**
   * Marks that the given node should be cleared when applying the new size
   */
  void clearWeight(NlComponent n) {
    if (mClearWeights == null) {
      mClearWeights = new ArrayList<>();
    }
    mClearWeights.add(n);
  }

  /**
   * Applies the state to the nodes
   */
  public void apply() {
    assert useWeight;

    String value = mWeight > 0 ? formatFloatAttribute(mWeight) : null;
    component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, value);

    if (mClearWeights != null) {
      for (NlComponent n : mClearWeights) {
        if (LinearLayoutHandler.getWeight(n) > 0.0f) {
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
    boolean isVertical = myHandler.isVertical(layout);
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
    float sum = LinearLayoutHandler.getWeightSum(layout);
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

    Rectangle layoutBounds = new Rectangle(NlComponentHelperKt.getX(layout), NlComponentHelperKt.getY(layout), NlComponentHelperKt.getW(layout), NlComponentHelperKt
      .getH(layout));
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
        }
        else if (wrapBounds != null && newBounds.height > wrapBounds.height) {
          // The weights concern how much space to ADD to the view.
          // What if we have resized it to a size *smaller* than its current
          // size without the weight delta? This can happen if you for example
          // have set a hardcoded size, such as 500dp, and then size it to some
          // smaller size.
          missing = newBounds.height - wrapBounds.height;
          remaining += nodeBounds.height - wrapBounds.height;
          wrapHeight = true;
        }
      }
      else {
        if (newBounds.width > nodeBounds.width) {
          missing = newBounds.width - nodeBounds.width;
        }
        else if (wrapBounds != null && newBounds.width > wrapBounds.width) {
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
      final boolean isVertical = myHandler.isVertical(layout);
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
      if (myHandler.isVertical(parent)) {
        width = getWidthAttribute();
        height = dimension;
      }
      else {
        width = dimension;
        height = getHeightAttribute();
      }

      if (horizontalEdge == null) {
        return width;
      }
      else if (verticalEdge == null) {
        return height;
      }
      else {
        // U+00D7: Unicode for multiplication sign
        return String.format("%s \u00D7 %s", width, height);
      }
    }
    else {
      return super.getResizeUpdateMessage(child, parent, newBounds,
                                          horizontalEdge, verticalEdge);
    }
  }
}
