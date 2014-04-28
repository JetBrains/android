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
package com.intellij.android.designer.model.layout.relative;

import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * The {@link com.intellij.designer.designSurface.feedbacks.AbstractTextFeedback} class does not support multiline text, so we
 * have to manage multiple {@link com.intellij.designer.designSurface.feedbacks.AbstractTextFeedback} objects, one for each line.
 */
public class MultiLineTooltipManager {
  /** Horizontal delta from the mouse cursor to shift the tooltip by */
  private static final int OFFSET_X = 20;

  /** Vertical delta from the mouse cursor to shift the tooltip by */
  private static final int OFFSET_Y = 20;

  private final TextFeedback[] myTextFeedbacks;
  @NotNull private final Dimension[] mySizes;
  @NotNull private final boolean[] myVisible;

  @NotNull private final FeedbackLayer myLayer;

  public MultiLineTooltipManager(@NotNull FeedbackLayer layer, int maxLines) {
    myLayer = layer;

    myTextFeedbacks = new TextFeedback[maxLines];
    mySizes = new Dimension[maxLines];
    myVisible = new boolean[maxLines];
  }

  @NotNull
  public TextFeedback getFeedback(int line) {
    assert line < myTextFeedbacks.length;

    TextFeedback feedback = myTextFeedbacks[line];
    if (feedback == null) {
      feedback = new TextFeedback();
      myTextFeedbacks[line] = feedback;
      myLayer.add(feedback);
    }

    return feedback;
  }

  public void setVisible(int line, boolean visible) {
    myVisible[line] = visible;
  }

  public void dispose() {
    for (TextFeedback feedback : myTextFeedbacks) {
      if (feedback != null) {
        myLayer.remove(feedback);
      }
    }
  }

  private Dimension getPreferredSize() {
    int width = 0;
    int height = 0;

    for (int i = 0; i < myTextFeedbacks.length; i++) {
      TextFeedback feedback = myTextFeedbacks[i];
      if (feedback != null && myVisible[i]) {
        Dimension preferredSize = feedback.getPreferredSize();
        width = Math.max(width, preferredSize.width);
        height += preferredSize.height;
        mySizes[i] = preferredSize;
      }
    }

    // Some padding
    width += 2;
    height += 2;

    return new Dimension(width, height);
  }

  /**
   * Update the tooltip such that it is placed within the given container layout.
   *
   * @param container the layout
   * @param mouseLocation the mouse location
   */
  public void update(RadViewComponent container, Point mouseLocation) {
    // Produce the bounds of the container
    Rectangle bounds = container.getBounds(myLayer);
    Dimension preferredSize = getPreferredSize();
    // TODO: Push it out of the way based on the mouse location, with a timer

    int x = bounds.x + bounds.width / 2 - preferredSize.width / 2;
    int y = bounds.y - 1 - preferredSize.height;
    if (y < 0) {
      y = bounds.y + bounds.height - preferredSize.height - 1;
    }

    for (int i = 0; i < myTextFeedbacks.length; i++) {
      TextFeedback feedback = myTextFeedbacks[i];
      if (feedback != null) {
        if (myVisible[i]) {
          int height = mySizes[i].height;
          feedback.setBounds(x, y, preferredSize.width, height);
          y += height;
          feedback.setVisible(true);
        } else {
          feedback.setVisible(false);
        }
      }
    }
  }

  /**
   * Update the tooltip and position it relative to the given x,y mouse position. The
   * {@code below} and {@code toRightOf} flags can be used to push the tooltip out
   * of the way; if you for example happen to be resizing the bottom right corner
   * of a view, then you'd probably want the tooltip above and to the left, such that
   * it does not obscure potential constraints to the right and below matched by the corner.
   *
   * @param below     if true, place the label below the y position
   * @param toRightOf if true, place the label to the right of the x position
   * @param x         the x position
   * @param y         the y position
   */
  public void update(boolean below, boolean toRightOf, int x, int y) {
    Dimension preferredSize = getPreferredSize();
    if (below) {
      y += OFFSET_Y;
    }
    else {
      y -= OFFSET_Y;
      y -= preferredSize.height;
    }

    if (toRightOf) {
      x += OFFSET_X;
    }
    else {
      x -= OFFSET_X;
      x -= preferredSize.width;
    }

    for (int i = 0; i < myTextFeedbacks.length; i++) {
      TextFeedback feedback = myTextFeedbacks[i];
      if (feedback != null) {
        if (myVisible[i]) {
          int height = mySizes[i].height;
          feedback.setBounds(x, y, preferredSize.width, height);
          y += height;
          feedback.setVisible(true);
        }
        else {
          feedback.setVisible(false);
        }
      }
    }
  }
}
