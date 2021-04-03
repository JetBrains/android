/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.model.StreamingTimeline
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent

internal class FakeStage(profilers: StudioProfilers) : Stage<StreamingTimeline>(profilers) {
  override fun enter() {}

  override fun exit() {}

  override fun getStageType() = AndroidProfilerEvent.Stage.UNKNOWN_STAGE

  override fun getTimeline(): StreamingTimeline {
    return studioProfilers.timeline
  }
}
