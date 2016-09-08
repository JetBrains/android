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
package com.android.tools.adtui.chart.hchart;

import java.awt.*;
import java.awt.geom.Rectangle2D;

public class NativeMethodUsageHRenderer extends MethodUsageHRenderer {

  /**
   * Find the best text for the given rectangle constraints.
   */
  @Override
  protected String generateFittingText(MethodUsage method, Rectangle2D rect, FontMetrics fontMetrics) {
    String fullyQualified = method.getNameSpace() + Separators.NATIVE_CODE + method.getName();
    if (fontMetrics.stringWidth(fullyQualified) < rect.getWidth()) {
      return fullyQualified;
    }

    if (fontMetrics.stringWidth(method.getName()) < rect.getWidth()) {
      return method.getName();
    }

    return "";
  }
}
