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
package com.android.tools.adtui.model.trackgroup;

import com.android.tools.adtui.model.MultiSelectionModel;
import org.jetbrains.annotations.NotNull;

/**
 * Interface to be used with multiple {@link TrackModel}s in a {@link MultiSelectionModel}.
 */
public interface SelectableTrackModel {
  /**
   * @return true if this object can be added to the same {@link MultiSelectionModel} with the other object.
   */
  boolean isCompatibleWith(@NotNull SelectableTrackModel otherObj);
}
