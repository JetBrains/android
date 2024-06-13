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
package com.android.tools.idea.run;

import com.android.annotations.concurrency.Slow;
import org.jetbrains.annotations.NotNull;

public interface LaunchCompatibilityChecker {
  @Slow
  @NotNull
  LaunchCompatibility validate(@NotNull AndroidDevice device);

  /**
   * Lazily combine this [LaunchCompatibilityChecker] with another [LaunchCompatibilityChecker]; i.e.
   * only evaluate compatibility with the other checker if this returns OK or WARNING.
   */
  default LaunchCompatibilityChecker combine(LaunchCompatibilityChecker other) {
    return new LaunchCompatibilityChecker() {
      @Override
      public @NotNull LaunchCompatibility validate(@NotNull AndroidDevice device) {
        LaunchCompatibility compatibility = LaunchCompatibilityChecker.this.validate(device);
        return switch (compatibility.getState()) {
          case ERROR -> compatibility;
          default -> compatibility.combine(other.validate(device));
        };
      }
    };
  }
}
