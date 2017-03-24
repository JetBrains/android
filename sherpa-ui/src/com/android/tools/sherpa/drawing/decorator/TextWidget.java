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

package com.android.tools.sherpa.drawing.decorator;

import android.support.constraint.solver.widgets.ConstraintWidget;
import com.android.tools.sherpa.drawing.ViewTransform;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

/**
 * Decorator for text widgets
 */
public class TextWidget extends WidgetDecorator {
  static boolean DO_WRAP = false;
  protected int mHorizontalPadding = 0;
  protected int mVerticalPadding = 0;
  protected int mVerticalMargin = 0;
  protected int mHorizontalMargin = 0;
  protected boolean mToUpperCase = false;
  public static final int TEXT_ALIGNMENT_TEXT_START = 2;
  public static final int TEXT_ALIGNMENT_TEXT_END = 3;
  public static final int TEXT_ALIGNMENT_VIEW_START = 5;
  public static final int TEXT_ALIGNMENT_VIEW_END = 6;
  public static final int TEXT_ALIGNMENT_CENTER = 4;
  protected int mAlignmentX = TEXT_ALIGNMENT_VIEW_START;
  protected int mAlignmentY = TEXT_ALIGNMENT_VIEW_START;
  private String mText;
  protected Font mFont = new Font("Helvetica", Font.PLAIN, 12);
  private float mFontSize = 14;
  private boolean mDisplayText = true;
  private boolean mSingleLine = false;
  JTextPane mTextPane = new JTextPane();

  /**
   * Base constructor
   *
   * @param widget the widget we are decorating
   * @param text   the text content
   */
  public TextWidget(ConstraintWidget widget, String text) {
    super(widget);
    setText(text);
    mTextPane.setBackground(new Color(0, 0, 0, 0));
  }

  /**
   * sets the text alignment.
   *
   * @param textAlignment on of "TEXT_ALIGNMENT_VIEW_START", "TEXT_ALIGNMENT_VIEW_END", "TEXT_ALIGNMENT_CENTER"
   */
  public void setTextAlignment(int textAlignment) {
    mAlignmentX = textAlignment;
  }


  public void setSingleLine(boolean singleLine) {
    mSingleLine = singleLine;
  }

  /**
   * Accessor for the font Size
   *
   * @return text content
   */
  public float getTextSize() {
    return mFontSize;
  }

  /**
   * Setter for the font Size
   *
   * @param fontSize
   */
  public void setTextSize(float fontSize) {
    mFontSize = fontSize;
    // regression derived approximation of Android to Java font size
    int size = androidToSwingFontSize(mFontSize);
    mFont = new Font("Helvetica", Font.PLAIN, size);
    wrapContent();
  }

  public static int androidToSwingFontSize(float fontSize) {
    return Math.round((fontSize * 2f + 4.5f) / 2.41f);
  }

  /**
   * Accessor for the text content
   *
   * @return text content
   */
  public String getText() {
    return mText;
  }

  /**
   * Setter for the text content
   *
   * @param text
   */
  public void setText(String text) {
    mText = text;
    wrapContent();
  }

  /**
   * Utility method computing the size of the widget if dimensions are set
   * to wrap_content, using the default font
   */
  protected void wrapContent() {
    if (!DO_WRAP) {
      return;
    }
    if (mText == null) {
      return;
    }
    Canvas c = new Canvas();
    c.setFont(mFont);
    FontMetrics fm = c.getFontMetrics(mFont);

    String string = getText();
    if (mToUpperCase) {
      string = string.toUpperCase();
    }
    int tw = fm.stringWidth(string) + 2 * (mHorizontalPadding + mHorizontalMargin);
    int th = fm.getMaxAscent() + 2 * fm.getMaxDescent() + 2 * (mVerticalPadding + mVerticalMargin);
    mWidget.setWrapWidth(tw);
    mWidget.setWrapHeight(th);
    if (tw > mWidget.getMinWidth()) {
      mWidget.setMinWidth(tw);
    }
    if (th > mWidget.getMinHeight()) {
      mWidget.setMinHeight(th);
    }
    if (mWidget.getHorizontalDimensionBehaviour()
        == ConstraintWidget.DimensionBehaviour.WRAP_CONTENT) {
      mWidget.setWidth(tw);
    }
    if (mWidget.getVerticalDimensionBehaviour()
        == ConstraintWidget.DimensionBehaviour.WRAP_CONTENT) {
      mWidget.setHeight(th);
    }
    if (mWidget.getHorizontalDimensionBehaviour() ==
        ConstraintWidget.DimensionBehaviour.FIXED) {
      if (mWidget.getWidth() <= mWidget.getMinWidth()) {
        mWidget.setHorizontalDimensionBehaviour(
          ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      }
    }
    if (mWidget.getVerticalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.FIXED) {
      if (mWidget.getHeight() <= mWidget.getMinHeight()) {
        mWidget.setVerticalDimensionBehaviour(
          ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      }
    }
    int baseline = fm.getAscent() + fm.getMaxDescent() + mVerticalPadding + mVerticalMargin;
    mWidget.setBaselineDistance(baseline);
  }


  @Override
  public void onPaintBackground(ViewTransform transform, Graphics2D g) {
    super.onPaintBackground(transform, g);
    if (mColorSet.drawBackground() && mDisplayText) {
      drawText(transform, g, mWidget.getDrawX(), mWidget.getDrawY());
    }
  }


  protected void drawText(ViewTransform transform, Graphics2D g, int x, int y) {
    int tx = transform.getSwingX(x);
    int ty = transform.getSwingY(y);
    int h = transform.getSwingDimension(mWidget.getDrawHeight());
    int w = transform.getSwingDimension(mWidget.getDrawWidth());

    int horizontalPadding = transform.getSwingDimension(mHorizontalPadding + mHorizontalMargin);
    int verticalPadding = transform.getSwingDimension(mVerticalPadding + mVerticalMargin);
    int originalSize = mFont.getSize();
    int scaleSize = transform.getSwingDimension(originalSize);
    g.setFont(mFont.deriveFont((float)scaleSize));
    FontMetrics fontMetrics = g.getFontMetrics();
    Color color = mTextColor.getColor();
    if (mWidget.getVisibility() == ConstraintWidget.INVISIBLE) {
      color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 100);
    }
    g.setColor(color);
    String string = getText();
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
      mTextPane.setFont(mFont.deriveFont((float)scaleSize * 0.88f));
      StyledDocument doc = mTextPane.getStyledDocument();
      SimpleAttributeSet attributeSet = new SimpleAttributeSet();
      switch (mAlignmentX) {
        case TEXT_ALIGNMENT_TEXT_START:
        case TEXT_ALIGNMENT_VIEW_START:
          StyleConstants.setAlignment(attributeSet, StyleConstants.ALIGN_LEFT);
          break;
        case TEXT_ALIGNMENT_CENTER:
          StyleConstants.setAlignment(attributeSet, StyleConstants.ALIGN_CENTER);
          break;
        case TEXT_ALIGNMENT_TEXT_END:
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
      int alignX = switchAlignment(string, mAlignmentX);
      switch (alignX) {
        case TEXT_ALIGNMENT_TEXT_START:
        case TEXT_ALIGNMENT_VIEW_START: {
          ftx = tx + horizontalPadding;
        }
        break;
        case TEXT_ALIGNMENT_TEXT_END:
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
      switch (mAlignmentY) {
        case TEXT_ALIGNMENT_VIEW_START: {
          fty = ty + fontMetrics.getAscent() + fontMetrics.getMaxDescent() +
                verticalPadding;
        }
        break;
        case TEXT_ALIGNMENT_CENTER: {
          fty = ty + fontMetrics.getAscent() + (h - fontMetrics.getAscent()) / 2;
        }
        break;
        case TEXT_ALIGNMENT_VIEW_END: {
          fty = ty + h - fontMetrics.getMaxDescent() - verticalPadding;
        }
        break;
      }

      Shape clip = g.getClip();
      g.clipRect(tx, ty, w, h);
      g.drawString(string, ftx, fty);
      g.setClip(clip);
    }
  }

  private static int switchAlignment(String string, int alignmentX) {
    if (string.isEmpty()) {
      return alignmentX;
    }
    char c = string.charAt(0);
    boolean flip_text = c >= 0x590 && c <= 0x6ff;
    if (flip_text) {
      switch (alignmentX) {
        case TEXT_ALIGNMENT_TEXT_END:
          return TEXT_ALIGNMENT_TEXT_START;
        case TEXT_ALIGNMENT_TEXT_START:
          return TEXT_ALIGNMENT_TEXT_END;
      }
    }
    return alignmentX;
  }
}
