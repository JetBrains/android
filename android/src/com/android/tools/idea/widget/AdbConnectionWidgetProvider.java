/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.widget;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetProvider;
import com.intellij.openapi.wm.WindowManager;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AdbConnectionWidgetProvider implements StatusBarWidgetProvider {
  @Nullable
  @Override
  public StatusBarWidget getWidget(@NotNull Project project) {
    if (!StudioFlags.ADB_CONNECTION_STATUS_WIDGET_ENABLED.get()) {
      return null;
    }
    return new AdbConnectionWidget(new AdbConnectionWidget.StudioAdapter() {
      @Override
      public boolean isBridgeConnected() {
        AdbService adb = AdbService.getInstance();
        File adbFile = AndroidSdkUtils.getAdb(project);
        if (adbFile != null) {
          Future<AndroidDebugBridge> bridgeFuture = adb.getDebugBridge(adbFile);
          if (!bridgeFuture.isCancelled() && bridgeFuture.isDone()) {
            try {
              return bridgeFuture.get().isConnected();
            }
            catch (InterruptedException | ExecutionException e) {
              // do nothing
            }
          }
        }
        return false;
      }

      @Override
      public boolean isBridgeInUserManagedMode() {
        return AndroidDebugBridge.isUserManagedAdbMode();
      }

      @NotNull
      @Override
      public ModalityState getModalityState() {
        IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(project);
        if (ideFrame == null) {
          return ModalityState.defaultModalityState();
        }

        return ModalityState.stateForComponent(ideFrame.getComponent());
      }

      @Nullable
      @Override
      public StatusBar getVisibleStatusBar() {
        IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
        if (frame == null) {
          return null;
        }

        StatusBar statusBar = frame.getStatusBar();
        if (statusBar == null) {
          return null;
        }

        if (!statusBar.getComponent().isShowing()) {
          // Skip update if the status bar is hidden (e.g. Distraction Free Mode).
          return null;
        }

        return statusBar;
      }

      @Override
      public void addTimerListener(@NotNull TimerListener timerListener) {
        ActionManager.getInstance().addTimerListener(0, timerListener);
      }

      @Override
      public void removeTimerListener(@NotNull TimerListener timerListener) {
        ActionManager.getInstance().removeTimerListener(timerListener);
      }
    });
  }

  @NotNull
  @Override
  public String getAnchor() {
    return StatusBar.Anchors.after("InspectionProfile");
  }
}
