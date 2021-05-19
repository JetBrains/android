/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.chart.hchart.HRenderer;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.DataVisualizationColors;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.cpu.nodemodel.NativeNodeModel;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.android.tools.profilers.cpu.nodemodel.SystemTraceNodeModel;
import com.google.common.annotations.VisibleForTesting;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Specifies render characteristics (i.e. text and color) of {@link com.android.tools.adtui.chart.hchart.HTreeChart} nodes that represent
 * instances of {@link CaptureNode}.
 */
public class CaptureNodeHRenderer implements HRenderer<CaptureNode> {

  private static final int MARGIN_PX = 3; // Padding on left and right of node label

  @NotNull
  private CaptureDetails.Type myType;

  @NotNull
  private TextFitsPredicate myTextFitsPredicate;

  @VisibleForTesting
  CaptureNodeHRenderer(@NotNull CaptureDetails.Type type, @NotNull TextFitsPredicate textFitPredicate) {
    if (type != CaptureDetails.Type.CALL_CHART && type != CaptureDetails.Type.FLAME_CHART) {
      throw new IllegalStateException("Chart type not supported and can't be rendered.");
    }
    myType = type;
    myTextFitsPredicate = textFitPredicate;
  }

  @VisibleForTesting
  public CaptureNodeHRenderer() {
    this(CaptureDetails.Type.CALL_CHART);
  }

  public CaptureNodeHRenderer(@NotNull CaptureDetails.Type type) {
    this(type, (text, metrics, width, height) -> metrics.stringWidth(text) <= width && metrics.getHeight() <= height);
  }

  private Color getFillColor(CaptureNode node, boolean isFocused, boolean isDeselected) {
    // TODO (b/74349846): Change this function to use a binder base on CaptureNode.
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof JavaMethodModel) {
      return JavaMethodHChartColors.getFillColor(nodeModel, myType, node.isUnmatched(), isFocused, isDeselected);
    }
    else if (nodeModel instanceof NativeNodeModel) {
      return NativeModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched(), isFocused, isDeselected);
    }
    // AtraceNodeModel is a SingleNameModel as such this check needs to happen before SingleNameModel check.
    else if (nodeModel instanceof SystemTraceNodeModel) {
      return SystemTraceNodeModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched(), isFocused, isDeselected);
    }
    else if (nodeModel instanceof SingleNameModel) {
      return SingleNameModelHChartColors.getFillColor(nodeModel, myType, node.isUnmatched(), isFocused, isDeselected);
    }
    throw new IllegalStateException("Node type not supported.");
  }

  private Color getIdleCpuColor(CaptureNode node, boolean isFocused, boolean isDeselected) {
    // TODO (b/74349846): Change this function to use a binder base on CaptureNode.

    // The only nodes that actually show idle time are the atrace nodes. As such they are the only ones,
    // that return a custom color for the idle cpu time.
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof SystemTraceNodeModel) {
      return SystemTraceNodeModelHChartColors.getIdleCpuColor(nodeModel, myType, node.isUnmatched(), isFocused, isDeselected);
    }
    return getFillColor(node, isFocused, isDeselected);
  }

  private Color getTextColor(CaptureNode node, boolean isDeselected) {
    CaptureNodeModel nodeModel = node.getData();
    if (nodeModel instanceof SystemTraceNodeModel) {
      return SystemTraceNodeModelHChartColors.getTextColor(nodeModel, myType, isDeselected);
    }
    return DataVisualizationColors.DEFAULT_DARK_TEXT_COLOR;
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
  public void render(@NotNull Graphics2D g,
                     @NotNull CaptureNode node,
                     @NotNull Rectangle2D fullDrawingArea,
                     @NotNull Rectangle2D drawingArea,
                     boolean isFocused,
                     boolean isDeselected) {
    // Draw rectangle background
    CaptureNode captureNode = node;
    CaptureNodeModel nodeModel = node.getData();
    Color nodeColor = getFillColor(captureNode, isFocused, isDeselected);
    Color idleColor = getIdleCpuColor(captureNode, isFocused, isDeselected);
    g.setPaint(nodeColor);
    g.fill(drawingArea);

    double clockLength = node.getEnd() - node.getStart();
    double threadLength = node.getEndThread() - node.getStartThread();
    // If we have a difference in our clock length and thread time that means we have idle time.
    // For now we only do this for node that have an atrace capture node model.
    if (nodeModel instanceof SystemTraceNodeModel && threadLength > 0 && clockLength - threadLength > 0) {
      // Idle time width is 1 minus the  ratio of clock time, and thread time.
      double ratio = 1 - (threadLength / clockLength);
      double idleTimeWidth = ratio * fullDrawingArea.getWidth();
      // The Idle time is drawn at the end of our total time, as such we start at our width minus our idle time.
      double startPosition = fullDrawingArea.getX() + fullDrawingArea.getWidth() - idleTimeWidth;
      // Need to remove the clampped start from our width when normalizing our width else we end up with the wrong width.
      double clamppedStart = Math.min(drawingArea.getWidth() + drawingArea.getX(), Math.max(0, startPosition));
      // The minimum of our clamped areas ending position and our idle time ending position.
      double clampedWidth =
        Math.max(0, (drawingArea.getX() + drawingArea.getWidth()) - clamppedStart);
      g.setPaint(idleColor);
      g.fill(new Rectangle2D.Double(clamppedStart,
                                    fullDrawingArea.getY(),
                                    clampedWidth,
                                    fullDrawingArea.getHeight()));
    }

    // Draw text
    Font font = g.getFont();
    Font restoreFont = font;
    Color textColor = getTextColor(node, isDeselected);
    if (captureNode.getFilterType() == CaptureNode.FilterType.MATCH) {
      g.setPaint(textColor);
    }
    else if (captureNode.getFilterType() == CaptureNode.FilterType.UNMATCH) {
      g.setPaint(toUnmatchColor(textColor));
    }
    else {
      g.setPaint(textColor);
      font = font.deriveFont(Font.BOLD);
      g.setFont(font);
    }
    FontMetrics fontMetrics = g.getFontMetrics(font);

    float availableWidth = (float)drawingArea.getWidth() - 2 * MARGIN_PX; // Left and right margin
    float availableHeight = (float)drawingArea.getHeight();
    // Testing text fit can be slow from calling FontMetrics#stringWidth, so skip the calculation here if [availableWidth] is sufficiently
    // small.
    if (availableWidth > 1.0f) {
      String text = generateFittingText(
        node.getData(), s -> myTextFitsPredicate.test(s, fontMetrics, availableWidth, availableHeight));
      float textPositionX = MARGIN_PX + (float)drawingArea.getX();
      float textPositionY = (float)(drawingArea.getY() + fontMetrics.getAscent());
      g.drawString(text, textPositionX, textPositionY);
    }

    g.setFont(restoreFont);
  }

  /**
   * Find the best text for the given rectangle constraints.
   */
  private static String generateFittingText(CaptureNodeModel model, Predicate<String> textFitsPredicate) {
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
      // String#split receives a regex. As "." is a special regex character, we need to handle the case.
      String[] classElements = classOrNamespace.split(separator.equals(".") ? "\\." : separator);

      // Try abbreviating a few first packages, e.g for "java.lang.String.toString" try in this order ->
      // "java.lang.String.toString", "j.lang.String.toString", "j.l.String.toString", "j.l.S.toString"
      for (int abbreviationCount = 0; abbreviationCount <= classElements.length; ++abbreviationCount) {
        // Try to reduce by using abbreviations for first |abbreviationCount| classElements.
        StringBuilder textBuilder = new StringBuilder();
        for (int i = 0; i < classElements.length; ++i) {
          // Occasionally, profiling tools can return a class path like "..ABC.X", so we need to be
          // able to handle empty classElements.
          if (i < abbreviationCount && !classElements[i].isEmpty()) {
            textBuilder.append(classElements[i].charAt(0));
          }
          else {
            textBuilder.append(classElements[i]);
          }
          textBuilder.append(separator);
        }
        textBuilder.append(model.getName());
        String text = textBuilder.toString();
        if (textFitsPredicate.test(text)) {
          return text;
        }
      }
    }

    return AdtUiUtils.shrinkToFit(model.getName(), textFitsPredicate);
  }

  public interface TextFitsPredicate {
    boolean test(String text, FontMetrics metrics, float width, float height);
  }
}
