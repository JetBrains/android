/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.utils.XmlUtils.formatFloatValue;

import com.android.SdkConstants;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.utils.CharSequences;
import com.google.common.collect.ImmutableSet;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Methods for manipulating vector drawables.
 */
public final class VectorDrawableTransformer {
  private static final ImmutableSet<String> NAMES_OF_HANDLED_ATTRIBUTES =
      ImmutableSet.of("width", "height", "viewportWidth", "viewportHeight", "tint", "alpha");
  private static final String INDENT = "  ";
  private static final String DOUBLE_INDENT = INDENT + INDENT;

  /** Do not instantiate. All methods are static. */
  private VectorDrawableTransformer() {}

  /**
   * Transforms a vector drawable to fit in a rectangle with the {@code targetSize} dimensions.
   *
   * @param originalDrawable the original drawable, preserved intact by the method
   * @param targetSize the size of the target rectangle
   * @return the transformed drawable; may be the same as the original if no transformation was
   *     required, or if the drawable is not a vector one
   */
  @NotNull
  public static String transform(@NotNull String originalDrawable, @NotNull Dimension targetSize) {
    return transform(originalDrawable, targetSize, Gravity.CENTER, 1, null, null, null, 1, true);
  }

  /**
   * Transforms a vector drawable to fit in a rectangle with the {@code targetSize} dimensions and optionally
   * applies tint and opacity to it.
   * Conceptually, the geometric transformation includes of the following steps:
   * <ul>
   *   <li>The drawable is resized and centered in a rectangle of the target size</li>
   *   <li>If {@code clipRectangle} is not null, the drawable is clipped, resized and re-centered again</li>
   *   <li>The drawable is scaled according to {@code scaleFactor}</li>
   *   <li>The drawable is either padded or clipped to fit into the target rectangle</li>
   *   <li>If {@code shift} is not null, the drawable is shifted</li>
   * </ul>
   *
   * @param originalDrawable the original drawable, preserved intact by the method
   * @param targetSize the size of the target rectangle
   * @param gravity determines alignment of the original image in the target rectangle
   * @param scaleFactor a scale factor to apply
   * @param clipRectangle an optional clip rectangle in coordinates expressed as fraction of the {@code targetSize}
   * @param shift an optional shift vector in coordinates expressed as fraction of the {@code targetSize}
   * @param tint an optional tint to apply to the drawable
   * @param opacity opacity to apply to the drawable
   * @param addClipPath add a clip path to the vector drawable
   * @return the transformed drawable; may be the same as the original if no transformation was
   *     required, or if the drawable is not a vector one
   */
  @NotNull
  public static String transform(@NotNull String originalDrawable,
                                 @NotNull Dimension targetSize,
                                 @NotNull Gravity gravity,
                                 double scaleFactor,
                                 @Nullable Rectangle2D clipRectangle,
                                 @Nullable Point2D shift,
                                 @Nullable Color tint,
                                 double opacity,
                                 boolean addClipPath) {
    KXmlParser parser = new KXmlParser();

    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(CharSequences.getReader(originalDrawable, true));
      int startLine = 1;
      int startColumn = 1;
      int token;
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.START_TAG) {
        startLine = parser.getLineNumber();
        startColumn = parser.getColumnNumber();
      }
      // Skip to the first tag.
      if (parser.getEventType() != XmlPullParser.START_TAG || !"vector".equals(parser.getName()) || parser.getPrefix() != null) {
        return originalDrawable; // Not a vector drawable.
      }

      String originalTintValue = parser.getAttributeValue(ANDROID_URI, "tint");
      String tintValue = tint == null ? originalTintValue : IdeResourcesUtil.colorToString(tint);

      String originalAlphaValue = parser.getAttributeValue(ANDROID_URI, "alpha");
      if (originalAlphaValue != null) {
        opacity *= parseDoubleValue(originalAlphaValue, "");
      }
      String alphaValue = formatFloatValue(opacity);
      if (alphaValue.equals("1")) {
        alphaValue = null; // No need to set the default opacity.
      }

      double targetWidth = targetSize.getWidth();
      double targetHeight = targetSize.getHeight();
      double width = targetWidth;
      double height = targetHeight;
      double originalViewportWidth = getDoubleAttributeValue(parser, ANDROID_URI, "viewportWidth", "");
      double originalViewportHeight = getDoubleAttributeValue(parser, ANDROID_URI, "viewportHeight", "");
      String widthValue = parser.getAttributeValue(ANDROID_URI, "width");
      if (widthValue != null) {
        String suffix = getSuffix(widthValue);
        width = getDoubleAttributeValue(parser, ANDROID_URI, "width", suffix);
        height = getDoubleAttributeValue(parser, ANDROID_URI, "height", suffix);

        //noinspection FloatingPointEquality -- safe in this context since all integer values are representable as double.
        if (suffix.equals("dp") && width == targetWidth && height == targetHeight &&
            originalViewportWidth == targetWidth && originalViewportHeight == targetHeight &&
            scaleFactor == 1 && clipRectangle == null &&
            Objects.equals(tintValue, originalTintValue) && Objects.equals(alphaValue, originalAlphaValue)) {
          return originalDrawable; // No transformation is needed.
        }
        if (Double.isNaN(width) || width == 0 || Double.isNaN(height) || height == 0) {
          width = targetWidth;
          height = targetHeight;
        }
      }

      if (Double.isNaN(originalViewportWidth) || originalViewportWidth == 0 ||
          Double.isNaN(originalViewportHeight) || originalViewportHeight == 0) {
        originalViewportWidth = width;
        originalViewportHeight = height;
      }

      // Components of the translation vector in viewport coordinates.
      double x = 0;
      double y = 0;
      if (clipRectangle != null) {
        // Adjust scale.
        scaleFactor /= Math.max(clipRectangle.getWidth(), clipRectangle.getHeight());
        // Re-center the image relative to the clip rectangle.
        x += (0.5 - clipRectangle.getCenterX()) * originalViewportWidth * scaleFactor;
        y += (0.5 - clipRectangle.getCenterY()) * originalViewportHeight * scaleFactor;
      }

      double scaleFactorX = scaleFactor;
      double scaleFactorY = scaleFactor;
      double originalAspectRatio = width / height;
      double targetAspectRatio = targetWidth / targetHeight;
      double aspectFactor = originalAspectRatio / targetAspectRatio;
      if (aspectFactor < 1.0) {
        // The image was stretched horizontally, modify the scale factor to restore the original shape
        scaleFactorX *= aspectFactor;
      } else if (aspectFactor > 1.0) {
        // The image was stretched vertically, modify the scale factor to restore the original shape
        scaleFactorY /= aspectFactor;
      }

      // Recenter after scaling
      x += originalViewportWidth * ((1.0 - scaleFactorX) / 2.0);
      y += originalViewportHeight * ((1.0 - scaleFactorY) / 2.0);

      if (shift != null) {
        x += originalViewportWidth * shift.getX();
        y += originalViewportHeight * shift.getY();
      }

      StringBuilder result = new StringBuilder(originalDrawable.length() + originalDrawable.length() / 8);

      Indenter indenter = new Indenter(originalDrawable);
      // Copy contents before the first element.
      indenter.copy(1, 1, startLine, startColumn, "", result);
      String lineSeparator = detectLineSeparator(originalDrawable);
      // Output the "vector" element with the xmlns:android attribute.
      result.append(String.format("<vector %s:%s=\"%s\"", SdkConstants.XMLNS, SdkConstants.ANDROID_NS_NAME, ANDROID_URI));
      // Copy remaining namespace attributes.
      for (int i = 0; i < parser.getNamespaceCount(1); i++) {
        String prefix = parser.getNamespacePrefix(i);
        String uri = parser.getNamespaceUri(i);
        if (!SdkConstants.ANDROID_NS_NAME.equals(prefix) || !ANDROID_URI.equals(uri)) {
          result.append(String.format("%s%s%s:%s=\"%s\"", lineSeparator, DOUBLE_INDENT, SdkConstants.XMLNS, prefix, uri));
        }
      }

      result.append(String.format("%s%sandroid:width=\"%sdp\"", lineSeparator, DOUBLE_INDENT, formatFloatValue(targetWidth)));
      result.append(String.format("%s%sandroid:height=\"%sdp\"", lineSeparator, DOUBLE_INDENT, formatFloatValue(targetHeight)));
      result.append(String.format("%s%sandroid:viewportWidth=\"%s\"", lineSeparator, DOUBLE_INDENT,
                                  formatFloatValue(originalViewportWidth)));
      result.append(String.format("%s%sandroid:viewportHeight=\"%s\"", lineSeparator, DOUBLE_INDENT,
                                  formatFloatValue(originalViewportHeight)));
      if (tintValue != null) {
        result.append(String.format("%s%sandroid:tint=\"%s\"", lineSeparator, DOUBLE_INDENT, tintValue));
      }
      if (alphaValue != null) {
        result.append(String.format("%s%sandroid:alpha=\"%s\"", lineSeparator, DOUBLE_INDENT, alphaValue));
      }

      // Copy remaining attributes.
      for (int i = 0; i < parser.getAttributeCount(); i++) {
        String prefix = parser.getAttributePrefix(i);
        String name = parser.getAttributeName(i);
        if (!SdkConstants.ANDROID_NS_NAME.equals(prefix) || !NAMES_OF_HANDLED_ATTRIBUTES.contains(name)) {
          if (prefix != null) {
            name = prefix + ':' + name;
          }
          result.append(String.format("%s%s%s=\"%s\"", lineSeparator, DOUBLE_INDENT, name, parser.getAttributeValue(i)));
        }
      }
      result.append('>');

      String indent = "";
      int copyDepth = 2;
      startLine = parser.getLineNumber();
      startColumn = parser.getColumnNumber();
      String translateX = isSignificantlyDifferentFromZero(x / targetWidth) ? formatFloatValue(x) : null;
      String translateY = isSignificantlyDifferentFromZero(y / targetHeight) ? formatFloatValue(y) : null;
      String scaleX = formatFloatValue(scaleFactorX);
      String scaleY = formatFloatValue(scaleFactorY);
      String clipX = formatFloatValue(originalViewportWidth);
      String clipY = formatFloatValue(originalViewportHeight);
      boolean adjustmentNeeded = !scaleX.equals("1") || !scaleY.equals("1") || translateX != null || translateY != null;
      if (adjustmentNeeded) {
        // Wrap contents of the drawable into a translation group.
        result.append(lineSeparator).append(INDENT);
        result.append("<group");
        String delimiter = " ";
        if (!scaleX.equals("1")) {
          result.append(String.format("%sandroid:scaleX=\"%s\"", delimiter, scaleX));
          delimiter = lineSeparator + INDENT + DOUBLE_INDENT;
        }
        if (!scaleY.equals("1")) {
          result.append(String.format("%sandroid:scaleY=\"%s\"", delimiter, scaleY));
          delimiter = lineSeparator + INDENT + DOUBLE_INDENT;
        }
        if (translateX != null) {
          result.append(String.format("%sandroid:translateX=\"%s\"", delimiter, translateX));
          delimiter = lineSeparator + INDENT + DOUBLE_INDENT;
        }
        if (translateY != null) {
          result.append(String.format("%sandroid:translateY=\"%s\"", delimiter, translateY));
        }
        result.append('>');
        if (addClipPath) {
          // Clip to viewport since the new size may have space for more:
          result.append(lineSeparator).append(DOUBLE_INDENT);
          result.append(String.format("<clip-path android:pathData=\"M0,0 L0,%s L%s,%s L%s,0 z\"/>", clipY, clipX, clipY, clipX));
        }
        indent = INDENT;
      }

      // Copy contents before the </vector> tag.
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.END_TAG ||
             parser.getDepth() >= copyDepth) {
        int endLineNumber = parser.getLineNumber();
        int endColumnNumber = parser.getColumnNumber();
        indenter.copy(startLine, startColumn, endLineNumber, endColumnNumber, token == XmlPullParser.CDSECT ? "" : indent, result);
        startLine = endLineNumber;
        startColumn = endColumnNumber;
      }
      if (startColumn > INDENT.length() + 1) {
        result.append(lineSeparator);
        startColumn = 1;
      }
      if (adjustmentNeeded) {
        if (startColumn == 1) {
          result.append(INDENT);
        }
        result.append(String.format("</group>%s", lineSeparator));
      }

      // Copy the closing </group> tag, the </vector> tag and the remainder of the document.
      for (; token != XmlPullParser.END_DOCUMENT; token = parser.nextToken()) {
        int endLineNumber = parser.getLineNumber();
        int endColumnNumber = parser.getColumnNumber();
        indenter.copy(startLine, startColumn, endLineNumber, endColumnNumber, "", result);
        startLine = endLineNumber;
        startColumn = endColumnNumber;
      }

      return result.toString();
    }
    catch (XmlPullParserException | IOException e) {
      return originalDrawable;  // Ignore and return the original drawable.
    }
  }

  /**
   * Merges two vector drawables. The drawables must have identical width, height, viewport width and viewport height.
   *
   * @param drawable1 the first drawable to merge
   * @param drawable2 the second drawable to merge
   * @return the merged drawable
   */
  public static String merge(@NotNull String drawable1, @NotNull String drawable2) {
    KXmlParser parser = new KXmlParser();

    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(CharSequences.getReader(drawable1, true));
      int token;
      // Skip to the first tag.
      //noinspection StatementWithEmptyBody
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.START_TAG) {
      }
      if (parser.getEventType() != XmlPullParser.START_TAG || !"vector".equals(parser.getName()) || parser.getPrefix() != null) {
        return drawable1; // Not a vector drawable.
      }

      StringBuilder result = new StringBuilder(drawable1.length() + drawable2.length());

      Indenter indenter = new Indenter(drawable1);
      // Copy contents of the first drawable before the </vector> tag.
      int startLine = 1;
      int startColumn = 1;
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.END_TAG ||
             parser.getDepth() > 1) {
        int endLineNumber = parser.getLineNumber();
        int endColumnNumber = parser.getColumnNumber();
        indenter.copy(startLine, startColumn, endLineNumber, endColumnNumber, "", result);
        startLine = endLineNumber;
        startColumn = endColumnNumber;
      }

      parser.setInput(CharSequences.getReader(drawable2, true));
      //noinspection StatementWithEmptyBody
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.START_TAG) {
      }
      // Skip to the first tag.
      if (parser.getEventType() != XmlPullParser.START_TAG || !"vector".equals(parser.getName()) || parser.getPrefix() != null) {
        return drawable1; // Not a vector drawable.
      }
      startLine = parser.getLineNumber();
      startColumn = parser.getColumnNumber();

      indenter = new Indenter(drawable2);
      // Copy contents of the second drawable after the opening <vector> tag.
      while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
        int endLineNumber = parser.getLineNumber();
        int endColumnNumber = parser.getColumnNumber();
        indenter.copy(startLine, startColumn, endLineNumber, endColumnNumber, "", result);
        startLine = endLineNumber;
        startColumn = endColumnNumber;
      }

      return result.toString();
    }
    catch (XmlPullParserException | IOException e) {
      return drawable1;  // Ignore and return the original drawable.
    }
  }

  /**
   * Returns viewport size of the vector drawable, or null if the parameter is not a valid vector drawable.
   *
   * @param drawable XML text of a vector drawable
   * @return the viewport size of the drawable or null
   */
  @Nullable
  public static Point2D getViewportSize(@NotNull String drawable) {
    KXmlParser parser = new KXmlParser();

    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(CharSequences.getReader(drawable, true));
      // Skip to the first tag.
      int token;
      //noinspection StatementWithEmptyBody
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.START_TAG) {
      }
      if (parser.getEventType() != XmlPullParser.START_TAG || !"vector".equals(parser.getName()) || parser.getPrefix() != null) {
        return null; // Not a vector drawable.
      }
      double viewportWidth = getDoubleAttributeValue(parser, ANDROID_URI, "viewportWidth", "");
      double viewportHeight = getDoubleAttributeValue(parser, ANDROID_URI, "viewportHeight", "");
      return new Point2D.Double(viewportWidth, viewportHeight);
    }
    catch (XmlPullParserException | IOException e) {
      return null;  // Ignore and return null.
    }
  }

  /**
   * Returns size of the vector drawable in "dp", or null if the parameter is not a valid vector drawable,
   * or the width and height are not specified in "dp".
   *
   * @param drawable XML text of a vector drawable
   * @return the size of the drawable or null
   */
  @Nullable
  public static Dimension getSizeDp(@NotNull String drawable) {
    KXmlParser parser = new KXmlParser();

    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(CharSequences.getReader(drawable, true));
      // Skip to the first tag.
      int token;
      //noinspection StatementWithEmptyBody
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.START_TAG) {
      }
      if (parser.getEventType() != XmlPullParser.START_TAG || !"vector".equals(parser.getName()) || parser.getPrefix() != null) {
        return null; // Not a vector drawable.
      }
      String widthValue = parser.getAttributeValue(ANDROID_URI, "width");
      if (widthValue != null) {
        String suffix = getSuffix(widthValue);
        if (suffix.equals("dp")) {
          double width = getDoubleAttributeValue(parser, ANDROID_URI, "width", suffix);
          double height = getDoubleAttributeValue(parser, ANDROID_URI, "height", suffix);
          return new Dimension(Math.round((float)width), Math.round((float)height));
        }
      }
      return null;
    }
    catch (XmlPullParserException | IOException e) {
      return null;  // Ignore and return null.
    }
  }

  private static String detectLineSeparator(@NotNull CharSequence str) {
    LineSeparator separator = StringUtil.detectSeparators(str);
    if (separator != null) {
      return separator.getSeparatorString();
    }
    return CodeStyle.getDefaultSettings().getLineSeparator();
  }

  @SuppressWarnings("SameParameterValue")
  private static double getDoubleAttributeValue(@NotNull KXmlParser parser, @NotNull String namespaceUri, @NotNull String attributeName,
                                                @NotNull String expectedSuffix) {
    String value = parser.getAttributeValue(namespaceUri, attributeName);
    return parseDoubleValue(value, expectedSuffix);
  }

  private static double parseDoubleValue(String value, @NotNull String expectedSuffix) {
    if (value == null || !value.endsWith(expectedSuffix)) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(value.substring(0, value.length() - expectedSuffix.length()));
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  @NotNull
  private static String getSuffix(@NotNull String value) {
    int i = value.length();
    while (--i >= 0) {
      if (Character.isDigit(value.charAt(i))) {
        break;
      }
    }
    ++i;
    return value.substring(i);
  }

  private static boolean isSignificantlyDifferentFromZero(double value) {
    return Math.abs(value) >= 1.e-6;
  }

  private static class Indenter {
    private int myLine;
    private int myColumn;
    private int myOffset;
    private @NotNull final CharSequence myText;

    Indenter(@NotNull CharSequence text) {
      myText = text;
      myLine = 1;
      myColumn = 1;
    }

    void copy(int fromLine, int fromColumn, int toLine, int toColumn, @NotNull String indent, @NotNull StringBuilder out) {
      if (myLine != fromLine) {
        if (myLine > fromLine) {
          myLine = 1;
          myColumn = 1;
          myOffset = 0;
        }
        while (myLine < fromLine) {
          char c = myText.charAt(myOffset);
          if (c == '\n') {
            myLine++;
            myColumn = 1;
          } else {
            if (myLine != 1 || myColumn != 1 || c != '\uFEFF') {  // Byte order mark doesn't occupy a column.
              myColumn++;
            }
          }
          myOffset++;
        }
      }
      myOffset += fromColumn - myColumn;
      myColumn = fromColumn;
      while (myLine < toLine || myLine == toLine && myColumn < toColumn) {
        char c = myText.charAt(myOffset);
        if (c == '\n') {
          myLine++;
          myColumn = 1;
        } else {
          if (myLine != 1 || myColumn != 1 || c != '\uFEFF') {  // Byte order mark doesn't occupy a column.
            if (myColumn == 1 &&
                (c != '\r' || myOffset >= myText.length() || myText.charAt(myOffset + 1) != '\n')) { // Don't indent empty lines on Windows
              out.append(indent);
            }
            myColumn++;
          }
        }
        myOffset++;
        out.append(c);
      }
    }
  }
}
