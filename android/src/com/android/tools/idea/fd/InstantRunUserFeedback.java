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
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class InstantRunUserFeedback implements UserFeedback {
  private static boolean ourHideRestartTip;
  @NotNull private final Module myModule;

  public InstantRunUserFeedback(@NotNull Module module) {
    myModule = module;
  }

  @Override
  public void error(String message) {
    InstantRunManager.postBalloon(MessageType.ERROR, message, myModule.getProject());
  }

  @Override
  public void warning(String message) {
    InstantRunManager.postBalloon(MessageType.WARNING, message, myModule.getProject());
  }

  @Override
  public void info(String message) {
    InstantRunManager.postBalloon(MessageType.INFO, message, myModule.getProject());
  }

  @Override
  public void noChanges() {
    Notification notification =
      InstantRunManager.NOTIFICATION_GROUP.createNotification("Instant Run:", "No Changes.", NotificationType.INFORMATION, null);
    notification.notify(myModule.getProject());
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
              notificationRef.get().hideBalloon();
            }
            else if ("dismiss_all".equals(action)) {
              //noinspection AssignmentToStaticFieldFromInstanceMethod
              ourHideRestartTip = true;
              notificationRef.get().hideBalloon();
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
}
