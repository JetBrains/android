/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.RangedTable;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.RangedTableModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerColors.NETWORK_RECEIVING_COLOR;
import static com.android.tools.profilers.ProfilerColors.NETWORK_SENDING_COLOR;
import static com.android.tools.profilers.ProfilerColors.NETWORK_WAITING_COLOR;

/**
 * This class responsible for displaying table of requests information (e.g url, duration, timeline) for network profiling.
 */
public class NetworkRequestsView {
  private static final int ROW_HEIGHT_PADDING = 5;

  public interface DetailedViewListener {
    void showDetailedConnection(HttpData data);
  }

  private enum NetworkState {
    SENDING, RECEIVING, WAITING, NONE
  }

  private static final EnumMap<NetworkState, Color> NETWORK_STATE_COLORS = new EnumMap<>(NetworkState.class);

  static {
    NETWORK_STATE_COLORS.put(NetworkState.SENDING, NETWORK_SENDING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.RECEIVING, NETWORK_RECEIVING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.WAITING, NETWORK_WAITING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
  }

  /**
   * Columns for each connection information
   */
  private enum Column {
    INDEX, URL, SIZE, DURATION, TIMELINE
  }

  @NotNull
  private final DetailedViewListener myDetailedViewListener;

  @NotNull
  private final NetworkRequestsModel myModel;

  @NotNull
  private final NetworkProfilerStageView myStageView;

  @NotNull
  private final NetworkRequestsTableModel myTableModel;

  @NotNull
  private final JTable myRequestsTable;

  public NetworkRequestsView(@NotNull NetworkProfilerStageView stageView,
                             @NotNull NetworkRequestsModel model,
                             @NotNull DetailedViewListener detailedViewListener) {
    myStageView = stageView;
    myModel = model;
    myDetailedViewListener = detailedViewListener;
    myTableModel = new NetworkRequestsTableModel();
    myRequestsTable = createRequestsTable();
    RangedTable rangedTable = new RangedTable(stageView.getTimeline().getViewRange(), myTableModel);
    stageView.getChoreographer().register(rangedTable);
  }

  @NotNull
  public JComponent getComponent() {
    return myRequestsTable;
  }

  @NotNull
  private JTable createRequestsTable() {
    JTable table = new JBTable(myTableModel);
    table.setDefaultRenderer(StateChart.class, new NetworkTimelineRenderer(myTableModel));
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(e -> {
      int selectedRow = table.getSelectedRow();
      if (0 <= selectedRow && selectedRow < myTableModel.getRowCount()) {
        myDetailedViewListener.showDetailedConnection(myTableModel.getHttpData(selectedRow));
      }
    });
    table.setFont(AdtUiUtils.DEFAULT_FONT);

    int defaultFontHeight = table.getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    table.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);

    table.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        table.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setPreferredWidth(myRequestsTable.getWidth() / 2);
      }
    });

    return table;
  }

  private final class NetworkRequestsTableModel extends AbstractTableModel implements RangedTableModel {
    @NotNull private List<HttpData> myDataList = new ArrayList<>();

    @Override
    public int getRowCount() {
      return myDataList.size();
    }

    @Override
    public int getColumnCount() {
      return Column.values().length;
    }

    @Override
    public String getColumnName(int column) {
      return StringUtil.capitalize(Column.values()[column].toString().toLowerCase(Locale.getDefault()));
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      switch (Column.values()[columnIndex]) {
        case TIMELINE:
          return StateChart.class;
        default:
          return Object.class;
      }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (Column.values()[columnIndex]) {
        case INDEX:
          return String.valueOf(rowIndex);

        case URL:
          return myDataList.get(rowIndex).getUrl();

        case SIZE:
          long bytes = myDataList.get(rowIndex).getHttpResponseBodySize();
          return bytes >= 0 ? String.valueOf(bytes / 1024) + " K" : "";

        case DURATION:
          HttpData httpData = myDataList.get(rowIndex);
          if (httpData.getEndTimeUs() >= httpData.getStartTimeUs()) {
            long durationMs = TimeUnit.MICROSECONDS.toMillis(httpData.getEndTimeUs() - httpData.getStartTimeUs());
            return String.valueOf(durationMs) + " ms";
          }
          break;

        case TIMELINE:
          // This column has a custom renderer; see getColumnClass
          break;

        default:
          throw new UnsupportedOperationException("Unexpected getValueAt called with: " + Column.values()[columnIndex]);
      }
      return "";
    }

    @NotNull
    public HttpData getHttpData(int rowIndex) {
      return myDataList.get(rowIndex);
    }

    @Override
    public void update(@NotNull Range range) {
      myDataList = myModel.getData(range);
      fireTableDataChanged();
    }
  }

  private final class NetworkTimelineRenderer implements TableCellRenderer, TableModelListener {
    @NotNull private final List<StateChart<NetworkState>> myCharts;
    @NotNull private final NetworkRequestsTableModel myTableModel;

    NetworkTimelineRenderer(@NotNull NetworkRequestsTableModel tableModel) {
      myCharts = new ArrayList<>();
      myTableModel = tableModel;
      myTableModel.addTableModelListener(this);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return myCharts.get(row);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      myCharts.clear();
      for (int i = 0; i < myTableModel.getRowCount(); ++i) {
        HttpData data = myTableModel.getHttpData(i);
        DefaultDataSeries<NetworkState> series = new DefaultDataSeries<>();
        series.add(0, NetworkState.NONE);
        series.add(data.getStartTimeUs(), NetworkState.SENDING);
        if (data.getDownloadingTimeUs() > 0) {
          series.add(data.getDownloadingTimeUs(), NetworkState.RECEIVING);
        }
        if (data.getEndTimeUs() > 0) {
          series.add(data.getEndTimeUs(), NetworkState.NONE);
        }

        StateChart<NetworkState> chart = new StateChart<>(NETWORK_STATE_COLORS);
        chart.addSeries(new RangedSeries<>(myStageView.getTimeline().getViewRange(), series));
        chart.animate(1);
        myCharts.add(chart);
      }
    }
  }
}
