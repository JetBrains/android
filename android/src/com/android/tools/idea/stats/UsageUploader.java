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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * {@link UsageUploader} provides the APIs to upload analytics events. Currently, there are two possible destinations: GA and Dremel.
 * In almost all cases, data should go only to GA.
 *
 * Note: Do not use this API directly in client code. Use the API as provided by {@link UsageTracker}.
 */
public interface UsageUploader {
  /** Tracks an event as defined by GA. The various parameters match the values that can be provided to a GA Event Hit. */
  void trackEvent(@NotNull String eventCategory, @NotNull String eventAction, @Nullable String eventLabel, @Nullable Integer eventValue);

  /**
   * Track a set of key-value pairs. This is reported only to tools.google.com.
   * The parameters will be escaped using {@link java.net.URLEncoder#encode(String, String)}.
   */
  void trackEvent(@NotNull String categoryId, @NotNull Map<String, String> parameters);
}
