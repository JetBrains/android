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

import com.android.tools.adtui.model.AspectModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

import static com.android.tools.profilers.StudioProfilers.INVALID_PROCESS_ID;

/**
 * This stage only gets set when the profilers first open. It represents the stage of no-data, and no-device.
 */
public class NullMonitorStage extends Stage {

  public NullMonitorStage(@NotNull StudioProfilers profiler) {
    super(profiler);
  }

  @Override
  public void enter() {
  }

  @Override
  public void exit() {
  }
}
