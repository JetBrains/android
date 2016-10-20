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
package com.android.tools.idea.monitor.ui.network.view;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.network.model.HttpData;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NetworkCaptureSegment extends BaseSegment implements Animatable {

  public interface DetailedViewListener {
    void showDetailedConnection(HttpData data);
  }

  private static final String SEGMENT_NAME = "Network Capture";

  private static final int ROW_HEIGHT_PADDING = 5;

  public enum NetworkState {
    SENDING, RECEIVING, WAITING, NONE
  }

  /**
   * Our table is transparent, but the default table selection color assumes an opaque background.
   * We sidestep this readability issue by specifying the text color explicitly.
   */
  private static final Color TABLE_TEXT_COLOR = JBColor.BLACK;

  private static final EnumMap<NetworkState, Color> NETWORK_STATE_COLORS = new EnumMap<>(NetworkState.class);

  static {
    NETWORK_STATE_COLORS.put(NetworkState.SENDING, Constants.NETWORK_SENDING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.RECEIVING, Constants.NETWORK_RECEIVING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.WAITING, Constants.NETWORK_WAITING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
  }

  /**
   * Columns for each connection information, the last SPACER column is intentionally left blank.
   */
  private enum Column {
    INDEX, URL, BYTES, DURATION, SPACER
  }

  private int myRowHeight;

  @NotNull
  private final List<StateChart<NetworkState>> myCharts;

  @NotNull
  private final List<HttpData> myDataList;

  @NotNull
  private final DetailedViewListener myDetailedViewListener;

  @NotNull
  private final SeriesDataStore myDataStore;

  private JComponent myLayeredComponent;

  private JTable myInformationTable;

  private JTable myStateTable;

  public NetworkCaptureSegment(@NotNull Range timeCurrentRangeUs,
                               @NotNull SeriesDataStore dataStore,
                               @NotNull DetailedViewListener detailedViewListener,
                               @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dispatcher);
    myDetailedViewListener = detailedViewListener;
    myCharts = new ArrayList<>();
    myDataList = new ArrayList<>();
    myDataStore = dataStore;

    int defaultFontHeight = getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    myRowHeight = defaultFontHeight + ROW_HEIGHT_PADDING;
  }

  @NotNull
  private JTable createInformationTable() {
    JBTable table = new JBTable(new AbstractTableModel() {
      @Override
      public int getRowCount() {
        return myCharts.size();
      }

      @Override
      public int getColumnCount() {
        return Column.values().length;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        switch (Column.values()[columnIndex]) {
          case INDEX:
            return String.valueOf(rowIndex);

          case URL:
            return myDataList.get(rowIndex).getUrl();

          case BYTES:
            long bytes = myDataList.get(rowIndex).getHttpResponseBodySize();
            return bytes >= 0 ? String.valueOf(bytes / 1024) + " K" : "";

          case DURATION:
            HttpData httpData = myDataList.get(rowIndex);
            if (httpData.getEndTimeUs() >= httpData.getStartTimeUs()) {
              long durationMs = TimeUnit.MICROSECONDS.toMillis(httpData.getEndTimeUs() - httpData.getStartTimeUs());
              return String.valueOf(durationMs) + " ms";
            }
            break;

          default:
            // Empty
            break;
        }
        return "";
      }
    });
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(e -> {
      if (table.getSelectedRow() < myDataList.size()) {
        myDetailedViewListener.showDetailedConnection(myDataList.get(table.getSelectedRow()));
      }
    });
    table.setFont(AdtUiUtils.DEFAULT_FONT);
    table.setOpaque(false);
    ((DefaultTableCellRenderer)table.getDefaultRenderer(Object.class)).setOpaque(false);
    table.setForeground(TABLE_TEXT_COLOR);
    table.setSelectionForeground(TABLE_TEXT_COLOR);

    table.setRowHeight(myRowHeight);
    return table;
  }

  @NotNull
  private JTable createStateChartTable() {
    JBTable table = new JBTable(new AbstractTableModel() {

      @Override
      public int getRowCount() {
        return myCharts.size();
      }

      @Override
      public int getColumnCount() {
        return 1;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        return myCharts.get(rowIndex);
      }
    });
    table.setDefaultRenderer(Object.class, (t, value, isSelected, hasFocus, row, column) -> myCharts.get(row));
    table.setRowHeight(myRowHeight);
    return table;
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    myLayeredComponent = new JLayeredPane();
    myInformationTable = createInformationTable();
    myStateTable = createStateChartTable();

    myLayeredComponent.add(myInformationTable);
    myLayeredComponent.add(myStateTable);

    myLayeredComponent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            c.setBounds(0, 0, dim.width, dim.height);
          }
        }
      }
    });

    panel.add(new JBScrollPane(myLayeredComponent), BorderLayout.CENTER);
  }

  @Override
  public void animate(float frameLength) {

    List<SeriesData<HttpData>> dataList = myDataStore.getSeriesData(SeriesDataType.NETWORK_HTTP_DATA, myTimeCurrentRangeUs);

    // TODO: currently we recreate charts from scratch, instead consider reusing charts
    myCharts.clear();
    myDataList.clear();
    for (SeriesData<HttpData> data: dataList) {
      DefaultDataSeries<NetworkState> series = new DefaultDataSeries<>();
      series.add(0, NetworkState.NONE);
      series.add(data.value.getStartTimeUs(), NetworkState.SENDING);
      if (data.value.getDownloadingTimeUs() > 0) {
        series.add(data.value.getDownloadingTimeUs(), NetworkState.RECEIVING);
      }
      if (data.value.getEndTimeUs() > 0) {
        series.add(data.value.getEndTimeUs(), NetworkState.NONE);
      }

      StateChart<NetworkState> chart = new StateChart<>(NETWORK_STATE_COLORS);
      chart.addSeries(new RangedSeries<>(myTimeCurrentRangeUs, series));
      chart.animate(frameLength);
      myCharts.add(chart);
      myDataList.add(data.value);
    }
    myLayeredComponent.setPreferredSize(new Dimension(0, myRowHeight * myCharts.size()));
    myStateTable.repaint();
    myInformationTable.repaint();
  }
}
