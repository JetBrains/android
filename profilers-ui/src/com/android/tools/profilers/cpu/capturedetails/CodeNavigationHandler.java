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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.annotations.NonNull;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.idea.codenavigation.CodeNavigator;
import com.android.tools.profilers.FeatureConfig;
import com.android.tools.profilers.cpu.CaptureNode;
import com.intellij.ui.DoubleClickListener;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CodeNavigationHandler extends MouseAdapter {
  @NotNull private final HTreeChart<CaptureNode> myChart;
  @NonNull private final FeatureConfig myFeatureConfig;
  private Point myLastPopupPoint;

  public CodeNavigationHandler(@NotNull HTreeChart<CaptureNode> chart, @NotNull CodeNavigator navigator,
                               @NonNull FeatureConfig featureConfig) {
    myChart = chart;
    myFeatureConfig = featureConfig;
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        setLastPopupPoint(event);
        CodeLocation codeLocation = getCodeLocation();
        if (codeLocation != null) {
          navigator.navigate(codeLocation);
        }
        return false;
      }
    }.installOn(chart);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    handlePopup(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    handlePopup(e);
  }

  private void handlePopup(MouseEvent e) {
    if (e.isPopupTrigger()) {
      setLastPopupPoint(e);
    }
  }

  private void setLastPopupPoint(MouseEvent e) {
    myLastPopupPoint = e.getPoint();
  }

  @Nullable
  public CodeLocation getCodeLocation() {
    CaptureNode n = myChart.getNodeAt(myLastPopupPoint);
    if (n == null) {
      return null;
    }
    return ChartDetailsView.modelToCodeLocation(n.getData(), myFeatureConfig);
  }
}
