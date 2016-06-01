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
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedDiscreteSeries;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
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

public class NetworkCaptureSegment extends BaseSegment {
  private static final String SEGMENT_NAME = "Network Capture";
  private static final int ROW_HEIGHT_PADDING = 5;

  public enum NetworkState {
    SENDING, RECEIVING, WAITING, NONE
  }

  private static final EnumMap<NetworkState, Color> NETWORK_STATE_COLORS = new EnumMap<>(NetworkState.class);

  static {
    NETWORK_STATE_COLORS.put(NetworkState.SENDING, AdtUiUtils.NETWORK_SENDING);
    NETWORK_STATE_COLORS.put(NetworkState.RECEIVING, AdtUiUtils.NETWORK_RECEIVING);
    NETWORK_STATE_COLORS.put(NetworkState.WAITING, AdtUiUtils.NETWORK_WAITING);
    NETWORK_STATE_COLORS.put(NetworkState.NONE, JBColor.white);
  }

  private int mRowHeight;

  @NotNull
  private final List<StateChart<NetworkState>> mCharts;

  @NotNull
  private final List<RangedDiscreteSeries<NetworkState>> mData;

  public NetworkCaptureSegment(@NotNull Range timeRange, @NotNull List<RangedDiscreteSeries<NetworkState>> data) {
    super(SEGMENT_NAME, timeRange);
    mCharts = new ArrayList<>();
    mData = data;

    int defaultFontHeight = getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    mRowHeight = defaultFontHeight + ROW_HEIGHT_PADDING;
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    for (int i = 0; i < mData.size(); ++i) {
      StateChart<NetworkState> chart = new StateChart<>(NETWORK_STATE_COLORS);
      chart.addSeries(mData.get(i));
      animatables.add(chart);
      mCharts.add(chart);
    }
  }

  @NotNull
  private JTable createInformationTable() {
    JBTable table = new JBTable(new AbstractTableModel() {
      final String[] SAMPLE = {"2","http://myapp.example.com/list/xml", "746 K", "322 ms", ""};

      @Override
      public int getRowCount() {
        return mCharts.size();
      }

      @Override
      public int getColumnCount() {
        return SAMPLE.length;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
          return "" + rowIndex;
        }
        return SAMPLE[columnIndex];
      }
    });
    table.setFont(AdtUiUtils.DEFAULT_FONT);
    table.setOpaque(false);
    ((DefaultTableCellRenderer)table.getDefaultRenderer(Object.class)).setOpaque(false);

    table.setRowHeight(mRowHeight);
    return table;
  }

  @NotNull
  private JTable createStateChartTable() {
    JBTable table = new JBTable(new AbstractTableModel() {

      @Override
      public int getRowCount() {
        return mCharts.size();
      }

      @Override
      public int getColumnCount() {
        return 1;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        return mCharts.get(rowIndex);
      }
    });
    table.setDefaultRenderer(Object.class, (t, value, isSelected, hasFocus, row, column) -> mCharts.get(row));
    table.setRowHeight(mRowHeight);
    return table;
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    JLayeredPane pane = new JLayeredPane();

    pane.add(createInformationTable());
    pane.add(createStateChartTable());

    pane.addComponentListener(new ComponentAdapter() {
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

    pane.setPreferredSize(new Dimension(0, mRowHeight * mCharts.size()));
    panel.add(new JBScrollPane(pane), BorderLayout.CENTER);
  }
}
