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
package com.android.tools.profilers;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

/**
 * Common layout constants that are shared across profiler views.
 */
public final class ProfilerLayout {

  /**
   * Common length for spacing between axis tick markers
   */
  public static final int MARKER_LENGTH = JBUIScale.scale(5);

  public static final int TIME_AXIS_HEIGHT = JBUIScale.scale(15);

  /**
   * Common space left on top of a vertical axis to make sure label text can fit there
   */
  public static final int Y_AXIS_TOP_MARGIN = JBUIScale.scale(30);

  public static final Border MONITOR_LABEL_PADDING = JBUI.Borders.empty(5, 10);

  public static final Border MONITOR_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER);

  public static final int MONITOR_LEGEND_RIGHT_PADDING = JBUIScale.scale(12);

  /**
   * Space on the right for all legends when inside a profiler. Chosen so it lines up with the right axis units.
   */
  public static final int PROFILER_LEGEND_RIGHT_PADDING = JBUIScale.scale(9);

  public static final int ROW_HEIGHT_PADDING = JBUIScale.scale(4);

  public static final Border TABLE_ROW_BORDER = JBUI.Borders.emptyLeft(10);
  public static final Border TABLE_COLUMN_HEADER_BORDER = JBUI.Borders.empty(3, 10, 3, 0);
  public static final Border TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER = JBUI.Borders.empty(3, 0, 3, 10);

  public static final Insets TABLE_COLUMN_CELL_INSETS = new Insets(3, 10, 3, 0);
  public static final Insets TABLE_COLUMN_RIGHT_ALIGNED_CELL_INSETS = new Insets(3, 0, 3, 10);

  public static final int TABLE_COLUMN_CELL_SPARKLINE_RIGHT_PADDING = JBUIScale.scale(2);
  public static final int TABLE_COLUMN_CELL_SPARKLINE_TOP_BOTTOM_PADDING = JBUIScale.scale(1);

  public static final Border TOOLTIP_BORDER = new JBEmptyBorder(8, 10, 8, 10);

  public static final int PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER = JBUIScale.scale(16);
  public static final int PROFILING_INSTRUCTIONS_ICON_PADDING = JBUIScale.scale(1);

  public static final int FILTER_TEXT_FIELD_WIDTH = JBUIScale.scale(245);
  public static final int FILTER_TEXT_FIELD_TRIGGER_DELAY_MS = 250;
  public static final int FILTER_TEXT_HISTORY_SIZE = 5;

  // The total usable height of the toolbar is 30px the 1px is for a 1px border at the bottom of the toolbar.
  public static final int TOOLBAR_HEIGHT = JBUIScale.scale(31);
  public static final Border TOOLBAR_LABEL_BORDER = JBUI.Borders.empty(3, 8, 3, 3);
  public static final Border TOOLBAR_ICON_BORDER = JBUI.Borders.empty(4);

  /**
   * Used in the CPU threads/kernel cell renderer's to set the preferred height.
   */
  public static final int CPU_STATE_LIST_ITEM_HEIGHT = 21;
  public static final Border CPU_THREADS_BORDER = JBUI.Borders.empty();

  // To be combined with CPU threads/kernel cell label border to draw a vertical right border.
  public static final Border CPU_THREADS_RIGHT_BORDER = JBUI.Borders.customLine(ProfilerColors.CPU_AXIS_GUIDE_COLOR, 0, 0, 0, 2);
  public static final int CPU_HIDEABLE_PANEL_TITLE_LEFT_PADDING = 7;
  public static final int CPU_HIDEABLE_PANEL_TITLE_ICON_TEXT_GAP = 6;

  /**
   * Used in CustomEventTrackRenderer to set the preferred height.
   */
  public static final int CUSTOM_EVENT_VISUALIZATION_TRACK_HEIGHT = 100;

  private ProfilerLayout() {
    // Static class designed to hold constants only
  }

  @NotNull
  public static LayoutManager createToolbarLayout() {
    // We use a GridBagLayout because by default it acts like a FlowLayout that is vertically aligned.
    return new GridBagLayout();
  }
}
