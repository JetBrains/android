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
package com.android.tools.idea.rendering.multi;

import com.android.tools.idea.configurations.RenderContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.rendering.ShadowPainter.SHADOW_SIZE;
import static com.android.tools.idea.rendering.multi.RenderPreviewManager.HORIZONTAL_GAP;
import static com.android.tools.idea.rendering.multi.RenderPreviewManager.VERTICAL_GAP;

/**
 * Regular row layout for render previews
 */
public class PreviewBinPackingLayout {
  private final @NotNull List<RenderPreview> myPreviews;
  private final @NotNull RenderContext myRenderContext;
  private final int myX;
  private final int myY;
  @SuppressWarnings("UnusedDeclaration")
  private BinPacker myPacker; // Debug only

  public PreviewBinPackingLayout(@NotNull List<RenderPreview> previews, @NotNull RenderContext renderContext, int x, int y) {
    myPreviews = previews;
    myRenderContext = renderContext;
    myX = x;
    myY = y;
  }

  private int myLayoutHeight;

  public boolean performLayout() {
    Rectangle clientArea = myRenderContext.getClientArea();
    Dimension scaledImageSize = myRenderContext.getScaledImageSize();
    int scaledImageWidth = scaledImageSize.width;
    int scaledImageHeight = scaledImageSize.height;

    int availableWidth = clientArea.x + clientArea.width - myX;
    int availableHeight = clientArea.y + clientArea.height - myY;
    int maxVisibleY = clientArea.y + clientArea.height;
    int bottomBorder = scaledImageHeight + SHADOW_SIZE;
    int rightHandSide = scaledImageWidth + HORIZONTAL_GAP + SHADOW_SIZE;

    int minWidth = Integer.MAX_VALUE;
    int minHeight = Integer.MAX_VALUE;
    for (RenderPreview preview : myPreviews) {
      minWidth = Math.min(minWidth, preview.getLayoutWidth());
      minHeight = Math.min(minHeight, preview.getLayoutHeight());
    }

    BinPacker packer = new BinPacker(minWidth, minHeight);
    if (BinPacker.DEBUG) {
      myPacker = null;
    }

    // TODO: Instead of this, just start with client area and occupy scaled image size!

    // Add in gap on right and bottom since we'll add that requirement on the width and
    // height rectangles too (for spacing)
    packer.addSpace(new Rectangle(rightHandSide, 0, availableWidth - rightHandSide + HORIZONTAL_GAP, availableHeight + VERTICAL_GAP));
    if (maxVisibleY > bottomBorder) {
      packer.addSpace(new Rectangle(0, bottomBorder + VERTICAL_GAP, availableWidth + HORIZONTAL_GAP,
                                    maxVisibleY - bottomBorder + VERTICAL_GAP));
    }

    // TODO: Sort previews first before attempting to position them?

    ArrayList<RenderPreview> aspectOrder = new ArrayList<RenderPreview>(myPreviews);
    Collections.sort(aspectOrder, RenderPreview.INCREASING_ASPECT_RATIO);

    if (BinPacker.DEBUG) {
      packer.dumpDiagnostics();
    }

    for (RenderPreview preview : aspectOrder) {
      int previewWidth = preview.getLayoutWidth();
      int previewHeight = preview.getLayoutHeight();
      previewHeight += VERTICAL_GAP;
      if (preview.isForked()) {
        previewHeight += VERTICAL_GAP;
      }
      previewWidth += HORIZONTAL_GAP;
      // title height? how do I account for that?
      Rectangle position = packer.occupy(previewWidth, previewHeight);
      if (position != null) {
        preview.setPosition(position.x, position.y);
        preview.setVisible(true);

        if (BinPacker.DEBUG) {
          myPacker = packer;
        }
      }
      else {
        // Can't fit: give up
        return false;
      }
    }

    myLayoutHeight = availableHeight;
    return true;
  }

  public int getLayoutHeight() {
    return myLayoutHeight;
  }
}
