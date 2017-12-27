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
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.android.tools.log.LogWrapper;
import com.google.common.base.Strings;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.ProductDetails;
import com.google.wireless.android.sdk.stats.ProductDetails.SoftwareLifeCycleChannel;
import com.google.wireless.android.sdk.stats.StudioProjectChange;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks Android Studio specific metrics
 **/
public class AndroidStudioUsageTracker {

  public static void setup(ScheduledExecutorService scheduler) {
    // Send initial report immediately, daily from then on.
    scheduler.scheduleWithFixedDelay(AndroidStudioUsageTracker::runDailyReports, 0, 1, TimeUnit.DAYS);
    // Send initial report immediately, hourly from then on.
    scheduler.scheduleWithFixedDelay(AndroidStudioUsageTracker::runHourlyReports, 0, 1, TimeUnit.HOURS);

    subscribeToEvents();
  }

  private static void subscribeToEvents() {
    Application app = ApplicationManager.getApplication();
    MessageBusConnection connection = app.getMessageBus().connect();
    connection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleTracker());
  }

  private static void runDailyReports() {
    ApplicationInfo application = ApplicationInfo.getInstance();

    UsageTracker.getInstance().log(
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PING)
        .setKind(AndroidStudioEvent.EventKind.STUDIO_PING)
        .setProductDetails(
          ProductDetails.newBuilder()
            .setProduct(ProductDetails.ProductKind.STUDIO)
            .setBuild(application.getBuild().asString())
            .setVersion(application.getStrictVersion())
            .setOsArchitecture(CommonMetricsData.getOsArchitecture())
          .setChannel(lifecycleChannelFromUpdateSettings()))
        .setMachineDetails(CommonMetricsData.getMachineDetails(new File(PathManager.getHomePath())))
        .setJvmDetails(CommonMetricsData.getJvmDetails()));
  }

  private static void runHourlyReports() {
    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                     .setCategory(AndroidStudioEvent.EventCategory.SYSTEM)
                                     .setKind(AndroidStudioEvent.EventKind.STUDIO_PROCESS_STATS)
                                     .setJavaProcessStats(CommonMetricsData.getJavaProcessStats()));
  }

  /**
   * Like {@link Anonymizer#anonymizeUtf8(ILogger, String)} but maintains its own IntelliJ logger and upon error
   * reports to logger and returns ANONYMIZATION_ERROR instead of throwing an exception.
   *
   * @deprecated Use {@link AnonymizerUtil#anonymizeUtf8(String)}} instead.
   */
  @Deprecated
  @NotNull
  public static String anonymizeUtf8(@NotNull String value) {
    return AnonymizerUtil.anonymizeUtf8(value);
  }

  /**
   * Creates a {@link DeviceInfo} from a {@link IDevice} instance.
   */
  @NotNull
  public static DeviceInfo deviceToDeviceInfo(@NotNull IDevice device) {
    return DeviceInfo.newBuilder()
      .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(device.getSerialNumber()))
      .setBuildTags(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TAGS)))
      .setBuildType(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_TYPE)))
      .setBuildVersionRelease(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_VERSION)))
      .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
      .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(device.getProperty(IDevice.PROP_DEVICE_CPU_ABI)))
      .setManufacturer(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)))
      .setDeviceType(device.isEmulator() ? DeviceInfo.DeviceType.LOCAL_EMULATOR : DeviceInfo.DeviceType.LOCAL_PHYSICAL)
      .setModel(Strings.nullToEmpty(device.getProperty(IDevice.PROP_DEVICE_MODEL))).build();
  }

  /**
   * Creates a {@link DeviceInfo} from a {@link IDevice} instance
   * containing api level only.
   */
  @NotNull
  public static DeviceInfo deviceToDeviceInfoApilLevelOnly(@NotNull IDevice device) {
    return DeviceInfo.newBuilder()
      .setBuildApiLevelFull(Strings.nullToEmpty(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)))
      .build();
  }

  /**
   * Reads the channel selected by the user from UpdateSettings and converts it into a {@link SoftwareLifeCycleChannel} value.
   */
  private static SoftwareLifeCycleChannel lifecycleChannelFromUpdateSettings() {
    switch (UpdateSettings.getInstance().getSelectedChannelStatus()) {
      case EAP: return SoftwareLifeCycleChannel.CANARY;
      case MILESTONE: return SoftwareLifeCycleChannel.DEV;
      case BETA: return SoftwareLifeCycleChannel.BETA;
      case RELEASE: return SoftwareLifeCycleChannel.STABLE;
      default: return SoftwareLifeCycleChannel.UNKNOWN_LIFE_CYCLE_CHANNEL;
    }
  }

  /**
   * Tracks use of projects (open, close, # of projects) in an instance of Android Studio.
   */
  private static class ProjectLifecycleTracker implements ProjectLifecycleListener {
    @Override
    public void beforeProjectLoaded(@NotNull Project project) {
      int projectsOpen = ProjectManager.getInstance().getOpenProjects().length;
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_OPENED)
                                       .setStudioProjectChange(StudioProjectChange.newBuilder()
                                                                 .setProjectsOpen(projectsOpen)));
    }

    @Override
    public void afterProjectClosed(@NotNull Project project) {
      int projectsOpen = ProjectManager.getInstance().getOpenProjects().length;
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setKind(AndroidStudioEvent.EventKind.STUDIO_PROJECT_CLOSED)
                                       .setStudioProjectChange(StudioProjectChange.newBuilder()
                                                                 .setProjectsOpen(projectsOpen)));
    }
  }
}
