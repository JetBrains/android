/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.panels;

import static com.android.build.attribution.ui.BuildAttributionUIUtilKt.durationString;
import static com.android.build.attribution.ui.BuildAttributionUIUtilKt.percentageString;

import com.android.build.attribution.ui.data.TimeWithPercentage;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class TimeDistributionChart<T> extends JPanel {

  private static final int MIN_OTHER_TASKS_SECTION_HEIGHT_PX = 25;
  private static final int PIXELS_BY_PERCENT = 2;

  private final List<ChartItem> myChartItems;
  private final ChartDataItem<T> myHighlightedItem;

  public TimeDistributionChart(List<ChartDataItem<T>> dataItems,
                               ChartDataItem<T> highlightedItem,
                               boolean fullTable) {
    super(new GridBagLayout());
    myHighlightedItem = highlightedItem;

    myChartItems = ContainerUtil.map(dataItems, ChartItem::new);

    JPanel table = fullTable ? createFullPluginsTable() : createShortPluginsTable();

    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    c.gridy = 0;
    c.gridx = 0;
    c.weightx = 0.0;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.VERTICAL;
    add(new ChartPane(), c);

    c.gridx = 1;
    c.weightx = 1.0;
    c.weighty = 0.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    add(table, c);
  }

  private JPanel createFullPluginsTable() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    myChartItems.forEach(item -> {
      c.weightx = 0d;
      c.anchor = GridBagConstraints.LINE_START;
      c.insets = JBUI.emptyInsets();
      c.gridx = 0;
      c.insets = JBUI.insets(0, 9, 0, 0);
      panel.add(item.myTimeLabel, c);
      c.gridx = 1;
      c.anchor = GridBagConstraints.LINE_END;
      panel.add(createTableLabel(percentageString(item.time()), item), c);
      c.gridx = 2;
      panel.add(new JBLabel(item.getTableIcon()), c); //Warning / info icon placeholder
      c.gridx = 3;
      c.anchor = GridBagConstraints.LINE_START;
      c.weightx = 1d;
      panel.add(createTableLabel(item.text(), item), c);

      c.gridy++;
    });
    return panel;
  }

  private JPanel createShortPluginsTable() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    myChartItems.forEach(item -> {
      c.gridx = 0;
      c.weightx = 0d;
      c.anchor = GridBagConstraints.LINE_START;
      c.insets = JBUI.emptyInsets();
      c.insets = JBUI.insets(0, 9, 0, 0);
      panel.add(item.myTimeLabel, c);

      c.gridy++;
    });
    return panel;
  }

  private JBLabel createTableLabel(String text, ChartItem item) {
    JBLabel label = new JBLabel(text);
    label.setForeground(item.getTableTextColor());
    return label;
  }

  public interface ChartDataItem<T> {
    TimeWithPercentage time();

    String text();

    Icon getTableIcon();

    CriticalPathChartLegend.ChartColor getLegendColor();

    String chartBoxText();

    default Color selectedTextColor() {
      return UIUtil.getActiveTextColor();
    }

    default Color unselectedTextColor() {
      return UIUtil.getInactiveTextColor();
    }
  }

  public interface SingularChartDataItem<T> extends ChartDataItem<T> {
    T getUnderlyingData();
  }

  public interface AggregatedChartDataItem<T> extends ChartDataItem<T> {
    List<T> getUnderlyingData();
  }

  private class ChartItem {
    @NotNull
    final ChartDataItem<T> myDataItem;
    @NotNull
    final Color myBaseColor;
    final int myChartBoxHeight;
    @NotNull
    final JBLabel myTimeLabel;

    private ChartItem(@NotNull ChartDataItem<T> dataItem) {
      myDataItem = dataItem;
      myChartBoxHeight = JBUIScale.scale(calculateBoxHeightPx());
      myBaseColor = myDataItem.getLegendColor().baseColor;
      myTimeLabel = createTableLabel(durationString(time()), this);
    }

    private int calculateBoxHeightPx() {
      int minHeightPixels = PIXELS_BY_PERCENT;
      if (myDataItem.chartBoxText() != null) {
        minHeightPixels = MIN_OTHER_TASKS_SECTION_HEIGHT_PX;
      }
      int heightInPercentage = (int)Math.round(Math.ceil(time().getPercentage()));
      int heightInPixels = heightInPercentage * PIXELS_BY_PERCENT;
      return Math.max(heightInPixels, minHeightPixels);
    }

    public TimeWithPercentage time() {
      return myDataItem.time();
    }

    public String text() {
      return myDataItem.text();
    }

    public Icon getTableIcon() {
      return myDataItem.getTableIcon();
    }

    private boolean isSelected() {
      return myHighlightedItem == myDataItem;
    }

    private Color getTableTextColor() {
      if (myHighlightedItem == null) {
        // When there is no selection in the chart, all text should be as when it is active.
        return myDataItem.selectedTextColor();
      }
      else if (isSelected()) {
        return myDataItem.selectedTextColor();
      }
      else {
        return myDataItem.unselectedTextColor();
      }
    }

    private int getRowCenterVerticalLocation() {
      return myTimeLabel.getY() + myTimeLabel.getHeight() / 2;
    }
  }

  @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeStatic"})
  private class ChartPane extends JBPanel<ChartPane> {
    private final int myChartBoxMarginPx = JBUIScale.scale(1);
    private final int myMinCursorHeight = JBUIScale.scale(10);

    private final int myCursorWidth = JBUIScale.scale(10);
    private final int myChartBoxWidth = JBUIScale.scale(100);
    private final int myConnectorsWidth = JBUIScale.scale(50);
    private final int myBulletSize = JBUIScale.scale(5);
    private final int myBulletSelectionBorderThickness = JBUIScale.scale(2);
    private final int mySelectedBulletWidth = myBulletSize + 2 * myBulletSelectionBorderThickness;

    private final int myCursorLeftBoundX = 0;
    private final int myChartBoxLeftBoundX = myCursorLeftBoundX + myCursorWidth + myChartBoxMarginPx;
    private final int myConnectorLeftBoundX = myChartBoxLeftBoundX + myChartBoxWidth;
    private final int myBulletLeftBoundX = myConnectorLeftBoundX + myConnectorsWidth;
    private final int myChartWidth = myBulletLeftBoundX + mySelectedBulletWidth;


    private ChartPane() {
      int itemsHeightSum = myChartItems.stream().mapToInt(item -> item.myChartBoxHeight).sum();
      int itemsNumber = myChartItems.size();
      int minHeight = myMinCursorHeight + itemsHeightSum + itemsNumber * myChartBoxMarginPx + myMinCursorHeight;
      withMinimumHeight(minHeight);
      withPreferredHeight(minHeight);
      withMinimumWidth(myChartWidth);
      withMaximumWidth(myChartWidth);
      withPreferredWidth(myChartWidth);
    }

    @Override
    protected void paintComponent(Graphics g) {
      GraphicsUtil.setupAntialiasing(g);
      GraphicsUtil.setupAAPainting(g);
      super.paintComponent(g);
      int curY = myMinCursorHeight;
      for (ChartItem item : myChartItems) {
        g.setColor(item.myBaseColor);
        drawMainBox(g, item, curY);
        drawBulletPoint(g, item);
        drawConnector(g, item, curY);
        if (item.isSelected()) {
          drawCursor(g, item, curY);
        }
        if (item.myDataItem.chartBoxText() != null) {
          drawTextInBox(g, item, curY);
        }
        curY += item.myChartBoxHeight + myChartBoxMarginPx;
      }
    }

    private void drawMainBox(Graphics g, ChartItem item, int curY) {
      g.fillRect(myChartBoxLeftBoundX, curY, myChartBoxWidth, item.myChartBoxHeight);
    }

    private void drawTextInBox(Graphics g, ChartItem item, int curY) {
      g.setFont(JBUI.Fonts.smallFont());
      g.setColor(CriticalPathChartLegend.OTHER_TASKS_TEXT_COLOR);

      String boxText = item.myDataItem.chartBoxText();
      FontMetrics metrics = g.getFontMetrics();
      int textWidth = metrics.stringWidth(boxText);
      int textX = myChartBoxLeftBoundX + (myChartBoxWidth - textWidth) / 2;

      int textMiddleY = metrics.getAscent() / 2;
      int textY = curY + item.myChartBoxHeight / 2 + textMiddleY;

      g.drawString(boxText, textX, textY);
    }

    private void drawBulletPoint(Graphics g, ChartItem item) {
      int bulletX = myBulletLeftBoundX + myBulletSelectionBorderThickness;
      int rowCenterY = item.getRowCenterVerticalLocation();
      int bulletY = rowCenterY - myBulletSize / 2;
      g.fillRect(bulletX, bulletY, myBulletSize, myBulletSize);

      if (item.isSelected()) {
        int borderSize = this.myBulletSize + 2 * myBulletSelectionBorderThickness - 1;
        int borderY = bulletY - myBulletSelectionBorderThickness;
        g.drawRoundRect(myBulletLeftBoundX, borderY, borderSize, borderSize, 4, 4);
      }
      else {
        g.drawLine(myBulletLeftBoundX, rowCenterY, myBulletLeftBoundX + myBulletSelectionBorderThickness, rowCenterY);
      }
    }

    private void drawConnector(Graphics g, ChartItem item, int curY) {
      int leftY = curY + item.myChartBoxHeight / 2;
      int rightX = myConnectorLeftBoundX + myConnectorsWidth;
      int rightY = item.getRowCenterVerticalLocation();
      g.drawLine(myConnectorLeftBoundX, leftY, rightX, rightY);
    }

    private void drawCursor(Graphics g, ChartItem item, int curY) {
      int rightX = myCursorLeftBoundX + myCursorWidth;
      if (myMinCursorHeight > item.myChartBoxHeight) {
        int middleY = curY + item.myChartBoxHeight / 2;
        int leftTopY = middleY - myMinCursorHeight / 2;
        int leftBottomY = middleY + myMinCursorHeight / 2;
        g.fillPolygon(
          new int[]{myCursorLeftBoundX, myCursorLeftBoundX, rightX, rightX},
          new int[]{leftTopY, leftBottomY, curY + item.myChartBoxHeight, curY},
          4
        );
      }
      else {
        g.fillRect(myCursorLeftBoundX, curY, myCursorWidth, item.myChartBoxHeight);
      }
    }
  }
}
