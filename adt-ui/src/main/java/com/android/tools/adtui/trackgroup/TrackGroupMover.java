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
package com.android.tools.adtui.trackgroup;

import org.jetbrains.annotations.NotNull;

public interface TrackGroupMover {
  /**
   * Moves a track group up one position in a list.
   *
   * @param trackGroup the track group to move up
   */
  void moveTrackGroupUp(@NotNull TrackGroup trackGroup);

  /**
   * Moves a track group down one position in a list.
   *
   * @param trackGroup the track group to move down
   */
  void moveTrackGroupDown(@NotNull TrackGroup trackGroup);
}
