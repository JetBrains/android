/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.configurations.OverlayContainer;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The {@link IncludeOverlay} class renders masks to <b>partially</b> hide everything outside
 * an included file's own content. This overlay is in use when you are editing an included
 * file shown within a different file's context (e.g. "Show In > other").
 * <p>
 * TODO: Instead of masking out the entire viewport, consider only masking out the root
 * layout, and leaving the system bars alone? One problem with that approach is that
 * we may be rendering the action bar from the outer layout, which is really system UI
 * that should be grayed out. Perhaps we can consider combining the root bounds with the
 * bottom bounds of the root layout? We also need to handle the case where the surrounding
 * layout does not fill up the full height of the available screen (e.g. its layout_height
 * is wrap_content).
 */
public class IncludeOverlay extends Overlay {
  /** Mask transparency - 0 is transparent, 255 is opaque */
  private static final int MASK_TRANSPARENCY = 160;

  private final OverlayContainer myContainer;

  /**
   * Constructs an {@link IncludeOverlay} tied to the given result.
   *
   * @param container The {@link OverlayContainer} containing this overlay
   */
  public IncludeOverlay(@NotNull OverlayContainer container) {
    myContainer = container;
  }

  @Override
  public void paint(@Nullable Component component, @NotNull Graphics2D gc, int deltaX, int deltaY) {
    RenderedViewHierarchy viewHierarchy = myContainer.getViewHierarchy();
    if (viewHierarchy == null) {
      return;
    }
    List<RenderedView> includedRoots = viewHierarchy.getIncludedRoots();
    if (includedRoots == null || includedRoots.size() == 0 || component == null) {
      // We don't support multiple included children yet. When that works,
      // this code should use a BSP tree to figure out which regions to paint
      // to leave holes in the mask.
      return;
    }

    //noinspection UseJBColor
    gc.setColor(Color.GRAY);
    Composite prevComposite = gc.getComposite();
    gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MASK_TRANSPARENCY / 255.0f));

    Shape prevClip = gc.getClip();
    Shape clip = setScreenClip(myContainer, component, gc, deltaX, deltaY);
    Collection<Rectangle> masks = computeMasks(component, includedRoots);
    for (Rectangle r : masks) {
      gc.fillRect(r.x + deltaX, r.y + deltaY, r.width, r.height);
    }
    gc.setComposite(prevComposite);
    if (clip != null) {
      gc.setClip(prevClip);
    }
  }

  /** Computes the set of rectangles we should paint to cover everything up <b>except</b> for
   * the included root bounds. The coordinates will be in the coordinate system of the given
   * component.
   */
  protected Collection<Rectangle> computeMasks(Component component, List<RenderedView> includedRoots) {
    Dimension fullImageSize = myContainer.getFullImageSize();
    Rectangle whole = new Rectangle(0, 0, fullImageSize.width, fullImageSize.height);
    whole = myContainer.fromModel(component, whole);
    List<Rectangle> includedBounds = Lists.newArrayListWithExpectedSize(includedRoots.size());
    for (RenderedView view : includedRoots) {
      includedBounds.add(myContainer.fromModel(component, view.getBounds()));
    }
    return subtractRectangles(whole, includedBounds);
  }

  /**
   * Given a Rectangle, remove holes from it (specified as a collection of Rectangles) such
   * that the result is a list of rectangles that cover everything that is not a hole.
   *
   * @param rectangle the rectangle to subtract from
   * @param holes the holes to subtract from the rectangle
   * @return a list of sub rectangles that remain after subtracting out the given list of holes
   */
  @VisibleForTesting
  static Collection<Rectangle> subtractRectangles(Rectangle rectangle, Collection<Rectangle> holes) {
    List<Rectangle> result = new ArrayList<Rectangle>();
    result.add(rectangle);

    for (Rectangle hole : holes) {
      List<Rectangle> tempResult = new ArrayList<Rectangle>();
      for (Rectangle r : result) {
        if (hole.intersects(r)) {
          // Clip the hole to fit the rectangle bounds
          Rectangle h = hole.intersection(r);

          // Split the rectangle

          // Above (includes the NW and NE corners)
          if (h.y > r.y) {
            tempResult.add(new Rectangle(r.x, r.y, r.width, h.y - r.y));
          }

          // Left (not including corners)
          if (h.x > r.x) {
            tempResult.add(new Rectangle(r.x, h.y, h.x - r.x, h.height));
          }

          int hx2 = h.x + h.width;
          int hy2 = h.y + h.height;
          int rx2 = r.x + r.width;
          int ry2 = r.y + r.height;

          // Below (includes the SW and SE corners)
          if (hy2 < ry2) {
            tempResult.add(new Rectangle(r.x, hy2, r.width, ry2 - hy2));
          }

          // Right (not including corners)
          if (hx2 < rx2) {
            tempResult.add(new Rectangle(hx2, h.y, rx2 - hx2, h.height));
          }
        } else {
          tempResult.add(r);
        }
      }

      result = tempResult;
    }

    return result;
  }
}
