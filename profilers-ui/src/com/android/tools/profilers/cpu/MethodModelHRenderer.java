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
import com.android.tools.adtui.model.HNode;
import com.android.tools.profilers.ProfilerColors;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Specifies render characteristics (i.e. text and color) of {@link com.android.tools.adtui.chart.hchart.HTreeChart} nodes that represent
 * instances of {@link MethodModel}.
 */
public class MethodModelHRenderer implements HRenderer<MethodModel> {

  private static final int LEFT_MARGIN_PX = 3;

  @NotNull
  private CaptureModel.Details.Type myType;

  public MethodModelHRenderer(@NotNull CaptureModel.Details.Type type) {
    super();
    myType = type;
  }

  @VisibleForTesting
  public MethodModelHRenderer() {
    this(CaptureModel.Details.Type.CALL_CHART);
  }

  private static boolean isMethodPlatform(MethodModel method) {
    return isMethodPlatformJava(method) || isMethodPlatformCpp(method);
  }

  private static boolean isMethodPlatformJava(MethodModel method) {
    return method.getClassOrNamespace().startsWith("android.") || method.getClassOrNamespace().startsWith("com.android.");
  }

  private static boolean isMethodPlatformCpp(MethodModel method) {
    // TODO: include all the art-related methods (e.g. artQuickToInterpreterBridge and artMterpAsmInstructionStart)
    return method.getFullName().startsWith("art::") ||
           method.getFullName().startsWith("android::") ||
           method.getFullName().startsWith("art_") ||
           method.getFullName().startsWith("dalvik-jit-code-cache");
  }

  private static boolean isMethodVendor(MethodModel method) {
    return isMethodVendorJava(method) || isMethodVendorCpp(method);
  }

  private static boolean isMethodVendorJava(MethodModel method) {
    return method.getClassOrNamespace().startsWith("java.") ||
           method.getClassOrNamespace().startsWith("sun.") ||
           method.getClassOrNamespace().startsWith("javax.") ||
           method.getClassOrNamespace().startsWith("apple.") ||
           method.getClassOrNamespace().startsWith("com.apple.");
  }

  private static boolean isMethodVendorCpp(MethodModel method) {
    return method.getFullName().startsWith("openjdkjvmti::");
  }

  private Color getFillColor(CaptureNode node) {
    Color color;
    if (myType == CaptureModel.Details.Type.CALL_CHART) {
      if (isMethodVendor(node.getMethodModel())) {
        color = ProfilerColors.CPU_CALLCHART_VENDOR;
      }
      else if (isMethodPlatform(node.getMethodModel())) {
        color = ProfilerColors.CPU_CALLCHART_PLATFORM;
      }
      else {
        color = ProfilerColors.CPU_CALLCHART_APP;
      }
    }
    else {
      if (isMethodVendor(node.getMethodModel())) {
        color = ProfilerColors.CPU_FLAMECHART_VENDOR;
      }
      else if (isMethodPlatform(node.getMethodModel())) {
        color = ProfilerColors.CPU_FLAMECHART_PLATFORM;
      }
      else {
        color = ProfilerColors.CPU_FLAMECHART_APP;
      }
    }

    return node.isUnmatched() ? toUnmatchColor(color) : color;
  }

  private Color getBordColor(CaptureNode node) {
    Color color;
    if (myType == CaptureModel.Details.Type.CALL_CHART) {
      if (isMethodVendor(node.getMethodModel())) {
        color = ProfilerColors.CPU_CALLCHART_VENDOR_BORDER;
      }
      else if (isMethodPlatform(node.getMethodModel())) {
        color = ProfilerColors.CPU_CALLCHART_PLATFORM_BORDER;
      }
      else {
        color = ProfilerColors.CPU_CALLCHART_APP_BORDER;
      }
    }
    else {
      if (isMethodVendor(node.getMethodModel())) {
        color = ProfilerColors.CPU_FLAMECHART_VENDOR_BORDER;
      }
      else if (isMethodPlatform(node.getMethodModel())) {
        color = ProfilerColors.CPU_FLAMECHART_PLATFORM_BORDER;
      }
      else {
        color = ProfilerColors.CPU_FLAMECHART_APP_BORDER;
      }
    }
    return node.isUnmatched() ? toUnmatchColor(color) : color;
  }

  /**
   * @return the same color but with 20% opacity that is used to represent an unmatching node.
   */
  @NotNull
  private static Color toUnmatchColor(@NotNull Color color) {
    // TODO: maybe cache for performance?
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * 0.2));
  }

  @Override
  public void render(@NotNull Graphics2D g, @NotNull HNode<MethodModel> node, @NotNull Rectangle2D drawingArea) {
    // Draw rectangle background
    CaptureNode captureNode = (CaptureNode)node;
    g.setPaint(getFillColor(captureNode));
    g.fill(drawingArea);

    // Draw rectangle outline.
    g.setPaint(getBordColor(captureNode));
    g.draw(drawingArea);

    // Draw text
    Font font = g.getFont();
    FontMetrics fontMetrics = g.getFontMetrics(font);
    if (captureNode.getFilterType() == CaptureNode.FilterType.UNMATCH) {
      g.setPaint(toUnmatchColor(Color.BLACK));
    } else if (captureNode.getFilterType() == CaptureNode.FilterType.MATCH) {
      g.setPaint(Color.BLACK);
    } else {
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
  private String generateFittingText(MethodModel node, Rectangle2D rect, FontMetrics fontMetrics) {
    String methodSeparator = node.getSeparator();

    double maxWidth = rect.getWidth() - LEFT_MARGIN_PX;
    // Try: java.lang.String.toString. Add a "." separator between class name and method name.
    // Native methods (e.g. clock_gettime) don't have a class name and, therefore, we don't add a "." before them.
    String separator = StringUtil.isEmpty(node.getClassOrNamespace()) ? "" : methodSeparator;
    String fullyQualified = node.getClassOrNamespace() + separator + node.getName();
    if (fontMetrics.stringWidth(fullyQualified) < maxWidth) {
      return fullyQualified;
    }

    // Try: j.l.s.toString
    String shortPackage = getShortPackageName(node.getClassOrNamespace(), methodSeparator);
    separator = StringUtil.isEmpty(shortPackage) ? "" : methodSeparator;
    String abbrevPackage = shortPackage + separator + node.getName();
    if (fontMetrics.stringWidth(abbrevPackage) < maxWidth) {
      return abbrevPackage;
    }

    String name = node.getName();
    // Try: toString
    if (fontMetrics.stringWidth(name) < maxWidth) {
      return name;
    }

    // Try: t...
    return AdtUiUtils.getFittedString(fontMetrics, name, (float)maxWidth, 1);
  }

  /**
   * Reduces the size of a namespace by using only the first character of each name separated by the namespace separator.
   * e.g. "art::interpreter -> a::i" and "java.util.List" -> j.u.L"
   * TODO (b/67640322): try a less agressive string first (e.g. j.u.List or a::interpreter)
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
