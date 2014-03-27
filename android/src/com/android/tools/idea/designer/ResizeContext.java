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

import com.android.SdkConstants;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.android.SdkConstants.*;

/** State held during resizing operations */
public class ResizeContext {
  private final EditableArea myArea;

  /**
   * The node being resized
   */
  public final RadViewComponent node;

  /**
   * The layout containing the resized node
   */
  public final RadViewComponent layout;

  /** The proposed resized bounds of the node */
  public Rectangle bounds;

  /** The preferred wrap_content bounds of the node */
  public Dimension wrapSize;

  /** The match parent bounds of the node */
  public Dimension fillSize;

  /** The suggested horizontal fill_parent guideline position */
  public Segment horizontalFillSegment;

  /** The suggested vertical fill_parent guideline position */
  public Segment verticalFillSegment;

  /** The type of horizontal edge being resized, or null */
  public SegmentType horizontalEdgeType;

  /** The type of vertical edge being resized, or null */
  public SegmentType verticalEdgeType;

  /** Whether the user has snapped to the wrap_content width */
  public boolean wrapWidth;

  /** Whether the user has snapped to the wrap_content height */
  public boolean wrapHeight;

  /** Whether the user has snapped to the match_parent width */
  public boolean fillWidth;

  /** Whether the user has snapped to the match_parent height */
  public boolean fillHeight;

  /** Custom field for use by subclasses */
  public Object clientData;

  /** Keyboard mask */
  public int modifierMask;

  /**
   * The actual view object for the layout containing the resizing operation,
   * or null if not known
   */
  @Nullable
  public Object layoutView;

  /**
   * Constructs a new {@link ResizeContext}
   *
   * @param area the associated area
   * @param layout the parent layout containing the resized node
   * @param layoutView the actual View instance for the layout, or null if not known
   * @param node the node being resized
   */
  ResizeContext(EditableArea area, RadViewComponent layout, @Nullable Object layoutView, RadViewComponent node) {
    myArea = area;
    this.layout = layout;
    this.node = node;
    this.layoutView = layoutView;

    initializeSnapBounds();
  }

  protected void initializeSnapBounds() {
    wrapSize = node.calculateWrapSize(myArea);

    String width = node.getAttribute(ATTR_LAYOUT_WIDTH, SdkConstants.NS_RESOURCES);
    String height = node.getAttribute(ATTR_LAYOUT_HEIGHT, SdkConstants.NS_RESOURCES);

    fillSize = new Dimension();
    Rectangle bounds = node.getBounds();
    Rectangle parentBounds = node.getParent().getBounds();
    if (width != null && isFill(width)) {
      // Already have the fill bounds since view specifies match parent already
      fillSize.width = bounds.width;
    } else {
      // TODO: Subtract padding too?
      fillSize.width = parentBounds.x + parentBounds.width - bounds.x;
    }
    if (height != null && isFill(height)) {
      // Already have the fill bounds since view specifies match parent already
      fillSize.height = bounds.height;
    } else {
      // TODO: Subtract padding too?
      fillSize.height = parentBounds.y + parentBounds.height - bounds.y;
    }

    // If fillSize and wrapSize are identical, space them out a bit to let the user select each
    if (fillSize.width == wrapSize.width) {
      fillSize.width += 5;
    }
    if (fillSize.height == wrapSize.height) {
      fillSize.height += 5;
    }
  }

  public static boolean isFill(String value) {
    return VALUE_FILL_PARENT.equals(value) || VALUE_MATCH_PARENT.equals(value);
  }

  /**
   * Returns the width attribute to be set to match the new bounds
   *
   * @return the width string, never null
   */
  public String getWidthAttribute() {
    if (wrapWidth) {
      return VALUE_WRAP_CONTENT;
    } else if (fillWidth) {
      //return mRule.getFillParentValueName();
      return VALUE_MATCH_PARENT;
    } else {
      return AndroidDesignerUtils.pxToDpWithUnits(myArea, bounds.width);
    }
  }

  /**
   * Returns the height attribute to be set to match the new bounds
   *
   * @return the height string, never null
   */
  public String getHeightAttribute() {
    if (wrapHeight) {
      return VALUE_WRAP_CONTENT;
    } else if (fillHeight) {
      //return mRule.getFillParentValueName();
      return VALUE_MATCH_PARENT;
    } else {
      return AndroidDesignerUtils.pxToDpWithUnits(myArea, bounds.height);
    }
  }
}
