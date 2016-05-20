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
package com.android.tools.adtui;

import com.android.tools.adtui.common.AdtUIUtils;
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A component that draws grid lines within the given dimension. The grid lines correspond to the
 * tick markers of the {@link AxisComponent} that were added to this grid.
 */
public final class GridComponent extends AnimatedComponent {

  @NotNull
  private List<AxisComponent> mAxes;

  public GridComponent() {
    mAxes = new ArrayList<>();
  }

  public void addAxis(AxisComponent axis) {
    mAxes.add(axis);
  }

  @Override
  protected void updateData() {
  }

  @Override
  protected void draw(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(AdtUIUtils.GRID_COLOR);

    Dimension dim = getSize();
    Line2D.Float line = new Line2D.Float();
    for (final AxisComponent axis : mAxes) {
      TFloatArrayList markers = axis.getMajorMarkerPositions();
      switch (axis.getOrientation()) {
        case LEFT:
        case RIGHT:
          for (int j = 0; j < markers.size(); j++) {
            line.setLine(0,
                         (dim.height - 1) * (1 - markers.get(j)),
                         dim.width - 1,
                         (dim.height - 1) * (1 - markers.get(j)));
            g.draw(line);
          }
          break;
        case TOP:
        case BOTTOM:
          for (int j = 0; j < markers.size(); j++) {
            line.setLine((dim.width - 1) * markers.get(j),
                         0,
                         (dim.width - 1) * markers.get(j),
                         dim.height - 1);
            g.draw(line);
          }
          break;
      }
    }
  }
}
