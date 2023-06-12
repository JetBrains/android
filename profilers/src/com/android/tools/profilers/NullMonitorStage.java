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

import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This stage gets set when the profilers first open and no session has been selected.
 */
public class NullMonitorStage extends StreamingStage {
  @Nullable private String myUnsupportedReason;

  public NullMonitorStage(@NotNull StudioProfilers profiler) {
    this(profiler, null);
  }

  public NullMonitorStage(@NotNull StudioProfilers profilers, @Nullable String unsupportedReason) {
    super(profilers);
    myUnsupportedReason = unsupportedReason;
  }

  /**
   * @return string representing the reason if device is unsupported, null otherwise.
   */
  @Nullable
  public String getUnsupportedReason() {
    return myUnsupportedReason;
  }

  @Override
  public void enter() {
    logEnterStage();
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());
  }

  @Override
  public void exit() {
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.NULL_STAGE;
  }
}
