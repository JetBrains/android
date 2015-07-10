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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android Studio Usage Tracker.
 */
public abstract class UsageTracker {
  /**
   * GA only allows sending a single <category,action,value> tuple per event
   * However, we'd like to track different components of the avd such as its version, arch, etc
   * So this category will consist of info events, but note that the total event count is somewhat meaningless
   * Note: Custom dimensions could possibly alleviate this issue, and we should consider switching to
   * that when we have more info on the sorts of custom dimensions we'd need.
   */
  public static final String CATEGORY_AVDINFO = "avdInfo";
  public static final String ACTION_AVDINFO_ABI = "abi";
  public static final String ACTION_AVDINFO_TARGET_VERSION = "version";

  // Similar to CATEGORY_AVDINFO, this tracks info about the device during deployment
  public static final String CATEGORY_DEVICEINFO = "deviceInfo";
  public static final String INFO_DEVICE_BUILD_TAGS = IDevice.PROP_BUILD_TAGS; // "unsigned,debug" or "dev-keys"
  public static final String INFO_DEVICE_BUILD_TYPE = IDevice.PROP_BUILD_TYPE; // "user" or "eng"
  public static final String INFO_DEVICE_BUILD_VERSION_RELEASE = IDevice.PROP_BUILD_VERSION; // "4.4.4"
  public static final String INFO_DEVICE_BUILD_API_LEVEL = IDevice.PROP_BUILD_API_LEVEL; // "22"
  public static final String INFO_DEVICE_MANUFACTURER = IDevice.PROP_DEVICE_MANUFACTURER;
  public static final String INFO_DEVICE_MODEL = IDevice.PROP_DEVICE_MODEL;
  public static final String INFO_DEVICE_SERIAL_HASH = "ro.serialno.hashed";
  public static final String INFO_DEVICE_CPU_ABI = IDevice.PROP_DEVICE_CPU_ABI;

  public static final String CATEGORY_DEPLOYMENT = "deployment";
  public static final String ACTION_DEPLOYMENT_APK = "apkDeployed";
  public static final String ACTION_DEPLOYMENT_EMULATOR = "emulatorLaunch";

  public static final String CATEGORY_DEVELOPER_SERVICES = "devServices";
  public static final String ACTION_DEVELOPER_SERVICES_INSTALLED = "installed";
  public static final String ACTION_DEVELOPER_SERVICES_REMOVED = "removed";

  public static final String CATEGORY_GRADLE = "gradle";
  public static final String ACTION_GRADLE_SYNC_STARTED = "syncStarted";
  public static final String ACTION_GRADLE_SYNC_ENDED = "syncEnded";
  public static final String ACTION_GRADLE_SYNC_SKIPPED = "syncSkipped";
  public static final String ACTION_GRADLE_SYNC_FAILED = "syncFailed";
  public static final String ACTION_GRADLE_CPP_SYNC_COMPLETED = "cppSyncCompleted";

  public static final String CATEGORY_PROFILING = "profiling";
  public static final String ACTION_PROFILING_CAPTURE = "captureCreated";
  public static final String ACTION_PROFILING_OPEN = "captureOpened";

  public static final String CATEGORY_SDK_MANAGER = "sdkManager";
  public static final String ACTION_SDK_MANAGER_TOOLBAR_CLICKED = "toolbarButtonClicked";
  public static final String ACTION_SDK_MANAGER_STANDALONE_LAUNCHED = "standaloneLaunched";
  public static final String ACTION_SDK_MANAGER_LOADED = "sdkManagerLoaded";

  /**
   * Tracking when a template.xml file is rendered (instantiated) into the project.
   */
  public static final String CATEGORY_TEMPLATE = "template";
  public static final String ACTION_TEMPLATE_RENDER = "render";


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

  public boolean canTrack() {
    return AndroidStudioSpecificInitializer.isAndroidStudio() && StatisticsUploadAssistant.isSendAllowed();
  }
}
