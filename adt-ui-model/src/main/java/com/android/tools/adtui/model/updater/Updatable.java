/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.adtui.model.updater;

public interface Updatable {

  /**
   * Used for resetting any cached states from previous frames that an {@link Updatable}
   * depends on.
   */
  default void reset() {
  }

  /**
   * Triggered by the {@link Choreographer} to give an {@link Updatable} a chance to
   * update/interpolate any components or data based on the current frame rate.
   * @param elapsedNs the time elapsed since the last update in nanoseconds.
   */
  void update(long elapsedNs);

  /**
   * Triggered by the {@link Choreographer} after all components have finished animating.
   * This allows an {@link Updatable} to read any data modified by other components
   * during {@link #update(float)}.
   */
  default void postUpdate() {
  }

  /**
   * An auxiliary function to allow an {@link Updatable} to configure its interpolation speed when calling the
   * {@link Choreographer#lerp(float, float, float, float, float)} method.
   */
  default void setLerpFraction(float fraction) {
  }

  /**
   * An auxiliary function to allow an {@link Updatable} to configure its lerp snapping threshold when calling the
   * {@link Choreographer#lerp(float, float, float, float, float)} method.
   */
  default void setLerpThreshold(float threshold) {
  }
}
