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
public class PreviewRowLayout {
  private final @NotNull List<RenderPreview> myPreviews;
  private final @NotNull RenderContext myRenderContext;
  private final boolean myFixedOrder;

  public PreviewRowLayout(@NotNull List<RenderPreview> previews, @NotNull RenderContext renderContext, boolean fixedOrder) {
    myPreviews = previews;
    myRenderContext = renderContext;
    myFixedOrder = fixedOrder;
  }

  private int myLayoutHeight;

  public void performLayout() {
    // TODO: Separate layout heuristics for portrait and landscape orientations (though
    // it also depends on the dimensions of the canvas window, which determines the
    // shape of the leftover space)

    Rectangle clientArea = myRenderContext.getClientArea();
    Dimension scaledImageSize = myRenderContext.getScaledImageSize();
    int scaledImageWidth = scaledImageSize.width;
    int scaledImageHeight = scaledImageSize.height;

    int availableWidth = clientArea.width;
    int availableHeight = clientArea.height;
    int maxVisibleY = clientArea.y + clientArea.height;
    int bottomBorder = scaledImageHeight + SHADOW_SIZE;
    int rightHandSide = scaledImageWidth + HORIZONTAL_GAP + SHADOW_SIZE;
    int nextY = 0;

    // First lay out images across the top right hand side
    int x = rightHandSide;
    int y = 0;
    boolean wrapped = false;

    int vgap = VERTICAL_GAP;
    for (RenderPreview preview : myPreviews) {
      // If we have forked previews, double the vgap to allow space for two labels
      if (preview.isForked()) {
        vgap *= 2;
        break;
      }
    }

    List<RenderPreview> aspectOrder;
    if (!myFixedOrder) {
      aspectOrder = new ArrayList<RenderPreview>(myPreviews);
      Collections.sort(aspectOrder, RenderPreview.INCREASING_ASPECT_RATIO);
    }
    else {
      aspectOrder = myPreviews;
    }

    for (RenderPreview preview : aspectOrder) {
      if (x > 0 && x + preview.getLayoutWidth() > availableWidth) {
        x = rightHandSide;
        int prevY = y;
        y = nextY;
        if ((prevY <= bottomBorder || y <= bottomBorder) && Math.max(nextY, y + preview.getLayoutHeight()) > bottomBorder) {
          // If there's really no visible room below, don't bother
          // Similarly, don't wrap individually scaled views
          if (bottomBorder < availableHeight - 40 && preview.getScale() < 1.2) {
            // If it's closer to the top row than the bottom, just
            // mark the next row for left justify instead
            if (bottomBorder - y > y + preview.getLayoutHeight() - bottomBorder) {
              rightHandSide = 0;
              wrapped = true;
            }
            else if (!wrapped) {
              y = nextY = Math.max(nextY, bottomBorder + vgap);
              x = rightHandSide = 0;
              wrapped = true;
            }
          }
        }
      }
      if (x > 0 && y <= bottomBorder && Math.max(nextY, y + preview.getLayoutHeight()) > bottomBorder) {
        //noinspection StatementWithEmptyBody
        if (clientArea.height - bottomBorder < preview.getLayoutHeight()) {
          // No room below the device on the left; just continue on the
          // bottom row
        }
        else if (preview.getScale() < 1.2) {
          if (bottomBorder - y > y + preview.getLayoutHeight() - bottomBorder) {
            rightHandSide = 0;
            wrapped = true;
          }
          else {
            y = nextY = Math.max(nextY, bottomBorder + vgap);
            x = rightHandSide = 0;
            wrapped = true;
          }
        }
      }

      preview.setPosition(x, y);

      if (y > maxVisibleY && maxVisibleY > 0) {
        preview.setVisible(false);
      }
      else if (!preview.isVisible()) {
        preview.setVisible(true);
      }

      x += preview.getLayoutWidth();
      x += HORIZONTAL_GAP;
      nextY = Math.max(nextY, y + preview.getLayoutHeight() + vgap);
    }

    myLayoutHeight = nextY;
  }

  public int getLayoutHeight() {
    return myLayoutHeight;
  }
}
