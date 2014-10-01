/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * There is a possible case that we want to display particular text at UI and that available horizontal space is not large
 * enough to show it as is. We might want to display a single text line on more than one visual line then.
 * <p/>
 * This class encapsulates that logic of representing a line of text on one or more visual line. It's main purpose is to separate
 * that logic from UI processing in order to be able to cover it by tests.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 10/09/14
 */
public class WrapsAwareTextHelper {

  @SuppressWarnings("UndesirableClassUsage")
  private static final Graphics2D ourGraphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

  static {
    GraphicsUtil.setupFractionalMetrics(ourGraphics);
    GraphicsUtil.setupAntialiasing(ourGraphics, true, true);
  }

  private static final String LINE_BREAK_MARKER = "___LINE_BREAK___";

  @NotNull private final DimensionCalculator myDimensionCalculator;

  public WrapsAwareTextHelper(@NotNull final JComponent component) {
    this(new DimensionCalculator() {
      @Override
      public void calculate(@NotNull String inText, @NotNull Font inFont, @NotNull Dimension outDimension) {
        FontMetrics metrics = component.getFontMetrics(inFont);
        if (UIUtil.isRetina() && SystemInfo.isOracleJvm) {
          stringDimension(inText, inFont, outDimension);
        }
        else {
          outDimension.width = metrics.stringWidth(inText);
          outDimension.height = metrics.getHeight();
        }
      }

      private void stringDimension(@NotNull String text, @NotNull Font font, @NotNull Dimension resultHolder) {
        GraphicsUtil.setupAntialiasing(ourGraphics, true, true);
        FontMetrics metrics = ourGraphics.getFontMetrics(font);
        Rectangle2D bounds = metrics.getStringBounds(text, 0, text.length(), ourGraphics);
        resultHolder.width = (int)bounds.getWidth();
        resultHolder.height = (int)bounds.getHeight();
      }
    });
  }

  public WrapsAwareTextHelper(@NotNull DimensionCalculator dimensionCalculator) {
    myDimensionCalculator = dimensionCalculator;
  }

  public static void appendLineBreak(@NotNull List<String> textFragments) {
    textFragments.add(LINE_BREAK_MARKER);
  }

  /**
   * Processes given styled text and fills out parameters with information on how to display the given text.
   *
   * @param inTextFragments   text fragments to use
   * @param inTextAttributes  styled text attributes to use for the given test fragments (is assumed to be of the same size
   *                          as the given text tokens collection)
   * @param font              base font to use for calculating given text dimensions
   * @param inMinimumWidths   collections which holds information about minimum width (in pixels) for the target text fragments
   *                          (a key is a fragment's index and the value is its minimum width)
   * @param inWidthLimit      available width limit to use for calculation. Non-positive value means that no width limit should be used
   * @param outTextDimension  an object which will be filled by information about given styled text dimensions when this method returns
   * @param outBreakOffsets   a collection which will hold offsets where given text should visually break into a new line.
   *                          Target text fragment's index at the given fragments collection is a key and list of offsets within that
   *                          fragment is a value. Note that current method doesn't attempt to modify this collection over than
   *                          to populate it
   * @param outLineHeights    a collection which holds information about text line heights
   */
  public void wrap(@NotNull List<String> inTextFragments,
                   @NotNull List<SimpleTextAttributes> inTextAttributes,
                   @NotNull Font font,
                   @NotNull TIntIntHashMap inMinimumWidths,
                   int inWidthLimit,
                   @NotNull Dimension outTextDimension,
                   @NotNull TIntObjectHashMap<TIntArrayList> outBreakOffsets,
                   @NotNull TIntIntHashMap outLineHeights)
  {
    WrapContext context = new WrapContext(inTextFragments, outBreakOffsets, outLineHeights, font, inWidthLimit);
    for (int i = 0; i < inTextAttributes.size(); i++) {
      final String text = inTextFragments.get(i);
      if (LINE_BREAK_MARKER.equals(text)) {
        context.processTextLineBreak();
        continue;
      }

      context.apply(inTextAttributes.get(i));
      myDimensionCalculator.calculate(text, context.font, context.tmp);
      final int minWidth = inMinimumWidths.get(i);
      if (minWidth > 0 && minWidth > context.tmp.width) {
        context.processFixedFragmentWidth(minWidth, i);
        continue;
      }
      context.processRegularFragment(i, 0);
    }
    outTextDimension.width = Math.max(context.textDimension.width, context.currentLineDimension.width);
    outTextDimension.height = context.textDimension.height + context.currentLineDimension.height;
    outLineHeights.put(context.line, context.currentLineDimension.height);
  }

  /**
   * Tries to map one of the given fragments to the given (x; y) coordinates.
   *
   * @param textFragments   fragments to use
   * @param textAttributes  styled text attributes for the given fragments (this list is assumed to be of the same size as fragments list
   *                        and holds attributes for the i-th fragment at the i-th position)
   * @param minimumWidths   collections which holds information about minimum width (in pixels) for the target text fragments
   *                        (a key is a fragment's index and the value is its minimum width)
   * @param breakOffsets    a collection which will hold offsets where given text should visually break into a new line.
   *                        Target text fragment's index at the given fragments collection is a key and list of offsets within that
   *                        fragment is a value
   * @param lineHeights     a collection which holds information about text line heights
   * @param font            base font used for displaying given text fragments (implies text dimensions)
   * @param x               target x
   * @param y               target y
   * @return                index of the fragment at the given fragments list which corresponds to the given (x; y) (if found);
   *                        negative value as an indication that there is no text fragment at the target point
   */
  public int mapFragment(@NotNull List<String> textFragments,
                         @NotNull List<SimpleTextAttributes> textAttributes,
                         @NotNull TIntIntHashMap minimumWidths,
                         @NotNull TIntObjectHashMap<TIntArrayList> breakOffsets,
                         @NotNull TIntIntHashMap lineHeights,
                         @NotNull Font font,
                         int x,
                         int y)
  {
    if (lineHeights.isEmpty()) {
      // No lines are there.
      return -1;
    }
    MapContext context = new MapContext(textFragments, breakOffsets, lineHeights, font,minimumWidths, x, y);
    for (int i = 0; i < textFragments.size(); i++) {
      final String text = textFragments.get(i);
      if (LINE_BREAK_MARKER.equals(text)) {
        boolean canContinue = context.onLineBreak();
        if (!canContinue) {
          return -1;
        }
        continue;
      }

      context.apply(textAttributes.get(i));
      Boolean match = context.processTextFragment(i, 0);
      if (match == Boolean.TRUE) {
        return i;
      }
      else if (match == Boolean.FALSE) {
        return -1;
      }
    }
    return -1;
  }

  public interface DimensionCalculator {
    void calculate(@NotNull String inText, @NotNull Font inFont, @NotNull Dimension outDimension);
  }

  private static class CommonContext {

    @NotNull final Dimension tmp = new Dimension();

    int line;

    @NotNull protected final List<String>                     myTextFragments;
    @NotNull protected final TIntObjectHashMap<TIntArrayList> myBreakOffsets;
    @NotNull protected final TIntIntHashMap                   myLineHeights;

    @NotNull Font font;

    protected final int myBaseFontSize;

    private boolean myFontWasSmaller;

    CommonContext(@NotNull List<String> textFragments,
                  @NotNull TIntObjectHashMap<TIntArrayList> breakOffsets,
                  @NotNull TIntIntHashMap lineHeights,
                  @NotNull Font font)
    {
      myTextFragments = textFragments;
      myBreakOffsets = breakOffsets;
      myLineHeights = lineHeights;
      this.font = font;
      myBaseFontSize = font.getSize();
    }

    void apply(@NotNull SimpleTextAttributes attributes) {
      boolean isSmaller = attributes.isSmaller();
      if (font.getStyle() != attributes.getFontStyle() || isSmaller != myFontWasSmaller) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : myBaseFontSize);
      }
      myFontWasSmaller = isSmaller;
    }
  }

  private class WrapContext extends CommonContext {

    @NotNull final Dimension textDimension        = new Dimension();
    @NotNull final Dimension currentLineDimension = new Dimension();

    private final int myWidthLimit;
    private final int mySampleWidth;

    WrapContext(@NotNull List<String> textFragments,
                @NotNull TIntObjectHashMap<TIntArrayList> breakOffsets,
                @NotNull TIntIntHashMap lineHeights,
                @NotNull Font font,
                int widthLimit)
    {
      super(textFragments, breakOffsets, lineHeights, font);
      myWidthLimit = widthLimit;
      myDimensionCalculator.calculate("W", font, tmp);
      mySampleWidth = tmp.width;
    }

    void processTextLineBreak() {
      if (currentLineDimension.height <= 0) {
        myDimensionCalculator.calculate("A", font, tmp);
        currentLineDimension.height = tmp.height;
      }
      onNewLine();
    }

    void processFixedFragmentWidth(int fixedWidth, int textFragmentIndex) {
      if (myWidthLimit > 0 && currentLineDimension.width + fixedWidth >= myWidthLimit) {
        if (currentLineDimension.width > 0) {
          onNewLine();
          storeBreakOffset(textFragmentIndex, 0);
          currentLineDimension.width = fixedWidth;
          currentLineDimension.height = tmp.height;
        }
        else {
          currentLineDimension.width += fixedWidth;
          currentLineDimension.height = Math.max(currentLineDimension.height, tmp.height);
          onNewLine();
          if (textFragmentIndex < myTextFragments.size() - 1) {
            storeBreakOffset(textFragmentIndex + 1, 0);
          }
        }
      }
      else {
        currentLineDimension.width += fixedWidth;
        currentLineDimension.height = Math.max(currentLineDimension.height, tmp.height);
      }
    }

    void processRegularFragment(int fragmentIndex, int textFragmentStartOffset) {
      String fragmentText = myTextFragments.get(fragmentIndex);
      myDimensionCalculator.calculate(fragmentText.substring(textFragmentStartOffset), font, tmp);
      currentLineDimension.height = Math.max(currentLineDimension.height, tmp.height);
      if (myWidthLimit <= 0 || currentLineDimension.width + tmp.width <= myWidthLimit) {
        currentLineDimension.width += tmp.width;
        return;
      }

      int breakOffset = textFragmentStartOffset + (myWidthLimit - currentLineDimension.width) / mySampleWidth;
      breakOffset = Math.min(fragmentText.length(), breakOffset);
      myDimensionCalculator.calculate(fragmentText.substring(textFragmentStartOffset, breakOffset), font, tmp);
      if (currentLineDimension.width + tmp.width <= myWidthLimit) {
        currentLineDimension.width += tmp.width;
        for (int i = breakOffset; i < fragmentText.length(); i++) {
          myDimensionCalculator.calculate(fragmentText.substring(i, i + 1), font, tmp);
          if (currentLineDimension.width + tmp.width > myWidthLimit) {
            break;
          }
          currentLineDimension.width += tmp.width;
          breakOffset++;
        }
      }
      else {
        for (--breakOffset; breakOffset > textFragmentStartOffset; breakOffset--) {
          myDimensionCalculator.calculate(fragmentText.substring(textFragmentStartOffset, breakOffset), font, tmp);
          if (currentLineDimension.width + tmp.width <= myWidthLimit) {
            currentLineDimension.width += tmp.width;
            break;
          }
        }
      }
      storeBreakOffset(fragmentIndex, breakOffset);
      onNewLine();
      processRegularFragment(fragmentIndex, breakOffset);
    }

    private void onNewLine() {
      textDimension.width = Math.max(currentLineDimension.width, textDimension.width);
      textDimension.height += currentLineDimension.height;
      myLineHeights.put(line++, currentLineDimension.height);
      currentLineDimension.width = currentLineDimension.height = 0;
    }

    private void storeBreakOffset(int fragmentIndex, int breakOffset) {
      TIntArrayList list = myBreakOffsets.get(fragmentIndex);
      if (list == null) {
        myBreakOffsets.put(fragmentIndex, list = new TIntArrayList());
      }
      list.add(breakOffset);
    }
  }

  private class MapContext extends CommonContext {

    @NotNull private final TIntIntHashMap myMinimumWidths;

    private final int myTargetX;
    private final int myTargetY;
    private       int myLineStartY;
    private       int myLineEndY;
    private       int myLineX;

    MapContext(@NotNull List<String> textFragments,
               @NotNull TIntObjectHashMap<TIntArrayList> breakOffsets,
               @NotNull TIntIntHashMap lineHeights,
               @NotNull Font font,
               @NotNull TIntIntHashMap minimumWidths,
               int targetX,
               int targetY)
    {
      super(textFragments, breakOffsets, lineHeights, font);
      myMinimumWidths = minimumWidths;
      myTargetX = targetX;
      myTargetY = targetY;
      myLineEndY = lineHeights.get(0);
    }

    /**
     * @return  <code>true</code> if we can continue match process; <code>false</code> as an indication that no match will be found
     */
    public boolean onLineBreak() {
      int lineHeight = myLineHeights.get(++line);
      if (lineHeight <= 0) {
        // No more lines left.
        return false;
      }
      myLineStartY = myLineEndY;
      myLineEndY += lineHeight;
      myLineX = 0;
      return myTargetY >= myLineStartY;
    }

    /**
     * Asks to process target text fragment identifies by the given index.
     *
     * @param textFragmentIndex    target text fragment's index
     * @param fragmentStartOffset  there is a possible case that particular fragment is split into more than one visual line.
     *                             We need to process such fragments parts separately then. This arguments defines start offset
     *                             of the target fragment's part to process
     * @return                     {@link Boolean#TRUE} as an indication that target fragment matches target coordinates;
     *                             {@link Boolean#FALSE} as an indication that no match will be found and the whole match process
     *                             should be stopped;
     *                             <code>null</code> as an indication that given fragment doesn't match target coordinates and
     *                             match process should be continued
     */
    @Nullable
    public Boolean processTextFragment(int textFragmentIndex, int fragmentStartOffset) {
      // Check if we are on the target line.
      String wholeFragmentText = myTextFragments.get(textFragmentIndex);
      if (myTargetY >= myLineStartY && myTargetY <= myLineEndY) {
        int endOffset = findFragmentPartEndOffset(textFragmentIndex, fragmentStartOffset);
        if (endOffset < 0) {
          return null;
        }

        String textToProcess = wholeFragmentText.substring(fragmentStartOffset, endOffset);
        myDimensionCalculator.calculate(textToProcess, font, tmp);
        int minimumWidth = myMinimumWidths.get(textFragmentIndex);
        if (minimumWidth <= 0 || minimumWidth <= tmp.width || fragmentStartOffset > 0 || endOffset < wholeFragmentText.length()) {
          // Reset forced minimum width if target fragment is wrapped or minimum width is less than the actual width.
          minimumWidth = -1;
        }
        if (myTargetX < myLineX + Math.max(tmp.width, minimumWidth)) {
          // We want to report 'no match' if target location points into space reserved for a minimum width but not actually occupied
          // by fragment text.
          return myTargetX < myLineX + tmp.width;
        }

        if (endOffset == wholeFragmentText.length()) {
          myLineX += Math.max(tmp.width, minimumWidth);
          return null;
        }
        else {
          // Target point is located beyond the fragment's part and there is a line break.
          return false;
        }
      }

      int endOffset = findFragmentPartEndOffset(textFragmentIndex, fragmentStartOffset);
      if (endOffset < 0) {
        return null;
      }
      else if (endOffset == wholeFragmentText.length()) {
        return null;
      }
      else {
        onLineBreak();
        return processTextFragment(textFragmentIndex, endOffset);
      }
    }

    private int findFragmentPartEndOffset(int fragmentIndex, int fragmentPartStartOffset) {
      String fragmentText = myTextFragments.get(fragmentIndex);
      if (fragmentPartStartOffset >= fragmentText.length()) {
        return -1;
      }
      TIntArrayList breakOffsets = myBreakOffsets.get(fragmentIndex);
      if (breakOffsets == null || breakOffsets.isEmpty()) {
        return fragmentText.length();
      }
      if (fragmentPartStartOffset == 0) {
        return breakOffsets.get(0);
      }
      for (int i = 0; i < breakOffsets.size(); i++) {
        if (fragmentPartStartOffset == breakOffsets.get(i)) {
          return i < breakOffsets.size() - 1 ? breakOffsets.get(i + 1) : fragmentText.length();
        }
      }
      return -1;
    }
  }
}
