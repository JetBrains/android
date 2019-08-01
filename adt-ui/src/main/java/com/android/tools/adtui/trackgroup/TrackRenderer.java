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

import com.android.tools.adtui.model.trackgroup.TrackModel;
import java.awt.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for rendering the content of a track. Implementation should instantiate a UI component to visualize a {@link TrackModel}.
 *
 * @param <M> data model type
 * @param <R> renderer enum type
 */
public interface TrackRenderer<M, R extends Enum> {
  /**
   * Renders {@link TrackModel} into a specialized UI component, to be added to a {@link Track}.
   */
  @NotNull
  Component render(@NotNull TrackModel<M, R> trackModel);
}
