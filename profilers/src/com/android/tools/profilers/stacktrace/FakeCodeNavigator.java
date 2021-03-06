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
package com.android.tools.profilers.stacktrace;

import com.android.tools.inspectors.common.api.stacktrace.CodeLocation;
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator;
import com.android.tools.profilers.analytics.FeatureTracker;
import org.jetbrains.annotations.NotNull;

public class FakeCodeNavigator extends CodeNavigator {
  @NotNull
  private final FeatureTracker myFeatureTracker;

  public FakeCodeNavigator(@NotNull FeatureTracker featureTracker) {
    myFeatureTracker = featureTracker;
  }

  @Override
  public boolean isNavigatable(@NotNull CodeLocation location) {
    return true;
  }

  @Override
  protected void handleNavigate(@NotNull CodeLocation location) {
    myFeatureTracker.trackNavigateToCode();
  }
}
