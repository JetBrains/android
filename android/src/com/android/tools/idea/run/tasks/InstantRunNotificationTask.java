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
package com.android.tools.idea.run.tasks;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.IDevice;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.fd.actions.RestartActivityAction;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Set;

public class InstantRunNotificationTask implements LaunchTask {
  public static final String INSTANT_RUN_URL = "http://developer.android.com/r/studio-ui/instant-run.html";
  // We only need to show this message once per IDE session, but we do want to show it once per project
  // So this is the set of all projects for which we've displayed that message once in this IDE session.
  @GuardedBy("LOCK")
  private static Set<String> ourBrokenForSecondaryUserMessageDisplayed = new HashSet<>();
  private static final Object LOCK = new Object();

  private static boolean ourShouldShowIRAd = true;

  private final Project myProject;
  private final InstantRunContext myContext;
  private final InstantRunNotificationProvider myNotificationsProvider;
  private final boolean myShowBrokenForSecondaryUserMessage;
  private final BuildSelection myBuildSelection;

  public InstantRunNotificationTask(@NotNull Project project,
                                    @NotNull InstantRunContext context,
                                    @NotNull InstantRunNotificationProvider provider,
                                    @Nullable BuildSelection buildSelection) {
    myProject = project;
    myContext = context;
    myNotificationsProvider = provider;
    myBuildSelection = buildSelection;
    myShowBrokenForSecondaryUserMessage = buildSelection.brokenForSecondaryUser;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Display Instant Run notification";
  }

  @Override
  public int getDuration() {
    return 0;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    if (!InstantRunSettings.isShowNotificationsEnabled()) {
      return true;
    }

    String notificationText =
      myNotificationsProvider.getNotificationText();
    if (notificationText != null) {
      showNotification(myProject, myContext, notificationText);
    }

    if (myBuildSelection != null && shouldShowIRAd(myBuildSelection.why, myContext.getInstantRunBuildInfo().getBuildInstantRunEligibility())) {
      ourShouldShowIRAd = false;
      new InstantRunPrompt(myProject).show();
    }

    if (myShowBrokenForSecondaryUserMessage) {
      boolean show = false;
      synchronized (LOCK) {
        if (!ourBrokenForSecondaryUserMessageDisplayed.contains(myProject.getLocationHash())) {
          ourBrokenForSecondaryUserMessageDisplayed.add(myProject.getLocationHash());
          show = true;
        }
      }

      if (show) {
        showIrBrokenForSecondaryUsersNotification(myProject);
      }
    }

    return true;
  }

  private boolean shouldShowIRAd(@NotNull BuildCause buildCause, String eligibility) {
    return ourShouldShowIRAd &&
           buildCause == BuildCause.USER_CHOSE_TO_COLDSWAP &&
           InstantRunBuildInfo.VALUE_VERIFIER_STATUS_COMPATIBLE.equals(eligibility);
  }

  private static void showIrBrokenForSecondaryUsersNotification(@NotNull Project project) {
    NotificationListener l = (notification, event) -> {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
        return;
      }

      String description = event.getDescription();
      if ("learnmore".equals(description)) {
        BrowserUtil.browse("http://developers.android.com/r/studio-ui/run-with-work-profile.html", project);
      }
    };

    InstantRunManager.NOTIFICATION_GROUP.createNotification("",
                                                            AndroidBundle.message("instant.run.notification.ir.broken.for.secondary.user"),
                                                            NotificationType.INFORMATION,
                                                            l)
      .notify(project);
  }

  public static void showNotification(@NotNull Project project, @Nullable InstantRunContext context, @NotNull String notificationText) {
    if (!InstantRunSettings.isShowNotificationsEnabled()) {
      return;
    }

    @Language("HTML")
    String message = String.format("<html>%1$s<br>(<a href=\"mute\">Don't show again</a>)</html>", notificationText);

    NotificationListener l = (notification, event) -> {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        String description = event.getDescription();
        if (description != null && description.startsWith("http")) {
          BrowserUtil.browse(description, project);
        }
        else if ("mute".equals(description)) {
          InstantRunSettings.setShowStatusNotifications(false);
        }
        else if ("configure".equals(description)) {
          InstantRunConfigurable configurable = new InstantRunConfigurable();
          ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
        }
        else if ("restart".equals(description)) {
          assert context != null : "Notifications that include a restart activity option need to have a valid instant run context";
          RestartActivityAction.restartActivity(project, context);
        }
        else if ("learnmore".equals(description)) {
          BrowserUtil.browse(INSTANT_RUN_URL, project);
        }
        else if ("updategradle".equals(description)) {
          InstantRunConfigurable.updateProjectToInstantRunTools(project, null);
        }
      }
    };

    InstantRunManager.NOTIFICATION_GROUP.createNotification("", message, NotificationType.INFORMATION, l).notify(project);
  }
}
