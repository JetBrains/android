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

import com.android.tools.profilers.analytics.FeatureTracker;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class for a service responsible for handling navigations to target {@link CodeLocation}s,
 * as well as registering and triggering listeners interested in the event.
 */
public abstract class CodeNavigator {
  private final List<Listener> myListeners = Lists.newArrayList();
  @NotNull private final FeatureTracker myFeatureTracker;

  /**
   * @param featureTracker Tracker used to report how often users navigate to code.
   */
  public CodeNavigator(@NotNull FeatureTracker featureTracker) {
    myFeatureTracker = featureTracker;
  }

  public final void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  public final void removeListener(@NotNull Listener listener) {
    myListeners.remove(listener);
  }

  public final void navigate(@NotNull CodeLocation location) {
    handleNavigate(location);
    myListeners.forEach(l -> l.onNavigated(location));
    myFeatureTracker.trackNavigateToCode();
  }

  protected abstract void handleNavigate(@NotNull CodeLocation location);

  public interface Listener {
    void onNavigated(@NotNull CodeLocation location);
  }
}
