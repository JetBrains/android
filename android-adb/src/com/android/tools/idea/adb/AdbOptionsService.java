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
import com.intellij.openapi.components.Service;
import com.intellij.util.SmartList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@Service
public final class AdbOptionsService {
  static final int USER_MANAGED_ADB_PORT_MIN_VALUE = 5037;

  /**
   * Default user managed adb port.
   */
  public static final int USER_MANAGED_ADB_PORT_DEFAULT = USER_MANAGED_ADB_PORT_MIN_VALUE;

  /**
   * Max ephemeral port number for most modern operating systems.
   */
  static final int USER_MANAGED_ADB_PORT_MAX_VALUE = 65535;

  private static final String USE_LIBUSB = "adb.use.libusb";
  private static final String USE_MDNS_OPENSCREEN = "adb.use.mdns.openscreen";
  private static final String USE_USER_MANAGED_ADB = "AdbOptionsService.use.user.managed.adb";
  private static final String USER_MANAGED_ADB_PORT = "AdbOptionsService.user.managed.adb.port";
  private static final boolean LIBUSB_DEFAULT = false;
  private static final boolean USE_MDNS_OPENSCREEN_DEFAULT = true;
  private static final boolean USE_USER_MANAGED_ADB_DEFAULT = false;

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
  @NotNull private final List<AdbOptionsListener> myListeners = new SmartList<>();

  public interface AdbOptionsListener {
    void optionsChanged();
  }

  public static AdbOptionsService getInstance() {
    return ApplicationManager.getApplication().getService(AdbOptionsService.class);
  }

  public boolean shouldUseLibusb() {
    return PropertiesComponent.getInstance().getBoolean(USE_LIBUSB, LIBUSB_DEFAULT);
  }

  public boolean shouldUseMdnsOpenScreen() {
    return PropertiesComponent.getInstance().getBoolean(USE_MDNS_OPENSCREEN, USE_MDNS_OPENSCREEN_DEFAULT);
  }

  boolean shouldUseUserManagedAdb() {
    return PropertiesComponent.getInstance().getBoolean(USE_USER_MANAGED_ADB, USE_USER_MANAGED_ADB_DEFAULT);
  }

  int getUserManagedAdbPort() {
    return PropertiesComponent.getInstance().getInt(USER_MANAGED_ADB_PORT, USER_MANAGED_ADB_PORT_DEFAULT);
  }

  @NotNull
  public AdbOptionsUpdater getOptionsUpdater() {
    return new AdbOptionsUpdater(this);
  }

  private void commitOptions(@NotNull AdbOptionsUpdater options) {
    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(USE_LIBUSB, options.useLibusb());
    props.setValue(USE_MDNS_OPENSCREEN, options.useMdnsOpenScreen(), USE_MDNS_OPENSCREEN_DEFAULT);
    props.setValue(USE_USER_MANAGED_ADB, options.useUserManagedAdb());
    props.setValue(USER_MANAGED_ADB_PORT, options.getUserManagedAdbPort(), USER_MANAGED_ADB_PORT_DEFAULT);
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

  public static class AdbOptionsUpdater {
    @NotNull private final AdbOptionsService myService;
    private boolean myUseLibusb;
    private boolean myUseMdnsOpenScreen;
    private boolean myUseUserManagedAdb;
    private int myUserManagedAdbPort;

    private AdbOptionsUpdater(@NotNull AdbOptionsService service) {
      myService = service;
      myUseLibusb = service.shouldUseLibusb();
      myUseMdnsOpenScreen = service.shouldUseMdnsOpenScreen();
      myUseUserManagedAdb = service.shouldUseUserManagedAdb();
      myUserManagedAdbPort = service.getUserManagedAdbPort();
    }

    public boolean useLibusb() {
      return myUseLibusb;
    }

    public AdbOptionsUpdater setUseLibusb(boolean useLibusb) {
      myUseLibusb = useLibusb;
      return this;
    }

    public boolean useMdnsOpenScreen() {
      return myUseMdnsOpenScreen;
    }

    public AdbOptionsUpdater setUseMdnsOpenScreen(boolean useMdnsOpenScreen) {
      myUseMdnsOpenScreen = useMdnsOpenScreen;
      return this;
    }

    public boolean useUserManagedAdb() {
      return myUseUserManagedAdb;
    }

    public AdbOptionsUpdater setUseUserManagedAdb(boolean useUserManagedAdb) {
      myUseUserManagedAdb = useUserManagedAdb;
      return this;
    }

    public int getUserManagedAdbPort() {
      return myUserManagedAdbPort;
    }

    public AdbOptionsUpdater setUserManagedAdbPort(int userManagedAdbPort) {
      myUserManagedAdbPort = userManagedAdbPort;
      return this;
    }

    public void commit() {
      myService.commitOptions(this);
    }
  }
}
