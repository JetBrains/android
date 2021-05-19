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
import com.android.tools.adtui.model.BoxSelectionModel;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
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
public class BoxSelectionComponent extends RangeSelectionComponent implements MouseListener, MouseMotionListener {
  @NotNull private final BoxSelectionModel myBoxSelectionModel;
  @NotNull private final JList<?> myList;

  /**
   * @param model model that handles the range selection.
   * @param list  the list that the box selection component sits on top of.
   */
  public BoxSelectionComponent(@NotNull BoxSelectionModel model, @NotNull JList<?> list) {
    super(model);
    myBoxSelectionModel = model;
    myList = list;
  }

  public void clearSelection() {
    getModel().clear();
    myList.clearSelection();
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

    // Draw time measurement text, e.g.
    // |----- 2 ms -----|
    // Add spaces to both ends as padding between text and indicators.
    String measurementText = ' ' + TimeFormatter.getSingleUnitDurationString((long)getModel().getSelectionRange().getLength()) + ' ';
    int textWidth = g.getFontMetrics().stringWidth(measurementText);
    int textHeight = g.getFontMetrics().getHeight();
    int textCenterX = startX + (endX - startX) / 2;
    int textStartX = textCenterX - textWidth / 2;
    int textEndX = textCenterX + textWidth / 2;
    g.setColor(StudioColorsKt.getPrimaryContentBackground());
    g.fillRect(startX, endY, endX - startX, textHeight);
    g.setColor(UIUtil.getLabelForeground());
    g.drawString(measurementText, textStartX, endY + g.getFontMetrics().getAscent());
    // Only draw indicators when the text is shorter than the box.
    if (textStartX > startX) {
      // Left vertical indicator.
      g.drawLine(startX, endY, startX, endY + textHeight);
      // Right vertical indicator.
      g.drawLine(endX, endY, endX, endY + textHeight);
      // Left horizontal indicator.
      g.drawLine(startX, endY + textHeight / 2, textStartX, endY + textHeight / 2);
      // Right horizontal indicator.
      g.drawLine(textEndX, endY + textHeight / 2, endX, endY + textHeight / 2);
    }
  }

  @Override
  protected void initListeners() {
    setEventHandlersEnabled(true);
  }

  /**
   * Enable/disable mouse event handlers.
   */
  public void setEventHandlersEnabled(boolean enabled) {
    if (enabled) {
      addMouseListener(this);
      addMouseMotionListener(this);
    }
    else {
      removeMouseListener(this);
      removeMouseMotionListener(this);
    }
  }

  private int myLastX = 0;
  private int myLastRowIndex = -1;

  @Override
  public void mousePressed(MouseEvent e) {
    getModel().beginUpdate();
    clearSelection();
    myLastX = e.getX();
    myLastRowIndex = myList.locationToIndex(e.getPoint());
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    getModel().endUpdate();
    myLastX = 0;
    myLastRowIndex = -1;
    myBoxSelectionModel.selectionCreated((long)getModel().getSelectionRange().getLength(),
                                         myList.getLeadSelectionIndex() - myList.getAnchorSelectionIndex() + 1);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    double pressed = xToRange(myLastX);
    double current = xToRange(e.getX());
    int rowIndex = myList.locationToIndex(e.getPoint());
    getModel().set(Math.min(pressed, current), Math.max(pressed, current));
    myList.setSelectionInterval(myLastRowIndex, rowIndex);
  }

  @Override
  public void mouseClicked(MouseEvent e) { }

  @Override
  public void mouseEntered(MouseEvent e) { }

  @Override
  public void mouseExited(MouseEvent e) { }

  @Override
  public void mouseMoved(MouseEvent e) { }
}
