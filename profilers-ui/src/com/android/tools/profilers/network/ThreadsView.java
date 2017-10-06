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
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.ProfilerTimeline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays network connection information of all threads.
 */
final class ThreadsView {
  private static final int STATE_HEIGHT = 15;
  private static final int SELECTION_OUTLINE_PADDING = 3;
  private static final int SELECTION_OUTLINE_BORDER = 2;
  private static final int ROW_HEIGHT = STATE_HEIGHT + 2 * (SELECTION_OUTLINE_BORDER + SELECTION_OUTLINE_PADDING);

  @NotNull
  private final JTable myThreadsTable;

  @NotNull
  private final JLayeredPane myPanel;

  @NotNull
  private final AspectObserver myObserver;

  ThreadsView(@NotNull NetworkProfilerStageView stageView) {
    myThreadsTable =
      new HoverRowTable(new ThreadsTableModel(stageView.getStage().getHttpDataFetcher()), ProfilerColors.DEFAULT_HOVER_COLOR);
    TimelineRenderer timelineRenderer = new TimelineRenderer(myThreadsTable, stageView.getStage());
    myThreadsTable.getColumnModel().getColumn(1).setCellRenderer(timelineRenderer);
    myThreadsTable.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myThreadsTable.setShowVerticalLines(true);
    myThreadsTable.setShowHorizontalLines(false);
    myThreadsTable.setTableHeader(null);
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

    myPanel = new JLayeredPane();
    JComponent tooltip = new TableTooltipView(myThreadsTable, stageView.getStage()).getComponent();

    myPanel.add(myThreadsTable, Integer.valueOf(0));
    myPanel.add(tooltip, Integer.valueOf(1));
    myPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myThreadsTable.setSize(myPanel.getSize());
        tooltip.setSize(myPanel.getSize());
      }
    });

    myObserver = new AspectObserver();
    stageView.getStage().getAspect().addDependency(myObserver)
      .onChange(NetworkProfilerAspect.SELECTED_CONNECTION, timelineRenderer::updateRows);
  }

  @NotNull
  JComponent getComponent() {
    return myPanel;
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
    @NotNull private final List<JComponent> myRows;
    @NotNull private final NetworkProfilerStage myStage;

    TimelineRenderer(@NotNull JTable table, @NotNull NetworkProfilerStage stage) {
      myTable = table;
      myRows = new ArrayList<>();
      myStage = stage;
      myTable.getModel().addTableModelListener(this);
      tableChanged(new TableModelEvent(myTable.getModel()));
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      updateRows();
    }

    private void updateRows() {
      myRows.clear();
      for (int index = 0; index < myTable.getModel().getRowCount(); ++index) {
        List<HttpData> data = (List<HttpData>)myTable.getModel().getValueAt(index, 1);
        assert !data.isEmpty();

        AxisComponent axisTicks = createAxis();
        axisTicks.setMarkerLengths(myTable.getRowHeight(), 0);
        axisTicks.setShowLabels(false);

        JPanel panel = new JPanel(new TabularLayout("*", "*"));
        panel.setPreferredSize(new Dimension((int)panel.getPreferredSize().getWidth(), myTable.getRowHeight()));

        if (index == 0) {
          AxisComponent axisLabels = createAxis();
          axisLabels.setMarkerLengths(0, 0);
          axisLabels.setShowLabels(true);
          panel.add(axisLabels, new TabularLayout.Constraint(0, 0));
        }
        panel.add(new ConnectionsInfoComponent(myTable, data, myStage), new TabularLayout.Constraint(0, 0));
        panel.add(axisTicks, new TabularLayout.Constraint(0, 0));
        myRows.add(panel);
      }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return myRows.get(row);
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
    private static final int WARNING_SIZE = 10;

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

        if (data.getJavaThreads().size() > 1) {
          drawWarning(g2d, data, endLimit);
        }

        drawConnectionName(g2d, data, endLimit);
      }

      if (myStage.getSelectedConnection() != null && myDataList.contains(myStage.getSelectedConnection())) {
        drawSelection(g2d, myStage.getSelectedConnection(), getWidth());
      }

      g2d.dispose();
    }

    private void drawState(@NotNull Graphics2D g2d, @NotNull HttpData data, double endLimit) {
      double prev = rangeToPosition(data.getStartTimeUs());
      g2d.setColor(ProfilerColors.NETWORK_THREADS_TABLE_SENDING);

      if (data.getDownloadingTimeUs() > 0) {
        double download = rangeToPosition(data.getDownloadingTimeUs());
        // draw sending
        g2d.fill(new Rectangle2D.Double(prev, (getHeight() - STATE_HEIGHT) / 2, download - prev, STATE_HEIGHT));
        g2d.setColor(ProfilerColors.NETWORK_THREADS_TABLE_RECEIVING);
        prev = download;
      }

      double end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;
      g2d.fill(new Rectangle2D.Double(prev, (getHeight() - STATE_HEIGHT) / 2, end - prev, STATE_HEIGHT));
    }

    private void drawWarning(@NotNull Graphics2D g2d, @NotNull HttpData data, double endLimit) {
      double start = rangeToPosition(data.getStartTimeUs());
      double end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;

      double stateY = (getHeight() - STATE_HEIGHT) / 2;

      Path2D triangle = new Path2D.Double();
      triangle.moveTo(end - Math.min(end - start, WARNING_SIZE), stateY);
      triangle.lineTo(end, stateY);
      triangle.lineTo(end, stateY + WARNING_SIZE);
      triangle.closePath();

      g2d.setColor(getBackground());
      g2d.setStroke(new BasicStroke(2));
      g2d.draw(triangle);

      g2d.setColor(ProfilerColors.NETWORK_THREADS_TABLE_WARNING);
      g2d.fill(triangle);
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
                                                (getHeight() - STATE_HEIGHT) / 2 - SELECTION_OUTLINE_PADDING,
                                                end - start + 2 * SELECTION_OUTLINE_PADDING,
                                                STATE_HEIGHT + 2 * SELECTION_OUTLINE_PADDING);
      g2d.draw(rect);
    }

    private double rangeToPosition(double r) {
      return (r - myRange.getMin()) / myRange.getLength() * getWidth();
    }
  }

  private final static class TableTooltipView extends MouseAdapter {
    @NotNull private final NetworkProfilerStage myStage;
    @NotNull private final JTable myTable;

    @NotNull private final JPanel myComponent;
    @NotNull private final TooltipComponent myTooltipComponent;
    @NotNull private final JLabel myLabel;


    TableTooltipView(@NotNull JTable table, @NotNull NetworkProfilerStage stage) {
      myTable = table;
      myStage = stage;

      myLabel = new JLabel();
      myLabel.setForeground(ProfilerColors.MONITORS_HEADER_TEXT);
      myLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
      myLabel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
      myLabel.setFont(myLabel.getFont().deriveFont(ProfilerLayout.TOOLTIP_FONT_SIZE));
      myLabel.setOpaque(true);

      myComponent = new JPanel(new TabularLayout("*", "*"));
      myTooltipComponent = new TooltipComponent(myLabel);
      myTooltipComponent.registerListenersOn(myComponent);
      myTooltipComponent.setVisible(false);

      myComponent.add(myTooltipComponent, new TabularLayout.Constraint(0, 0));
      myComponent.setOpaque(false);
      myComponent.addMouseMotionListener(this);
      myComponent.addMouseListener(this);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      myTooltipComponent.setVisible(false);
      HttpData data = findHttpDataUnderCursor(e);
      if (data != null) {
        showTooltip(data);
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      HttpData data = findHttpDataUnderCursor(e);
      if (data != null) {
        myStage.setSelectedConnection(data);
        e.consume();
      }
    }

    @Nullable
    private HttpData findHttpDataUnderCursor(@NotNull MouseEvent e) {
      Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myTable);
      int row = myTable.rowAtPoint(p);
      int column = myTable.columnAtPoint(p);

      if (row == -1 || column == -1) {
        return null;
      }

      if (column == 1) {
        Rectangle cellBounds = myTable.getCellRect(row, column, false);
        List<HttpData> dataList = (List<HttpData>)myTable.getModel().getValueAt(row, 1);
        double at = positionToRange(p.x - cellBounds.x, cellBounds.getWidth());
        for (HttpData data : dataList) {
          if (data.getStartTimeUs() <= at && at <= data.getEndTimeUs()) {
            return data;
          }
        }
      }

      return null;
    }

    JComponent getComponent() {
      return myComponent;
    }

    private void showTooltip(@NotNull HttpData data) {
      myTooltipComponent.setVisible(true);

      StringBuilder text = new StringBuilder("<html> <style> p { margin-bottom: 5px; }  p, li { font-size: 11;}</style>");
      text.append("<p style='font-size:12.5'>").append(data.getJavaThreads().get(0).getName()).append("</p>");

      text.append("<p>").append(HttpData.getUrlName(data.getUrl())).append("</p>");

      if (data.getJavaThreads().size() > 1) {
        text.append("<p style='margin-bottom:-5;'>Also accessed by:</p>");
        text.append("<ul>");
        for (int i = 1; i < data.getJavaThreads().size(); ++i) {
          text.append("<li>").append(data.getJavaThreads().get(i).getName()).append("</li>");
        }
        text.append("</ul>");
      }
      text.append("</html>");

      myLabel.setText(text.toString());
    }

    private double positionToRange(double x, double width) {
      Range range = myStage.getStudioProfilers().getTimeline().getSelectionRange();
      return (x * range.getLength()) / width + range.getMin();
    }
  }
}
