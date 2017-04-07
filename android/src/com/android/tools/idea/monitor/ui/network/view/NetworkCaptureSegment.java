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
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.network.model.HttpData;
import com.android.tools.idea.monitor.ui.network.model.NetworkCaptureModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
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

  private static final EnumMap<NetworkState, Color> NETWORK_STATE_COLORS = new EnumMap<>(NetworkState.class);

  static {
    NETWORK_STATE_COLORS.put(NetworkState.SENDING, Constants.NETWORK_SENDING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.RECEIVING, Constants.NETWORK_RECEIVING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.WAITING, Constants.NETWORK_WAITING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
  }

  /**
   * Columns for each connection information
   */
  private enum Column {
    INDEX, URL, SIZE, DURATION, TIMELINE
  }

  private int myRowHeight;

  @NotNull
  private final List<StateChart<NetworkState>> myCharts;

  @NotNull
  private List<HttpData> myDataList;

  @NotNull
  private final DetailedViewListener myDetailedViewListener;

  @NotNull
  private final NetworkCaptureModel myModel;

  private JTable myCaptureTable;

  public NetworkCaptureSegment(@NotNull Range timeCurrentRangeUs,
                               @NotNull NetworkCaptureModel model,
                               @NotNull DetailedViewListener detailedViewListener,
                               @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dispatcher);
    myDetailedViewListener = detailedViewListener;
    myCharts = new ArrayList<>();
    myDataList = new ArrayList<>();
    myModel = model;
    int defaultFontHeight = getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    myRowHeight = defaultFontHeight + ROW_HEIGHT_PADDING;
  }

  @NotNull
  private JTable createCaptureTable() {
    JBTable table = new JBTable(new NetworkCaptureTableModel());
    table.setDefaultRenderer(StateChart.class, (t, value, isSelected, hasFocus, row, column) -> myCharts.get(row));
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(e -> {
      if (table.getSelectedRow() < myDataList.size()) {
        myDetailedViewListener.showDetailedConnection(myDataList.get(table.getSelectedRow()));
      }
    });
    table.setFont(AdtUiUtils.DEFAULT_FONT);
    table.setRowHeight(myRowHeight);

    table.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        table.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setPreferredWidth(myCaptureTable.getWidth() / 2);
      }
    });
    return table;
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    myCaptureTable = createCaptureTable();

    panel.add(new JBScrollPane(myCaptureTable), BorderLayout.CENTER);
  }

  @Override
  public void animate(float frameLength) {
    myDataList = myModel.getData(myTimeCurrentRangeUs);
    // TODO: currently we recreate charts from scratch, instead consider reusing charts
    myCharts.clear();

    for (HttpData data: myDataList) {
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
      chart.addSeries(new RangedSeries<>(myTimeCurrentRangeUs, series));
      chart.animate(frameLength);
      myCharts.add(chart);
    }
  }

  private final class NetworkCaptureTableModel extends AbstractTableModel {
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
      return StringUtil.capitalize(Column.values()[column].toString().toLowerCase());
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
  }
}
