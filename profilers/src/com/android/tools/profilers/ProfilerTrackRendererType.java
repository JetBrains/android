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
package com.android.tools.profilers;

/**
 * Enum of {@link com.android.tools.adtui.model.trackgroup.TrackModel} renderer types used in profilers.
 */
public enum ProfilerTrackRendererType {
  /**
   * For user interaction events (e.g. touch).
   */
  USER_INTERACTION,
  /**
   * For app lifecycle events (i.e. activities and fragments).
   */
  APP_LIFECYCLE,
  /**
   * For Atrace frame rendering data.
   */
  FRAMES,
  /**
   * For Atrace Surfaceflinger events.
   */
  SURFACEFLINGER,
  /**
   * For Atrace VSYNC signals.
   */
  VSYNC,
  /**
   * For CPU thread states and trace events.
   */
  CPU_THREAD
}
