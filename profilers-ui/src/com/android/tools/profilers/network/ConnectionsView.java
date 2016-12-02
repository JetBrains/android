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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerColors.*;

/**
 * This class responsible for displaying table of connections information (e.g url, duration, timeline)
 * for network profiling. Each row in the table represents a single connection.
 */
final class ConnectionsView {
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
    URL(0.25), SIZE(0.25/4), TYPE(0.25/4), STATUS(0.25/4), TIME(0.25/4), TIMELINE(0.5);

    private final double myWidthPercentage;

    Column(double widthPercentage) {
      myWidthPercentage = widthPercentage;
    }

    public double getWidthPercentage() {
      return myWidthPercentage;
    }

    public String toDisplayString() {
      return StringUtil.capitalize(name().toLowerCase(Locale.getDefault()));
    }
  }

  @NotNull
  private final DetailedViewListener myDetailedViewListener;

  @NotNull
  private final NetworkProfilerStageView myStageView;

  @NotNull
  private final ConnectionsTableModel myTableModel;

  @NotNull
  private final JTable myConnectionsTable;

  public ConnectionsView(@NotNull NetworkProfilerStageView stageView,
                         @NotNull DetailedViewListener detailedViewListener) {
    myStageView = stageView;
    myDetailedViewListener = detailedViewListener;
    myTableModel = new ConnectionsTableModel();
    myConnectionsTable = createRequestsTable();
    RangedTable rangedTable = new RangedTable(stageView.getTimeline().getSelectionRange(), myTableModel);
    stageView.getChoreographer().register(rangedTable);
  }

  @NotNull
  public JComponent getComponent() {
    return myConnectionsTable;
  }

  @NotNull
  private JTable createRequestsTable() {
    JTable table = new JBTable(myTableModel);
    table.setAutoCreateRowSorter(true);
    table.getColumnModel().getColumn(Column.SIZE.ordinal()).setCellRenderer(new SizeRenderer());
    table.getColumnModel().getColumn(Column.STATUS.ordinal()).setCellRenderer(new StatusRenderer());
    table.getColumnModel().getColumn(Column.TIME.ordinal()).setCellRenderer(new TimeRenderer());
    table.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setCellRenderer(
      new TimelineRenderer(table, myStageView.getTimeline().getSelectionRange()));

    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(e -> {
      int selectedRow = table.getSelectedRow();
      if (0 <= selectedRow && selectedRow < myTableModel.getRowCount()) {
        int modelRow = myConnectionsTable.convertRowIndexToModel(selectedRow);
        myDetailedViewListener.showDetailedConnection(myTableModel.getHttpData(modelRow));
      }
    });
    table.setFont(AdtUiUtils.DEFAULT_FONT);

    int defaultFontHeight = table.getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    table.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);

    table.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        for (int i = 0; i < Column.values().length; ++i) {
          Column column = Column.values()[i];
          table.getColumnModel().getColumn(i).setPreferredWidth((int)(table.getWidth() * column.getWidthPercentage()));
        }
      }
    });

    // Keep the previously selected row selected if it's still there
    myTableModel.addTableModelListener(e ->
      ApplicationManager.getApplication().invokeLater(() -> {
        // Invoke later, because table itself listener of table model.
        HttpData selectedData = myStageView.getStage().getConnection();
        if (selectedData != null) {
          for (int i = 0; i < myTableModel.getRowCount(); ++i) {
            if (myTableModel.getHttpData(i).getId() == selectedData.getId()) {
              int row = table.convertRowIndexToView(i);
              table.setRowSelectionInterval(row, row);
              break;
            }
          }
        }
      })
    );

    return table;
  }

  private final class ConnectionsTableModel extends AbstractTableModel implements RangedTableModel {
    @NotNull private List<HttpData> myDataList = new ArrayList<>();
    @NotNull private final Range myLastRange = new Range(0, 0);

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
      return Column.values()[column].toDisplayString();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      HttpData data = myDataList.get(rowIndex);
      switch (Column.values()[columnIndex]) {
        case URL:
          return data.getUrl();

        case TYPE:
          String contentType = data.getResponseField(HttpData.FIELD_CONTENT_TYPE);
          return StringUtil.notNullize(contentType);

        case SIZE:
          String contentLength = data.getResponseField(HttpData.FIELD_CONTENT_LENGTH);
          return (contentLength != null) ? Integer.parseInt(contentLength) : -1;

        case STATUS:
          return data.getStatusCode();

        case TIME:
          return data.getEndTimeUs() - data.getStartTimeUs();

        case TIMELINE:
          return data.getStartTimeUs();

        default:
          throw new UnsupportedOperationException("Unexpected getValueAt called with: " + Column.values()[columnIndex]);
      }
    }

    @NotNull
    public HttpData getHttpData(int rowIndex) {
      return myDataList.get(rowIndex);
    }

    @Override
    public void update(@NotNull Range range) {
      if (myLastRange.getMin() != range.getMin() || myLastRange.getMax() != range.getMax()) {
        myDataList = myStageView.getStage().getRequestsModel().getData(range);
        fireTableDataChanged();
        myLastRange.set(range);
      }
    }
  }

  private static final class SizeRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      int bytes = (Integer)value;
      setText(bytes >= 0 ? StringUtil.formatFileSize(bytes) : "");
    }
  }

  private static final class StatusRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      Integer status = (Integer)value;
      setText(status > -1 ? Integer.toString(status) : "");
    }
  }

  private static final class TimeRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      Long durationUs = (Long)value;
      if (durationUs >= 0) {
        long durationMs = TimeUnit.MICROSECONDS.toMillis(durationUs);
        setText(StringUtil.formatDuration(durationMs));
      }
      else {
        setText("");
      }
    }
  }

  private static final class TimelineRenderer implements TableCellRenderer, TableModelListener {
    /**
     * Keep in sync 1:1 with {@link ConnectionsTableModel#myDataList}. When the table asks for the
     * chart to render, it will be converted from model index to view index.
     */
    @NotNull private final List<StateChart<NetworkState>> myCharts;
    @NotNull private final JTable myTable;
    private final Range myRange;

    TimelineRenderer(@NotNull JTable table, @NotNull Range range) {
      myRange = range;
      myCharts = new ArrayList<>();
      myTable = table;
      myTable.getModel().addTableModelListener(this);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return myCharts.get(myTable.convertRowIndexToModel(row));
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      myCharts.clear();
      ConnectionsTableModel model = (ConnectionsTableModel)myTable.getModel();
      for (int i = 0; i < model.getRowCount(); ++i) {
        HttpData data = model.getHttpData(i);
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
        chart.addSeries(new RangedSeries<>(myRange, series));
        chart.animate(1);
        myCharts.add(chart);
      }
    }
  }
}
