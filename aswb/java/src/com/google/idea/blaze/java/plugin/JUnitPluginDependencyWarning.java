/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.plugin;

import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupActivity.DumbAware;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PlatformUtils;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;

/**
 * Runs on startup, displaying an error if the JUnit plugin (required for the blaze plugin to
 * properly function) is not enabled.
 */
public class JUnitPluginDependencyWarning implements StartupActivity, DumbAware {

  private static final String JUNIT_PLUGIN_ID = "JUnit";

  @Override
  public void runActivity(Project project) {
    if (PlatformUtils.isIntelliJ()
        && Blaze.isBlazeProject(project)
        && !PluginUtils.isPluginEnabled(JUNIT_PLUGIN_ID)) {
      notifyJUnitNotEnabled(project);
    }
  }

  /**
   * Pop up a notification asking user to enable the JUnit plugin, and also add an error item to the
   * event log.
   */
  private static void notifyJUnitNotEnabled(Project project) {
    String buildSystem = Blaze.defaultBuildSystemName();

    String message =
        String.format(
            "<html>The JUnit plugin is disabled, but it's required for the %s plugin to function."
                + "<br>Please <a href=\"fix\">enable the JUnit plugin</a> and restart the IDE",
            buildSystem);

    NotificationListener listener =
        new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(Notification notification, HyperlinkEvent e) {
            if ("fix".equals(e.getDescription())) {
              PluginUtils.installOrEnablePlugin(JUNIT_PLUGIN_ID);
            }
          }
        };

    Notification notification =
        new Notification(
            buildSystem + " Plugin Error",
            buildSystem + " plugin dependencies are missing",
            message,
            NotificationType.ERROR,
            listener);
    notification.setImportant(true);

    // Adds an error item to the 'Event Log' tab.
    // Easy to ignore, but remains in event log until manually cleared.
    Notifications.Bus.notify(notification, project);
    // Popup dialog on project open.
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      showPopupNotification(message);
    }
  }

  private static void showPopupNotification(String message) {
    JComponent component = WindowManager.getInstance().findVisibleFrame().getRootPane();
    if (component == null) {
      return;
    }
    Rectangle rect = component.getVisibleRect();
    JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(
            message,
            MessageType.WARNING,
            new HyperlinkAdapter() {
              @Override
              protected void hyperlinkActivated(HyperlinkEvent e) {
                PluginUtils.installOrEnablePlugin(JUNIT_PLUGIN_ID);
              }
            })
        .setFadeoutTime(-1)
        .setHideOnLinkClick(true)
        .setHideOnFrameResize(false)
        .setHideOnClickOutside(false)
        .setHideOnKeyOutside(false)
        .setDisposable(ApplicationManager.getApplication())
        .createBalloon()
        .show(
            new RelativePoint(component, new Point(rect.x + 30, rect.y + rect.height - 10)),
            Balloon.Position.above);
  }
}
