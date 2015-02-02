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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberLabelData;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ScrubberLabel extends JBLabel {
  private static final long CYCLE_LENGTH = 800l;
  private static final int CORNER_RADIUS = 3;
  private static final Icon[] LOADING_ICONS =
    {AllIcons.Process.Big.Step_1, AllIcons.Process.Big.Step_2, AllIcons.Process.Big.Step_3, AllIcons.Process.Big.Step_4,
      AllIcons.Process.Big.Step_5, AllIcons.Process.Big.Step_6, AllIcons.Process.Big.Step_7, AllIcons.Process.Big.Step_8,
      AllIcons.Process.Big.Step_9, AllIcons.Process.Big.Step_10, AllIcons.Process.Big.Step_11, AllIcons.Process.Big.Step_12};
  @Nullable private ScrubberLabelData myData;

  public ScrubberLabel() {
  }

  @Nullable
  public ScrubberLabelData getUserData() {
    return myData;
  }

  public void setUserData(@NotNull ScrubberLabelData data) {
    myData = data;
  }

  /**
   * This method is a custom paint method to handle cases where the underlying image is still being generated/loaded on the server.
   * <p/>
   * All images are generated remotely on the server. Since the image may not necessarily be resident in memory when they need to be
   * rendered, there needs to exist a way for the UI to let the user know that the image is still being loaded. This method mimics the IJ
   * JBLoadingPanel class's painting behavior (even using the same icons). However, instead of creating multiple heavyweight components,
   * this method just simply overrides the default label paint method and paints the loading icons directly.
   */
  @Override
  public void paintComponent(Graphics g) {
    if (ui != null) {
      Graphics pushedContext = (g == null) ? null : g.create();
      try {
        assert (myData != null);

        setBackground(UIUtil.getListBackground(myData.isSelected()));

        if (pushedContext != null) {
          Icon previewImage = myData.getIcon();
          int previewImageWidth = previewImage.getIconWidth();
          int previewImageHeight = previewImage.getIconHeight();
          int backgroundOffsetX = (getWidth() - previewImageWidth) / 2;
          int backgroundOffsetY = (getHeight() - previewImageHeight) / 2;

          if (myData.isLoading()) {
            setOpaque(true);
            pushedContext.setColor(myData.isSelected() ? getBackground() : UIUtil.getLabelDisabledForeground());
            pushedContext.fillRoundRect(backgroundOffsetX, backgroundOffsetY, previewImageWidth - 1, previewImageHeight - 1, CORNER_RADIUS,
                                        CORNER_RADIUS);
            Icon targetIcon =
              LOADING_ICONS[(int)((((System.currentTimeMillis() - myData.getLoadIconStartTime()) % CYCLE_LENGTH) * 12l) / CYCLE_LENGTH)];
            targetIcon.paintIcon(this, g, backgroundOffsetX + (previewImageWidth - targetIcon.getIconWidth()) / 2,
                                 backgroundOffsetY + (previewImageHeight - targetIcon.getIconHeight()) / 2);
          }
          else {
            setIcon(previewImage);
            setOpaque(false);
            pushedContext.setColor(getBackground());
            pushedContext.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS);
          }
        }
        if (!myData.isLoading()) {
          ui.update(pushedContext, this);
        }
        if (pushedContext != null) {
          paintFrameOverlay(g);
        }
      }
      finally {
        if (pushedContext != null) {
          pushedContext.dispose();
        }
      }
    }
  }

  protected void paintFrameOverlay(Graphics g) {
    final int OFFSET = 7;
    final int PADDING = 1;

    assert (myData != null);

    FontMetrics metrics = g.getFontMetrics();
    int fontHeight = metrics.getHeight();

    String frameString = myData.getLabel();
    int frameStringWidth = metrics.stringWidth(frameString);

    //noinspection UseJBColor
    g.setColor(new Color(255, 255, 255, 192));
    g.fillRoundRect(OFFSET, OFFSET, frameStringWidth + 2 * PADDING + 1, fontHeight + 2 * PADDING + 1, CORNER_RADIUS, CORNER_RADIUS);

    g.setColor(getForeground());
    g.drawString(frameString, OFFSET + PADDING + 1, OFFSET - PADDING + fontHeight);
  }
}
