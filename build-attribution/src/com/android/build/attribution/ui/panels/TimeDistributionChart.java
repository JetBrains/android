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
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class TimeDistributionChart<T> extends JPanel {

  private static final int PIXELS_BY_PERCENT = 2;
  private static final int BOX_WIDTH = 100;
  public static final int MIN_OTHER_TASKS_SECTION_HEIGHT_PX = 25;
  // Was decided to always have white (1px lines between boxes) for better visibility.
  @SuppressWarnings("UseJBColor")
  public static final Color BOXES_STACK_BACKGROUND = Color.WHITE;

  private final List<ChartItem> myChartItems;
  private final ChartDataItem<T> myHighlightedItem;

  public TimeDistributionChart(List<ChartDataItem<T>> dataItems,
                               ChartDataItem<T> highlightedItem,
                               boolean fullTable) {
    super(new GridBagLayout());
    myHighlightedItem = highlightedItem;

    myChartItems = dataItems.stream().map(ChartItem::new).collect(Collectors.toList());

    JBPanel boxes = new JBPanel(new VerticalLayout(1, SwingConstants.RIGHT));
    boxes.setBackground(BOXES_STACK_BACKGROUND);

    myChartItems.forEach(item -> {
      item.init();
      boxes.add(item.leftBox);
    });

    ConnectionsPane connections = new ConnectionsPane();
    connections.withMinimumWidth(50).withPreferredWidth(50);

    JPanel table = fullTable ? createFullPluginsTable() : createShortPluginsTable();

    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    c.gridx = 0;
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    c.fill = GridBagConstraints.NONE;
    add(boxes, c);

    c.gridx = 1;
    c.fill = GridBagConstraints.BOTH;
    add(connections, c);

    c.gridx = 2;
    c.weightx = 1.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    add(table, c);
  }

  private JPanel createFullPluginsTable() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 0;
    myChartItems.forEach(item -> {
      c.gridx = 0;
      c.weightx = 0d;
      c.anchor = GridBagConstraints.LINE_START;
      c.insets = JBUI.emptyInsets();
      panel.add(item.rightAnchor, c);
      c.gridx = 1;
      c.insets = JBUI.insets(0, 9, 0, 0);
      panel.add(createTableLabel(durationString(item.time()), item), c);
      c.gridx = 2;
      c.anchor = GridBagConstraints.LINE_END;
      panel.add(createTableLabel(percentageString(item.time()), item), c);
      c.gridx = 3;
      panel.add(new JBLabel(item.getTableIcon()), c); //Warning / info icon placeholder
      c.gridx = 4;
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
      panel.add(item.rightAnchor, c);
      c.gridx = 1;
      c.insets = JBUI.insets(0, 9, 0, 0);
      panel.add(createTableLabel(durationString(item.time()), item), c);

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
    final ChartDataItem<T> myDataItem;
    Color myConnectorLineColor;
    JBPanel leftBox;
    JBPanel rightAnchor;

    private ChartItem(ChartDataItem<T> dataItem) {
      myDataItem = dataItem;
    }

    public void init() {
      this.leftBox = createMainBox();
      this.rightAnchor = createBulletPointBox();
      if (isSelected()) {
        myConnectorLineColor = myDataItem.getLegendColor().selectionColor;
      }
      else {
        myConnectorLineColor = myDataItem.getLegendColor().baseColor;
      }
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

    private Box createMainBox() {
      int heightPx = calculateBoxHeightPx();
      String boxText = myDataItem.chartBoxText();
      Box box = new Box(myDataItem.getLegendColor().baseColor, BOX_WIDTH, heightPx);
      if (boxText != null) {
        JBLabel label = new JBLabel(boxText, SwingConstants.CENTER).withFont(JBUI.Fonts.smallFont());
        label.setForeground(CriticalPathChartLegend.OTHER_TASKS_TEXT_COLOR);
        box.add(label, BorderLayout.CENTER);
      }

      Color borderColor = isSelected() ? myDataItem.getLegendColor().selectionColor : myDataItem.getLegendColor().baseColor;
      box.setBorder(JBUI.Borders.customLine(borderColor, 0, 0, 0, 5));
      return box;
    }

    private Box createBulletPointBox() {
      Color color = isSelected() ? myDataItem.getLegendColor().selectionColor : myDataItem.getLegendColor().baseColor;
      return new Box(color, 5, 5);
    }
  }

  private static class Box extends JBPanel {
    private Box(Color baseColor, int width, int height) {
      super();
      withMinimumWidth(width);
      withPreferredWidth(width);
      withMaximumWidth(width);
      withMinimumHeight(height);
      withPreferredHeight(height);
      withMaximumHeight(height);
      setBackground(baseColor);
    }
  }

  private class ConnectionsPane extends JBPanel<ConnectionsPane> {
    @Override
    protected void paintComponent(Graphics g) {
      GraphicsUtil.setupAntialiasing(g);
      GraphicsUtil.setupAAPainting(g);
      super.paintComponent(g);
      myChartItems.forEach(item -> {
        g.setColor(item.myConnectorLineColor);
        int lx = 0;
        int ly = item.leftBox.getY() + item.leftBox.getHeight() / 2;
        int rx = getWidth();
        int ry = item.rightAnchor.getY() + item.rightAnchor.getHeight() / 2;
        g.drawLine(lx, ly, rx, ry);
      });
    }
  }
}
