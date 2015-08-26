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

import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsageTrackerAnalyticsImpl extends UsageTracker {
  private static final ExtensionPointName<UsageUploader> EP_NAME = ExtensionPointName.create("com.android.tools.idea.stats.tracker");

  private final UsageUploader myUploader;

  public UsageTrackerAnalyticsImpl() {
    UsageUploader[] uploaders = EP_NAME.getExtensions();
    myUploader = uploaders.length > 0 ? uploaders[0] : null;
  }

  @Override
  public void trackEvent(@NotNull String eventCategory,
                         @NotNull String eventAction,
                         @Nullable String eventLabel,
                         @Nullable Integer eventValue) {
    if (ApplicationManager.getApplication().isUnitTestMode()
      || !AndroidStudioInitializer.isAndroidStudio()
      || !StatisticsUploadAssistant.isSendAllowed()
      || myUploader == null) {
      return;
    }

    myUploader.trackEvent(eventCategory, eventAction, eventLabel, eventValue);
  }
}
