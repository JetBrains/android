/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui;

import static com.android.tools.adtui.stdui.StandardColors.AXIS_MARKER_COLOR;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.HoverRowTable;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;

/**
 * A class that represents a table which includes a timeline axis (that is, a set of ticks and time
 * values) in the header, with actual timeline bars appearing in the table cells below it.
 * <p>
 * Without having the axis in the header, you'd have to always jam it into the first row, which can
 * make it hard to read when it overlaps with real data.
 * <p>
 * In order to make it easier to ensure that your timeline cells line up with your timeline header,
 * this class also provides {@link CellRenderer}, which handles rendering the timeline markers at
 * appropriate intervals.
 * <p>
 * Use {@link #create(TableModel, Timeline, String, boolean)} to create a table, and then set the
 * appropriate column's renderer by extending {@link CellRenderer} and setting it using
 * {@link TableColumn#setCellRenderer(TableCellRenderer)}.
 */
public final class TimelineTable {
  private static final int HEADER_HEIGHT = 20;
  private static final int TIMELINE_HEIGHT = 14;

  /**
   * Create a timeline table. You have to specify the {@code timelineColumnName} that the timeline will appear
   * in, as all other columns will be left blank.
   *
   * @param showsAllWhenEmpty if the table shows full timeline with an empty selection range
   */
  @NotNull
  public static JBTable create(@NotNull TableModel model,
                               @NotNull Timeline timeline,
                               @NotNull String timelineColumnName,
                               boolean showsAllWhenEmpty) {
    JBTable table = new HoverRowTable(model);
    table.getTableHeader().setDefaultRenderer(new HeaderRenderer(table, timeline, timelineColumnName, showsAllWhenEmpty));

    AspectObserver timelineObserver = new AspectObserver();
    table.addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
        if (table.isShowing()) {
          timeline.getSelectionRange().addDependency(timelineObserver).onChange(
            Range.Aspect.RANGE, () -> {
              table.repaint();
              table.getTableHeader().revalidate();
            }
          );
          if (showsAllWhenEmpty) {
            timeline.getDataRange().addDependency(timelineObserver).onChange(
              Range.Aspect.RANGE, () -> {
                if (timeline.getSelectionRange().isEmpty()) {
                  table.repaint();
                  table.getTableHeader().repaint();
                }
              }
            );
          }
        }
        else {
          timeline.getSelectionRange().removeDependencies(timelineObserver);
          timeline.getDataRange().removeDependencies(timelineObserver);
        }
      }
    });

    boolean[] lastIsEmpty = new boolean[]{true};
    table.getModel().addTableModelListener(e -> {
      // The height of the header column changes whether or not the table is empty. Unfortunately
      // the JTable class does not handle this case well, and if we don't force a resize then
      // what will happen is the table header will be stretched or squashed into whatever the
      // previous value was.
      boolean isEmpty = table.getRowCount() == 0;
      if (lastIsEmpty[0] != isEmpty) {
        lastIsEmpty[0] = isEmpty;
        table.getTableHeader().resizeAndRepaint();
      }
    });
    return table;
  }

  @NotNull
  private static AxisComponent createAxis(@NotNull Timeline timeline, boolean showsAllWhenEmpty) {
    Range range = timeline.getSelectionRange();
    if (range.isEmpty() && showsAllWhenEmpty) {
      range = timeline.getDataRange();
    }

    AxisComponentModel model = new ResizingAxisComponentModel.Builder(range, new TimeAxisFormatter(1, 5, 1))
      .setGlobalRange(timeline.getDataRange()).build();

    AxisComponent axis = new AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM, true);
    axis.setShowAxisLine(false);
    axis.setMarkerColor(AXIS_MARKER_COLOR);

    return axis;
  }

  /**
   * Class which handles adding the timeline below an existing table header.
   */
  private static final class HeaderRenderer implements TableCellRenderer {
    @NotNull
    private final TableCellRenderer myDelegateRenderer;
    @NotNull
    private final Timeline myTimeline;
    @NotNull private final String myTimelineColumnName;

    private final boolean myShowsAllWhenEmpty;

    private HeaderRenderer(@NotNull JTable table,
                           @NotNull Timeline timeline,
                           @NotNull String timelineColumnName,
                           boolean showsAllWhenEmpty) {
      myDelegateRenderer = table.getTableHeader().getDefaultRenderer();
      myTimeline = timeline;
      myShowsAllWhenEmpty = showsAllWhenEmpty;
      myTimelineColumnName = timelineColumnName;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JComponent headerComponent =
        (JComponent)myDelegateRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (headerComponent instanceof JLabel) {
        headerComponent.setBorder(JBUI.Borders.empty(3, 10, 3, 0));
        ((JLabel)headerComponent).setHorizontalAlignment(SwingConstants.LEFT);
      }

      if (table.getRowCount() == 0) {
        return headerComponent;
      }

      JPanel rendererPanel = new JPanel(
        new TabularLayout("*", String.format(Locale.getDefault(), "%dpx,%dpx", HEADER_HEIGHT, TIMELINE_HEIGHT)));
      rendererPanel.setOpaque(false); // If opaque, myDelegateRenderer component's border doesn't render
      headerComponent.setOpaque(false);

      JPanel axisPanel = new JPanel(new BorderLayout());
      axisPanel.setBackground(StandardColors.DEFAULT_CONTENT_BACKGROUND_COLOR);

      if (myTimelineColumnName.equals(value)) {
        // Only show the timeline axis if we also show at least one timeline row below
        AxisComponent header = createAxis(myTimeline, myShowsAllWhenEmpty);
        header.setShowAxisLine(false);
        header.setMarkerLengths(TIMELINE_HEIGHT, 0);
        axisPanel.add(header);
      }
      else {
        // Add a horizontal line in non-timeline columns, as otherwise extra space looks awkward
        JComponent separator = AdtUiUtils.createHorizontalSeparator();
        separator.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        axisPanel.add(separator);
      }

      // We need to redraw the grid line because axisPanel paints over it. Otherwise, there's a
      // gap between the header and the first row.
      axisPanel.setBorder(new GridBorder(table));

      rendererPanel.add(headerComponent, new TabularLayout.Constraint(0, 0));
      rendererPanel.add(axisPanel, new TabularLayout.Constraint(1, 0));

      return rendererPanel;
    }
  }

  /**
   * A {@link TableCellRenderer} that automatically renders timeline markers underneath the rest of
   * the contents of the cell. This allows subclasses to focus on rendering their contents without
   * worrying about lining up correctly with the parent timeline axis.
   */
  public static abstract class CellRenderer implements TableCellRenderer {
    private Timeline myTimeline;
    private final boolean myShowsAllWhenEmpty;

    public CellRenderer(@NotNull Timeline timeline) {
      this(timeline, false);
    }

    public CellRenderer(@NotNull Timeline timeline, boolean showsAllWhenEmpty) {
      myTimeline = timeline;
      myShowsAllWhenEmpty = showsAllWhenEmpty;
    }

    @Override
    public final Component getTableCellRendererComponent(JTable table,
                                                         Object value,
                                                         boolean isSelected,
                                                         boolean hasFocus,
                                                         int row,
                                                         int column) {
      JPanel panel = new JPanel(new TabularLayout("*", "*"));

      panel.add(getTableCellRendererComponent(isSelected, row), new TabularLayout.Constraint(0, 0));

      // Show timeline lines behind chart components
      AxisComponent axisTicks = createAxis(myTimeline, myShowsAllWhenEmpty);
      axisTicks.setShowLabels(false);
      axisTicks.setMarkerLengths(table.getRowHeight(), 0);
      panel.add(axisTicks, new TabularLayout.Constraint(0, 0));

      return panel;
    }

    @NotNull
    protected final Timeline getTimeline() {
      return myTimeline;
    }

    /**
     * Returns the active range for rendering timeline in the cell.
     *
     * The returned range could be the selection or data range of the timeline and
     * do not modify or add aspect observers to the active range directly.
     */
    @NotNull
    public final Range getActiveRange() {
      return (myTimeline.getSelectionRange().isEmpty() && myShowsAllWhenEmpty) ? myTimeline.getDataRange() : myTimeline.getSelectionRange();
    }

    @NotNull
    protected abstract Component getTableCellRendererComponent(boolean isSelected, int row);
  }

  // Minor hack: The timeline header ends up painting partially over the vertical grid lines in
  // this table. To restore them, we create a border which doesn't actually take up any space but
  // renders a single grid line (which lines up with how tables render their grid lines).
  public static final class GridBorder implements Border {
    private final Color myColor;

    public GridBorder(@NotNull JTable table) {
      myColor = table.getGridColor();
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      g.setColor(myColor);
      int xRight = x + width - 1;
      g.drawLine(xRight, y, xRight, y + height);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return JBInsets.emptyInsets();
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }
}
