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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.MergedManifest;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Service;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    MergedManifest info = ManifestInfo.get(facet);
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
    List<UsesFeature> usedFeatures = ManifestInfo.get(facet).getUsedFeatures();

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

  public static void showNotification(@NotNull final Project project,
                                      @NotNull final Executor executor,
                                      @NotNull final String sessionName,
                                      @NotNull final String message,
                                      @NotNull final NotificationType type) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }

        String toolWindowId = executor.getToolWindowId();
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        if (toolWindow.isVisible()) {
          return;
        }

        final String notificationMessage = "Session <a href=''>'" + sessionName + "'</a>: " + message;

        NotificationGroup group = getNotificationGroup(toolWindowId);
        group.createNotification("", notificationMessage, type, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              final RunContentManager contentManager = ExecutionManager.getInstance(project).getContentManager();

              for (RunContentDescriptor d : contentManager.getAllDescriptors()) {
                if (sessionName.equals(d.getDisplayName())) {
                  final Content content = d.getAttachedContent();
                  if (content != null) {
                    content.getManager().setSelectedContent(content);
                  }
                  toolWindow.activate(null, true, true);
                  break;
                }
              }
            }
          }
        }).notify(project);
      }

      @NotNull
      private NotificationGroup getNotificationGroup(@NotNull String toolWindowId) {
        String displayId = "Launch Notifications for " + toolWindowId;
        NotificationGroup group = NotificationGroup.findRegisteredGroup(displayId);
        if (group == null) {
          group = NotificationGroup.toolWindowGroup(displayId, toolWindowId);
        }
        return group;
      }
    });
  }

  public static void initiateDismissKeyguard(@NotNull final IDevice device) {
    // From Version 23 onwards (in the emulator, possibly later on devices), we can dismiss the keyguard
    // with "adb shell wm dismiss-keyguard". This allows the application to show up without the user having
    // to manually dismiss the keyguard.
    final AndroidVersion canDismissKeyguard = new AndroidVersion(23, null);
    if (canDismissKeyguard.compareTo(device.getVersion()) <= 0) {
      // It is not necessary to wait for the keyguard to be dismissed. On a slow emulator, this seems
      // to take a while (6s on my machine)
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            device.executeShellCommand("wm dismiss-keyguard", new NullOutputReceiver(), 10, TimeUnit.SECONDS);
          }
          catch (Exception e) {
            Logger.getInstance(LaunchUtils.class).warn("Unable to dismiss keyguard before launching activity");
          }
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
