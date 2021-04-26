/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.adb;

import com.android.annotations.concurrency.GuardedBy;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Getter;
import com.intellij.util.SmartList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AdbOptionsService implements Getter<AdbOptionsService> {
  /**
   * Default user managed adb port. Same as {@link #USER_MANAGED_ADB_PORT_MIN_VALUE}.
   */
  public static final int USER_MANAGED_ADB_PORT_DEFAULT = 5038;

  /**
   * Default adb port +1 to avoid conflict.
   */
  static final int USER_MANAGED_ADB_PORT_MIN_VALUE = 5038;

  /**
   * Max ephemeral port number for most modern operating systems.
   */
  static final int USER_MANAGED_ADB_PORT_MAX_VALUE = 65535;

  private static final String USE_LIBUSB = "adb.use.libusb";
  private static final String USE_USER_MANAGED_ADB = "AdbOptionsService.use.user.managed.adb";
  private static final String USER_MANAGED_ADB_PORT = "AdbOptionsService.user.managed.adb.port";
  private static final boolean LIBUSB_DEFAULT = false;
  private static final boolean USE_USER_MANAGED_ADB_DEFAULT = false;

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
  private List<AdbOptionsListener> myListeners = new SmartList<>();

  public interface AdbOptionsListener {
    void optionsChanged();
  }

  public static AdbOptionsService getInstance() {
    return ApplicationManager.getApplication().getService(AdbOptionsService.class);
  }

  @Override
  public AdbOptionsService get() {
    return this;
  }

  public boolean shouldUseLibusb() {
    return PropertiesComponent.getInstance().getBoolean(USE_LIBUSB, LIBUSB_DEFAULT);
  }

  boolean shouldUseUserManagedAdb() {
    return PropertiesComponent.getInstance().getBoolean(USE_USER_MANAGED_ADB, USE_USER_MANAGED_ADB_DEFAULT);
  }

  int getUserManagedAdbPort() {
    return PropertiesComponent.getInstance().getInt(USER_MANAGED_ADB_PORT, USER_MANAGED_ADB_PORT_DEFAULT);
  }

  public void setAdbConfigs(boolean useLibusb, boolean useUserManagedAdb, int userManagedAdbPort) {
    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(USE_LIBUSB, useLibusb);
    props.setValue(USE_USER_MANAGED_ADB, useUserManagedAdb);
    props.setValue(USER_MANAGED_ADB_PORT, userManagedAdbPort, USER_MANAGED_ADB_PORT_DEFAULT);
    updateListeners();
  }

  private void updateListeners() {
    List<AdbOptionsListener> listeners;
    synchronized (LOCK) {
      listeners = ImmutableList.copyOf(myListeners);
    }

    for (AdbOptionsListener listener : listeners) {
      listener.optionsChanged();
    }
  }

  public void addListener(@NotNull AdbOptionsListener listener) {
    synchronized (LOCK) {
      myListeners.add(listener);
    }
  }

  public void removeListener(@NotNull AdbOptionsListener listener) {
    synchronized (LOCK) {
      myListeners.remove(listener);
    }
  }
}
