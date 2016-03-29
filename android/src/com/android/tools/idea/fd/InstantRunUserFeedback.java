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
package com.android.tools.idea.fd;

import com.android.tools.fd.client.UpdateMode;
import com.android.tools.fd.client.UserFeedback;
import com.android.tools.idea.fd.actions.RestartActivityAction;
import com.google.common.html.HtmlEscapers;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

public class InstantRunUserFeedback implements UserFeedback {
  @Language("HTML") public static String LEARN_MORE_LINK = " <a href=\"http://developer.android.com/r/studio-ui/instant-run.html\">Learn More</a>.";

  @NotNull private final Module myModule;

  public InstantRunUserFeedback(@NotNull Module module) {
    myModule = module;
  }

  @Override
  public void error(String message) {
    postText(NotificationType.ERROR, message);
  }

  @Override
  public void warning(String message) {
    postText(NotificationType.WARNING, message);
  }

  @Override
  public void info(String message) {
    postText(NotificationType.INFORMATION, message);
  }

  @Override
  public void noChanges() {
    postText(NotificationType.INFORMATION, "No Changes.");
  }

  @Override
  public void notifyEnd(UpdateMode updateMode) {
    if (updateMode == UpdateMode.HOT_SWAP && !InstantRunSettings.isRestartActivity()) {
      StringBuilder sb = new StringBuilder(300);
      sb.append("Instant Run applied code changes.\n");
      sb.append("You may need to <a href=\"restart\">restart</a>");
      Shortcut[] shortcuts = ActionManager.getInstance().getAction("Android.RestartActivity").getShortcutSet().getShortcuts();
      String shortcut;
      if (shortcuts.length > 0) {
        shortcut = KeymapUtil.getShortcutText(shortcuts[0]);
        sb.append(" ( ").append(shortcut).append(" )");
      }
      sb.append(" the current activity to see the changes.\n");

      sb.append("You can also <a href=\"configure\">configure</a> Instant Run to restart Activities automatically.");
      @Language("HTML") String message = sb.toString();
      NotificationListener listener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String action = event.getDescription();
            if ("restart".equals(action)) {
              RestartActivityAction.restartActivity(myModule);
            }
            else if ("configure".equals(action)) {
              InstantRunConfigurable configurable = new InstantRunConfigurable();
              ShowSettingsUtil.getInstance().editConfigurable(myModule.getProject(), configurable);
            }
            else {
              assert false : action;
            }
          }
        }
      };
      postHtml(NotificationType.INFORMATION, message, listener);
    }
    else if (updateMode == UpdateMode.WARM_SWAP) {
      @Language("HTML") String message = "Instant Run applied code changes and restarted the current Activity.";
      postHtml(NotificationType.INFORMATION, message, null);
    }
  }

  public void verifierFailure(@Language("HTML") String htmlMessage) {
    postHtml(NotificationType.INFORMATION, htmlMessage, null);
  }

  public void notifyDisabledForLaunch(@Language("HTML") @NotNull String reason) {
    postHtml(NotificationType.INFORMATION, reason, null);
  }

  public void postText(@NotNull NotificationType type, @NotNull final String message) {
    postHtml(type, HtmlEscapers.htmlEscaper().escape(message), null);
  }

  public void postHtml(@NotNull NotificationType type,
                       @Language("HTML") @NotNull final String htmlMessage,
                       @Nullable final NotificationListener listener) {
    if (!InstantRunSettings.isShowNotificationsEnabled()) {
      return;
    }

    NotificationGroup group = InstantRunManager.NOTIFICATION_GROUP;

    String message = "<html>" + htmlMessage + "<br/>(<a href=\"mute\">Don't show again</a>)</html>";
    NotificationListener l = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          String description = event.getDescription();
          if (description != null && description.startsWith("http")) {
            BrowserUtil.browse(description);
          }
          else if ("mute".equals(description)) {
            InstantRunSettings.setShowStatusNotifications(false);
          }
          else if (listener != null) {
            listener.hyperlinkUpdate(notification, event);
          }
        }
      }
    };

    Notification notification = group.createNotification("", message, type, l);
    notification.notify(myModule.getProject());
  }
}
