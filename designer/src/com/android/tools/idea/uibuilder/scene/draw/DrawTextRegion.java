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

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.sherpa.drawing.ColorSet;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Base Class for drawing text components
 */
public class DrawTextRegion extends DrawRegion {
  static boolean DO_WRAP = false;
  protected int mFontSize = 14;
  protected float mScale = 1.0f;
  protected int myBaseLineOffset = 0;
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
  protected String mText;
  public static final float SCALE_ADJUST = .88f; // a factor to scale funts from android to Java2d
  protected Font mFont = new Font("Helvetica", Font.PLAIN, (int)mFontSize)
    .deriveFont(AffineTransform.getScaleInstance(mScale * SCALE_ADJUST, mScale * SCALE_ADJUST));
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
    mFontSize = Integer.parseInt(sp[c++]);
    mScale = java.lang.Float.parseFloat(sp[c++]);
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
           "," +
           mFontSize +
           "," +
           mScale +
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
                        int fontSize, float scale) {
    super(x, y, width, height);
    mText = text;
    myBaseLineOffset = baseLineOffset;
    mSingleLine = singleLine;
    mToUpperCase = toUpperCase;
    mAlignmentX = textAlignmentX;
    mAlignmentY = textAlignmentY;
    mFontSize = fontSize;
    mScale = scale;

    mFont = new Font("Helvetica", Font.PLAIN, (int)(mFontSize))
      .deriveFont(AffineTransform.getScaleInstance(mScale * SCALE_ADJUST, mScale * SCALE_ADJUST)); // Convert to swing size font
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
    if (stringWidth > (w + 10) && !mSingleLine) { // if it is multi lined text use a swing text pane to do the wrap
      mTextPane.setText(string);
      mTextPane.setForeground(color);
      mTextPane.setSize(w, h);
      mTextPane.setFont(mFont);
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
      int alignX = switchAlignment(string, mAlignmentX);

      switch (alignX) {
        case TEXT_ALIGNMENT_TEXT_START:
        case TEXT_ALIGNMENT_VIEW_START: {
          ftx = tx + horizontalPadding;
        }
        break;
        case TEXT_ALIGNMENT_CENTER: {
          int paddx = (w - stringWidth) / 2;
          ftx = tx + paddx;
        }
        break;
        case TEXT_ALIGNMENT_TEXT_END:
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

  public static int getFont(NlComponent nlc, String default_dim) {
    Configuration configuration = nlc.getModel().getConfiguration();
    ResourceResolver resourceResolver = configuration.getResourceResolver();

    Integer size = null;

    if (resourceResolver != null) {
      String textSize = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_SIZE);
      if (textSize != null) {
        size = ViewEditor.resolveDimensionPixelSize(resourceResolver, textSize, configuration);
      }
    }

    if (size == null) {
      // With the specified string, this method cannot return null
      //noinspection ConstantConditions
      size = ViewEditor.resolveDimensionPixelSize(resourceResolver, default_dim, configuration);
    }
    return size;
  }
}
