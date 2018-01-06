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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.chart.hchart.HRenderer;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.cpu.nodemodel.NativeNodeModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Specifies render characteristics (i.e. text and color) of {@link com.android.tools.adtui.chart.hchart.HTreeChart} nodes that represent
 * instances of {@link CaptureNodeModel}.
 */
public class CaptureNodeModelHRenderer implements HRenderer<CaptureNode> {

  private static final int LEFT_MARGIN_PX = 3;

  @NotNull
  private CaptureModel.Details.Type myType;

  public CaptureNodeModelHRenderer(@NotNull CaptureModel.Details.Type type) {
    super();
    if (type != CaptureModel.Details.Type.CALL_CHART && type != CaptureModel.Details.Type.FLAME_CHART) {
      throw new IllegalStateException("Chart type not supported and can't be rendered.");
    }
    myType = type;
  }

  @VisibleForTesting
  public CaptureNodeModelHRenderer() {
    this(CaptureModel.Details.Type.CALL_CHART);
  }

  private Color getFillColor(CaptureNode node) {
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof JavaMethodModel) {
      return JavaMethodHChartColors.getFillColor(nodeModel, myType, node.isUnmatched());
    }
    else if (nodeModel instanceof NativeNodeModel) {
      return NativeModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched());
    }
    throw new IllegalStateException("Node type not supported.");
  }

  private Color getBorderColor(CaptureNode node) {
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof JavaMethodModel) {
      return JavaMethodHChartColors.getBorderColor(nodeModel, myType, node.isUnmatched());
    }
    else if (nodeModel instanceof NativeNodeModel) {
      return NativeModelHChartColors.getBorderColor(nodeModel, myType, node.isUnmatched());
    }
    throw new IllegalStateException("Node type not supported.");
  }

  /**
   * @return the same color but with 20% opacity that is used to represent an unmatching node.
   */
  @NotNull
  static Color toUnmatchColor(@NotNull Color color) {
    // TODO: maybe cache for performance?
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * 0.2));
  }

  @Override
  public void render(@NotNull Graphics2D g, @NotNull CaptureNode node, @NotNull Rectangle2D drawingArea, boolean isFocused) {
    // Draw rectangle background
    CaptureNode captureNode = node;
    Color nodeColor = getFillColor(captureNode);
    if (isFocused) {
      // All colors we use in call and flame charts are pretty bright, so darkening them works as an effective highlight
      nodeColor = ColorUtil.darker(nodeColor, 2);
    }
    g.setPaint(nodeColor);
    g.fill(drawingArea);

    // Draw rectangle outline.
    g.setPaint(getBorderColor(captureNode));
    g.draw(drawingArea);

    // Draw text
    Font font = g.getFont();
    FontMetrics fontMetrics = g.getFontMetrics(font);
    if (captureNode.getFilterType() == CaptureNode.FilterType.MATCH) {
      g.setPaint(Color.BLACK);
    }
    else if (captureNode.getFilterType() == CaptureNode.FilterType.UNMATCH) {
      g.setPaint(toUnmatchColor(Color.BLACK));
    }
    else {
      g.setPaint(Color.BLACK);
      g.setFont(font.deriveFont(Font.BOLD));
    }
    String text = generateFittingText(node.getData(), drawingArea, g.getFontMetrics());
    float textPositionX = LEFT_MARGIN_PX + (float)drawingArea.getX();
    float textPositionY = (float)(drawingArea.getY() + fontMetrics.getAscent());
    g.drawString(text, textPositionX, textPositionY);

    g.setFont(font);
  }

  /**
   * Find the best text for the given rectangle constraints.
   */
  private static String generateFittingText(CaptureNodeModel model, Rectangle2D rect, FontMetrics fontMetrics) {
    double maxWidth = rect.getWidth() - LEFT_MARGIN_PX;

    String classOrNamespace = "";
    String separator = "";
    if (model instanceof CppFunctionModel) {
      classOrNamespace = ((CppFunctionModel)model).getClassOrNamespace();
      separator = "::";
    }
    else if (model instanceof JavaMethodModel) {
      classOrNamespace = ((JavaMethodModel)model).getClassName();
      separator = ".";
    }

    if (!separator.isEmpty() && !classOrNamespace.isEmpty()) {
      // Try: java.lang.String.toString. Add the separator between class name (or namespace) and method/function name.
      String fullyQualified = classOrNamespace + separator + model.getName();
      if (fontMetrics.stringWidth(fullyQualified) < maxWidth) {
        return fullyQualified;
      }

      // Try: j.l.s.toString
      String shortPackage = getShortPackageName(classOrNamespace, separator);
      String abbrevPackage = shortPackage + separator + model.getName();
      if (fontMetrics.stringWidth(abbrevPackage) < maxWidth) {
        return abbrevPackage;
      }
    }

    // Try: toString or t...
    return AdtUiUtils.getFittedString(fontMetrics, model.getName(), (float)maxWidth, 1);
  }

  /**
   * Reduces the size of a namespace by using only the first character of each name separated by the namespace separator.
   * e.g. "art::interpreter -> a::i" and "java.util.List" -> j.u.L"
   * TODO (b/67640322): try a less aggressive string first (e.g. j.u.List or a::interpreter)
   */
  private static String getShortPackageName(String namespace, String separator) {
    if (StringUtil.isEmpty(namespace)) {
      return "";
    }

    // String#split receives a regex. As "." is a special regex character, we need to handle the case.
    String splitterSeparator = separator.equals(".") ? "\\." : separator;
    String[] elements = namespace.split(splitterSeparator);

    StringBuilder b = new StringBuilder();
    for (int i = 0; i < elements.length - 1; i++) {
      b.append(elements[i].charAt(0));
      b.append(separator);
    }
    b.append(elements[elements.length - 1].charAt(0));

    return b.toString();
  }
}
