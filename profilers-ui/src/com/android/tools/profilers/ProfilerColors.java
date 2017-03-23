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
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.google.common.collect.ImmutableMap;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.Map;

public class ProfilerColors {

  // Collections of constant, do not instantiate.
  private ProfilerColors() {
  }

  public static final Color CPU_USAGE = new JBColor(0x57D9B2, 0x387358);

  public static final Color CPU_OTHER_USAGE = new JBColor(0xDFE6EE, 0x34383D);

  // TODO: define final color
  public static final Color CPU_CAPTURE_EVENT = new JBColor(0x888888, 0x888888);

  public static final Color THREADS_COUNT_COLOR = new JBColor(0x558A71, 0x71D7A6);

  /**
   * Represents pair of colors of non-selected and selected states of a thread.
   * The first color is for a non-selected thread, the second one is for a selected thread.
   */
  public static final EnumColors<CpuProfilerStage.ThreadState> THREAD_STATES = new EnumColors.Builder<CpuProfilerStage.ThreadState>(2)
    .add(CpuProfilerStage.ThreadState.RUNNING,
         CPU_USAGE,
         new JBColor(0x57D9B2, 0x387358))
    .add(CpuProfilerStage.ThreadState.RUNNING_CAPTURED,
         new JBColor(0x53B5A0, 0x44B67F),
         new JBColor(0x84DEA7, 0x84DEA7))
    .add(CpuProfilerStage.ThreadState.WAITING,
         new JBColor(0xD4E675, 0x94A244),
         new JBColor(0xD4E675, 0x94A244))
    .add(CpuProfilerStage.ThreadState.WAITING_CAPTURED,
         new JBColor(0xEFF35C, 0xDCF35C),
         new JBColor(0xEFF35C, 0xDCF35C))
    .add(CpuProfilerStage.ThreadState.SLEEPING,
         new JBColor(0xEDEFF1, 0x3B3E42),
         new JBColor(0x7BA6E9, 0x7BA6E9))
    .add(CpuProfilerStage.ThreadState.SLEEPING_CAPTURED,
         new JBColor(0xD4D7DA, 0x4B4E52),
         new JBColor(0x8FB3EA, 0x8FB3EA))
    .add(CpuProfilerStage.ThreadState.DEAD,
         Gray.TRANSPARENT,
         Gray.TRANSPARENT)
    .add(CpuProfilerStage.ThreadState.DEAD_CAPTURED,
         Gray.TRANSPARENT,
         Gray.TRANSPARENT)
    // TODO: remove UNKNOWN mapping when all states are covered.
    .add(CpuProfilerStage.ThreadState.UNKNOWN,
         new JBColor(0xC1D6F6, 0x5A6E7D),
         new JBColor(0xC1D6F6, 0x5A6E7D))
    .build();

  public static final Color TRANSPARENT_COLOR = new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0));

  public static final Color THREAD_HOVER_BACKGROUND = new JBColor(new Color(0x171650C5, true), new Color(0x0CFFFFFF, true));

  public static final Color THREAD_SELECTED_BACKGROUND = new JBColor(0x3476DC, 0x3476DC);

  public static final Color DEFAULT_BACKGROUND = new JBColor(0xFFFFFF, 0x313335);

  public static final Color MONITOR_FOCUSED = new JBColor(0xF5F7F8, 0x2B2C2D);

  public static final Color MONITOR_DISABLED = new JBColor(0xF8F8F8, 0x333436);

  public static final Color MONITOR_MAX_LINE = new JBColor(0xCCCDCD, 0x494949);

  public static final Color MONITOR_BORDER = new JBColor(0xC9C9C9, 0x3F4142);

  public static final Color NETWORK_CONNECTIONS_COLOR = new JBColor(new Color(0xB4E6A082, true), new Color(0x7FAC8C7E, true));

  public static final Color NETWORK_RECEIVING_COLOR = new JBColor(0x5983E0, 0x5983E0);

  public static final Color NETWORK_RECEIVING_SELECTED_COLOR = new JBColor(0x8ebdff, 0x8ebdff);

  public static final Color NETWORK_SENDING_COLOR = new JBColor(0xEF9F5C, 0xEF9F5C);

  public static final Color NETWORK_WAITING_COLOR = new JBColor(0xAAAAAA, 0xAAAAAA);

  public static final Color NETWORK_RADIO_WIFI = new JBColor(0xCAD3E2, 0x4E4F50);

  public static final Color NETWORK_RADIO_IDLE = new JBColor(0xB2CAF5, 0x384460);

  public static final Color NETWORK_RADIO_LOW = new JBColor(0x6A99FE, 0x567CCF);

  public static final Color NETWORK_RADIO_HIGH = new JBColor(0x396EE7, 0x3D5FAB);

  public static final Color NETWORK_TABLE_AXIS = new JBColor(Gray._103, Gray._120);

  public static final Color NETWORK_TABLE_AXIS_SELECTED = JBColor.BLACK;

  public static final Color MEMORY_TOTAL = new JBColor(new Color(98, 180, 223), new Color(83, 172, 209));

  public static final Color MEMORY_JAVA = new JBColor(new Color(69, 165, 207), new Color(60, 158, 201));

  public static final Color MEMORY_NATIVE = new JBColor(new Color(71, 142, 178), new Color(48, 126, 163));

  public static final Color MEMORY_CODE = new JBColor(new Color(107, 207, 191), new Color(103, 208, 165));

  public static final Color MEMORY_STACK = new JBColor(new Color(63, 177, 160), new Color(71, 169, 129));

  public static final Color MEMORY_GRAPHICS = new JBColor(new Color(226, 191, 138), new Color(203, 167, 109));

  public static final Color MEMORY_OTHERS = new JBColor(new Color(68, 107, 127), new Color(99, 122, 133));

  public static final Color MEMORY_OBJECTS = new JBColor(new Color(84, 110, 121), new Color(159, 190, 213));

  public static final Color MESSAGE_COLOR = new JBColor(0x787878, 0xC8C8C8);
}
