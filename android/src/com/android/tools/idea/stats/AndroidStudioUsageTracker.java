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

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.Anonymizer;
import com.android.tools.analytics.CommonMetricsData;
import com.android.utils.ILogger;
import com.google.common.base.Strings;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.ProductDetails;
import com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * Tracks Android Studio specific metrics
 **/
public class AndroidStudioUsageTracker {
    private static final String ANONYMIZATION_ERROR = "*ANONYMIZATION_ERROR*";
    public static ILogger LOGGER = new IntelliJLogger(AndroidStudioUsageTracker.class);

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
                                        .setBuild(application.getBuild().asString())
                                        .setVersion(application.getStrictVersion())
                                        .setOsArchitecture(CommonMetricsData.getOsArchitecture())));
    }

    /**
     * Like {@link Anonymizer#anonymizeUtf8(ILogger, String)} but maintains its own IntelliJ logger and upon error
     * reports to logger and returns ANONYMIZATION_ERROR instead of throwing an exception.
     */
    @NotNull
    public static String anonymizeUtf8(@NotNull String value) {
        try {
            return Anonymizer.anonymizeUtf8(LOGGER, value);
        }
        catch (IOException e) {
            LOGGER.error(e, "Unable to read anonymization settings, not reporting any values");
            return ANONYMIZATION_ERROR;
        }
    }

    /**
     * Creates a {@link AndroidStudioStats.DeviceInfo} from a {@link IDevice} instance.
     */
    @NotNull
    public static AndroidStudioStats.DeviceInfo deviceToDeviceInfo(@NotNull IDevice device) {
        return AndroidStudioStats.DeviceInfo.newBuilder()
            .setAnonymizedSerialNumber(anonymizeUtf8(device.getSerialNumber()))
            .setBuildTags(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TAGS)))
            .setBuildType(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TYPE)))
            .setBuildVersionRelease(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_VERSION)))
            .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
            .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(device.getProperty(IDevice.PROP_DEVICE_CPU_ABI)))
            .setManufacturer(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)))
            .setModel(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MODEL))).build();
    }

    /**
     * Creates a {@link AndroidStudioStats.DeviceInfo} from a {@link IDevice} instance
     * containing api level only.
     */
    @NotNull
    public static AndroidStudioStats.DeviceInfo deviceToDeviceInfoApilLevelOnly(@NotNull IDevice device) {
        return AndroidStudioStats.DeviceInfo.newBuilder()
            .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
            .build();
    }
}
