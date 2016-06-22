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
package com.android.tools.idea.stats;

import com.android.tools.analytics.CommonMetricsData;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.ProductDetails;
import com.intellij.openapi.application.ApplicationInfo;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks Android Studio specific metrics
 **/
public class AndroidStudioUsageTracker {
    public static void setup(ScheduledExecutorService scheduler) {
        // Send initial report immediately, daily from then on.
        scheduler.scheduleWithFixedDelay(
                AndroidStudioUsageTracker::runDailyReports, 0, 1, TimeUnit.DAYS);
    }

    private static void runDailyReports() {
        com.android.tools.analytics.UsageTracker tracker =
                com.android.tools.analytics.UsageTracker.getInstance();
        ApplicationInfo application = ApplicationInfo.getInstance();
        tracker.log(
                AndroidStudioEvent.newBuilder()
                        .setCategory(AndroidStudioEvent.EventCategory.PING)
                        .setKind(AndroidStudioEvent.EventKind.STUDIO_PING)
                        .setProductDetails(
                                ProductDetails.newBuilder()
                                        .setProduct(ProductDetails.ProductKind.STUDIO)
                                        .setBuild(application.getBuild().asStringWithAllDetails())
                                        .setVersion(application.getStrictVersion())
                                        .setOsArchitecture(CommonMetricsData.getOsArchitecture())));
    }
}
