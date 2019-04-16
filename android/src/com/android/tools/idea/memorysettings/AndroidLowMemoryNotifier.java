/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.LowMemoryWatcher;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

public class AndroidLowMemoryNotifier implements ApplicationComponent {

  private LowMemoryWatcher myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived, ONLY_AFTER_GC);
  private final AtomicBoolean myNotificationShown = new AtomicBoolean();

  private void onLowMemorySignalReceived() {
    int currentXmx = MemorySettingsUtil.getCurrentXmx();
    int xmxCap = MemorySettingsUtil.getIdeXmxCapInGB() * 1024;
    if (myNotificationShown.compareAndSet(false, true) && currentXmx < xmxCap) {
      Notification notification = new Notification(IdeBundle.message("low.memory.notification.title"),
                                                   IdeBundle.message("low.memory.notification.title"),
                                                   IdeBundle.message("low.memory.notification.content"),
                                                   NotificationType.WARNING);
      notification.addAction(new NotificationAction(IdeBundle.message("low.memory.notification.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          Project project = e.getProject();
          ShowSettingsUtilImpl.showSettingsDialog(project, "memory.settings", "");
          notification.expire();
        }
      });
      Notifications.Bus.notify(notification);
    }
  }

  @Override
  public void disposeComponent() {
    myWatcher.stop();
    myWatcher = null;
  }
}
