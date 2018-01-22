/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.BorderlessTableCellRenderer;
import com.android.tools.profilers.HoverRowTable;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBPanel;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;

/**
 * Energy events table view including wake lock, alarms, etc.
 */
public final class EnergyEventsView {

  /**
   * Columns of event duration data, including name, kind, timeline etc. Each column meaning varies, for example, the name column for
   * wake lock is the tag value.
   */
  enum Column {
    NAME(0.25, String.class) {
      @Override
      Object getValueFrom(@NotNull EventDuration data) {
        return data.getName();
      }
    },
    KIND(0.25, Integer.class) {
      @Override
      Object getValueFrom(@NotNull EventDuration data) {
        return data.getKind();
      }
    },
    TIMELINE(0.5, Long.class) {
      @Override
      Object getValueFrom(@NotNull EventDuration data) {
        return data.getInitialTimestamp();
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

    abstract Object getValueFrom(@NotNull EventDuration data);
  }

  @NotNull private final EnergyProfilerStage myStage;
  @NotNull private final EventsTableModel myTableModel;
  @NotNull private final JBTable myEventsTable;

  public EnergyEventsView(EnergyProfilerStageView stageView) {
    myStage = stageView.getStage();
    myTableModel = new EventsTableModel(myStage.getEnergyEventsFetcher());
    myEventsTable = new HoverRowTable(myTableModel, ProfilerColors.DEFAULT_HOVER_COLOR);
    buildEventsTable();
  }

  private void buildEventsTable() {
    myEventsTable.getColumnModel().getColumn(Column.NAME.ordinal()).setCellRenderer(new BorderlessTableCellRenderer());
    myEventsTable.getColumnModel().getColumn(Column.KIND.ordinal()).setCellRenderer(new BorderlessTableCellRenderer());
    myEventsTable.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setCellRenderer(
      new TimelineRenderer(myEventsTable, myStage.getStudioProfilers().getTimeline().getSelectionRange()));

    myEventsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myEventsTable.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myEventsTable.setShowVerticalLines(true);
    myEventsTable.setShowHorizontalLines(false);
    int defaultFontHeight = myEventsTable.getFontMetrics(myEventsTable.getFont()).getHeight();
    myEventsTable.setRowMargin(0);
    myEventsTable.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    myEventsTable.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
    myEventsTable.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

    myEventsTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        for (int i = 0; i < Column.values().length; ++i) {
          Column column = Column.values()[i];
          myEventsTable.getColumnModel().getColumn(i)
            .setPreferredWidth((int)(myEventsTable.getWidth() * column.getWidthPercentage()));
        }
      }
    });
  }

  public JComponent getComponent() {
    return myEventsTable;
  }

  private static final class EventsTableModel extends AbstractTableModel {
    @NotNull private List<EventDuration> myList = new ArrayList<>();

    private EventsTableModel(EnergyEventsFetcher fetcher) {
      fetcher.addListener(list -> {
        myList = list;
        fireTableDataChanged();
      });
    }

    @Override
    public int getRowCount() {
      return myList.size();
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
      EventDuration duration = myList.get(rowIndex);
      return Column.values()[columnIndex].getValueFrom(duration);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return Column.values()[columnIndex].getType();
    }

    @NotNull
    public EventDuration getValue(int rowIndex) {
      return myList.get(rowIndex);
    }
  }

  private final class TimelineRenderer implements TableCellRenderer, TableModelListener {
    /**
     * Keep in sync 1:1 with {@link EventsTableModel#myList}. When the table asks for the
     * chart to render, it will be converted from model index to view index.
     */
    @NotNull private final List<DurationStateChart> myEventCharts = new ArrayList<>();
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
      JPanel panel = new JBPanel(new TabularLayout("*", "*"));

      if (row == 0) {
        // Show timeline labels in front of the chart components
        AxisComponent axisLabels = createAxis();
        axisLabels.setMarkerLengths(0, 0);
        panel.add(axisLabels, new TabularLayout.Constraint(0, 0));
      }

      DurationStateChart chart = myEventCharts.get(myTable.convertRowIndexToModel(row));
      panel.add(chart, new TabularLayout.Constraint(0, 0));
      // Show timeline lines behind chart components
      AxisComponent axisTicks = createAxis();
      axisTicks.setMarkerLengths(myTable.getRowHeight(), 0);
      axisTicks.setShowLabels(false);
      panel.add(axisTicks, new TabularLayout.Constraint(0, 0));

      return panel;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      myEventCharts.clear();
      EventsTableModel model = (EventsTableModel) myTable.getModel();
      for (int i = 0; i < model.getRowCount(); ++i) {
        DurationStateChart chart = new DurationStateChart(model.getValue(i), myRange);
        chart.setHeightGap(0.3f);
        myEventCharts.add(chart);
      }
    }

    @NotNull
    private AxisComponent createAxis() {
      AxisComponentModel model = new AxisComponentModel(myRange, new TimeAxisFormatter(1, 4, 1));
      model.setClampToMajorTicks(false);
      model.setGlobalRange(myStage.getStudioProfilers().getTimeline().getDataRange());
      AxisComponent axis = new AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM);
      axis.setShowAxisLine(false);
      axis.setMarkerColor(ProfilerColors.NETWORK_TABLE_AXIS);

      model.update(1);
      return axis;
    }
  }
}
