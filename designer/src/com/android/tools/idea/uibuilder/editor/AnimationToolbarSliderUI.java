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

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicSliderUI;

class AnimationToolbarSliderUI extends BasicSliderUI {
  private static final int DEFAULT_FONT_SIZE = 10;
  private static final int THUMB_TIP_SIZE = 6;

  private final int[] myThumbPolygonX = new int[5];
  private final int[] myThumbPolygonY = new int[5];
  private int myThumbTipSize = JBUI.scale(THUMB_TIP_SIZE);
  private final JBColor myThumbFillColor = new JBColor(0xffffff, 0xafb1b3);
  private final JBColor myThumbLineColor = JBColor.lightGray;
  private final JBColor myTrackBackgroundColor = JBColor.GRAY;
  private final Rectangle myTrackRectangle = new Rectangle();
  private final StringBuilder myProgress = new StringBuilder(10);
  private final JBLabel myProgressLabelComponent = new JBLabel("0%");
  private final Rectangle myProgressLabelBounds = new Rectangle();

  AnimationToolbarSliderUI(JSlider b) {
    super(b);
    myProgressLabelComponent.setMinimumSize(JBUI.size(30, 15));
    myProgressLabelComponent.setHorizontalAlignment(SwingConstants.RIGHT);
    myProgressLabelComponent.setFont(JBUI.Fonts.label(DEFAULT_FONT_SIZE));
    myProgressLabelComponent.setFontColor(UIUtil.FontColor.NORMAL);
  }

  @Override
  protected void calculateGeometry() {
    super.calculateGeometry();
    calculateProgressRect();
  }

  @Override
  protected void calculateTrackRect() {
    super.calculateTrackRect();

    if (slider.getOrientation() == SwingConstants.VERTICAL) {
      return;
    }
    Dimension progressLabelSize = getProgressLabelSize();
    int trackHeight = thumbRect.height / 3;
    trackRect.width -= (progressLabelSize.width + JBUI.scale(10));
    myTrackRectangle.x = trackRect.x;
    myTrackRectangle.y = (trackRect.y + trackRect.height / 2) - trackHeight / 2;
    myTrackRectangle.width = trackRect.width;
    myTrackRectangle.height = trackHeight;
  }

  @Override
  protected void calculateThumbLocation() {
    super.calculateThumbLocation();
    updateTrackProgress();
  }

  /** Set the label for the current value of the slider. */
  protected void updateTrackProgress() {
    int current = slider.getValue();
    myProgress.setLength(0);
    myProgress.append(current);
    myProgress.append("%");
    myProgressLabelComponent.setText(myProgress.toString());
    myProgressLabelComponent.setFont(JBUI.Fonts.label(DEFAULT_FONT_SIZE));
  }

  /** Set the rectangle bounds for the label component. */
  protected void calculateProgressRect() {
    Dimension progressLabelSize = getProgressLabelSize();
    myProgressLabelBounds.x = myTrackRectangle.x + myTrackRectangle.width + JBUI.scale(10);
    myProgressLabelBounds.y = (int)((myTrackRectangle.y + (myTrackRectangle.height / 2f)) - (progressLabelSize.height / 2f));
    myProgressLabelBounds.width = progressLabelSize.width;
    myProgressLabelBounds.height = progressLabelSize.height;
    myProgressLabelComponent.setBounds(myProgressLabelBounds);
  }

  @Override
  public void paintThumb(Graphics g) {
    if (slider.getOrientation() == SwingConstants.VERTICAL) {
      super.paintThumb(g);
      return;
    }

    myThumbPolygonX[0] = myThumbPolygonX[1] = thumbRect.x;
    myThumbPolygonX[4] = myThumbPolygonX[3] = thumbRect.x + thumbRect.width;
    myThumbPolygonX[2] = (int) (thumbRect.x + (thumbRect.width / 2f));

    myThumbPolygonY[0] = myThumbPolygonY[4] = thumbRect.y;
    myThumbPolygonY[1] = myThumbPolygonY[3] = thumbRect.y + thumbRect.height - myThumbTipSize;
    myThumbPolygonY[2] = thumbRect.y + thumbRect.height;

    g.setColor(myThumbFillColor);
    g.fillPolygon(myThumbPolygonX, myThumbPolygonY, 5);

    g.setColor(myThumbLineColor);
    g.drawPolygon(myThumbPolygonX, myThumbPolygonY, 5);
  }

  @Override
  public void paintFocus(Graphics g) {}

  private void paintHorizontalTrack(Graphics g) {
    int thumbMidX = thumbRect.x + thumbRect.width / 2;
    int playedX =  thumbMidX - trackRect.x;

    int arc = myTrackRectangle.height / 2;
    g.setColor(ColorUtil.toAlpha(myTrackBackgroundColor, 100));
    g.fillRoundRect(myTrackRectangle.x, myTrackRectangle.y, myTrackRectangle.width, myTrackRectangle.height, arc, arc);
    g.drawRoundRect(myTrackRectangle.x, myTrackRectangle.y, myTrackRectangle.width, myTrackRectangle.height, arc, arc);

    g.setColor(myTrackBackgroundColor);
    g.fillRoundRect(myTrackRectangle.x, myTrackRectangle.y, playedX, myTrackRectangle.height, arc, arc);
    g.drawRoundRect(myTrackRectangle.x, myTrackRectangle.y, playedX, myTrackRectangle.height, arc, arc);

    int offsetTranslateX = myProgressLabelBounds.x;
    int offsetTranslateY = myProgressLabelBounds.y;
    g.translate(offsetTranslateX, offsetTranslateY);
    myProgressLabelComponent.paint(g);
    g.translate(-offsetTranslateX, -offsetTranslateY);
  }

  @Override
  protected void calculateThumbSize() {
    Dimension size = new JBDimension(12, 9);
    myThumbTipSize = JBUI.scale(THUMB_TIP_SIZE);
    thumbRect.width = size.width;
    thumbRect.height = size.height;
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
    }
    // Don't paint ticks.
  }

  private Dimension getProgressLabelSize() {
    Dimension progressLabelSize = myProgressLabelComponent.getPreferredSize();
    Dimension minSize = myProgressLabelComponent.getMinimumSize();
    progressLabelSize.width = Math.max(minSize.width, progressLabelSize.width);
    progressLabelSize.height = Math.max(minSize.height, progressLabelSize.height);
    return progressLabelSize;
  }
}
