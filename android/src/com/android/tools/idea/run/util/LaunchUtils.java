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
package com.android.tools.idea.run.util;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public final class LaunchUtils {
  /**
   * Returns whether the given application can be debugged on the given device.
   */
  public static boolean canDebugAppOnDevice(@NotNull AndroidFacet facet, @NotNull IDevice device) {
    return (canDebugApp(facet) || isDebuggableDevice(device));
  }

  public static boolean canDebugApp(@NotNull AndroidFacet facet) {
    return getModuleSystem(facet).isDebuggable();
  }

  public static boolean isDebuggableDevice(@NotNull IDevice device) {
    String buildType = device.getProperty(IDevice.PROP_BUILD_TYPE);
    return ("userdebug".equals(buildType) || "eng".equals(buildType));
  }

  /**
   * Returns whether the watch hardware feature is required for the given facet.
   */
  @Slow
  @WorkerThread
  public static boolean isWatchFeatureRequired(@NotNull AndroidFacet facet) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    if (AndroidFacet.getInstance(facet.getModule()) == null) {
      Logger.getInstance(LaunchUtils.class).warn("calling isWatchFeatureRequired when facet is not ready yet");
      return false;
    }

    try {
      MergedManifestSnapshot info = MergedManifestManager.getMergedManifest(facet.getModule()).get();
      Element usesFeatureElem = info.findUsedFeature(UsesFeature.HARDWARE_TYPE_WATCH);
      if (usesFeatureElem != null) {
        String required = usesFeatureElem.getAttributeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
        return isEmpty(required) || VALUE_TRUE.equals(required);
      }
    }
    catch (ExecutionException | InterruptedException ex) {
      Logger.getInstance(LaunchUtils.class).warn(ex);
    }
    return false;
  }

  public static void initiateDismissKeyguard(@NotNull final IDevice device) {
    // From Version 23 onwards (in the emulator, possibly later on devices), we can dismiss the keyguard
    // with "adb shell wm dismiss-keyguard". This allows the application to show up without the user having
    // to manually dismiss the keyguard.
    final AndroidVersion canDismissKeyguard = new AndroidVersion(23, null);
    if (canDismissKeyguard.compareTo(device.getVersion()) <= 0) {
      // It is not necessary to wait for the keyguard to be dismissed. On a slow emulator, this seems
      // to take a while (6s on my machine)
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          device.executeShellCommand("wm dismiss-keyguard", new NullOutputReceiver(), 10, TimeUnit.SECONDS);
        }
        catch (Exception e) {
          Logger.getInstance(LaunchUtils.class).warn("Unable to dismiss keyguard before launching activity");
        }
      });
    }
  }

  private static final Pattern idKeyPattern = Pattern.compile("--user\\s+([0-9]+)");

  @Nullable
  public static Integer getUserIdFromFlags(@Nullable String flags) {
    if (flags == null) {
      return null;
    }
    Matcher m = idKeyPattern.matcher(flags);
    return m.find() ? Integer.parseInt(m.group(1)) : null;
  }
}
