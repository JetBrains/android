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
package com.android.tools.idea.explorer;

import com.intellij.util.SystemProperties;

public class DeviceExplorer {
  private static final String DEVICE_EXPLORER_ENABLED = "android.device.explorer.enabled";
  private static boolean myEnabled;

  public static boolean isFeatureEnabled() {
    return myEnabled || SystemProperties.getBooleanProperty(DEVICE_EXPLORER_ENABLED, true);
  }

  public static void enableFeature(boolean enabled) {
    myEnabled = enabled;
  }
}
