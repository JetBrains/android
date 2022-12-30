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
package com.android.tools.idea.memorysettings;

import com.android.tools.analytics.HostData;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.flags.StudioFlags;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.MemorySettings;
import com.google.wireless.android.sdk.stats.MemorySettingsEvent;
import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.system.CpuArch;
import com.sun.management.OperatingSystemMXBean;
import java.awt.Window;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public final class MemorySettingsUtil {
  private static final Logger LOG = Logger.getInstance(MemorySettingsUtil.class);

  // Show memory settings configuration only for machines with at least this much RAM.
  private static final int MIN_RAM_IN_GB_FOR_CONFIG = 5;
  private static final int LOW_IDE_XMX_CAP_IN_GB = 4;
  private static final int HIGH_IDE_XMX_CAP_IN_GB = 8;

  static final int NO_XMX_IN_VM_ARGS = -1;

  static final int getIdeXmxCapInGB() {
    return StudioFlags.LOW_IDE_XMX_CAP.get() ? LOW_IDE_XMX_CAP_IN_GB : HIGH_IDE_XMX_CAP_IN_GB;
  }

  public static boolean memorySettingsEnabled() {
    return !CpuArch.is32Bit() && getMachineMem() >= MIN_RAM_IN_GB_FOR_CONFIG << 10;
  }

  public static int getCurrentXmx() {
    int stored = VMOptions.readOption(VMOptions.MemoryKind.HEAP, false);
    // Return -1 if unknown
    int current = stored == -1 ? VMOptions.readOption(VMOptions.MemoryKind.HEAP, true) : stored;
    if (ApplicationManager.getApplication().isUnitTestMode()
        && current == getIdeXmxCapInGB() << 10) {
      // In unit tests, reduce current xmx if it reaches cap in order to test recommendation.
      LOG.info("current Xmx reaches IDE_XMX_CAP_IN_GB already, reducing to 1GB");
      current = 1024;
    }
    return current;
  }

  public static int getMachineMem() {
    OperatingSystemMXBean osBean = HostData.getOsBean();
    return osBean == null ? -1 : Math.toIntExact(osBean.getTotalPhysicalMemorySize() >> 20);
  }

  public static void saveXmx(int newValue) {
    if (newValue <= 0) {
      LOG.info(String.format(Locale.US, "invalid value for Xmx: %d", newValue));
      return;
    }
    LOG.info("saving new Xmx value: " + newValue);
    try {
      VMOptions.setOption(VMOptions.MemoryKind.HEAP, newValue);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public static void log(MemorySettingsEvent.EventKind kind,
                         int currentIdeXmx, int currentGradleXmx, int currentKotlinXmx,
                         int recommendedIdeXmx, int recommendedGradleXmx, int recommendedKotlinXmx,
                         int changedIdeXmx, int changedGradleXmx, int changedKotlinXmx) {
    if (!ApplicationManager.getApplication().isInternal() && StatisticsUploadAssistant.isSendAllowed()) {
      MemorySettingsEvent.Builder eventBuilder =
        MemorySettingsEvent.newBuilder()
          .setKind(kind)
          .setCurrent(createMemorySettings(currentIdeXmx, currentGradleXmx, currentKotlinXmx))
          .setRecommended(createMemorySettings(recommendedIdeXmx, recommendedGradleXmx, recommendedKotlinXmx))
          .setChanged(createMemorySettings(changedIdeXmx, changedGradleXmx, changedKotlinXmx));

      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.MEMORY_SETTINGS_EVENT)
                         .setMemorySettingsEvent(eventBuilder));
    }
  }

  @Nullable
  static Project getCurrentProject() {
    Project result = null;
    Window activeWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (activeWindow != null) {
      result = CommonDataKeys.PROJECT
        .getData(DataManager.getInstance().getDataContext(activeWindow));
    }
    return result;
  }

  private static MemorySettings.Builder createMemorySettings(int ideXmx, int gradleXmx, int kotlinXmx) {
    MemorySettings.Builder builder = MemorySettings.newBuilder();
    if (ideXmx > 0) {
      builder.setIdeXmx(ideXmx);
    }
    if (gradleXmx > 0) {
      builder.setGradleDaemonXmx(gradleXmx);
    }
    if (kotlinXmx > 0) {
      builder.setKotlinDaemonXmx(kotlinXmx);
    }
    return builder;
  }
}
