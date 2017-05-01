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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTimeline;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/**
 * Displays network connection information of all threads.
 */
final class ThreadsView {
  private static final int ROW_HEIGHT = 19;

  @NotNull
  private final JTable myThreadsTable;

  ThreadsView(@NotNull NetworkProfilerStageView stageView) {
    myThreadsTable = new HoverRowTable(new ThreadsTableModel(stageView.getStage()), ProfilerColors.NETWORK_TABLE_HOVER_COLOR);
    myThreadsTable.getColumnModel().getColumn(1).setCellRenderer(new TimelineRenderer(myThreadsTable, stageView.getTimeline()));
    myThreadsTable.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myThreadsTable.setFont(AdtUiUtils.DEFAULT_FONT);
    myThreadsTable.setShowVerticalLines(true);
    myThreadsTable.setShowHorizontalLines(false);
    myThreadsTable.setTableHeader(null);
    myThreadsTable.setCellSelectionEnabled(false);
    myThreadsTable.setFocusable(false);
    myThreadsTable.setRowMargin(0);

    myThreadsTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myThreadsTable.getColumnModel().getColumn(0).setPreferredWidth((int)(myThreadsTable.getWidth() * 1.0 / 8));
        myThreadsTable.getColumnModel().getColumn(1).setPreferredWidth((int)(myThreadsTable.getWidth() * 7.0 / 8));
      }
    });

    myThreadsTable.setRowHeight(ROW_HEIGHT);
  }

  @NotNull
  JComponent getComponent() {
    return myThreadsTable;
  }

  private static final class ThreadsTableModel extends AbstractTableModel {
    @NotNull private final AspectObserver myAspectObserver;
    @NotNull private final NetworkProfilerStage myStage;
    @NotNull private final List<List<HttpData>> myThreads;

    private ThreadsTableModel(@NotNull NetworkProfilerStage stage) {
      myStage = stage;
      myAspectObserver = new AspectObserver();
      myThreads = new ArrayList<>();

      Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
      selection.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
      rangeChanged();
    }

    public void rangeChanged() {
      Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
      myThreads.clear();
      if (selection.isEmpty()) {
        fireTableDataChanged();
        return;
      }

      List<HttpData> dataList = myStage.getConnectionsModel().getData(selection);
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
    @NotNull private final ProfilerTimeline myTimeline;

    TimelineRenderer(@NotNull JTable table, @NotNull ProfilerTimeline timeline) {
      myTable = table;
      myTimeline = timeline;
      myRows = new ArrayList<>();

      myTable.getModel().addTableModelListener(this);
      tableChanged(new TableModelEvent(myTable.getModel()));
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      myRows.clear();
      for (int index = 0; index < myTable.getModel().getRowCount(); ++index) {
        List<HttpData> data = (List<HttpData>)myTable.getModel().getValueAt(index, 1);
        assert !data.isEmpty();
        AxisComponent axis = createAxis();
        axis.setMarkerLengths(myTable.getRowHeight(), 0);
        // If it is the first row show labels.
        axis.setShowLabels(index == 0);

        JPanel panel = new JPanel(new TabularLayout("*", "*"));
        panel.setPreferredSize(new Dimension((int)panel.getPreferredSize().getWidth(), myTable.getRowHeight()));
        panel.add(axis, new TabularLayout.Constraint(0, 0));
        panel.add(new ConnectionsInfoComponent(data, myTimeline.getSelectionRange()), new TabularLayout.Constraint(0, 0));
        myRows.add(panel);
      }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return myRows.get(row);
    }

    @NotNull
    private AxisComponent createAxis() {
      AxisComponentModel model = new AxisComponentModel(myTimeline.getSelectionRange(), new TimeAxisFormatter(1, 5, 1));
      model.setClampToMajorTicks(false);
      model.setGlobalRange(myTimeline.getDataRange());

      AxisComponent axis = new AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM);
      axis.setShowAxisLine(false);
      axis.setMarkerColor(ProfilerColors.NETWORK_TABLE_AXIS);
      axis.setForeground(ProfilerColors.NETWORK_TABLE_AXIS);
      model.update(1);
      return axis;
    }
  }

  /**
   * A component that responsible for rendering information of the given connections,
   * such as connection names, warnings, and lifecycle states.
   */
  private static final class ConnectionsInfoComponent extends JComponent {
    private static final int PADDING = 6;
    private static final int STATE_HEIGHT = 15;
    private static final int WARNING_SIZE = 10;
    @NotNull private final List<HttpData> myDataList;
    @NotNull private final Range myRange;

    private ConnectionsInfoComponent(@NotNull List<HttpData> data, @NotNull Range range) {
      myDataList = data;
      myRange = range;
      setFont(AdtUiUtils.DEFAULT_FONT);
      setForeground(AdtUiUtils.DEFAULT_FONT_COLOR);
      setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D)g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      for (int i = 0; i < myDataList.size(); ++i) {
        HttpData data = myDataList.get(i);
        float endLimit = (i + 1 < myDataList.size()) ? rangeToPosition(myDataList.get(i + 1).getStartTimeUs()) : getWidth();

        drawState(g2d,  data, endLimit);

        if (data.getJavaThreads().size() > 1) {
          drawWarning(g2d, data, endLimit);
        }

        drawConnectionName(g2d, data, endLimit);
      }

      g2d.dispose();
    }

    private void drawState(@NotNull Graphics2D g2d, @NotNull HttpData data, float endLimit) {
      float prev = rangeToPosition(data.getStartTimeUs());
      g2d.setColor(ProfilerColors.NETWORK_THREADS_TABLE_SENDING);

      if (data.getDownloadingTimeUs() > 0) {
        float download = rangeToPosition(data.getDownloadingTimeUs());
        // draw sending
        g2d.fill(new Rectangle2D.Float(prev, (getHeight() - STATE_HEIGHT) / 2, download - prev, STATE_HEIGHT));
        g2d.setColor(ProfilerColors.NETWORK_THREADS_TABLE_RECEIVING);
        prev = download;
      }

      float end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;
      g2d.fill(new Rectangle2D.Float(prev, (getHeight() - STATE_HEIGHT) / 2, end - prev, STATE_HEIGHT));
    }

    private void drawWarning(@NotNull Graphics2D g2d, @NotNull HttpData data, float endLimit) {
      float start = rangeToPosition(data.getStartTimeUs());
      float end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;

      float stateY = (getHeight() - STATE_HEIGHT) / 2;

      Path2D.Float triangle = new Path2D.Float();
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

    private void drawConnectionName(@NotNull Graphics2D g2d, @NotNull HttpData data, float endLimit) {
      g2d.setFont(getFont());
      g2d.setColor(getForeground());
      float start = rangeToPosition(data.getStartTimeUs());
      float end = (data.getEndTimeUs() > 0) ? rangeToPosition(data.getEndTimeUs()) : endLimit;

      FontMetrics metrics = getFontMetrics(getFont());
      String text =
        AdtUiUtils.getFittedString(metrics, HttpData.getUrlName(data.getUrl()), end - start - 2 * PADDING, 1);

      float availableSpace = (end - start - metrics.stringWidth(text));
      g2d.drawString(text, start + availableSpace / 2, (getHeight() - metrics.getHeight()) * 0.5f + metrics.getAscent());
    }

    private float rangeToPosition(float r) {
      return (float)((r - myRange.getMin()) / myRange.getLength() * getWidth());
    }
  }
}
