/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.ddmlib.IDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DevicePropertyUtil {
  private static final Set<String> ourManufacturerNameIsAcronym =
    ImmutableSet.of("ASUS", "HTC", "LG", "LGE", "ZTE");

  @VisibleForTesting
  static String fixManufacturerName(@NotNull String manufacturer) {
    String allCaps = StringUtil.toUpperCase(manufacturer);
    return ourManufacturerNameIsAcronym.contains(allCaps) ?
           allCaps : StringUtil.capitalizeWords(manufacturer, true);
  }

  @NotNull
  public static String getManufacturer(@NotNull IDevice d, @NotNull String unknown) {
    return getManufacturer(d.getProperty(IDevice.PROP_DEVICE_MANUFACTURER), d.isEmulator(), unknown);
  }

  @NotNull
  public static String getManufacturer(@Nullable String manufacturer, boolean isEmulator, @NotNull String unknown) {
    if (isEmulator && "unknown".equals(manufacturer)) {
      // use argument provided to method rather than the "unknown" hardcoded into the emulator system image
      manufacturer = unknown;
    }

    return manufacturer != null ? fixManufacturerName(manufacturer) : unknown;
  }

  @NotNull
  public static String getModel(@NotNull IDevice d, @NotNull String unknown) {
    return getModel(d.getProperty(IDevice.PROP_DEVICE_MODEL), unknown);
  }

  @NotNull
  public static String getModel(@Nullable String model, @NotNull String unknown) {
    return model != null ? StringUtil.capitalizeWords(model, true) : unknown;
  }

  @NotNull
  public static String getBuild(@NotNull IDevice d) {
    return getBuild(d.getProperty(IDevice.PROP_BUILD_VERSION),
                    d.getProperty(IDevice.PROP_BUILD_CODENAME),
                    d.getProperty(IDevice.PROP_BUILD_API_LEVEL));
  }

  @NotNull
  public static String getBuild(@Nullable String buildVersion, @Nullable String codeName, @Nullable String apiLevel) {
    StringBuilder sb = new StringBuilder(20);
    if (buildVersion != null) {
      sb.append("Android ");
      sb.append(buildVersion);
    }

    String apiString = (codeName == null || codeName.equals("REL")) ? apiLevel : codeName;
    if (apiString != null) {
      sb.append(String.format(", API %1$s", apiString));
    }

    return sb.toString();
  }
}
