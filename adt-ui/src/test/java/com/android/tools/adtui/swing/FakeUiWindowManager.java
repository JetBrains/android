/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.swing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slightly modified copy of {@link com.intellij.openapi.wm.impl.TestWindowManager} that
 * returns null from the {@link #getFocusedComponent(Window)} method instead of throwing
 * an UnsupportedOperationException.
 */
public final class FakeUiWindowManager extends WindowManagerEx {
  private static final Key<StatusBar> STATUS_BAR = Key.create("STATUS_BAR");

  @Override
  public final void doNotSuggestAsParent(Window window) {}

  @Override
  public final Window suggestParentWindow(@Nullable Project project) {
    return null;
  }

  @Override
  public final StatusBar getStatusBar(@NotNull Project project) {
    synchronized (STATUS_BAR) {
      StatusBar statusBar = project.getUserData(STATUS_BAR);
      if (statusBar == null) {
        project.putUserData(STATUS_BAR, statusBar = new DummyStatusBar());
      }
      return statusBar;
    }
  }

  @Override
  public IdeFrame getIdeFrame(Project project) {
    return null;
  }

  @Override
  public @Nullable ProjectFrameHelper findFrameHelper(@Nullable Project project) {
    return null;
  }

  @Override
  public @Nullable ProjectFrameHelper getFrameHelper(@Nullable Project project) {
    return null;
  }

  @Override
  public Rectangle getScreenBounds(@NotNull Project project) {
    return null;
  }

  @Override
  public void setWindowMask(Window window, Shape mask) {}

  @Override
  public void resetWindow(Window window) {}

  @Override
  public ProjectFrameHelper @NotNull [] getAllProjectFrames() {
    return new ProjectFrameHelper[0];
  }

  @Override
  public JFrame findVisibleFrame() {
    return null;
  }

  @Override
  public final @Nullable IdeFrameImpl getFrame(Project project) {
    return null;
  }

  @Override
  public final Component getFocusedComponent(@NotNull Window window) {
    return null;
  }

  @Override
  public final Component getFocusedComponent(Project project) {
    return null;
  }

  @Override
  public final Window getMostRecentFocusedWindow() {
    return null;
  }

  @Override
  public IdeFrame findFrameFor(@Nullable Project project) {
    return null;
  }

  @Override
  public final void dispatchComponentEvent(ComponentEvent e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final @NotNull Rectangle getScreenBounds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public final boolean isInsideScreenBounds(int x, int y, int width) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final boolean isAlphaModeSupported() {
    return false;
  }

  @Override
  public final void setAlphaModeRatio(Window window, float ratio) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final boolean isAlphaModeEnabled(Window window) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final void setAlphaModeEnabled(Window window, boolean state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setWindowShadow(Window window, WindowShadowMode mode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFullScreenSupportedInCurrentOS() {
    return false;
  }

  private static final class DummyStatusBar implements StatusBarEx {
    private final Map<String, StatusBarWidget> myWidgetMap = new HashMap<>();

    @Override
    public @Nullable Project getProject() {
      return null;
    }

    @Override
    public Dimension getSize() {
      return new Dimension(0, 0);
    }

    @Override
    public @Nullable StatusBar createChild(@NotNull IdeFrame frame) {
      return null;
    }

    @Override
    public StatusBar findChild(@NotNull Component c) {
      return null;
    }

    @Override
    public void setInfo(@Nullable String s, @Nullable String requestor) {}

    @Override
    public boolean isVisible() {
      return false;
    }

    @Override
    public void addProgress(@NotNull ProgressIndicatorEx indicator, @NotNull TaskInfo info) {}

    @Override
    public List<Pair<TaskInfo, ProgressIndicator>> getBackgroundProcesses() {
      return Collections.emptyList();
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget) {
      myWidgetMap.put(widget.ID(), widget);
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor) {
      addWidget(widget);
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget, @NotNull Disposable parentDisposable) {
      Disposer.register(parentDisposable, widget);
      Disposer.register(widget, () -> myWidgetMap.remove(widget.ID()));
      addWidget(widget);
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor, @NotNull Disposable parentDisposable) {
      addWidget(widget, parentDisposable);
    }

    @Override
    public void updateWidget(@NotNull String id) {}

    @Override
    public StatusBarWidget getWidget(@NotNull String id) {
      return myWidgetMap.get(id);
    }

    @Override
    public void removeWidget(@NotNull String id) {}

    @Override
    public void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor) {}

    @Override
    public JComponent getComponent() {
      return null;
    }

    @Override
    public final String getInfo() {
      return null;
    }

    @Override
    public final void setInfo(String s) {}

    @Override
    public void startRefreshIndication(String tooltipText) {}

    @Override
    public void stopRefreshIndication() {}

    @Override
    public boolean isProcessWindowOpen() {
      return false;
    }

    @Override
    public void setProcessWindowOpen(boolean open) {}

    @Override
    public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody) {
      return () -> {};
    }

    @Override
    public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type,
                                                  @NotNull String htmlBody,
                                                  @Nullable Icon icon,
                                                  @Nullable HyperlinkListener listener) {
      return () -> {};
    }
  }

  @Override
  public void releaseFrame(@NotNull ProjectFrameHelper frameHelper) {
    frameHelper.getFrame().dispose();
  }

  @Override
  public boolean isFrameReused(@NotNull ProjectFrameHelper frameHelper) {
    return false;
  }

  @Override
  public @NotNull List<ProjectFrameHelper> getProjectFrameHelpers() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable IdeFrameEx findFirstVisibleFrameHelper() {
    return null;
  }
}