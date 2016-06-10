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

package com.android.tools.idea.npw.assetstudio.assets;

import com.android.SdkConstants;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.PixelProbe;
import com.android.tools.pixelprobe.ShapeInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a layered image from a file and converts it to a Vector Drawable XML representation.
 */
class LayeredImageConverter {
  private LayeredImageConverter() {
  }

  /**
   * Loads the specified file as a pixelprobe {@link Image}, finds its vector layers
   * and converts them to a Vector Drawable XML representation.
   *
   * @param path The file to convert to Vector Drawable
   * @return The XML representation of a Vector Drawable
   *
   * @throws IOException If an error occur while parsing the file
   */
  @NotNull
  static String toVectorDrawableXml(@NotNull File path) throws IOException {
    FileInputStream in = new FileInputStream(path);
    Image image = PixelProbe.probe(in);

    // Find the total bounds of all the vector layers
    Rectangle2D bounds = new Rectangle2D.Double();
    extractBounds(image.getLayers(), bounds);

    Rectangle2D.Double document = new Rectangle2D.Double(0.0, 0.0, image.getWidth(), image.getHeight());
    bounds = bounds.createIntersection(document);

    DecimalFormat format = createDecimalFormat((float) bounds.getWidth(), (float) bounds.getHeight());

    Element vector = new Element(SdkConstants.TAG_VECTOR);
    extractPathLayers(vector, image.getLayers(), bounds, format);

    vector
      .attribute("width", String.valueOf((int) bounds.getWidth()) + "dp")
      .attribute("height", String.valueOf((int) bounds.getHeight()) + "dp")
      .attribute("viewportWidth", String.valueOf((int) bounds.getWidth()))
      .attribute("viewportHeight", String.valueOf((int) bounds.getHeight()));

    String xml = toVectorDrawable(vector);
    in.close();
    return xml;
  }

  /**
   * Finds all vector layers and accumulate their bounds in the specified rectangle.
   *
   * @param layers List of layers to iterate
   * @param bounds Total bounds of all vector layers
   */
  private static void extractBounds(@NotNull List<Layer> layers, @NotNull Rectangle2D bounds) {
    for (Layer layer : layers) {
      if (!layer.isVisible()) continue;

      Layer.Type type = layer.getType();
      if (type == Layer.Type.SHAPE) {
        Rectangle2D layerBounds = layer.getBounds();
        if (bounds.isEmpty()) {
          bounds.setRect(layerBounds);
        } else {
          bounds.add(layerBounds);
        }
      }
      else if (type == Layer.Type.GROUP) {
        extractBounds(layer.getChildren(), bounds);
      }
    }
  }

  /**
   * Extracts all the vector layers from the specified list and transforms them into an
   * XML representation.
   *  @param root Root element of the Vector Drawable XML representation
   * @param layers List of layers to traverse
   * @param bounds Total bounds of all vector layers
   * @param format
   */
  private static void extractPathLayers(@NotNull Element root, @NotNull List<Layer> layers,
                                        @NotNull Rectangle2D bounds, @NotNull DecimalFormat format) {
    for (int i = 0; i < layers.size(); i++) {
      Layer layer = layers.get(i);
      if (!layer.isVisible()) continue;

      Layer.Type type = layer.getType();
      if (type == Layer.Type.SHAPE) {
        ShapeInfo shapeInfo = layer.getShapeInfo();
        if (shapeInfo.getStyle() == ShapeInfo.Style.NONE) continue;

        Shape path = getTransformedPath(layer, bounds);

        float opacityModifier = 1.0f;
        boolean fullyClipped = false;

        // The layer is clipped by the next clipping base
        // We only support shape clipping bases
        if (!layer.isClipBase()) {
          // The clipping base is only valid in the current group
          // (it might be another group)
          for (int j = i + 1; j < layers.size(); j++) {
            Layer clipBase = layers.get(j);
            if (clipBase.isClipBase()) {
              if (!clipBase.isVisible()) {
                fullyClipped = true;
                break;
              }

              // TODO: handle group clipping bases (take all their shapes)
              if (clipBase.getType() != Layer.Type.SHAPE) {
                break;
              }

              opacityModifier = clipBase.getOpacity();

              Area source = new Area(path);
              Area clip = new Area(getTransformedPath(clipBase, bounds));
              source.intersect(clip);
              path = source;

              break;
            }
          }
        }

        if (!fullyClipped) {
          Element element = new Element("path")
            .attribute("name", StringUtil.escapeXml(layer.getName()))
            .attribute("pathData", toPathData(path, format));

          extractFill(layer, shapeInfo, element, opacityModifier);
          extractStroke(layer, shapeInfo, element, opacityModifier);

          root.childAtFront(element);
        }
      }
      else if (type == Layer.Type.GROUP) {
        extractPathLayers(root, layer.getChildren(), bounds, format);
      }
    }
  }

  @NotNull
  private static Path2D getTransformedPath(Layer layer, @NotNull Rectangle2D bounds) {
    Path2D path = layer.getShapeInfo().getPath();
    Rectangle2D layerBounds = layer.getBounds();
    path.transform(AffineTransform.getTranslateInstance(
      layerBounds.getX() - bounds.getX(), layerBounds.getY() - bounds.getY()));
    return path;
  }

  private static void extractStroke(Layer layer, ShapeInfo shapeInfo, Element element, float opacityModifier) {
    if (shapeInfo.getStyle() != ShapeInfo.Style.FILL) {
      Paint strokePaint = shapeInfo.getStrokePaint();
      //noinspection UseJBColor
      Color color = Color.BLACK;
      if (strokePaint instanceof Color) color = (Color)strokePaint;
      float strokeAlpha = layer.getOpacity() * shapeInfo.getStrokeOpacity() * opacityModifier;

      element
        .attribute("strokeColor", "#" + ColorUtil.toHex(color))
        .attribute("strokeAlpha", String.valueOf(strokeAlpha));

      if (shapeInfo.getStroke() instanceof BasicStroke) {
        BasicStroke stroke = (BasicStroke)shapeInfo.getStroke();
        element
          .attribute("strokeWidth", String.valueOf(stroke.getLineWidth()))
          .attribute("strokeLineJoin", getJoinValue(stroke.getLineJoin()))
          .attribute("strokeLineCap", getCapValue(stroke.getEndCap()));
      } else {
        element.attribute("strokeWidth", String.valueOf(0.0f));
      }
    }
  }

  private static void extractFill(Layer layer, ShapeInfo shapeInfo, Element element, float opacityModifier) {
    if (shapeInfo.getStyle() != ShapeInfo.Style.STROKE) {
      Paint fillPaint = shapeInfo.getFillPaint();
      //noinspection UseJBColor
      Color color = Color.BLACK;
      if (fillPaint instanceof Color) color = (Color)fillPaint;
      float fillAlpha = layer.getOpacity() * shapeInfo.getFillOpacity() * opacityModifier;

      element
        .attribute("fillColor", "#" + ColorUtil.toHex(color))
        .attribute("fillAlpha", String.valueOf(fillAlpha));
    }
  }

  private static String getCapValue(int endCap) {
    switch (endCap) {
      case BasicStroke.CAP_BUTT: return "butt";
      case BasicStroke.CAP_ROUND: return "round";
      case BasicStroke.CAP_SQUARE: return "square";
    }
    return "inherit";
  }

  private static String getJoinValue(int lineJoin) {
    switch (lineJoin) {
      case BasicStroke.JOIN_BEVEL: return "bevel";
      case BasicStroke.JOIN_ROUND: return "round";
      case BasicStroke.JOIN_MITER: return "miter";
    }
    return "inherit";
  }

  @NotNull
  private static String toVectorDrawable(@NotNull Element element) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter out = new PrintWriter(stringWriter);
    outputElement(element, out, true, 0);
    out.flush();
    return stringWriter.toString();
  }

  private static void outputElement(@NotNull Element element, @NotNull PrintWriter out, boolean isRoot, int indent) {
    indent(out, indent);
    out.write("<");
    out.write(element.name);
    if (isRoot) out.write(" xmlns:android=\"http://schemas.android.com/apk/res/android\"");
    out.write("\n");

    boolean hasChildren = element.children.size() > 0;

    indent++;
    outputAttributes(element, out, indent);
    if (hasChildren) {
      out.write(">\n");
      outputChildren(element, out, indent);
    } else {
      out.write(" />");
    }
    indent--;

    if (hasChildren) {
      indent(out, indent);
      out.write("</");
      out.write(element.name);
      out.write(">");
    }
    out.write("\n");
  }

  private static void outputChildren(@NotNull Element element, @NotNull PrintWriter out, int indent) {
    for (Element child : element.children) {
      outputElement(child, out, false, indent);
    }
  }

  private static void outputAttributes(@NotNull Element element, @NotNull PrintWriter out, int indent) {
    List<Attribute> attributes = element.attributes;
    int size = attributes.size();

    for (int i = 0; i < size; i++) {
      Attribute attribute = attributes.get(i);
      indent(out, indent);
      out.write("android:");
      out.write(attribute.name);
      out.write("=\"");
      out.write(attribute.value);
      out.write("\"");
      if (i != size - 1) out.write("\n");
    }
  }

  private static void indent(@NotNull PrintWriter out, int indent) {
    for (int i = 0; i < indent; i++) {
      out.write("    ");
    }
  }

  @NotNull
  private static String toPathData(@NotNull Shape path, @NotNull DecimalFormat format) {
    StringBuilder buffer = new StringBuilder(1024);

    float[] coords = new float[6];
    PathIterator iterator = path.getPathIterator(new AffineTransform());

    while (!iterator.isDone()) {
      int segment = iterator.currentSegment(coords);
      switch (segment) {
        case PathIterator.SEG_MOVETO:
          buffer.append('M');
          buffer.append(cleanup(coords[0], format));
          buffer.append(',');
          buffer.append(cleanup(coords[1], format));
          break;
        case PathIterator.SEG_LINETO:
          buffer.append('L');
          buffer.append(cleanup(coords[0], format));
          buffer.append(',');
          buffer.append(cleanup(coords[1], format));
          break;
        case PathIterator.SEG_CUBICTO:
          buffer.append('C');
          buffer.append(cleanup(coords[0], format));
          buffer.append(',');
          buffer.append(cleanup(coords[1], format));
          buffer.append(' ');
          buffer.append(cleanup(coords[2], format));
          buffer.append(',');
          buffer.append(cleanup(coords[3], format));
          buffer.append(' ');
          buffer.append(cleanup(coords[4], format));
          buffer.append(',');
          buffer.append(cleanup(coords[5], format));
          break;
        case PathIterator.SEG_QUADTO:
          buffer.append('Q');
          buffer.append(cleanup(coords[0], format));
          buffer.append(',');
          buffer.append(cleanup(coords[1], format));
          buffer.append(' ');
          buffer.append(cleanup(coords[2], format));
          buffer.append(',');
          buffer.append(cleanup(coords[3], format));
          break;
        case PathIterator.SEG_CLOSE:
          buffer.append('Z');
          break;
      }

      iterator.next();
      if (!iterator.isDone()) buffer.append(' ');
    }

    return buffer.toString();
  }

  @NotNull
  private static DecimalFormat createDecimalFormat(float viewportWidth, float viewportHeight) {
    float minSize = Math.min(viewportHeight, viewportWidth);
    float exponent = Math.round(Math.log10(minSize));

    int decimalPlace = (int) Math.floor(exponent - 4);
    String decimalFormatString = "#";
    if (decimalPlace < 0) {
      // Build a string with decimal places for "#.##...", and cap on 6 digits.
      if (decimalPlace < -6) {
        decimalPlace = -6;
      }
      decimalFormatString += ".";
      for (int i = 0 ; i < -decimalPlace; i++) {
        decimalFormatString += "#";
      }
    }

    DecimalFormatSymbols fractionSeparator = new DecimalFormatSymbols();
    fractionSeparator.setDecimalSeparator('.');

    DecimalFormat decimalFormat = new DecimalFormat(decimalFormatString, fractionSeparator);
    decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

    return decimalFormat;
  }

  @NotNull
  private static String cleanup(float value, @NotNull DecimalFormat format) {
    if (value == (long) value) {
      return String.valueOf((long) value);
    } else {
      return format.format(value);
    }
  }

  private static class Attribute {
    final String name;
    final String value;

    Attribute(@NotNull String name, @NotNull String value) {
      this.name = name;
      this.value = value;
    }
  }

  private static class Element {
    final String name;
    final List<Element> children = new ArrayList<>();
    final List<Attribute> attributes = new ArrayList<>();

    Element(@NotNull String name) {
      this.name = name;
    }

    Element attribute(@NotNull String name, @NotNull String value) {
      attributes.add(new Attribute(name, value));
      return this;
    }

    Element childAtFront(@NotNull Element child) {
      children.add(0, child);
      return this;
    }
  }
}
