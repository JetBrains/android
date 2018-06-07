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
package com.android.tools.idea.uibuilder.editor;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;

class AnimationToolbarSliderUI extends BasicSliderUI {
  private final JBColor myTickColor = new JBColor(Color.BLACK, Color.WHITE);
  private final JBColor myTrackPlayedColor = new JBColor(Color.decode("#1A237E"), Color.decode("#B2EBF2"));
  private final JBColor myTrackBackgroundColor = JBColor.GRAY;
  private final Rectangle myTrackRectangle = new Rectangle();

  public AnimationToolbarSliderUI(JSlider b) {
    super(b);
  }

  @Override
  protected void calculateTrackRect() {
    super.calculateTrackRect();

    if (slider.getOrientation() == SwingConstants.VERTICAL) {
      return;
    }

    int trackHeight = thumbRect.height / 4;
    myTrackRectangle.x = trackRect.x;
    myTrackRectangle.y = (trackRect.y + trackRect.height / 2) - trackHeight / 2;
    myTrackRectangle.width = trackRect.width;
    myTrackRectangle.height = trackHeight;
  }

  @Override
  public void paintThumb(Graphics g) {
    if (slider.getOrientation() == SwingConstants.VERTICAL) {
      super.paintThumb(g);
      return;
    }

    g.setColor(ColorUtil.toAlpha(myTrackPlayedColor, 255));
    int side = Math.min(thumbRect.width, thumbRect.height);
    int midX = thumbRect.x + thumbRect.width / 2;
    int midY = thumbRect.y + thumbRect.height / 2;
    g.fillRect(midX - side / 2, midY - side / 2, side / 2, side);
  }

  private void paintHorizontalTrack(Graphics g) {
    int thumbMidX = thumbRect.x + thumbRect.width / 2;
    int playedX =  thumbMidX - trackRect.x;

    g.setColor(ColorUtil.toAlpha(myTrackBackgroundColor, 100));
    g.fillRect(myTrackRectangle.x, myTrackRectangle.y, myTrackRectangle.width, myTrackRectangle.height);

    g.setColor(myTrackPlayedColor);
    g.fillRect(myTrackRectangle.x, myTrackRectangle.y, playedX, myTrackRectangle.height);

  }

  @Override
  public void paintTrack(Graphics g) {
    if (slider.getOrientation() == SwingConstants.VERTICAL) {
      super.paintTrack(g);
      return;
    }

    paintHorizontalTrack(g);
  }

  @Override
  public void paintTicks(Graphics g) {
    if (slider.getOrientation() == SwingConstants.VERTICAL) {
      super.paintTicks(g);
      return;
    }

    if (slider.getMajorTickSpacing() > 0) {
      g.setColor(myTickColor);
      int value = slider.getMinimum();
      while ( value <= slider.getMaximum() ) {
        int xPos = xPositionForValue(value);
        g.fillRect(xPos - 1, myTrackRectangle.y, 3, myTrackRectangle.height);

        // Overflow checking
        if (Integer.MAX_VALUE - slider.getMajorTickSpacing() < value) {
          break;
        }

        value += slider.getMajorTickSpacing();
      }
    }
  }
}
