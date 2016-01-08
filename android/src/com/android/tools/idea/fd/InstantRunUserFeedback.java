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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.Ref;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

public class InstantRunUserFeedback implements UserFeedback {
  private static final boolean ALLOW_MUTE_VERIFIER_FAILURE = false;

  private static boolean ourHideRestartTip;
  @SuppressWarnings("FieldCanBeLocal")
  private static boolean ourMuteVerifierMessages;

  @NotNull private final Module myModule;

  public InstantRunUserFeedback(@NotNull Module module) {
    myModule = module;
  }

  @Override
  public void error(String message) {
    postText(NotificationType.ERROR, null, message, null);
  }

  @Override
  public void warning(String message) {
    postText(NotificationType.WARNING, null, message, null);
  }

  @Override
  public void info(String message) {
    postText(NotificationType.INFORMATION, null, message, null);
  }

  @Override
  public void noChanges() {
    postText("Instant Run:", "No Changes.");
  }

  @Override
  public void notifyEnd(UpdateMode updateMode) {
    if (updateMode == UpdateMode.HOT_SWAP && !InstantRunSettings.isRestartActivity(myModule.getProject()) && !ourHideRestartTip) {
      StringBuilder sb = new StringBuilder(300);
      sb.append("<html>");
      sb.append("Instant Run applied code changes.\n");
      sb.append("You can restart the current activity by clicking <a href=\"restart\">here</a>");
      Shortcut[] shortcuts = ActionManager.getInstance().getAction("Android.RestartActivity").getShortcutSet().getShortcuts();
      String shortcut;
      if (shortcuts.length > 0) {
        shortcut = KeymapUtil.getShortcutText(shortcuts[0]);
        sb.append(" or pressing ").append(shortcut).append(" anytime");
      }
      sb.append(".\n");

      sb.append("You can also <a href=\"configure\">configure</a> restarts to happen automatically. ");
      sb.append("(<a href=\"dismiss\">Dismiss</a>, <a href=\"dismiss_all\">Dismiss All</a>)");
      sb.append("</html>");
      String message = sb.toString();
      final Ref<Notification> notificationRef = Ref.create();
      NotificationListener listener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String action = event.getDescription();
            if ("restart".equals(action)) {
              RestartActivityAction.restartActivity(myModule);
            }
            else if ("configure".equals(action)) {
              InstantRunConfigurable configurable = new InstantRunConfigurable(myModule.getProject());
              ShowSettingsUtil.getInstance().editConfigurable(myModule.getProject(), configurable);
            }
            else if ("dismiss".equals(action)) {
              notificationRef.get().expire();
            }
            else if ("dismiss_all".equals(action)) {
              //noinspection AssignmentToStaticFieldFromInstanceMethod
              ourHideRestartTip = true;
              notificationRef.get().expire();
            }
            else {
              assert false : action;
            }
          }
        }
      };
      Notification notification =
        InstantRunManager.NOTIFICATION_GROUP.createNotification("Instant Run", message, NotificationType.INFORMATION, listener);
      notificationRef.set(notification);
      notification.notify(myModule.getProject());
    }
  }

  public void verifierFailure(@Language("HTML") String htmlMessage) {
    if (ALLOW_MUTE_VERIFIER_FAILURE) {
      if (ourMuteVerifierMessages) {
        return;
      }

      NotificationListener listener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            assert "mute".equals(event.getDescription()) : event.getDescription();
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ourMuteVerifierMessages = true;
          }
        }
      };
      postHtml(NotificationType.INFORMATION, null, htmlMessage + "<br/>" +
                                                   "(<a href=\"mute\">Mute</a>)", listener);
    } else {
      postHtml(NotificationType.INFORMATION, "", htmlMessage, null);
    }
  }

  public void postText(@NotNull final String message) {
    postText(NotificationType.INFORMATION, null, message, null);
  }

  public void postText(@Nullable String title, @NotNull final String message) {
    postText(NotificationType.INFORMATION, title, message, null);
  }

  public void postText(@NotNull NotificationType type,
                       @Nullable String title,
                       @NotNull final String message,
                       @Nullable NotificationListener listener) {
    NotificationGroup group = InstantRunManager.NOTIFICATION_GROUP;
    if (title == null) {
      title = "";
    }
    Notification notification = group.createNotification(title, message, type, listener);
    notification.notify(myModule.getProject());
  }

  public void postHtml(@NotNull NotificationType type,
                       @Nullable String title,
                       @Language("HTML") @NotNull final String htmlMessage,
                       @Nullable NotificationListener listener) {
    NotificationGroup group = InstantRunManager.NOTIFICATION_GROUP;
    if (title == null) {
      title = "";
    }
    String message = "<html>" + htmlMessage + "</html>";
    Notification notification = group.createNotification(title, message, type, listener);
    notification.notify(myModule.getProject());
  }
}
