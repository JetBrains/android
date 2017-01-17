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

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.*;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExpandedItemRendererComponentWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerColors.*;

/**
 * This class responsible for displaying table of connections information (e.g url, duration, timeline)
 * for network profiling. Each row in the table represents a single connection.
 */
final class ConnectionsView {
  private static final int ROW_HEIGHT_PADDING = JBUI.scale(8);

  private Range mySelectionRange;

  public interface DetailedViewListener {
    void showDetailedConnection(HttpData data);
  }

  private enum NetworkState {
    SENDING, RECEIVING, WAITING, NONE
  }


  /**
   * Columns for each connection information
   */
  @VisibleForTesting
  enum Column {
    URL(0.25, String.class) {
      @Override
      Object getValueFrom(@NotNull HttpData data) {
        return data.getUrl();
      }
    },
    SIZE(0.25/4, Integer.class) {
      @Override
      Object getValueFrom(@NotNull HttpData data) {
        String contentLength = data.getResponseField(HttpData.FIELD_CONTENT_LENGTH);
        return (contentLength != null) ? Integer.parseInt(contentLength) : -1;
      }
    },
    TYPE(0.25/4, String.class) {
      @Override
      Object getValueFrom(@NotNull HttpData data) {
        String contentType = data.getResponseField(HttpData.FIELD_CONTENT_TYPE);
        return StringUtil.notNullize(contentType);
      }
    },
    STATUS(0.25/4, Integer.class) {
      @Override
      Object getValueFrom(@NotNull HttpData data) {
        return data.getStatusCode();
      }
    },
    TIME(0.25/4, Long.class) {
      @Override
      Object getValueFrom(@NotNull HttpData data) {
        return data.getEndTimeUs() - data.getStartTimeUs();
      }
    },
    TIMELINE(0.5, Long.class) {
      @Override
      Object getValueFrom(@NotNull HttpData data) {
        return data.getStartTimeUs();
      }
    };

    private final double myWidthPercentage;
    private final Class<?> myType;

    Column(double widthPercentage, Class<?> type) {
      myWidthPercentage = widthPercentage;
      myType = type;
    }

    public double getWidthPercentage() {
      return myWidthPercentage;
    }

    public Class<?> getType() {
      return myType;
    }

    public String toDisplayString() {
      return StringUtil.capitalize(name().toLowerCase(Locale.getDefault()));
    }

    abstract Object getValueFrom(@NotNull HttpData data);
  }

  @NotNull
  private final DetailedViewListener myDetailedViewListener;

  @NotNull
  private final NetworkProfilerStage myStage;

  @NotNull
  private final ConnectionsTableModel myTableModel;

  @NotNull
  private final JTable myConnectionsTable;

  public ConnectionsView(@NotNull NetworkProfilerStageView stageView,
                         @NotNull DetailedViewListener detailedViewListener) {
    this(stageView.getStage(), stageView.getTimeline().getSelectionRange(), detailedViewListener);
  }

  @VisibleForTesting
  public ConnectionsView(@NotNull NetworkProfilerStage stage,
                         @NotNull Range selectionRange,
                         @NotNull DetailedViewListener detailedViewListener) {
    myStage = stage;
    myDetailedViewListener = detailedViewListener;
    myTableModel = new ConnectionsTableModel(selectionRange);
    mySelectionRange = selectionRange;

    myConnectionsTable = new ConnectionsTable(myTableModel);
    customizeConnectionsTable();
  }

  @NotNull
  public JComponent getComponent() {
    return myConnectionsTable;
  }

  private void customizeConnectionsTable() {
    myConnectionsTable.setAutoCreateRowSorter(true);
    myConnectionsTable.getColumnModel().getColumn(Column.SIZE.ordinal()).setCellRenderer(new SizeRenderer());
    myConnectionsTable.getColumnModel().getColumn(Column.STATUS.ordinal()).setCellRenderer(new StatusRenderer());
    myConnectionsTable.getColumnModel().getColumn(Column.TIME.ordinal()).setCellRenderer(new TimeRenderer());
    myConnectionsTable.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setCellRenderer(
      new TimelineRenderer(myConnectionsTable, mySelectionRange));

    myConnectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myConnectionsTable.getSelectionModel().addListSelectionListener(e -> {
      int selectedRow = myConnectionsTable.getSelectedRow();
      if (0 <= selectedRow && selectedRow < myTableModel.getRowCount()) {
        int modelRow = myConnectionsTable.convertRowIndexToModel(selectedRow);
        myDetailedViewListener.showDetailedConnection(myTableModel.getHttpData(modelRow));
      }
    });

    myConnectionsTable.setFont(AdtUiUtils.DEFAULT_FONT);
    myConnectionsTable.setShowVerticalLines(true);
    myConnectionsTable.setShowHorizontalLines(false);
    int defaultFontHeight = myConnectionsTable.getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    myConnectionsTable.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);

    myConnectionsTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        for (int i = 0; i < Column.values().length; ++i) {
          Column column = Column.values()[i];
          myConnectionsTable.getColumnModel().getColumn(i).setPreferredWidth((int)(myConnectionsTable.getWidth() * column.getWidthPercentage()));
        }
      }
    });

    // Keep the previously selected row selected if it's still there

    // IJ wants to use Application.invokeLater, but we use SwingUtilities.invokeLater because it's
    // testable (Application is null in basic unit tests)
    //noinspection SSBasedInspection
    myTableModel.addTableModelListener(e ->
      SwingUtilities.invokeLater(() -> {
        // Invoke later, because table itself listener of table model.
        HttpData selectedData = myStage.getSelectedConnection();
        if (selectedData != null) {
          for (int i = 0; i < myTableModel.getRowCount(); ++i) {
            if (myTableModel.getHttpData(i).getId() == selectedData.getId()) {
              int row = myConnectionsTable.convertRowIndexToView(i);
              myConnectionsTable.setRowSelectionInterval(row, row);
              break;
            }
          }
        }
      })
    );
  }

  private final class ConnectionsTableModel extends AbstractTableModel {
    private final AspectObserver myAspectObserver;
    @NotNull private List<HttpData> myDataList = new ArrayList<>();
    @NotNull private final Range myRange;

    private ConnectionsTableModel(@NotNull Range range) {
      myRange = range;
      myAspectObserver = new AspectObserver();
      myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
      if (!myRange.isEmpty()) {
        rangeChanged();
      }
    }

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
      return Column.values()[columnIndex].getValueFrom(data);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return Column.values()[columnIndex].getType();
    }

    @NotNull
    public HttpData getHttpData(int rowIndex) {
      return myDataList.get(rowIndex);
    }

    public void rangeChanged() {
      myDataList = myStage.getConnectionsModel().getData(myRange);
      fireTableDataChanged();
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

  private final class TimelineRenderer implements TableCellRenderer, TableModelListener {
    private final EnumColors<NetworkState> NETWORK_STATE_COLORS = new EnumColors.Builder<NetworkState>(2)
      .add(NetworkState.SENDING, NETWORK_SENDING_COLOR, NETWORK_SENDING_COLOR)
      .add(NetworkState.RECEIVING, NETWORK_RECEIVING_COLOR, NETWORK_RECEIVING_SELECTED_COLOR)
      .add(NetworkState.WAITING, NETWORK_WAITING_COLOR, NETWORK_WAITING_COLOR)
      .add(NetworkState.NONE, TRANSPARENT_COLOR, TRANSPARENT_COLOR)
      .build();

    /**
     * Keep in sync 1:1 with {@link ConnectionsTableModel#myDataList}. When the table asks for the
     * chart to render, it will be converted from model index to view index.
     */
    @NotNull private final List<StateChart<NetworkState>> myCharts = new ArrayList<>();
    @NotNull private final JTable myTable;
    @NotNull private final Range myRange;

    TimelineRenderer(@NotNull JTable table, @NotNull Range range) {
      myTable = table;
      myRange = range;
      myTable.getModel().addTableModelListener(this);
      tableChanged(new TableModelEvent(myTable.getModel()));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      StateChart<NetworkState> chart = myCharts.get(myTable.convertRowIndexToModel(row));
      chart.getColors().setColorIndex(isSelected ? 1 : 0);

      JPanel panel = new JBPanel(new TabularLayout("*" , "*"));
      panel.add(chart, new TabularLayout.Constraint(0, 0));

      return panel;
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

        StateChartModel<NetworkState> stateModel = new StateChartModel<>();
        StateChart<NetworkState> chart = new StateChart<>(stateModel, NETWORK_STATE_COLORS);
        chart.setHeightGap(0.3f);
        stateModel.addSeries(new RangedSeries<>(myRange, series));
        myCharts.add(chart);
      }
    }
  }

  private static final class ConnectionsTable extends JBTable {
    private static final JBColor HOVER_COLOR = new JBColor(0xEAEFFA, 0x3B3D3F);
    private int myHoveredRow = -1;

    ConnectionsTable(@NotNull TableModel model) {
      super(model);
      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          myHoveredRow = rowAtPoint(e.getPoint());
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myHoveredRow = -1;
        }
      };
      addMouseMotionListener(mouseAdapter);
      addMouseListener(mouseAdapter);
    }

    @NotNull
    @Override
    public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
      Component comp = super.prepareRenderer(renderer, row, column);
      Component toChangeComp = comp;

      if (comp instanceof ExpandedItemRendererComponentWrapper) {
        // To be able to show extended value of a cell via popup, when the value is stripped,
        // JBTable wraps the cell component into ExpandedItemRendererComponentWrapper.
        // So, we need to change background and foreground colors of the cell component rather than the wrapper.
        toChangeComp = ((ExpandedItemRendererComponentWrapper)comp).getComponent(0);
      }

      if (isRowSelected(row)) {
        toChangeComp.setForeground(getSelectionForeground());
        toChangeComp.setBackground(getSelectionBackground());
      }
      else if (row == myHoveredRow) {
        toChangeComp.setBackground(HOVER_COLOR);
        toChangeComp.setForeground(getForeground());
      }
      else {
        toChangeComp.setBackground(getBackground());
        toChangeComp.setForeground(getForeground());
      }

      return comp;
    }
  }
}
