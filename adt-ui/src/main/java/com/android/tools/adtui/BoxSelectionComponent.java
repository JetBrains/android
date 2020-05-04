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
package com.android.tools.adtui;

import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.RangeSelectionModel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;

/**
 * A component that supports box selection backed by a {@link RangeSelectionModel} and {@link JList}.
 * <p>
 *       --------------------
 *       |     +++++++      |
 *       ------+-----+-------
 * JList |     + box +      |
 *       ------+-----+-------
 *       |     +++++++      |
 *       --------------------
 *       |<---- Range ----->|
 */
public class BoxSelectionComponent extends RangeSelectionComponent {

  @NotNull private final JList<?> myList;

  /**
   * @param model model that handles the range selection.
   * @param list  the list that the box selection component sits on top of.
   */
  public BoxSelectionComponent(@NotNull RangeSelectionModel model, @NotNull JList<?> list) {
    super(model);
    myList = list;
  }

  @Override
  protected void draw(Graphics2D g, Dimension size) {
    if (getModel().getSelectionRange().isEmpty() || myList.isSelectionEmpty()) {
      return;
    }
    int startX = (int)rangeToX(getModel().getSelectionRange().getMin(), size.getWidth());
    int endX = (int)rangeToX(getModel().getSelectionRange().getMax(), size.getWidth());
    Rectangle selectedCellBounds = myList.getCellBounds(myList.getMinSelectionIndex(), myList.getMaxSelectionIndex());
    int startY = selectedCellBounds.y;
    int endY = (int)selectedCellBounds.getMaxY();

    // Draw selection box.
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(StudioColorsKt.getSelectionBackground());
    g.drawRect(startX, startY, endX - startX, endY - startY);

    // Gray out the deselected area.
    // ------------------------
    // |         T            |
    // |   +++++++++++++++    |
    // | L +selection box+  R |
    // |   +++++++++++++++    |
    // |         B            |
    // ------------------------
    g.setColor(StudioColorsKt.getInactiveSelectionOverlayBackground());
    // Left
    if (startX > 0) {
      g.fillRect(0, 0, startX, getHeight());
    }
    // Right
    if (endX < size.getWidth()) {
      g.fillRect(endX, 0, getWidth() - endX, getHeight());
    }
    // Top
    if (myList.getMinSelectionIndex() > 0) {
      g.fillRect(startX, 0, endX - startX, startY);
    }
    // Bottom
    if (myList.getMaxSelectionIndex() < myList.getModel().getSize() - 1) {
      g.fillRect(startX, endY, endX - startX, getHeight() - endY);
    }
  }

  @Override
  protected void initListeners() {
    MouseAdapter adapter = new BoxSelectionMouseAdapter();
    this.addMouseListener(adapter);
    this.addMouseMotionListener(adapter);
  }

  private class BoxSelectionMouseAdapter extends MouseAdapter {
    private int myLastX = 0;
    private int myLastRowIndex = -1;

    @Override
    public void mousePressed(MouseEvent e) {
      getModel().beginUpdate();
      getModel().clear();
      myList.clearSelection();
      myLastX = e.getX();
      myLastRowIndex = myList.locationToIndex(e.getPoint());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      getModel().endUpdate();
      myLastX = 0;
      myLastRowIndex = -1;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      double pressed = xToRange(myLastX);
      double current = xToRange(e.getX());
      int rowIndex = myList.locationToIndex(e.getPoint());
      getModel().set(Math.min(pressed, current), Math.max(pressed, current));
      myList.setSelectionInterval(myLastRowIndex, rowIndex);
    }
  }
}
