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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.profilers.cpu.CaptureNode;
import org.jetbrains.annotations.NotNull;

public class CpuCaptureNodeTooltip implements TooltipModel {
  @NotNull private final Timeline myTimeline;
  @NotNull private final CaptureNode myCaptureNode;

  public CpuCaptureNodeTooltip(@NotNull Timeline timeline, @NotNull CaptureNode captureNode) {
    myTimeline = timeline;
    myCaptureNode = captureNode;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }

  @NotNull
  public CaptureNode getCaptureNode() {
    return myCaptureNode;
  }
}
