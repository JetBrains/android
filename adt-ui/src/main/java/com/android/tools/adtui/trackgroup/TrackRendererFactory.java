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

/**
 * Factory interface to instantiate {@link TrackRenderer}s. Implement this with a concrete enum of renderer types for a given domain (e.g.
 * profilers).
 *
 * @param <R> concrete renderer enum type for a specific domain.
 */
public interface TrackRendererFactory<R extends Enum> {
  /**
   * @param rendererType renderer type
   * @return renderer instance for the given renderer type.
   */
  @NotNull
  TrackRenderer<?, R> createRenderer(@NotNull R rendererType);
}
