/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.assets;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.XMLNS;
import static com.android.utils.XmlUtils.formatFloatValue;
import static java.awt.geom.PathIterator.SEG_CLOSE;
import static java.awt.geom.PathIterator.SEG_CUBICTO;
import static java.awt.geom.PathIterator.SEG_LINETO;
import static java.awt.geom.PathIterator.SEG_MOVETO;
import static java.awt.geom.PathIterator.SEG_QUADTO;
import static java.awt.geom.PathIterator.WIND_EVEN_ODD;
import static java.lang.Math.max;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.vectordrawable.PathBuilder;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static methods for rendering text as a vector drawable.
 */
public final class VectorTextRenderer {
  private static final String INDENT = "  ";
  private static final String DOUBLE_INDENT = INDENT + INDENT;

  /**
   * Renders the given text producing a vector drawable.
   *
   * @param text the text to render
   * @param fontFamily the font of the text
   * @param color the color of the text
   * @param opacity the opacity of the text
   * @return the XML of the resulting vector drawable
   */
  @NotNull
  public static String renderToVectorDrawable(@NotNull String text, @NotNull String fontFamily, int fontSize,
                                              @Nullable Color color, double opacity) {
    String[] lines = StringUtil.splitByLines(StringUtil.trimTrailing(text));
    StringBuilder result = new StringBuilder();
    Font font = new Font(fontFamily, Font.PLAIN, fontSize);
    BufferedImage newImage = AssetUtil.newArgbBufferedImage(fontSize, fontSize);
    Graphics2D gc = (Graphics2D)newImage.getGraphics();
    FontRenderContext frc = gc.getFontRenderContext();
    Rectangle2D textBounds = null;
    for (String line : lines) {
      LineMetrics lineMetrics = font.getLineMetrics(line, frc);
      GlyphVector glyphVector = font.createGlyphVector(frc, line);
      Rectangle2D lineBounds = glyphVector.getLogicalBounds();
      if (textBounds == null) {
        textBounds = lineBounds;
      }
      else {
        textBounds.setRect(textBounds.getX(), textBounds.getY(),
                       max(textBounds.getWidth(), lineBounds.getWidth()),
                       textBounds.getHeight() + lineMetrics.getLeading() + lineBounds.getHeight());
      }
    }
    double viewportWidth = textBounds.getWidth();
    double viewportHeight = textBounds.getHeight();
    result.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    // Output the "vector" element with the xmlns:android attribute.
    result.append(String.format("<vector %s:%s=\"%s\"", XMLNS, ANDROID_NS_NAME, ANDROID_URI));
    result.append('\n').append(DOUBLE_INDENT).append("android:width=\"").append(formatFloatValue(textBounds.getWidth())).append("dp\"");
    result.append('\n').append(DOUBLE_INDENT).append("android:height=\"").append(formatFloatValue(textBounds.getHeight())).append("dp\"");
    result.append('\n').append(DOUBLE_INDENT).append("android:viewportWidth=\"")
        .append(formatFloatValue(viewportWidth)).append("\"");
    result.append('\n').append(DOUBLE_INDENT).append("android:viewportHeight=\"")
        .append(formatFloatValue(viewportHeight)).append("\">");
    double lineOffsetY = 0;
    for (String line : lines) {
      LineMetrics lineMetrics = font.getLineMetrics(line, frc);
      GlyphVector glyphVector = font.createGlyphVector(frc, line);
      Rectangle2D lineBounds = glyphVector.getLogicalBounds();
      double offsetX = -textBounds.getX();
      double offsetY = -lineBounds.getY() + lineOffsetY;
      lineOffsetY += lineMetrics.getLeading() + lineBounds.getHeight();
      String indent = INDENT;
      String translateX = isSignificantlyDifferentFromZero(offsetX) ? formatFloatValue(offsetX) : null;
      String translateY = isSignificantlyDifferentFromZero(offsetY) ? formatFloatValue(offsetY) : null;
      if (translateX != null || translateY != null) {
        // Wrap the contents of the drawable into a translation group.
        result.append('\n').append(INDENT);
        result.append("<group");
        if (translateX != null) {
          result.append(' ').append("android:translateX=\"").append(translateX).append('"');
        }
        if (translateY != null) {
          result.append(' ').append("android:translateY=\"").append(translateY).append('"');
        }
        result.append('>');
        indent += INDENT;
      }

      if (color != null && opacity != 0) {
        int numGlyphs = glyphVector.getNumGlyphs();
        float[] coords = new float[6];
        for (int i = 0; i < numGlyphs; i++) {
          Shape outline = glyphVector.getGlyphOutline(i);
          renderGlyph(outline, indent, color, opacity, coords, result);
        }
      }

      if (translateX != null || translateY != null) {
        result.append('\n').append(INDENT).append("</group>");
      }
    }
    result.append('\n').append("</vector>");
    return result.toString();
  }

  private static boolean isSignificantlyDifferentFromZero(double value) {
    return Math.abs(value) >= 1.e-6;
  }

  private static void renderGlyph(@NotNull Shape outline, @NotNull String indent, @NotNull Color color, double opacity,
                                  @NotNull float[] coords, @NotNull StringBuilder result) {
    PathIterator pathIterator = outline.getPathIterator(null);
    int windingRule = pathIterator.getWindingRule();
    PathBuilder pathBuilder = new PathBuilder();
    while (!pathIterator.isDone()) {
      switch (pathIterator.currentSegment(coords)) {
        case SEG_MOVETO:
          pathBuilder.absoluteMoveTo(coords[0], coords[1]);
          break;
        case SEG_LINETO:
          pathBuilder.absoluteLineTo(coords[0], coords[1]);
          break;
        case SEG_QUADTO:
          pathBuilder.absoluteQuadraticCurveTo(coords[0], coords[1], coords[2], coords[3]);
          break;
        case SEG_CUBICTO:
          pathBuilder.absoluteCurveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
          break;
        case SEG_CLOSE:
          pathBuilder.absoluteClose();
          break;
      }
      pathIterator.next();
    }

    if (!pathBuilder.isEmpty()) {
      result.append('\n').append(indent).append("<path android:pathData=\"").append(pathBuilder.toString()).append('"')
          .append('\n').append(indent).append(DOUBLE_INDENT).append("android:fillColor=\"")
          .append(String.format("#%06X", color.getRGB() & 0xFFFFFF)).append('"');
      String opacityValue = formatFloatValue(opacity);
      if (!opacityValue.equals("1")) {
        result.append('\n').append(indent).append(DOUBLE_INDENT).append("android:fillAlpha=\"").append(opacityValue).append('"');
      }
      if (windingRule == WIND_EVEN_ODD) {
        result.append('\n').append(indent).append(DOUBLE_INDENT).append("android:fillType=\"evenOdd\"");
      }
      result.append("/>");
    }
  }
}
