/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayeredPane;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerLayout.TABLE_COLUMN_HEADER_BORDER;

/**
 * Displays network connection information of all threads.
 */
final class ThreadsView {
  private static final int STATE_HEIGHT = 15;
  private static final int SELECTION_OUTLINE_PADDING = 3;
  private static final int SELECTION_OUTLINE_BORDER = 2;
  private static final int ROW_HEIGHT = STATE_HEIGHT + 2 * (SELECTION_OUTLINE_BORDER + SELECTION_OUTLINE_PADDING);

  @NotNull
  private final HoverRowTable myThreadsTable;

  @NotNull
  private final AspectObserver myObserver;

  ThreadsView(@NotNull NetworkProfilerStageView stageView) {
    ThreadsTableModel model = new ThreadsTableModel(stageView.getStage().getHttpDataFetcher());
    myThreadsTable = new HoverRowTable(model, ProfilerColors.DEFAULT_HOVER_COLOR);
    TimelineRenderer timelineRenderer = new TimelineRenderer(myThreadsTable, stageView.getStage());
    myThreadsTable.getColumnModel().getColumn(1).setCellRenderer(timelineRenderer);
    myThreadsTable.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myThreadsTable.setShowVerticalLines(true);
    myThreadsTable.setShowHorizontalLines(false);
    myThreadsTable.setCellSelectionEnabled(false);
    myThreadsTable.setFocusable(false);
    myThreadsTable.setRowMargin(0);
    myThreadsTable.setRowHeight(ROW_HEIGHT);
    myThreadsTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
    myThreadsTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
    myThreadsTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myThreadsTable.getColumnModel().getColumn(0).setPreferredWidth((int)(myThreadsTable.getWidth() * 1.0 / 8));
        myThreadsTable.getColumnModel().getColumn(1).setPreferredWidth((int)(myThreadsTable.getWidth() * 7.0 / 8));
      }
    });
    myThreadsTable.setTableHeaderBorder(TABLE_COLUMN_HEADER_BORDER);

    TableRowSorter<ThreadsTableModel> sorter = new TableRowSorter<>(model);
    sorter.setComparator(0, Comparator.comparing(String::toString));
    sorter.setComparator(1, Comparator.comparing((List <HttpData> data) -> data.get(0).getStartTimeUs()));
    myThreadsTable.setRowSorter(sorter);

    myThreadsTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        Range selection = stageView.getStage().getStudioProfilers().getTimeline().getSelectionRange();
        HttpData data = findHttpDataUnderCursor(myThreadsTable, selection, e);
        if (data != null) {
          stageView.getStage().setSelectedConnection(data);
          e.consume();
        }
      }
    });

    TooltipView.install(myThreadsTable, stageView.getStage());

    myObserver = new AspectObserver();
    stageView.getStage().getAspect().addDependency(myObserver)
      .onChange(NetworkProfilerAspect.SELECTED_CONNECTION, () -> {
        timelineRenderer.updateRows();
        myThreadsTable.repaint();
      });
  }

  @NotNull
  JComponent getComponent() {
    return myThreadsTable;
  }

  @Nullable
  private static HttpData findHttpDataUnderCursor(@NotNull JTable table, @NotNull Range range, @NotNull MouseEvent e) {
    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), table);
    int row = table.rowAtPoint(p);
    int column = table.columnAtPoint(p);

    if (row == -1 || column == -1) {
      return null;
    }

    if (column == 1) {
      Rectangle cellBounds = table.getCellRect(row, column, false);
      int modelIndex = table.convertRowIndexToModel(row);
      List<HttpData> dataList = (List<HttpData>)table.getModel().getValueAt(modelIndex, 1);
      double at = positionToRange(p.x - cellBounds.x, cellBounds.getWidth(), range);
      for (HttpData data : dataList) {
        if (data.getStartTimeUs() <= at && at <= data.getEndTimeUs()) {
          return data;
        }
      }
    }

    return null;
  }

  private static double positionToRange(double x, double width, @NotNull Range range) {
    return (x * range.getLength()) / width + range.getMin();
  }

  private static final class ThreadsTableModel extends AbstractTableModel {
    @NotNull private final List<List<HttpData>> myThreads;

    private ThreadsTableModel(@NotNull HttpDataFetcher httpDataFetcher) {
      myThreads = new ArrayList<>();
      httpDataFetcher.addListener(this::httpDataChanged);
    }

    private void httpDataChanged(List<HttpData> dataList) {
      myThreads.clear();
      if (dataList.isEmpty()) {
        fireTableDataChanged();
        return;
      }

      Map<Long, List<HttpData>> threads = new HashMap<>();
      for (HttpData data : dataList) {
        if (data.getJavaThreads().isEmpty()) {
          continue;
        }
        if (!threads.containsKey(data.getJavaThreads().get(0).getId())) {
          threads.put(data.getJavaThreads().get(0).getId(), new ArrayList<>());
        }
        threads.get(data.getJavaThreads().get(0).getId()).add(data);
      }

      // Sort by thread name, so that they're consistently displayed in alphabetical order.
      // TODO: Implement sorting mechanism in JList and move this responsibility to the JList.
      threads.values().stream().sorted((o1, o2) -> {
        HttpData.JavaThread thread1 = o1.get(0).getJavaThreads().get(0);
        HttpData.JavaThread thread2 = o2.get(0).getJavaThreads().get(0);
        int nameCompare = thread1.getName().compareTo(thread2.getName());
        return (nameCompare != 0) ? nameCompare : Long.compare(thread1.getId(), thread2.getId());
      }).forEach(myThreads::add);

      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return myThreads.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int column) {
      return column == 0 ? "Initiating thread" : "Timeline";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        return myThreads.get(rowIndex).get(0).getJavaThreads().get(0).getName();
      }
      else {
        return myThreads.get(rowIndex);
      }
    }
  }

  private static final class TimelineRenderer implements TableCellRenderer, TableModelListener {
    @NotNull private final JTable myTable;
    @NotNull private final List<JComponent> myConnectionsInfo;
    @NotNull private final NetworkProfilerStage myStage;

    TimelineRenderer(@NotNull JTable table, @NotNull NetworkProfilerStage stage) {
      myTable = table;
      myConnectionsInfo = new ArrayList<>();
      myStage = stage;
      myTable.getModel().addTableModelListener(this);
      tableChanged(new TableModelEvent(myTable.getModel()));
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      updateRows();
    }

    private void updateRows() {
      myConnectionsInfo.clear();
      for (int index = 0; index < myTable.getModel().getRowCount(); ++index) {
        List<HttpData> data = (List<HttpData>)myTable.getModel().getValueAt(index, 1);
        myConnectionsInfo.add(new ConnectionsInfoComponent(myTable, data, myStage));
      }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JPanel panel = new JBPanel(new TabularLayout("*", "*"));

      if (row == 0) {
        // Show timeline labels in front of the chart components
        AxisComponent axisLabels = createAxis();
        axisLabels.setMarkerLengths(0, 0);
        panel.add(axisLabels, new TabularLayout.Constraint(0, 0));
      }

      panel.add(myConnectionsInfo.get(table.convertRowIndexToModel(row)), new TabularLayout.Constraint(0, 0));
      // Show timeline lines behind chart components
      AxisComponent axisTicks = createAxis();
      axisTicks.setMarkerLengths(myTable.getRowHeight(), 0);
      axisTicks.setShowLabels(false);
      panel.add(axisTicks, new TabularLayout.Constraint(0, 0));
      return panel;
    }

    @NotNull
    private AxisComponent createAxis() {
      ProfilerTimeline timeline = myStage.getStudioProfilers().getTimeline();
      AxisComponentModel model = new AxisComponentModel(timeline.getSelectionRange(), new TimeAxisFormatter(1, 5, 1));
      model.setClampToMajorTicks(false);
      model.setGlobalRange(timeline.getDataRange());

      AxisComponent axis = new AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM);
      axis.setShowAxisLine(false);
      axis.setMarkerColor(ProfilerColors.NETWORK_TABLE_AXIS);
      model.update(1);
      return axis;
    }
  }

  /**
   * A component that responsible for rendering information of the given connections,
   * such as connection names, warnings, and lifecycle states.
   */
  private static final class ConnectionsInfoComponent extends JComponent {
    private static final int NAME_PADDING = 6;

    @NotNull private final List<HttpData> myDataList;
    @NotNull private final Range myRange;
    @NotNull private final JTable myTable;
    @NotNull private final NetworkProfilerStage myStage;

    private ConnectionsInfoComponent(@NotNull JTable table, @NotNull List<HttpData> data, @NotNull NetworkProfilerStage stage) {
      myStage = stage;
      myDataList = data;
      myRange = stage.getStudioProfilers().getTimeline().getSelectionRange();
      setFont(AdtUiUtils.DEFAULT_FONT);
      setForeground(Color.BLACK);
      setBackground(ProfilerColors.DEFAULT_BACKGROUND);
      myTable = table;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D)g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      for (int i = 0; i < myDataList.size(); ++i) {
        HttpData data = myDataList.get(i);
        double endLimit = (i + 1 < myDataList.size()) ? rangeToPosition(myDataList.get(i + 1).getStartTimeUs()) : getWidth();

        drawState(g2d, data, endLimit);
        drawConnectionName(g2d, data, endLimit);
      }

      if (myStage.getSelectedConnection() != null && myDataList.contains(myStage.getSelectedConnection())) {
        drawSelection(g2d, myStage.getSelectedConnection(), getWidth());
      }

      g2d.dispose();
    }

    private void drawState(@NotNull Graphics2D g2d, @NotNull HttpData data, double endLimit) {
      double prev = rangeToPosition(data.getStartTimeUs());
      g2d.setColor(ProfilerColors.NETWORK_SENDING_COLOR);

      if (data.getDownloadingTimeUs() > 0) {
        double download = rangeToPosition(data.getDownloadingTimeUs());
        // draw sending
        g2d.fill(new Rectangle2D.Double(prev, (getHeight() - STATE_HEIGHT) / 2.0, download - prev, STATE_HEIGHT));
        g2d.setColor(ProfilerColors.NETWORK_RECEIVING_COLOR);
        prev = download;
      }

      double end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;
      g2d.fill(new Rectangle2D.Double(prev, (getHeight() - STATE_HEIGHT) / 2.0, end - prev, STATE_HEIGHT));
    }

    private void drawConnectionName(@NotNull Graphics2D g2d, @NotNull HttpData data, double endLimit) {
      g2d.setFont(getFont());
      g2d.setColor(getForeground());
      double start = rangeToPosition(data.getStartTimeUs());
      double end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;

      FontMetrics metrics = getFontMetrics(getFont());
      String text =
        AdtUiUtils.getFittedString(metrics, HttpData.getUrlName(data.getUrl()), (float)(end - start - 2 * NAME_PADDING), 1);

      double availableSpace = (end - start - metrics.stringWidth(text));
      g2d.drawString(text, (float)(start + availableSpace / 2.0), (float)((getHeight() - metrics.getHeight()) * 0.5 + metrics.getAscent()));
    }

    private void drawSelection(@NotNull Graphics2D g2d, @NotNull HttpData data, double endLimit) {
      double start = rangeToPosition(data.getStartTimeUs());
      double end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;
      g2d.setStroke(new BasicStroke(SELECTION_OUTLINE_BORDER));
      g2d.setColor(myTable.getSelectionBackground());
      Rectangle2D rect = new Rectangle2D.Double(start - SELECTION_OUTLINE_PADDING,
                                                (getHeight() - STATE_HEIGHT) / 2.0 - SELECTION_OUTLINE_PADDING,
                                                end - start + 2 * SELECTION_OUTLINE_PADDING,
                                                STATE_HEIGHT + 2 * SELECTION_OUTLINE_PADDING);
      g2d.draw(rect);
    }

    private double rangeToPosition(double r) {
      return (r - myRange.getMin()) / myRange.getLength() * getWidth();
    }
  }

  private final static class TooltipView extends MouseAdapter {
    @NotNull private final NetworkProfilerStage myStage;
    @NotNull private final JTable myTable;

    @NotNull private final TooltipComponent myTooltipComponent;
    @NotNull private final JLabel myLabel;

    private TooltipView(@NotNull JTable table, @NotNull NetworkProfilerStage stage) {
      myTable = table;
      myStage = stage;

      myLabel = new JLabel();
      myLabel.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
      myLabel.setBorder(ProfilerLayout.TOOLTIP_BORDER);
      myLabel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
      myLabel.setFont(myLabel.getFont().deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE));
      myLabel.setOpaque(true);

      myTooltipComponent = new TooltipComponent(myLabel, table, ProfilerLayeredPane.class);
      myTooltipComponent.registerListenersOn(table);
      myTooltipComponent.setVisible(false);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      myTooltipComponent.setVisible(false);
      Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
      HttpData data = findHttpDataUnderCursor(myTable, selection, e);
      if (data != null) {
        showTooltip(data);
      }
    }

    private void showTooltip(@NotNull HttpData data) {
      myTooltipComponent.setVisible(true);

      double ms_to_us = TimeUnit.MILLISECONDS.toMicros(1);
      String urlName = HttpData.getUrlName(data.getUrl());
      double duration = (data.getEndTimeUs() - data.getStartTimeUs()) / ms_to_us;

      StringBuilder htmlBuilder = new StringBuilder(
        String.format(
          "<html>" +
          "  <p> %s </p>" +
          "  <p style='color:%s; margin-top: 5px;'> %.2f ms </p>",
          urlName,
          ColorUtil.toHex(ProfilerColors.TOOLTIP_TIME_COLOR),
          duration
        )
      );

      if (data.getJavaThreads().size() > 1) {
        htmlBuilder.append(String.format("<div style='border-bottom: 1px solid #%s;'></div>",
                                         ColorUtil.toHex(ProfilerColors.NETWORK_THREADS_VIEW_TOOLTIP_DIVIDER)));
        htmlBuilder.append("<p style='margin-top: 5px;'> <b> Also accessed by </b> </p>");
        for (int i = 1; i < data.getJavaThreads().size(); ++i) {
          htmlBuilder.append(String.format("<p style='margin-top: 2px;'> %s </p>",
                                           data.getJavaThreads().get(i).getName()));
        }
      }

      htmlBuilder.append("</html>");
      myLabel.setText(htmlBuilder.toString());
    }

    /**
     * Construct our tooltip view and attach it to the target table.
     */
    public static void install(@NotNull JTable table, @NotNull NetworkProfilerStage stage) {
      table.addMouseMotionListener(new TooltipView(table, stage));
    }
  }
}
