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
package com.android.tools.idea.common.scene.draw;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;

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
  protected static final int DEFAULT_FONT_SIZE = 14;
  protected static final float DEFAULT_SCALE = 1.0f;
  protected static final float SCALE_ADJUST = .88f; // a factor to scale fonts from android to Java2d
  @SuppressWarnings("UseJBColor")
  private static final Color TEXT_PANE_BACKGROUND = new Color(0, 0, 0, 0);

  static boolean DO_WRAP = false;
  protected final int mFontSize;
  protected final int mMode;
  protected int mLevel = COMPONENT_LEVEL;
  protected final float mScale;
  protected final int myBaseLineOffset;
  @SwingCoordinate protected int mHorizontalPadding = 0;
  @SwingCoordinate protected int mVerticalPadding = 0;
  @SwingCoordinate protected int mVerticalMargin = 0;
  @SwingCoordinate protected int mHorizontalMargin = 0;
  protected final boolean mToUpperCase;
  public static final int TEXT_ALIGNMENT_TEXT_START = 2;
  public static final int TEXT_ALIGNMENT_TEXT_END = 3;
  public static final int TEXT_ALIGNMENT_VIEW_START = 5;
  public static final int TEXT_ALIGNMENT_VIEW_END = 6;
  public static final int TEXT_ALIGNMENT_CENTER = 4;
  protected final int mAlignmentX;
  protected final int mAlignmentY;
  protected final String mText;
  protected final Font mFont;
  protected boolean mSingleLine = false;
  /** {@link JTextPane} used to layout multi-line text */
  final static JTextPane sTextPane = new JTextPane();

  /**
   * Set the behavior to do a text wrap content or not
   * In Android Studio, this should not be active
   *
   * @param doWrap
   */
  public static void setDoWrap(boolean doWrap) {
    DO_WRAP = doWrap;
  }

  @Override
  public int getLevel() {
    return mLevel;
  }

  public static DrawTextRegion createFromString(String string) {
    String[] sp = string.split(",");
    int c = 0;
    int x = Integer.parseInt(sp[c++]);
    int y = Integer.parseInt(sp[c++]);
    int width = Integer.parseInt(sp[c++]);
    int height = Integer.parseInt(sp[c++]);
    int mode = Integer.parseInt(sp[c++]);
    int baseLineOffset = Integer.parseInt(sp[c++]);
    boolean singleLine = Boolean.parseBoolean(sp[c++]);
    boolean toUpperCase = Boolean.parseBoolean(sp[c++]);
    int alignmentX = Integer.parseInt(sp[c++]);
    int alignmentY = Integer.parseInt(sp[c++]);
    int fontSize = Integer.parseInt(sp[c++]);
    float scale = java.lang.Float.parseFloat(sp[c++]);
    String text = string.substring(string.indexOf('\"') + 1, string.lastIndexOf('\"'));

    return new DrawTextRegion(x, y, width, height, mode, baseLineOffset, text, singleLine, toUpperCase, alignmentX, alignmentY, fontSize, scale);
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
           mMode +
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

  public DrawTextRegion(@SwingCoordinate int x,
                        @SwingCoordinate int y,
                        @SwingCoordinate int width,
                        @SwingCoordinate int height,
                        int mode,
                        int baseLineOffset,
                        String text,
                        boolean singleLine,
                        boolean toUpperCase,
                        int textAlignmentX,
                        int textAlignmentY,
                        int fontSize, float scale) {
    super(x, y, width, height);
    mMode = mode;
    mText = text;
    myBaseLineOffset = baseLineOffset;
    mSingleLine = singleLine;
    mToUpperCase = toUpperCase;
    mAlignmentX = textAlignmentX;
    mAlignmentY = textAlignmentY;
    mFontSize = fontSize;
    mScale = scale;

    mFont = new Font("Helvetica", Font.PLAIN, mFontSize)
      .deriveFont(AffineTransform.getScaleInstance(scale * SCALE_ADJUST, scale * SCALE_ADJUST));

    switch (mMode) {
      case DecoratorUtilities.ViewStates.SELECTED_VALUE:
        mLevel = COMPONENT_SELECTED_LEVEL;
    }
  }

  public DrawTextRegion(@SwingCoordinate int x,
                        @SwingCoordinate int y,
                        @SwingCoordinate int width,
                        @SwingCoordinate int height,
                        int mode,
                        int baseLineOffset,
                        String text) {
    this(x, y, width, height, mode, baseLineOffset, text, false, false, TEXT_ALIGNMENT_TEXT_START, TEXT_ALIGNMENT_TEXT_START, DEFAULT_FONT_SIZE,
         DEFAULT_SCALE);
  }

  @Override
  public void paint(Graphics2D g2d, SceneContext sceneContext) {
    int tx = x;
    int ty = y;
    int h = height;
    int w = width;
    if (!sceneContext.getColorSet().drawBackground()) {
      return;
    }

    ColorSet colorSet = sceneContext.getColorSet();
    int horizontalPadding = mHorizontalPadding + mHorizontalMargin;
    int verticalPadding = mVerticalPadding + mVerticalMargin;
    g2d.setFont(mFont);
    FontMetrics fontMetrics = g2d.getFontMetrics();
    Color color = colorSet.getFrames();
    g2d.setColor(color);
    String string = mText;
    if (mToUpperCase) {
      string = string.toUpperCase();
    }
    int ftx = 0;
    int fty = 0;
    int stringWidth = fontMetrics.stringWidth(string);
    if (stringWidth > (w + 10) && !mSingleLine) { // if it is multi lined text use a swing text pane to do the wrap
      sTextPane.setBackground(TEXT_PANE_BACKGROUND);
      sTextPane.setText(string);
      sTextPane.setForeground(color);
      sTextPane.setSize(w, h);
      sTextPane.setFont(mFont);
      StyledDocument doc = sTextPane.getStyledDocument();
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
          sTextPane.setAlignmentY(Component.TOP_ALIGNMENT);
          break;
        case TEXT_ALIGNMENT_CENTER:
          sTextPane.setAlignmentY(Component.CENTER_ALIGNMENT);
          break;
        case TEXT_ALIGNMENT_VIEW_END:
          sTextPane.setAlignmentY(Component.BOTTOM_ALIGNMENT);
          break;
      }
      doc.setParagraphAttributes(0, doc.getLength(), attributeSet, false);
      g2d.translate(tx, ty);
      Shape clip = g2d.getClip();
      g2d.clipRect(0, 0, w, h);
      sTextPane.paint(g2d);
      g2d.setClip(clip);
      g2d.translate(-tx, -ty);
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

      Shape clip = g2d.getClip();
      g2d.clipRect(tx, ty, w, h);
      g2d.drawString(string, ftx, fty);
      g2d.setClip(clip);
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
    if (size != null) {
      return size;
    }
    return -1;
  }
}
