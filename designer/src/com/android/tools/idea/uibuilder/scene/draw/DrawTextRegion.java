/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.draw;

import android.support.constraint.solver.widgets.ConstraintWidget;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ViewTransform;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

/**
 * Base Class for drawing text components
 */
public class DrawTextRegion extends DrawRegion {
  static boolean DO_WRAP = false;
  protected int myBaseLineOffset = 0;
  protected int mHorizontalPadding = 0;
  protected int mVerticalPadding = 0;
  protected int mVerticalMargin = 0;
  protected int mHorizontalMargin = 0;
  protected boolean mToUpperCase = false;
  public static final int TEXT_ALIGNMENT_VIEW_START = 5;
  public static final int TEXT_ALIGNMENT_VIEW_END = 6;
  public static final int TEXT_ALIGNMENT_CENTER = 4;
  protected int mAlignmentX = TEXT_ALIGNMENT_VIEW_START;
  protected int mAlignmentY = TEXT_ALIGNMENT_VIEW_START;
  protected String mText;
  protected Font mFont = new Font("Helvetica", Font.PLAIN, 22);
  protected boolean mSingleLine = false;
  JTextPane mTextPane = new JTextPane();

  {
    mTextPane.setBackground(new Color(0, 0, 0, 0));
  }

  /**
   * Set the behavior to do a text wrap content or not
   * In Android Studio, this should not be active
   *
   * @param doWrap
   */
  public static void setDoWrap(boolean doWrap) {
    DO_WRAP = doWrap;
  }

  public DrawTextRegion(String string) {
    String[] sp = string.split(",");
    int c = super.parse(sp, 0);
    myBaseLineOffset = Integer.parseInt(sp[c++]);
    mSingleLine = Boolean.parseBoolean(sp[c++]);
    mToUpperCase = Boolean.parseBoolean(sp[c++]);
    mAlignmentX = Integer.parseInt(sp[c++]);
    mAlignmentY = Integer.parseInt(sp[c++]);
    mText = string.substring(string.indexOf('\"') + 1, string.lastIndexOf('\"'));
  }

  @Override
  public String serialize() {
    return this.getClass().getSimpleName() +
           "," +
           x +
           "," +
           y +
           "," +
           width +
           "," +
           height +
           "," +
           myBaseLineOffset +
           "," +
           mSingleLine +
           "," +
           mToUpperCase +
           "," +
           mAlignmentX +
           "," +
           mAlignmentY +
           ",\"" +
           mText +
           "\"";
  }

  public DrawTextRegion() {

  }

  public DrawTextRegion(int x, int y, int width, int height, int baseLineOffset, String text) {
    super(x, y, width, height);
    myBaseLineOffset = baseLineOffset;
    mText = text;
  }

  public DrawTextRegion(int x, int y, int width, int height, int baseLineOffset,
                        String text,
                        boolean singleLine,
                        boolean toUpperCase,
                        int textAlignmentX,
                        int textAlignmentY,
                        int fontSize) {
    super(x, y, width, height);
    mText = text;
    myBaseLineOffset = baseLineOffset;
    mSingleLine = singleLine;
    mToUpperCase = toUpperCase;
    mAlignmentX = textAlignmentX;
    mAlignmentY = textAlignmentY;
    mFont = new Font("Helvetica", Font.PLAIN, (int)(fontSize / 2.3)); // Convert to swing size font
  }

  public static int androidToSwingFontSize(float fontSize) {
    return Math.round((fontSize * 2f + 4.5f) / 2.41f);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    int tx = x;
    int ty = y;
    int h = height;
    int w = width;
    if (!sceneContext.getColorSet().drawBackground()) {
      return;
    }
    super.paint(g, sceneContext);
    ColorSet colorSet = sceneContext.getColorSet();
    int horizontalPadding = mHorizontalPadding + mHorizontalMargin;
    int verticalPadding = mVerticalPadding + mVerticalMargin;
    g.setFont(mFont);
    FontMetrics fontMetrics = g.getFontMetrics();
    Color color = colorSet.getFrames();
    g.setColor(color);
    String string = mText;
    if (mToUpperCase) {
      string = string.toUpperCase();
    }
    int ftx = 0;
    int fty = 0;
    int stringWidth = fontMetrics.stringWidth(string);
    if (stringWidth > w && !mSingleLine) { // if it is multi lined text use a swing text pane to do the wrap
      mTextPane.setText(string);
      mTextPane.setForeground(color);
      mTextPane.setSize(w, h);
      mTextPane.setFont(mFont.deriveFont((float)mFont.getSize() * 0.88f));
      StyledDocument doc = mTextPane.getStyledDocument();
      SimpleAttributeSet attributeSet = new SimpleAttributeSet();
      switch (mAlignmentX) {
        case TEXT_ALIGNMENT_VIEW_START:
          StyleConstants.setAlignment(attributeSet, StyleConstants.ALIGN_LEFT);
          break;
        case TEXT_ALIGNMENT_CENTER:
          StyleConstants.setAlignment(attributeSet, StyleConstants.ALIGN_CENTER);
          break;
        case TEXT_ALIGNMENT_VIEW_END:
          StyleConstants.setAlignment(attributeSet, StyleConstants.ALIGN_RIGHT);
          break;
      }
      switch (mAlignmentY) {
        case TEXT_ALIGNMENT_VIEW_START:
          mTextPane.setAlignmentY(JTextArea.TOP_ALIGNMENT);
          break;
        case TEXT_ALIGNMENT_CENTER:
          mTextPane.setAlignmentY(JTextArea.CENTER_ALIGNMENT);
          break;
        case TEXT_ALIGNMENT_VIEW_END:
          mTextPane.setAlignmentY(JTextArea.BOTTOM_ALIGNMENT);
          break;
      }
      doc.setParagraphAttributes(0, doc.getLength(), attributeSet, false);
      g.translate(tx, ty);
      Shape clip = g.getClip();
      g.clipRect(0, 0, w, h);
      mTextPane.paint(g);
      g.setClip(clip);
      g.translate(-tx, -ty);
    }
    else {
      switch (mAlignmentX) {
        case TEXT_ALIGNMENT_VIEW_START: {
          ftx = tx + horizontalPadding;
        }
        break;
        case TEXT_ALIGNMENT_CENTER: {
          int paddx = (w - stringWidth) / 2;
          ftx = tx + paddx;
        }
        break;
        case TEXT_ALIGNMENT_VIEW_END: {
          int padd = w - stringWidth + horizontalPadding;
          ftx = tx + padd;
        }
        break;
      }
      fty = myBaseLineOffset + ty;

      Shape clip = g.getClip();
      g.clipRect(tx, ty, w, h);
      g.drawString(string, ftx, fty);
      g.setClip(clip);
    }
  }
}
