/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ModificationTracker;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that owns an atomic counter for how many times the data binding setting is changed.
 * This allows it to provide a {@link ModificationTracker} which is used by IntelliJ for knowing
 * when to clear caches, etc.
 *
 * TODO(b/119823849): Investigate deleting this application-level class. There should probably be
 * one tracker per module.
 */
public final class DataBindingModeTrackingService {
  public static DataBindingModeTrackingService getInstance() {
    return ServiceManager.getService(DataBindingModeTrackingService.class);
  }

  /**
   * Tracker that changes when a facet's data binding enabled value changes
   */
  public static final ModificationTracker DATA_BINDING_ENABLED_TRACKER = () -> getInstance().myEnabledModificationCount.longValue();

  private final AtomicLong myEnabledModificationCount = new AtomicLong(0);

  public void incrementModificationCount() {
    myEnabledModificationCount.incrementAndGet();
  }
}
