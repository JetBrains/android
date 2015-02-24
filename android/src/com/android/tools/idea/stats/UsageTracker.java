/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.stats;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract Studio Usage Tracker. Implemented as Application Service.
 */
public abstract class UsageTracker {

  /**
   * When using the usage tracker, do NOT include any information that can identify the user
   */
  @NotNull
  public static UsageTracker getInstance() {
    return ServiceManager.getService(UsageTracker.class);
  }

  /**
   * When tracking events, do NOT include any information that can identify the user
   */
  public abstract void trackEvent(@NotNull String eventCategory,
                                  @NotNull String eventAction,
                                  @Nullable String eventLabel,
                                  @Nullable Integer eventValue);

}
