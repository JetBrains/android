/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor.gpu.gfxinfohandlers;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.adtui.TimelineData;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GfxinfoHandler {
  int SAMPLE_BUFFER_SIZE = 8192;

  /**
   * Tests if this sampler can process the given client.
   *
   * @param client represents the attached device
   * @return true if this sampler can process the given client, false otherwise
   */
  boolean accept(@NotNull Client client);

  /**
   * Callback for when the client has changed.
   */
  void setClient(@Nullable Client client);

  /**
   * Samples the GPU profiling information.
   */
  void sample(@NotNull IDevice device, @NotNull ClientData data, @NotNull TimelineData timeline) throws Exception;

  /**
   * Callback to create the model for this sampler.
   *
   * @return the TimelineData model
   */
  @NotNull
  TimelineData createTimelineData();

  /**
   * Checks if the developer option for GPU monitoring is turned on.
   *
   * @return a {@code ThreeState} enum. {@code ThreeState.YES} indicates it is enabled,
   * {@code ThreeState.NO} indicates it is disabled, and {@code ThreeState.UNSURE} indicates
   * the operation has timed out and we can not determine what the value is.
   * In this case, treat the value as the last known good value instead
   */
  ThreeState getIsEnabledOnDevice(@NotNull IDevice device);
}
