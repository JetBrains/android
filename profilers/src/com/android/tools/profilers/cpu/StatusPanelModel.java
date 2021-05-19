/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * This model is used to drive the data in the {@link StatusPanel}
 */
public interface StatusPanelModel {
  /**
   * @return Text to be used for the "Type" label this often is the {@link ProfilingConfiguration} name.
   */
  @NotNull
  String getConfigurationText();

  /**
   * @return The range to define the duration of the operation.
   */
  @NotNull
  Range getRange();

  /**
   * A way for the user to cancel / stop the current running operation if they deem it takes to long.
   */
  void abort();
}
