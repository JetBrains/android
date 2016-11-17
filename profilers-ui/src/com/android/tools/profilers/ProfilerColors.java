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

import com.android.tools.profiler.proto.CpuProfiler;
import com.google.common.collect.ImmutableMap;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.Map;

public class ProfilerColors {

  public static final Color CPU_USAGE = new JBColor(0x62C88E, 0x62C88E);

  public static final Color TOTAL_MEMORY = new JBColor(0x62B7DF, 0x62B7DF);

  public static final Color CPU_OTHER_USAGE = new JBColor(0xDFE6EE, 0xDFE6EE);

  public static final Color MONITOR_BACKGROUND = JBColor.background();

  public static final Color MONITOR_BORDER = new JBColor(0xC9C9C9, 0xC9C9C9);

  public static final Color NETWORK_CONNECTIONS_COLOR = new JBColor(0x5A8725, 0x5A8725);

  public static final Color NETWORK_RECEIVING_COLOR = new JBColor(0x2865BD, 0x2865BD);

  public static final Color NETWORK_SENDING_COLOR = new JBColor(0xFF7B00, 0xFF7B00);

  public static final Color NETWORK_WAITING_COLOR = new JBColor(0xAAAAAA, 0xAAAAAA);

  public static final Map<CpuProfiler.GetThreadsResponse.State, Color> THREAD_STATES = ImmutableMap.of(
    CpuProfiler.GetThreadsResponse.State.RUNNING, CPU_USAGE,
    CpuProfiler.GetThreadsResponse.State.SLEEPING, new JBColor(0xEDEFF1, 0xEDEFF1),
    CpuProfiler.GetThreadsResponse.State.DEAD, Gray.TRANSPARENT);
}
