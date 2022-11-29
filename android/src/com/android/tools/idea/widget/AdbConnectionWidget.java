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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.widget.AdbConnectionWidget.ConnectionState.*;
import static icons.StudioIcons.Shell.StatusBar.ADB_MANAGED;
import static icons.StudioIcons.Shell.StatusBar.ADB_UNMANAGED;
import static java.awt.Color.GREEN;
import static java.awt.Color.RED;

final class AdbConnectionWidget implements StatusBarWidget.IconPresentation, StatusBarWidget {
  @VisibleForTesting
  static final String ID = "AdbConnectionWidget";

  private @NotNull ConnectionState myLastConnectionState;
  private @Nullable StudioAdapter myAdapter;

  AdbConnectionWidget(@NotNull StudioAdapter adapter) {
    myAdapter = adapter;

    myAdapter.setOnUpdate(this::updateIfNeeded);
    Disposer.register(this, myAdapter);

    myLastConnectionState = getConnectionState();
  }

  @Override
  public @NotNull String ID() {
    return ID;
  }

  @Override
  public @NotNull WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void dispose() {
    myAdapter = null;
  }

  @Override
  public @NotNull String getTooltipText() {
    return myLastConnectionState.myTooltip;
  }

  @Override
  public @NotNull Icon getIcon() {
    return myLastConnectionState.myIcon;
  }

  private void updateIfNeeded() {
    if (myAdapter == null) {
      return;
    }

    StatusBar statusBar = myAdapter.getVisibleStatusBar();
    if (statusBar == null) {
      return;
    }

    ConnectionState connectionState = getConnectionState();
    if (connectionState == myLastConnectionState) {
      return;
    }

    myLastConnectionState = connectionState;
    statusBar.updateWidget(ID);
  }

  private @NotNull ConnectionState getConnectionState() {
    if (myAdapter == null) {
      return STUDIO_MANAGED_DISCONNECTED;
    }

    if (myAdapter.isBridgeConnected()) {
      return myAdapter.isBridgeInUserManagedMode() ? USER_MANAGED_CONNECTED : STUDIO_MANAGED_CONNECTED;
    }
    return myAdapter.isBridgeInUserManagedMode() ? USER_MANAGED_DISCONNECTED : STUDIO_MANAGED_DISCONNECTED;
  }

  @SuppressWarnings("UseJBColor")
  enum ConnectionState {
    STUDIO_MANAGED_DISCONNECTED(getStatusIcon(ADB_MANAGED, RED), "Not initialized/connected to local adb"),
    STUDIO_MANAGED_CONNECTED(getStatusIcon(ADB_MANAGED, GREEN), "Initialized/connected to local adb"),
    USER_MANAGED_DISCONNECTED(getStatusIcon(ADB_UNMANAGED, RED), "Not connected to remote adb"),
    USER_MANAGED_CONNECTED(getStatusIcon(ADB_UNMANAGED, GREEN), "Connected to remote adb");

    @VisibleForTesting
    @NotNull
    Icon myIcon;
    @VisibleForTesting private @NotNull String myTooltip;

    ConnectionState(@NotNull Icon icon, @NotNull String tooltip) {
      myIcon = icon;
      myTooltip = tooltip;
    }

    private static @NotNull Icon getStatusIcon(@NotNull Icon base, @NotNull Color color) {
      return ExecutionUtil.getIndicator(base, base.getIconWidth(), base.getIconHeight(), color);
    }
  }

  /**
   * Abstraction for interactions with ADB and Studio due to those dependencies being heavy and/or static.
   * This allows the widget class itself to be easily testable.
   */
  interface StudioAdapter extends Disposable {
    boolean isBridgeConnected();

    boolean isBridgeInUserManagedMode();

    @Nullable
    StatusBar getVisibleStatusBar();

    void setOnUpdate(@NotNull Runnable update);
  }
}
