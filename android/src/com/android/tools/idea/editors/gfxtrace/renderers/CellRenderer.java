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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.controllers.CellController;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.RoundedLineBorder;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CellRenderer implements ListCellRenderer {
  private static final int BORDER_SIZE = JBUI.scale(5);
  private static final int MIN_WIDTH = JBUI.scale(64);
  private static final int MIN_HEIGHT = JBUI.scale(64);
  public static final int MAX_WIDTH = JBUI.scale(192);
  public static final int MAX_HEIGHT = JBUI.scale(192);
  private static final int TICK_MILLISECONDS = 66;
  @NotNull private static final Border DEFAULT_BORDER = JBUI.Borders.empty(BORDER_SIZE, BORDER_SIZE);
  @NotNull private static final Border SELECTED_BORDER = new RoundedLineBorder(UIUtil.getFocusedBoundsColor(), JBUI.scale(5), false);
  @NotNull private static final Dimension DEFAULT_IMAGE_SIZE = new Dimension(MAX_WIDTH, MAX_HEIGHT);
  @NotNull private static final ScheduledExecutorService ourTickerScheduler =
    ConcurrencyUtil.newSingleScheduledThreadExecutor("CellAnimation");

  @NotNull private final CellLabel myCellLabel;
  @NotNull private final CellController myCellLoader;
  @NotNull private ImageIcon myBlankIcon;
  @NotNull final Dimension myLargestKnownIconDimension = new Dimension(MIN_WIDTH, MIN_HEIGHT);
  @Nullable private Runnable myTicker;

  public CellRenderer(CellController loader) {
    myCellLabel = new CellLabel();
    myCellLoader = loader;
    myBlankIcon = new ImageIcon(createBlankImage(DEFAULT_IMAGE_SIZE));
  }

  private static Image createBlankImage(@NotNull Dimension dimension) {
    //noinspection UndesirableClassUsage
    BufferedImage blankImage = new BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_BYTE_BINARY);
    Graphics2D g = blankImage.createGraphics();
    g.setPaint(UIUtil.getListForeground());
    g.fillRect(0, 0, dimension.width, dimension.height);
    g.dispose();
    return blankImage;
  }

  /**
   * This method returns a custom JBLabel to show the final frame render.
   */
  @Override
  public Component getListCellRendererComponent(@NotNull final JList jList,
                                                @NotNull Object data,
                                                final int index,
                                                final boolean isSelected,
                                                boolean cellHasFocus) {
    assert (data instanceof CellController.Data);
    final CellController.Data cell = (CellController.Data)data;
    myCellLabel.setUserData(cell);
    if (!(cell.isLoaded() || cell.isLoading) && myCellLoader.startLoad(cell)) {
      cell.loadstartTime = System.currentTimeMillis();
      cell.isLoading = true;
      if (myTicker == null) {
        myTicker = new Runnable() {
          @Override
          public void run() {
            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                if (cell.isLoading) {
                  jList.repaint();
                  ourTickerScheduler.schedule(myTicker, TICK_MILLISECONDS, TimeUnit.MILLISECONDS);
                }
                else {
                  myTicker = null;
                }
              }
            });
          }
        };
        myTicker.run();
      }
    }
    cell.isSelected = isSelected;
    myCellLabel.setBorder(isSelected ? SELECTED_BORDER : DEFAULT_BORDER);
    return myCellLabel;
  }

  @NotNull
  public Dimension getCellDimensions() {
    return new Dimension(myLargestKnownIconDimension.width + 2 * BORDER_SIZE, myLargestKnownIconDimension.height + 2 * BORDER_SIZE);
  }

  @NotNull
  public ImageIcon getDefaultIcon() {
    return myBlankIcon;
  }

  public boolean updateKnownSize(@NotNull Dimension dimension) {
    boolean updated = false;
    if (myLargestKnownIconDimension.width < dimension.width) {
      updated = true;
      myLargestKnownIconDimension.width = dimension.width;
    }
    if (myLargestKnownIconDimension.height < dimension.height) {
      updated = true;
      myLargestKnownIconDimension.height = dimension.height;
    }
    if (updated) {
      myBlankIcon.setImage(createBlankImage(myLargestKnownIconDimension));
      return true;
    }
    return false;
  }
}
