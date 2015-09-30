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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.ManifestInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Service;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LaunchUtils {
  /** Returns whether the given application can be debugged on the given device. */
  public static boolean canDebugAppOnDevice(@NotNull AndroidFacet facet, @NotNull IDevice device) {
    if (device.isEmulator()) {
      return true;
    }

    Boolean isDebuggable = AndroidModuleInfo.get(facet).isDebuggable();
    if (isDebuggable != null && isDebuggable) {
      return true;
    }

    String buildType = device.getProperty(IDevice.PROP_BUILD_TYPE);
    if ("userdebug".equals(buildType) || "eng".equals(buildType)) {
      return true;
    }

    return false;
  }

  /**
   * Returns whether the given module corresponds to a watch face app.
   * A module is considered to be a watch face app if there are no activities, and a single service with
   * a specific intent filter. This definition is likely stricter than it needs to be to but we are only
   * interested in matching the watch face template application.
   */
  public static boolean isWatchFaceApp(@NotNull AndroidFacet facet) {
    ManifestInfo info = ManifestInfo.get(facet, true);
    if (!info.getActivities().isEmpty()) {
      return false;
    }

    final List<Service> services = info.getServices();
    if (services.size() != 1) {
      return false;
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        List<IntentFilter> filters = services.get(0).getIntentFilters();
        return filters.size() == 1 &&
               AndroidDomUtil.containsAction(filters.get(0), AndroidUtils.WALLPAPER_SERVICE_ACTION_NAME) &&
               AndroidDomUtil.containsCategory(filters.get(0), AndroidUtils.WATCHFACE_CATEGORY_NAME);
      }
    });
  }

  /** Returns whether the watch hardware feature is required for the given facet. */
  public static boolean isWatchFeatureRequired(@NotNull AndroidFacet facet) {
    List<UsesFeature> usedFeatures = ManifestInfo.get(facet.getModule(), true).getUsedFeatures();

    for (UsesFeature feature : usedFeatures) {
      AndroidAttributeValue<String> name = feature.getName();
      if (name != null && UsesFeature.HARDWARE_TYPE_WATCH.equals(name.getStringValue())) {
        return isRequired(feature.getRequired());
      }
    }

    return false;
  }

  private static boolean isRequired(@Nullable AndroidAttributeValue<Boolean> required) {
    if (required == null) {
      return true;
    }

    Boolean value = required.getValue();
    return value == null // unspecified => required
           || value;
  }
}
