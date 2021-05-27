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

import static com.android.tools.profilers.ProfilerFonts.H2_FONT;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;

import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.stdui.BorderlessTableCellRenderer;
import com.android.tools.adtui.stdui.TimelineTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.StageView;
import com.intellij.ui.table.JBTable;
import java.awt.Color;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import org.jetbrains.annotations.NotNull;

/**
 * Energy events table view including wake lock, alarms, etc.
 */
public final class EnergyEventsView {

  /**
   * Columns of event duration data.
   */
  enum Column {
    EVENT(0.18, String.class, "System Event") {
      @Override
      Object getValueFrom(@NotNull EnergyDuration data) {
        return data.getName();
      }
    },
    DESCRIPTION(0.16, String.class, "Description") {
      @Override
      Object getValueFrom(@NotNull EnergyDuration data) {
        return data.getDescription();
      }
    },
    CALLED_BY(0.16, String.class, "Called By") {
      @Override
      Object getValueFrom(@NotNull EnergyDuration data) {
        return data.getCalledBy();
      }
    },
    TIMELINE(0.5, Long.class, "Timeline") {
      @Override
      Object getValueFrom(@NotNull EnergyDuration data) {
        return data.getInitialTimestamp();
      }
    };

    private final double myWidthPercentage;
    private final Class<?> myType;
    private final String myDisplayName;

    Column(double widthPercentage, Class<?> type, String name) {
      myWidthPercentage = widthPercentage;
      myType = type;
      myDisplayName = name;
    }

    public double getWidthPercentage() {
      return myWidthPercentage;
    }

    public Class<?> getType() {
      return myType;
    }

    public String toDisplayString() {
      return myDisplayName;
    }

    abstract Object getValueFrom(@NotNull EnergyDuration data);
  }

  @NotNull private final EnergyProfilerStage myStage;
  @NotNull private final EventsTableModel myTableModel;
  @NotNull private final JBTable myEventsTable;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal") private AspectObserver myAspectObserver = new AspectObserver();

  public EnergyEventsView(@NotNull EnergyProfilerStageView stageView) {
    myStage = stageView.getStage();
    myTableModel = new EventsTableModel(myStage);
    // Add a listener on model to update selection before construct table because otherwise it flickers. The table also adds a listener
    // on model that if the selection is set later then there is a clear and re-selection time gap on the view.
    myTableModel.addTableModelListener(e -> updateTableSelection());
    myEventsTable = TimelineTable.create(myTableModel, myStage.getTimeline(), Column.TIMELINE.ordinal());
    buildEventsTable(stageView);
    // TODO(b/188695273): to be removed.
    if (!stageView.getStage().getStudioProfilers().getIdeServices().getAppInspectionMigrationServices().isMigrationEnabled()) {
      myStage.getAspect().addDependency(myAspectObserver).onChange(EnergyProfilerAspect.SELECTED_EVENT_DURATION, this::updateTableSelection)
        .onChange(EnergyProfilerAspect.SELECTED_ORIGIN_FILTER, myTableModel::updateTableByOrigin);
    }
  }

  private void buildEventsTable(@NotNull StageView stageView) {
    myEventsTable.setAutoCreateRowSorter(true);
    myEventsTable.getColumnModel().getColumn(Column.EVENT.ordinal()).setCellRenderer(new BorderlessTableCellRenderer());
    myEventsTable.getColumnModel().getColumn(Column.DESCRIPTION.ordinal()).setCellRenderer(new BorderlessTableCellRenderer());
    myEventsTable.getColumnModel().getColumn(Column.CALLED_BY.ordinal()).setCellRenderer(new CalledByRenderer());
    myEventsTable.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setCellRenderer(
      new TimelineRenderer(myEventsTable, myStage.getTimeline()));

    myEventsTable.getEmptyText().setText("No system events for the selected range or filter.");
    myEventsTable.getEmptyText().setShowAboveCenter(false).setFont(H2_FONT);

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
    myEventsTable.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return; // Only handle listener on last event, not intermediate events
      }
      int row = myEventsTable.getSelectedRow();
      if (row >= 0 && row < myEventsTable.getRowCount()) {
        // There may be durations that finish between the time the table was first populated and when the event was selected,
        // so update the duration again, just in case.
        EnergyDuration duration = myTableModel.getValue(myEventsTable.convertRowIndexToModel(row));
        duration = myStage.updateDuration(duration);
        myStage.setSelectedDuration(duration);
      }
    });
    createTooltip(stageView);
  }

  private void updateTableSelection() {
    EnergyDuration duration = myStage.getSelectedDuration();
    if (duration != null) {
      long id = duration.getEventList().get(0).getGroupId();
      for (int i = 0; i < myTableModel.getRowCount(); ++i) {
        if (id == myTableModel.getValue(i).getEventList().get(0).getGroupId()) {
          int row = myEventsTable.convertRowIndexToView(i);
          myEventsTable.setRowSelectionInterval(row, row);
          return;
        }
      }
    }
    else {
      myEventsTable.clearSelection();
    }
  }

  public JComponent getComponent() {
    return myEventsTable;
  }

  private void createTooltip(@NotNull StageView stageView) {
    EnergyEventsTableTooltipInfoModel tooltipModel = new EnergyEventsTableTooltipInfoModel(myStage.getTimeline().getDataRange());
    EnergyEventsTableTooltipInfoComponent tooltipInfoComponent = new EnergyEventsTableTooltipInfoComponent(tooltipModel);
    tooltipInfoComponent.setForeground(ProfilerColors.TOOLTIP_TEXT);
    tooltipInfoComponent.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    TooltipComponent tooltip =
      new TooltipComponent.Builder(tooltipInfoComponent, myEventsTable, stageView.getProfilersView().getComponent()).build();
    tooltip.registerListenersOn(myEventsTable);

    // Convert mouse position to the table timeline tooltipRange and update the model.
    myEventsTable.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myEventsTable.rowAtPoint(e.getPoint());
        int col = myEventsTable.columnAtPoint(e.getPoint());
        boolean isTooltipVisible = false;
        // Display the tooltip if the mouse position is within a timeline cell.
        if (row >= 0 && col == Column.TIMELINE.ordinal()) {
          TableColumnModel columnModel = myEventsTable.getColumnModel();

          // Calculate the timestamp correspondent to the mouse position.
          int position = e.getX();
          for (int c = 0; c < Column.TIMELINE.ordinal(); ++c) {
            position -= columnModel.getColumn(c).getWidth();
          }
          int width = columnModel.getColumn(Column.TIMELINE.ordinal()).getWidth();
          Range range = myStage.getTimeline().getSelectionRange();
          long timestampUs = (long)(range.getMin() + range.getLength() * position / width);

          // Calculate the tooltip range base on timestamp and event highlight width.
          // Let the highlight range for a event at x be [x, x + highlightWidth],
          // then for a tooltip at position y , y in [x, x + highlightWidth] is equivalent to x in [y - highlightWidth, y]
          double highlightWidth = EnergyEventComponent.HIGHLIGHT_WIDTH * range.getLength() / Math.max(1, width);
          Range tooltipRange = new Range(timestampUs - highlightWidth, timestampUs);

          tooltipModel.update(((EventsTableModel)myEventsTable.getModel()).getValue(row), tooltipRange);
          if (tooltipModel.getDuration() != null) {
            isTooltipVisible = true;
          }
        }
        tooltip.setVisible(isTooltipVisible);
      }
    });
  }

  private static final class EventsTableModel extends AbstractTableModel {
    @NotNull private final EnergyProfilerStage myStage;
    // The data list without filtered by configuration.
    @NotNull private List<EnergyDuration> myDataList = new ArrayList<>();
    // The data list to display that has been filtered by configuration.
    @NotNull private List<EnergyDuration> myList = new ArrayList<>();

    private EventsTableModel(@NotNull EnergyProfilerStage stage) {
      myStage = stage;
      stage.getEnergyEventsFetcher().addListener(list -> {
        myDataList = list;
        updateTableByOrigin();
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
      EnergyDuration duration = myList.get(rowIndex);
      return Column.values()[columnIndex].getValueFrom(duration);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return Column.values()[columnIndex].getType();
    }

    @NotNull
    public EnergyDuration getValue(int rowIndex) {
      return myList.get(rowIndex);
    }

    public void updateTableByOrigin() {
      myList = myStage.filterByOrigin(myDataList);
      fireTableDataChanged();
    }
  }

  private static final class CalledByRenderer extends BorderlessTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      String calledByValue = "";
      if (value instanceof String) {
        calledByValue = (String)value;
        // LastDotIndex in the line is the method name start index, the second last index is the class name start index.
        int lastDotIndex = calledByValue.lastIndexOf('.');
        if (lastDotIndex > 0) {
          int secondLastDotIndex = calledByValue.substring(0, lastDotIndex).lastIndexOf('.');
          if (secondLastDotIndex != -1) {
            calledByValue = calledByValue.substring(secondLastDotIndex + 1);
          }
        }
      }
      return super.getTableCellRendererComponent(table, calledByValue, isSelected, hasFocus, row, column);
    }
  }

  private static final class TimelineRenderer extends TimelineTable.CellRenderer implements TableModelListener {
    /**
     * Keep in sync 1:1 with {@link EventsTableModel#myList}. When the table asks for the
     * chart to render, it will be converted from model index to view index.
     */
    @NotNull private final List<EnergyEventComponent> myEventComponents = new ArrayList<>();
    @NotNull private final JTable myTable;

    TimelineRenderer(@NotNull JTable table, @NotNull StreamingTimeline timeline) {
      super(timeline);
      myTable = table;
      myTable.getModel().addTableModelListener(this);
      tableChanged(new TableModelEvent(myTable.getModel()));
    }

    @NotNull
    @Override
    protected Component getTableCellRendererComponent(boolean isSelected, int row) {
      return myEventComponents.get(myTable.convertRowIndexToModel(row));
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      myEventComponents.clear();
      EventsTableModel model = (EventsTableModel)myTable.getModel();
      for (int i = 0; i < model.getRowCount(); ++i) {
        EnergyDuration duration = model.getValue(i);

        // An event duration starts from its timestamp and ends at the next event's timestamp.
        DefaultDataSeries<EventAction<Common.Event>> series = new DefaultDataSeries<>();
        Iterator<Common.Event> iterator = duration.getEventList().iterator();
        Common.Event event = iterator.hasNext() ? iterator.next() : null;
        long startTimeUs = event != null ? TimeUnit.NANOSECONDS.toMicros(event.getTimestamp()) : -1;
        while (event != null) {
          Common.Event nextEvent = iterator.hasNext() ? iterator.next() : null;
          long endTimeUs = nextEvent != null ? TimeUnit.NANOSECONDS.toMicros(nextEvent.getTimestamp()) : Long.MAX_VALUE;
          series.add(startTimeUs, new EventAction<>(startTimeUs, endTimeUs, event));
          startTimeUs = endTimeUs;
          event = nextEvent;
        }

        // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
        EventModel<Common.Event> eventModel = new EventModel<>(new RangedSeries<>(getTimeline().getSelectionRange(), series));
        Color highlightColor = EnergyEventStateChart.DURATION_STATE_ENUM_COLORS.getColor(duration.getKind());
        EnergyEventComponent component = new EnergyEventComponent(eventModel, highlightColor);
        myEventComponents.add(component);
      }
    }
  }
}
