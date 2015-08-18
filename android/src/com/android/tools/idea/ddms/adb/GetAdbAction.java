/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms.adb;

import com.android.ddmlib.AndroidDebugBridge;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import java.io.File;

public class GetAdbAction extends AnAction {
  public GetAdbAction() {
    super("Get ADB");
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    File adb = project == null ? null : AndroidSdkUtils.getAdb(project);
    getTemplatePresentation().setEnabled(adb != null && adb.exists());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Notifications.Bus.notify(new Notification("Android", "ADB", "ADB requested.", NotificationType.INFORMATION));
    Project project = getEventProject(e);
    File adb = project == null ? null : AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      return;
    }

    ListenableFuture<AndroidDebugBridge> bridge = AdbService.getInstance().getDebugBridge(adb);
    Futures.addCallback(bridge, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(AndroidDebugBridge result) {
        Notifications.Bus.notify(new Notification("Android", "ADB", "ADB obtained", NotificationType.INFORMATION));
      }

      @Override
      public void onFailure(Throwable t) {
        Notifications.Bus.notify(new Notification("Android", "ADB", "ADB error: " + t.toString(), NotificationType.INFORMATION));
      }
    });
  }
}
