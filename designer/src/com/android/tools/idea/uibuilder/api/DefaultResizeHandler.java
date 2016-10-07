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
package com.android.tools.idea.uibuilder.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.*;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.MAX_MATCH_DISTANCE;

/**
 * Default implementation of a {@link ResizeHandler} which provides
 * basic resizing (setting layout_width/layout_height to match_parent/
 * wrap_content/Ndp.
 */
public class DefaultResizeHandler extends ResizeHandler {
  /** The proposed resized bounds of the node */
  public Rectangle bounds;

  /** The preferred wrap_content bounds of the node */
  public Dimension wrapBounds;

  /** Whether the user has snapped to the wrap_content width */
  public boolean wrapWidth;

  /** Whether the user has snapped to the wrap_content height */
  public boolean wrapHeight;

  /** Whether the user has snapped to the match_parent width */
  public boolean fillWidth;

  /** Whether the user has snapped to the match_parent height */
  public boolean fillHeight;

  /** The suggested horizontal fill_parent guideline position */
  public Segment horizontalFillSegment;

  /** The suggested vertical fill_parent guideline position */
  public Segment verticalFillSegment;

  /**
   * Constructs a new resize handler to resize the given component
   *
   * @param editor             the associated IDE editor
   * @param handler            the view group handler that may receive the dragged components
   * @param component          the component being resized
   * @param horizontalEdgeType the horizontal (top or bottom) edge being resized, if any
   * @param verticalEdgeType   the vertical (left or right) edge being resized, if any
   */
  public DefaultResizeHandler(@NotNull ViewEditor editor,
                              @NotNull ViewGroupHandler handler,
                              @NotNull NlComponent component,
                              @Nullable SegmentType horizontalEdgeType,
                              @Nullable SegmentType verticalEdgeType) {
    super(editor, handler, component, horizontalEdgeType, verticalEdgeType);

    Map<NlComponent, Dimension> sizes = editor.measureChildren(layout, new RenderTask.AttributeFilter() {
      @Override
      public String getAttribute(@NotNull XmlTag n, @Nullable String namespace, @NotNull String localName) {
        // Change attributes to wrap_content
        if (ATTR_LAYOUT_WIDTH.equals(localName) && ANDROID_URI.equals(namespace)) {
          return VALUE_WRAP_CONTENT;
        }
        if (ATTR_LAYOUT_HEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
          return VALUE_WRAP_CONTENT;
        }

        return null;
      }
    });

    if (sizes != null) {
      wrapBounds = sizes.get(component);
    }
  }

  @Nullable
  @Override
  public String update(@AndroidCoordinate int x,
                       @AndroidCoordinate int y,
                       int modifiers,
                       @NotNull @AndroidCoordinate Rectangle newBounds) {
    super.update(x, y, modifiers, newBounds);
    bounds = newBounds;

    // Match on wrap bounds
    wrapWidth = wrapHeight = false;
    if (wrapBounds != null) {
      Dimension b = wrapBounds;
      int maxMatchDistance = MAX_MATCH_DISTANCE;
      if (horizontalEdgeType != null) {
        if (Math.abs(newBounds.height - b.height) < maxMatchDistance) {
          wrapHeight = true;
          if (horizontalEdgeType == SegmentType.TOP) {
            newBounds.y += newBounds.height - b.height;
          }
          newBounds.height = b.height;
        }
      }
      if (verticalEdgeType != null) {
        if (Math.abs(newBounds.width - b.width) < maxMatchDistance) {
          wrapWidth = true;
          if (verticalEdgeType == SegmentType.LEFT) {
            newBounds.x += newBounds.width - b.width;
          }
          newBounds.width = b.width;
        }
      }
    }

    // Match on fill bounds
    horizontalFillSegment = null;
    fillHeight = false;
    Rectangle parentBounds = new Rectangle(layout.x, layout.y, layout.w, layout.h);
    if (horizontalEdgeType == SegmentType.BOTTOM && !wrapHeight) {
      horizontalFillSegment = new Segment(parentBounds.y + parentBounds.height, newBounds.x,
                                          newBounds.x + newBounds.width,
                                          null /*node*/, null /*id*/, SegmentType.BOTTOM, MarginType.NO_MARGIN);
      if (Math.abs(newBounds.y + newBounds.height - (parentBounds.y + parentBounds.height)) < MAX_MATCH_DISTANCE) {
        fillHeight = true;
        newBounds.height = parentBounds.y + parentBounds.height - newBounds.y;
      }
    }
    verticalFillSegment = null;
    fillWidth = false;
    if (verticalEdgeType == SegmentType.RIGHT && !wrapWidth) {
      verticalFillSegment = new Segment(parentBounds.x + parentBounds.width, newBounds.y,
                                        newBounds.y + newBounds.height,
                                        null /*node*/, null /*id*/, SegmentType.RIGHT, MarginType.NO_MARGIN);
      if (Math.abs(newBounds.x + newBounds.width - (parentBounds.x + parentBounds.width)) < MAX_MATCH_DISTANCE) {
        fillWidth = true;
        newBounds.width = parentBounds.x + parentBounds.width - newBounds.x;
      }
    }
    return null;
  }

  @Override
  public void commit(@AndroidCoordinate int px,
                     @AndroidCoordinate int py,
                     int modifiers,
                     @NotNull @AndroidCoordinate Rectangle newBounds) {
    NlComponent parent = component.getParent();
    if (parent == null) {
      return;
    }
    setNewSizeBounds(component, parent, new Rectangle(component.x, component.y, component.w, component.h),
                     newBounds, horizontalEdgeType, verticalEdgeType);
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.RESIZE_PREVIEW);
    if (bounds == null) {
      return;
    }
    Rectangle b = bounds;
    graphics.drawRect(b.x, b.y, b.width, b.height);

    if (horizontalFillSegment != null) {
      graphics.useStyle(NlDrawingStyle.GUIDELINE);
      Segment s = horizontalFillSegment;
      graphics.drawLine(s.from, s.at, s.to, s.at);
    }
    if (verticalFillSegment != null) {
      graphics.useStyle(NlDrawingStyle.GUIDELINE);
      Segment s = verticalFillSegment;
      graphics.drawLine(s.at, s.from, s.at, s.to);
    }

    if (wrapBounds != null) {
      graphics.useStyle(NlDrawingStyle.GUIDELINE);
      int wrapWidth1 = wrapBounds.width;
      int wrapHeight1 = wrapBounds.height;

      // Show the "wrap_content" guideline.
      // If we are showing both the wrap_width and wrap_height lines
      // then we show at most the rectangle formed by the two lines;
      // otherwise we show the entire width of the line
      if (horizontalEdgeType != null) {
        int y = -1;
        switch (horizontalEdgeType) {
          case TOP:
            y = b.y + b.height - wrapHeight1;
            break;
          case BOTTOM:
            y = b.y + wrapHeight1;
            break;
          default: assert false : horizontalEdgeType;
        }
        if (verticalEdgeType != null) {
          switch (verticalEdgeType) {
            case LEFT:
              graphics.drawLine(b.x + b.width - wrapWidth1, y, b.x + b.width, y);
              break;
            case RIGHT:
              graphics.drawLine(b.x, y, b.x + wrapWidth1, y);
              break;
            default: assert false : verticalEdgeType;
          }
        } else {
          graphics.drawLine(b.x, y, b.x + b.width, y);
        }
      }
      if (verticalEdgeType != null) {
        int x = -1;
        switch (verticalEdgeType) {
          case LEFT:
            x = b.x + b.width - wrapWidth1;
            break;
          case RIGHT:
            x = b.x + wrapWidth1;
            break;
          default: assert false : verticalEdgeType;
        }
        if (horizontalEdgeType != null) {
          switch (horizontalEdgeType) {
            case TOP:
              graphics.drawLine(x, b.y + b.height - wrapHeight1, x, b.y + b.height);
              break;
            case BOTTOM:
              graphics.drawLine(x, b.y, x, b.y + wrapHeight1);
              break;
            default: assert false : horizontalEdgeType;
          }
        } else {
          graphics.drawLine(x, b.y, x, b.y + b.height);
        }
      }
    }
  }

  /**
   * Returns the width attribute to be set to match the new bounds
   *
   * @return the width string, never null
   */
  @NotNull
  public String getWidthAttribute() {
    if (wrapWidth) {
      return VALUE_WRAP_CONTENT;
    } else if (fillWidth) {
      return VALUE_MATCH_PARENT;
    } else {
      return String.format(VALUE_N_DP, editor.pxToDp(bounds.width));
    }
  }

  /**
   * Returns the height attribute to be set to match the new bounds
   *
   * @return the height string, never null
   */
  @NotNull
  public String getHeightAttribute() {
    if (wrapHeight) {
      return VALUE_WRAP_CONTENT;
    } else if (fillHeight) {
      return VALUE_MATCH_PARENT;
    } else {
      return String.format(VALUE_N_DP, editor.pxToDp(bounds.height));
    }
  }

  /**
   * Returns the message to display to the user during the resize operation
   *
   * @param child          the child node being resized
   * @param parent         the parent of the resized node
   * @param newBounds      the new bounds to resize the child to, in pixels
   * @param horizontalEdge the horizontal edge being resized
   * @param verticalEdge   the vertical edge being resized
   * @return the message to display for the current resize bounds
   */
  @Nullable
  protected String getResizeUpdateMessage(@NotNull NlComponent child,
                                          @NotNull NlComponent parent,
                                          @NotNull Rectangle newBounds,
                                          @Nullable SegmentType horizontalEdge,
                                          @Nullable SegmentType verticalEdge) {
    String width = getWidthAttribute();
    String height = getHeightAttribute();

    if (horizontalEdge == null) {
      return width;
    } else if (verticalEdge == null) {
      return height;
    } else {
      // U+00D7: Unicode for multiplication sign
      return String.format("%s \u00D7 %s", width, height);
    }
  }

  /**
   * Performs the edit on the node to complete a resizing operation. The actual edit
   * part is pulled out such that subclasses can change/add to the edits and be part of
   * the same undo event
   *
   * @param component      the child node being resized
   * @param layout         the parent of the resized node
   * @param newBounds      the new bounds to resize the child to, in pixels
   * @param horizontalEdge the horizontal edge being resized
   * @param verticalEdge   the vertical edge being resized
   */
  protected void setNewSizeBounds(@NotNull NlComponent component,
                                  @NotNull NlComponent layout,
                                  @NotNull Rectangle oldBounds,
                                  @NotNull Rectangle newBounds,
                                  @Nullable SegmentType horizontalEdge,
                                  @Nullable SegmentType verticalEdge) {
    if (verticalEdge != null
        && (newBounds.width != oldBounds.width || wrapWidth || fillWidth)) {
      component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, getWidthAttribute());
    }
    if (horizontalEdge != null
        && (newBounds.height != oldBounds.height || wrapHeight || fillHeight)) {
      component.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, getHeightAttribute());
    }
  }
}
