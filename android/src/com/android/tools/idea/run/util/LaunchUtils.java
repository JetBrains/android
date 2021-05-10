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
import static com.android.tools.idea.model.AndroidManifestIndexQueryUtils.queryUsedFeaturesFromManifestIndex;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidManifestIndex;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.model.UsedFeatureRawText;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.activity.DefaultActivityLocator.ActivityWrapper;
import com.android.utils.XmlUtils;
import com.intellij.execution.Executor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class LaunchUtils {
  /**
   * Returns whether the given application can be debugged on the given device.
   */
  public static boolean canDebugAppOnDevice(@NotNull AndroidFacet facet, @NotNull IDevice device) {
    return (canDebugApp(facet) || isDebuggableDevice(device));
  }

  public static boolean canDebugApp(@NotNull AndroidFacet facet) {
    Boolean isDebuggable = AndroidModuleInfo.getInstance(facet).isDebuggable();
    return (isDebuggable == null || isDebuggable);
  }

  public static boolean isDebuggableDevice(@NotNull IDevice device) {
    String buildType = device.getProperty(IDevice.PROP_BUILD_TYPE);
    return ("userdebug".equals(buildType) || "eng".equals(buildType));
  }

  /**
   * Returns whether the given module corresponds to a watch face app.
   * A module is considered to be a watch face app if there are no activities, and a single service with
   * a specific intent filter. This definition is likely stricter than it needs to be to but we are only
   * interested in matching the watch face template application.
   */
  public static boolean isWatchFaceApp(@NotNull AndroidFacet facet) {
    if (AndroidFacet.getInstance(facet.getModule()) == null) {
      Logger.getInstance(LaunchUtils.class).warn("calling isWatchFaceApp when facet is not ready yet");
      return false;
    }

    MergedManifestSnapshot info = MergedManifestManager.getSnapshot(facet);
    List<ActivityWrapper> activities =
      ActivityWrapper.get(info.getActivities(), info.getActivityAliases());
    boolean foundExportedActivity = activities.stream().anyMatch((activity) -> activity.isLogicallyExported());
    if (foundExportedActivity) {
      return false;
    }

    final List<Element> services = info.getServices();
    if (services.size() != 1) {
      return false;
    }

    Element service = services.get(0);
    Element subTag = XmlUtils.getFirstSubTag(service);
    while (subTag != null) {
      if (ActivityLocatorUtils.containsAction(subTag, AndroidUtils.WALLPAPER_SERVICE_ACTION_NAME) &&
          ActivityLocatorUtils.containsCategory(subTag, AndroidUtils.WATCHFACE_CATEGORY_NAME)) {
        return true;
      }
      subTag = XmlUtils.getNextTag(subTag);
    }
    return false;
  }

  /**
   * Returns whether the watch hardware feature is required for the given facet.
   *
   * First, we try to query from {@link AndroidManifestIndex}. And we fall back to {@link MergedManifestSnapshot}
   * if necessary.
   */
  public static boolean isWatchFeatureRequired(@NotNull AndroidFacet facet) {
    if (!AndroidManifestIndex.indexEnabled()) {
      return isWatchFeatureRequiredFromSnapshot(facet);
    }

    Project project = facet.getModule().getProject();
    Collection<UsedFeatureRawText> usedFeatures =
      DumbService.getInstance(project).runReadActionInSmartMode(() -> queryUsedFeaturesFromManifestIndex(facet));

    return usedFeatures.stream()
      .anyMatch(feature -> UsesFeature.HARDWARE_TYPE_WATCH.equals(feature.getName()) &&
                           (feature.getRequired() == null || VALUE_TRUE.equals(feature.getRequired())));
  }

  private static boolean isWatchFeatureRequiredFromSnapshot(@NotNull AndroidFacet facet) {
    MergedManifestSnapshot mergedManifest = MergedManifestManager.getSnapshot(facet);
    Element feature = mergedManifest.findUsedFeature(UsesFeature.HARDWARE_TYPE_WATCH);

    if (feature == null) {
      return false;
    }

    Attr requiredNode = feature.getAttributeNodeNS(ANDROID_URI, "required");
    if (requiredNode == null) { // unspecified => required
      return true;
    }

    return VALUE_TRUE.equals(requiredNode.getValue());
  }

  public static void showNotification(@NotNull final Project project,
                                      @NotNull final Executor executor,
                                      @NotNull final String sessionName,
                                      @NotNull final String message,
                                      @NotNull final NotificationType type,
                                      @Nullable final NotificationListener errorNotificationListener) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }

        String toolWindowId = executor.getToolWindowId();
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        if (toolWindow.isVisible() && errorNotificationListener == null) {
          return;
        }

        final String link = "toolWindow_" + sessionName;
        final String notificationMessage = String.format("Session <a href='%s'>'%s'</a>: %s", link, sessionName, message);

        getNotificationGroup(toolWindowId)
          .createNotification(notificationMessage, type)
          .setListener(new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              boolean handled = false;
              if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && link.equals(event.getDescription())) {
                for (RunContentDescriptor d : ExecutionManagerImpl.getAllDescriptors(project)) {
                  if (sessionName.equals(d.getDisplayName())) {
                    final Content content = d.getAttachedContent();
                    if (content != null) {
                      content.getManager().setSelectedContent(content);
                    }
                    toolWindow.activate(null, true, true);
                    handled = true;
                    break;
                  }
                }
              }

              if (!handled && errorNotificationListener != null) {
                errorNotificationListener.hyperlinkUpdate(notification, event);
              }
            }
          })
          .notify(project);
      }

      @NotNull
      private NotificationGroup getNotificationGroup(@NotNull String toolWindowId) {
        String displayId = "Launch Notifications for " + toolWindowId;
        NotificationGroup group = NotificationGroup.findRegisteredGroup(displayId);
        if (group == null) {
          group = NotificationGroup.toolWindowGroup(displayId, toolWindowId, true, PluginId.getId("org.jetbrains.android"));
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
