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
package com.android.tools.idea.common.analytics;

import com.android.tools.idea.rendering.RenderResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * No-op tracker used when stats tracking is disabled. It keeps track of the last logged event type.
 */
@VisibleForTesting
public class CommonNopTracker implements CommonUsageTracker {

  private LayoutEditorEvent.LayoutEditorEventType myLastTrackedEvent;

  @Override
  public void logAction(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType) {
    myLastTrackedEvent = eventType;
  }

  @Override
  public void logRenderResult(@Nullable LayoutEditorRenderResult.Trigger trigger, @NotNull RenderResult result, boolean wasInflated) {
  }

  @Override
  public void logStudioEvent(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType,
                             @Nullable Consumer<LayoutEditorEvent.Builder> consumer) {
    myLastTrackedEvent = eventType;
  }

  public LayoutEditorEvent.LayoutEditorEventType getLastTrackedEvent() {
    return myLastTrackedEvent;
  }

  @TestOnly
  public void resetLastTrackedEvent() {
    myLastTrackedEvent = null;
  }
}
