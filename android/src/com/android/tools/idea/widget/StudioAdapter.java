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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class StudioAdapter implements AdbConnectionWidget.StudioAdapter {
  @Nullable private Project myProject;
  @Nullable private TimerListener myTimerListener;

  StudioAdapter(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean isBridgeConnected() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    return bridge != null && bridge.isConnected();
  }

  @Override
  public boolean isBridgeInUserManagedMode() {
    return AndroidDebugBridge.isUserManagedAdbMode();
  }

  @Nullable
  @Override
  public StatusBar getVisibleStatusBar() {
    IdeFrame frame = WindowManager.getInstance().getIdeFrame(myProject);
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
  public void dispose() {
    removeTimerListener();
    myProject = null;
  }

  @Override
  public void setOnUpdate(@NotNull Runnable update) {
    if (myTimerListener != null) {
      removeTimerListener();
    }

    myTimerListener = new TimerListener() {
      @Override
      @NotNull
      public ModalityState getModalityState() {
        return StudioAdapter.this.getModalityState();
      }

      @Override
      public void run() {
        update.run();
      }
    };
    ActionManager.getInstance().addTimerListener(myTimerListener);
  }

  @NotNull
  private ModalityState getModalityState() {
    IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
    if (ideFrame == null) {
      return ModalityState.defaultModalityState();
    }

    return ModalityState.stateForComponent(ideFrame.getComponent());
  }

  private void removeTimerListener() {
    if (myTimerListener != null) {
      ActionManager.getInstance().removeTimerListener(myTimerListener);
    }
    myTimerListener = null;
  }
}
