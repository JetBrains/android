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
import com.android.tools.idea.editors.gfxtrace.widgets.Repaintable;
import com.intellij.icons.AllIcons;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class ImageCellRenderer<T extends ImageCellList.Data> extends CellRenderer<T> {
  public static final int BORDER_SIZE = JBUI.scale(5);
  private static final int INNER_BORDER_SIZE = JBUI.scale(2);
  private static final int CORNER_RADIUS = JBUI.scale(4);
  private static final int MIN_HEIGHT = JBUI.scale(30);
  @NotNull private static final Dimension INITIAL_SIZE = new Dimension(JBUI.scale(192) + BORDER_SIZE, JBUI.scale(108) + BORDER_SIZE);
  @NotNull private static final Border DEFAULT_BORDER = new RoundedLineBorder(UIUtil.getBoundsColor(), BORDER_SIZE, INNER_BORDER_SIZE);
  @NotNull private static final Border SELECTED_BORDER = new RoundedLineBorder(UIUtil.getListSelectionBackground(), BORDER_SIZE, BORDER_SIZE);
  @NotNull private static final ImageCellList.Data NULL_CELL = new ImageCellList.Data(null) {{
    loadingState = LoadingState.LOADED;
  }};

  @NotNull private final ImageComponent myCellComponent = new ImageComponent(Layout.CENTERED_WITH_OVERLAY, getInitialCellSize());
  @NotNull private final Dimension myLargestKnownIconDimension = new Dimension(0, 0);
  @NotNull private final Dimension myMaxSize;

  public ImageCellRenderer(CellLoader<T> loader, @NotNull Dimension maxSize) {
    super(loader);
    myMaxSize = maxSize;
  }

  @Override
  protected T createNullCell() {
    return (T)NULL_CELL;
  }

  @Override
  protected Component getRendererComponent(@NotNull JList list, @NotNull T cell) {
    myCellComponent.setCell(cell);
    if (cell.isLoading()) {
      LoadingIndicator.scheduleForRedraw(getRepaintable(list));
    }
    return myCellComponent;
  }

  public void setMinimumIconSize(Dimension dimension) {
    myLargestKnownIconDimension.width = Math.max(myLargestKnownIconDimension.width, dimension.width);
    myLargestKnownIconDimension.height = Math.max(myLargestKnownIconDimension.height, dimension.height);
    myCellComponent.setImageSize(myLargestKnownIconDimension);
  }

  @Override
  public Dimension getInitialCellSize() {
    return INITIAL_SIZE;
  }

  public void setLayout(Layout layout) {
    myCellComponent.setLayout(layout);
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
      myCellComponent.setImageSize(myLargestKnownIconDimension);
    }
  }

  private static class ImageComponent extends JComponent {
    private Layout myLayout;
    private Dimension myImageSize;
    private ImageCellList.Data myCell;

    public ImageComponent(Layout layout, Dimension imageSize) {
      myLayout = layout;
      myImageSize = imageSize;
    }

    public void setLayout(Layout layout) {
      myLayout = layout;
    }

    public void setImageSize(Dimension imageSize) {
      myImageSize = imageSize;
    }

    public void setCell(ImageCellList.Data cell) {
      myCell = cell;
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(myImageSize.width + 2 * BORDER_SIZE, myImageSize.height + 2 * BORDER_SIZE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      if (myCell == NULL_CELL) {
        return;
      }

      if (getHeight() < MIN_HEIGHT) {
        graphics.setColor(UIUtil.getListBackground());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        if (myCell.getLabel() != null) {
          paintLabel(graphics, BORDER_SIZE);
        }
        return;
      }

      int w = getWidth() - 2 * BORDER_SIZE, h = getHeight() - 2 * BORDER_SIZE;
      graphics.setColor(UIUtil.getListBackground());
      graphics.fillRect(BORDER_SIZE, BORDER_SIZE, w, h);

      int imageWidth, imageHeight;
      if (myLayout == Layout.CENTERED_WITH_OVERLAY) {
        imageWidth = w;
        imageHeight = h;
      } else {
        imageWidth = myImageSize.width;
        imageHeight = myImageSize.height;
      }

      if (myCell.isLoaded()) {
        if (myLayout == Layout.CENTERED_WITH_OVERLAY) {
          RenderUtils.drawImage(this, graphics, myCell.icon.getImage(), BORDER_SIZE, BORDER_SIZE, imageWidth, imageHeight);
        } else {
          RenderUtils.drawCroppedImage(this, graphics, myCell.icon.getImage(), BORDER_SIZE, BORDER_SIZE, imageWidth, imageHeight);
        }
      }
      else if (myCell.hasFailed()) {
        RenderUtils.drawIcon(this, graphics, AllIcons.General.Warning, BORDER_SIZE, BORDER_SIZE, imageWidth, imageHeight);
      }
      else {
        LoadingIndicator.paint(this, graphics, BORDER_SIZE, BORDER_SIZE, imageWidth, imageHeight);
      }

      if (myCell.isSelected) {
        SELECTED_BORDER.paintBorder(this, graphics, 0, 0, getWidth(), getHeight());
      }
      else {
        int d = BORDER_SIZE - INNER_BORDER_SIZE;
        DEFAULT_BORDER.paintBorder(this, graphics, d, d, getWidth() - 2 * d, getHeight() - 2 * d);
      }

      if (myCell.getLabel() != null) {
        setForeground(myCell.hasFailed() ? UIUtil.getLabelDisabledForeground() : UIUtil.getLabelForeground());
        paintLabel(graphics, myImageSize.width + 2 * BORDER_SIZE);
      }
    }

    protected void paintLabel(Graphics g, int offset) {
      final int OFFSET = 7;
      final int PADDING = 2;

      String label = myCell.getLabel();
      FontMetrics metrics = g.getFontMetrics();
      int fontHeight = metrics.getHeight();
      int frameStringWidth = metrics.stringWidth(label);

      if (myLayout == Layout.CENTERED_WITH_OVERLAY) {
        g.setColor(UIUtil.getDecoratedRowColor());
        g.fillRoundRect(OFFSET, OFFSET, frameStringWidth + 2 * PADDING + 1, fontHeight + 2 * PADDING + 1, CORNER_RADIUS, CORNER_RADIUS);
        g.setColor(getForeground());
        g.drawString(label, OFFSET + PADDING + 1, OFFSET + PADDING + fontHeight - metrics.getDescent());
      } else {
        g.setColor(getForeground());
        g.drawString(label, PADDING + offset, (getHeight() + fontHeight) / 2 - metrics.getDescent());
      }
    }
  }

  public enum Layout {
    CENTERED_WITH_OVERLAY, LEFT_TO_RIGHT;
  }
}
