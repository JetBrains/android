/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.widgets.ImageCellList;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadingIndicator;
import com.intellij.icons.AllIcons;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class ImageCellRenderer<T extends ImageCellList.Data> extends CellRenderer<T> {
  private static final int BORDER_SIZE = JBUI.scale(5);
  private static final int INNER_BORDER_SIZE = JBUI.scale(2);
  private static final int CORNER_RADIUS = JBUI.scale(3);
  @NotNull private static final Dimension INITIAL_SIZE = new Dimension(JBUI.scale(192), JBUI.scale(108));
  @NotNull private static final Border DEFAULT_BORDER = new RoundedLineBorder(UIUtil.getBoundsColor(), BORDER_SIZE, INNER_BORDER_SIZE);
  @NotNull private static final Border SELECTED_BORDER = new RoundedLineBorder(UIUtil.getListSelectionBackground(), BORDER_SIZE, BORDER_SIZE);
  @NotNull private static final Color TEXT_COLOR = new Color(255, 255, 255, 192); //noinspection UseJBColor

  @NotNull private final ImageComponent myCellComponent = new ImageComponent();
  @NotNull private final Dimension myLargestKnownIconDimension = new Dimension(0, 0);
  @NotNull private final Dimension myMaxSize;

  public ImageCellRenderer(CellLoader<T> loader, @NotNull Dimension maxSize) {
    super(loader);
    myMaxSize = maxSize;
  }

  @Override
  protected Component getRendererComponent(@NotNull JList list, @NotNull T cell) {
    myCellComponent.setCell(cell);
    if (cell.isLoading()) {
      LoadingIndicator.scheduleForRedraw(list);
    }
    return myCellComponent;
  }

  @Override
  public Dimension getInitialCellSize() {
    return INITIAL_SIZE;
  }

  @Override
  protected void onCellLoaded(JList list, T cell) {
    boolean updated = false;
    if (myLargestKnownIconDimension.width < Math.min(myMaxSize.width, cell.icon.getIconWidth())) {
      updated = true;
      myLargestKnownIconDimension.width = Math.min(myMaxSize.width, cell.icon.getIconWidth());
    }
    if (myLargestKnownIconDimension.height < Math.min(myMaxSize.height, cell.icon.getIconHeight())) {
      updated = true;
      myLargestKnownIconDimension.height = Math.min(myMaxSize.height, cell.icon.getIconHeight());
    }
    if (updated) {
      list.setFixedCellWidth(2 * BORDER_SIZE + myLargestKnownIconDimension.width);
      list.setFixedCellHeight(2 * BORDER_SIZE + myLargestKnownIconDimension.height);
    }
  }

  private static class ImageComponent extends JComponent {
    private ImageCellList.Data myCell;

    public ImageComponent() {
    }

    public void setCell(ImageCellList.Data cell) {
      myCell = cell;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      int w = getWidth() - 2 * BORDER_SIZE, h = getHeight() - 2 * BORDER_SIZE;
      graphics.setColor(myCell.hasFailed() ? UIUtil.getLabelDisabledForeground() : UIUtil.getListBackground());
      graphics.fillRect(BORDER_SIZE, BORDER_SIZE, w, h);

      if (myCell.isLoaded()) {
        RenderUtils.drawImage(this, graphics, myCell.icon.getImage(), BORDER_SIZE, BORDER_SIZE, w, h);
      }
      else if (myCell.hasFailed()) {
        RenderUtils.drawIcon(this, graphics, AllIcons.General.Error, BORDER_SIZE, BORDER_SIZE, w, h);
      }
      else {
        LoadingIndicator.paint(this, graphics, BORDER_SIZE, BORDER_SIZE, w, h);
      }

      if (myCell.isSelected) {
        SELECTED_BORDER.paintBorder(this, graphics, 0, 0, getWidth(), getHeight());
      }
      else {
        int d = BORDER_SIZE - INNER_BORDER_SIZE;
        DEFAULT_BORDER.paintBorder(this, graphics, d, d, getWidth() - 2 * d, getHeight() - 2 * d);
      }

      paintFrameOverlay(graphics);
    }

    protected void paintFrameOverlay(Graphics g) {
      final int OFFSET = 7;
      final int PADDING = 1;

      FontMetrics metrics = g.getFontMetrics();
      int fontHeight = metrics.getHeight();
      int frameStringWidth = metrics.stringWidth(myCell.label);
      g.setColor(TEXT_COLOR);
      g.fillRoundRect(OFFSET, OFFSET, frameStringWidth + 2 * PADDING + 1, fontHeight + 2 * PADDING + 1, CORNER_RADIUS, CORNER_RADIUS);
      g.setColor(getForeground());
      g.drawString(myCell.label, OFFSET + PADDING + 1, OFFSET - PADDING + fontHeight);
    }
  }
}
