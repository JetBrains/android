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

import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.profilers.cpu.ThreadState;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;

public class ProfilerColors {

  // Collections of constant, do not instantiate.
  private ProfilerColors() {
  }

  public static final Color ACTIVE_SESSION_COLOR = new JBColor(0x58AB5C, 0x65BB69);

  public static final Color SELECTED_SESSION_COLOR = new JBColor(0x397FE4, 0x7CAEFE);

  public static final Color HOVERED_SESSION_COLOR = new JBColor(0xD6D6D6, 0x313435);

  public static final Color SESSION_DIVIDER_COLOR = new JBColor(0xD8D6D6, 0x2F3031);

  public static final Color CPU_USAGE = new JBColor(0xa7e0d7, 0x397060);

  public static final Color CPU_USAGE_CAPTURED = new JBColor(0x19AF9A, 0x43CAA2);

  public static final Color CPU_USAGE_CAPTURED_HOVER = new JBColor(0x159482, 0x73D7BA);

  public static final Color CPU_OTHER_USAGE = new JBColor(0xe9f3f6, 0x39444e);

  public static final Color CPU_OTHER_USAGE_CAPTURED = new JBColor(0xC8E0E8, 0x455B75);

  public static final Color THREADS_COUNT = new JBColor(0x224E4D, 0xDAFFF4);

  public static final Color THREADS_COUNT_CAPTURED = new JBColor(0x224E4D, 0xDAFFF4);

  public static final Color CPU_TRACE_IDLE = new JBColor(0x218677, 0x33807E);

  public static final Color CPU_TRACE_IDLE_HOVER = new JBColor(0x1C7265, 0x44ACA9);

  // TODO: define final color
  public static final Color CPU_CAPTURE_EVENT = new JBColor(0x888888, 0x888888);

  public static final Color ENERGY_USAGE = new JBColor(0x966EC3, 0x8D6FC2);

  public static final Color CPU_KERNEL_APP_TEXT_SELECTED = new JBColor(0xEFFDFB, 0x2C443D);
  public static final Color CPU_KERNEL_APP_TEXT_HOVER = CPU_KERNEL_APP_TEXT_SELECTED;
  public static final Color CPU_KERNEL_APP_TEXT = new JBColor(0x4A877F, 0x56907D);
  public static final Color CPU_KERNEL_OTHER_TEXT = new JBColor(0x6E8C9A, 0x606D7E);
  public static final Color CPU_KERNEL_OTHER_TEXT_HOVER = new JBColor(0x232D31, 0xA9C7E9);
  public static final Color CPU_KERNEL_APP = new JBColor(0xC3E4E0, 0x33534C);
  public static final Color CPU_KERNEL_APP_SELECTED = CPU_USAGE_CAPTURED;
  public static final Color CPU_KERNEL_APP_HOVER = new JBColor(0x169986, 0x6BD5B6);
  public static final Color CPU_KERNEL_OTHER = new JBColor(0xE5F1F5, 0x353C45);
  public static final Color CPU_KERNEL_OTHER_HOVER = new JBColor(0x80B7CA, 0x587495);

  public static final Color SLOW_FRAME_COLOR = new JBColor(new Color(0xAAF0697D, true), new Color(0xAACD6767, true));
  public static final Color SLOW_FRAME_COLOR_HIGHLIGHTED = new JBColor(new Color(0xAACD6767, true), new Color(0xAAF0697D, true));

  public static final Color NORMAL_FRAME_COLOR = new JBColor(new Color(0xAAD4D4D4, true), new Color(0xAA58595A, true));
  public static final Color NORMAL_FRAME_COLOR_HIGHLIGHTED = new JBColor(new Color(0xAAB9B9B9, true), new Color(0xAA767778, true));

  /**
   * Represents pair of colors of non-selected and hovered states of a thread.
   * The first color is for a non-selected thread, the second one is for a hovered thread.
   */
  public static final EnumColors.Builder<ThreadState> THREAD_STATES =
    new EnumColors.Builder<ThreadState>(2)
      .add(ThreadState.RUNNING,
           CPU_USAGE,
           new JBColor(0x159482, 0x73D7BA))
      .add(ThreadState.RUNNING_CAPTURED,
           CPU_USAGE_CAPTURED,
           CPU_USAGE_CAPTURED_HOVER)
      .add(ThreadState.RUNNABLE_CAPTURED,
           CPU_TRACE_IDLE,
           CPU_TRACE_IDLE_HOVER)
      .add(ThreadState.WAITING,
           new JBColor(0xeccc8e, 0xa5956a),
           new JBColor(0xE3AD48, 0xF8E8C3))
      .add(ThreadState.WAITING_CAPTURED,
           new JBColor(0xEAC174, 0xF1D48C),
           new JBColor(0xE3AD48, 0xF8E8C3))
      .add(ThreadState.WAITING_IO_CAPTURED,
           new JBColor(0xFFB74D, 0xFFCA28),
           new JBColor(0xE3AD48, 0xF8E8C3))
      .add(ThreadState.SLEEPING,
           new JBColor(0xF2F6F8, 0x353739),
           new JBColor(0xD5DADD, 0x595C61))
      .add(ThreadState.SLEEPING_CAPTURED,
           new JBColor(0xE7ECED, 0x3C3E40),
           new JBColor(0xD5DADD, 0x595C61))
      .add(ThreadState.DEAD,
           Gray.TRANSPARENT,
           Gray.TRANSPARENT)
      .add(ThreadState.DEAD_CAPTURED,
           Gray.TRANSPARENT,
           Gray.TRANSPARENT)
      .add(ThreadState.NO_ACTIVITY,
           Gray.TRANSPARENT,
           Gray.TRANSPARENT)
      // TODO: remove UNKNOWN mapping when all states are covered.
      .add(ThreadState.UNKNOWN,
           new JBColor(0xC1D6F6, 0x5A6E7D),
           new JBColor(0xC1D6F6, 0x5A6E7D));

  public static final Color CPU_AXIS_GUIDE_COLOR = StandardColors.AXIS_MARKER_COLOR;

  public static final Color CPU_CAPTURE_STATUS = new JBColor(0x545454, 0xCACACA);

  public static final Color CPU_THREAD_SELECTED_BACKGROUND = new JBColor(0x3476DC, 0x3476DC);

  public static final Color THREAD_LABEL_TEXT = new JBColor(0x434343, 0xBCBCBC);

  public static final Color SELECTED_THREAD_LABEL_TEXT = Gray.xFF;

  public static final Color THREAD_LABEL_BACKGROUND = new JBColor(new Color(0xEFFFFFFF, true), new Color(0xEF2B2D2E, true));

  public static final Color THREAD_LABEL_BORDER = new JBColor(new Color(0x0C000000, true), new Color(0x0CFFFFFF, true));

  public static final Color CAPTURE_SPARKLINE = new JBColor(0xC2D6F6, 0x455563);

  public static final Color CAPTURE_SPARKLINE_ACCENT = new JBColor(0xA0B4D4, 0x677785);

  public static final Color CAPTURE_SPARKLINE_SELECTED = new JBColor(0x4785EB, 0x5887DC);

  public static final Color CAPTURE_SPARKLINE_SELECTED_ACCENT = new JBColor(0x2563C9, 0x3665BA);

  public static final Color CPU_CAPTURE_BACKGROUND = new JBColor(0xECF2FA, 0x323940);

  public static final Color CPU_CALLCHART_VENDOR = new JBColor(0xA2DEFF, 0xA2DEFF);

  public static final Color CPU_CALLCHART_VENDOR_HOVER = new JBColor(0xB2E5FF, 0xB2E5FF);

  public static final Color CPU_CALLCHART_APP = new JBColor(0x9FEAAD, 0x9FEAAD);

  public static final Color CPU_CALLCHART_APP_HOVER = new JBColor(0xADF0B9, 0xADF0B9);

  public static final Color CPU_CALLCHART_PLATFORM = new JBColor(0xFECC82, 0xFECC82);

  public static final Color CPU_CALLCHART_PLATFORM_HOVER = new JBColor(0xFFDAA2, 0xFFDAA2);

  public static final Color CPU_FLAMECHART_VENDOR = new JBColor(0xFFC56F, 0xFFC56F);

  public static final Color CPU_FLAMECHART_VENDOR_HOVER = new JBColor(0xFFD495, 0xFFD495);

  public static final Color CPU_FLAMECHART_APP = new JBColor(0xFFE0B2, 0xFFE0B2);

  public static final Color CPU_FLAMECHART_APP_IDLE = ColorUtil.darker(CPU_FLAMECHART_APP, 2);

  public static final Color CPU_FLAMECHART_APP_HOVER = new JBColor(0xFFECD1, 0xFFECD1);

  public static final Color CPU_FLAMECHART_APP_HOVER_IDLE = ColorUtil.darker(CPU_FLAMECHART_APP_HOVER, 2);

  public static final Color CPU_FLAMECHART_PLATFORM = new JBColor(0xFF855E, 0xFF855E);

  public static final Color CPU_FLAMECHART_PLATFORM_HOVER = new JBColor(0xFF9674, 0xFF9674);

  public static final Color CPU_STATECHART_DEFAULT_STATE = UIUtil.getPanelBackground();

  public static final Color CPU_PROFILING_CONFIGURATIONS_SELECTED = new JBColor(0x1155CC, 0x1155CC);

  public static final Color CPU_RECORDING_CONFIGURATION_DESCRIPTION = new JBColor(0x4E4E4E, 0xB5B5B5);

  public static final Color CPU_DURATION_LABEL_BACKGROUND = new JBColor(new Color(0x70000000, true), new Color(0x70000000, true));

  public static final Color COMBOBOX_BORDER = new JBColor(0xAAAAAA, 0xAAAAAA);

  public static final Color COMBOBOX_SELECTED_CELL = new JBColor(0x3875D6, 0x3875D6);

  public static final Color DEFAULT_BACKGROUND = StudioColorsKt.getPrimaryContentBackground();

  public static final Color DEFAULT_STAGE_BACKGROUND = StudioColorsKt.getPrimaryContentBackground();

  public static final Color MONITOR_FOCUSED = new JBColor(0xF5F7F8, 0x2B2C2D);

  public static final Color MONITOR_DISABLED = new JBColor(0xF8F8F8, 0x333436);

  public static final Color MONITOR_ERROR = new JBColor(0xFFEEEE, 0x332222);

  public static final Color MONITOR_MAX_LINE = new JBColor(0xCCCDCD, 0x494949);

  public static final Color MONITOR_BORDER = new JBColor(0xC9C9C9, 0x3F4142);

  public static final Color NETWORK_CONNECTIONS_COLOR = new JBColor(new Color(0xEFC4B2), new Color(0x7D6B64));

  public static final Color NETWORK_RECEIVING_COLOR = new JBColor(0x5882CC, 0x557CC1);

  public static final Color NETWORK_RECEIVING_SELECTED_COLOR = new JBColor(0x8ebdff, 0x8ebdff);

  public static final Color NETWORK_SENDING_COLOR = new JBColor(0xF4AF6F, 0xFFC187);

  public static final Color NETWORK_WAITING_COLOR = new JBColor(0xAAAAAA, 0xAAAAAA);

  public static final Color NETWORK_THREADS_VIEW_TOOLTIP_DIVIDER = new JBColor(0xD3D3D3, 0x565656);

  public static final Color MEMORY_TOTAL = new JBColor(new Color(0x56BFEC), new Color(0x2B7DA2));

  public static final Color MEMORY_JAVA = new JBColor(new Color(0x56BFEC), new Color(0x2B7DA2));

  public static final Color MEMORY_JAVA_CAPTURED = new JBColor(new Color(0x45A5CF), new Color(0x3C9EC9));

  public static final Color MEMORY_NATIVE = new JBColor(new Color(0x56A5CB), new Color(0x226484));

  public static final Color MEMORY_NATIVE_CAPTURED = new JBColor(new Color(0x478EB2), new Color(0x307EA3));

  public static final Color MEMORY_CODE = new JBColor(new Color(0x80EDDC), new Color(0x4EA783));

  public static final Color MEMORY_CODE_CAPTURED = new JBColor(new Color(0x6BCFBF), new Color(0x67D0A5));

  public static final Color MEMORY_STACK = new JBColor(new Color(0x50CBB8), new Color(0x348866));

  public static final Color MEMORY_STACK_CAPTURED = new JBColor(new Color(0x3FB1A0), new Color(0x47A981));

  public static final Color MEMORY_GRAPHICS = new JBColor(new Color(0xF4DEA2), new Color(0xA8825C));

  public static final Color MEMORY_GRAPHICS_CAPTURED = new JBColor(new Color(0xE2BF8A), new Color(0xCBA76D));

  public static final Color MEMORY_OTHERS = new JBColor(new Color(0x6E92A5), new Color(0x4E616B));

  public static final Color MEMORY_OTHERS_CAPTURED = new JBColor(new Color(0x5D7F91), new Color(0x637A85));

  public static final Color MEMORY_OBJECTS = new JBColor(new Color(0x1B6386), new Color(0xD8DBDE));

  public static final Color MEMORY_OBJECTS_CAPTURED = new JBColor(new Color(0x1B4D65), new Color(0xF6F6F6));

  public static final Color MEMORY_HEAP_DUMP_BG = new JBColor(new Color(0xD8D8D8), new Color(0x9C9C9C));

  public static final Color MEMORY_ALLOC_BG = new JBColor(new Color(0xECF2FA), new Color(0x323940));

  public static final Color ENERGY_BACKGROUND = new JBColor(new Color(0xF1B876), new Color(0xFFDFA6));

  public static final Color ENERGY_CPU = new JBColor(new Color(0xDCD0F3), new Color(0x685A83));

  public static final Color ENERGY_NETWORK = new JBColor(new Color(0xB39DDB), new Color(0xA78BD8));

  public static final Color ENERGY_WAKE_LOCK = new JBColor(new Color(0xF44271), new Color(0xF3596C));

  public static final Color ENERGY_LOCATION = new JBColor(new Color(0x7152A7), new Color(0xDCC8FF));

  public static final Color MESSAGE_COLOR = new JBColor(0x787878, 0xC8C8C8);

  public static final Color MONITORS_HEADER_TEXT = new JBColor(0x545454, 0xCACACA);

  public static final Color TOOLTIP_BACKGROUND = StudioColorsKt.getCanvasTooltipBackground();

  public static final Color TOOLTIP_TEXT = JBColor.foreground();

  public static final Color TOOLTIP_LOW_CONTRAST = new JBColor(0x888888, 0x838485);

  public static final Color USER_COUNTER_EVENT_NOT_STARTED = new JBColor(0xF1EBDA, 0xF1EBDA);

  public static final Color USER_COUNTER_EVENT_NONE = new JBColor(0XE6ecf0, 0xe6ecf0);

  public static final Color USER_COUNTER_EVENT_LIGHT = new JBColor(0xc6d4dc, 0xc6d4dc);

  public static final Color USER_COUNTER_EVENT_MED = new JBColor(0xa6bcc9, 0xa6bcc9);

  public static final Color USER_COUNTER_EVENT_DARK = new JBColor(0x7699ac, 0x7699ac);

  public static final Color USER_COUNTER_EVENT_USAGE = new JBColor(0xa6bcc9, 0xa6bcc9);

  public static final Color VSYNC_BACKGROUND = new JBColor(0xEEEEEE, 0x111111);

  public static final Color WARNING_BAR_COLOR = new JBColor(new Color(254, 248, 213), new Color(35, 56, 85));
}
