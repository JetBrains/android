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
import com.intellij.openapi.util.SystemInfo;
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

  private static final String USB_BACKEND_NAME = "adb.usb.backend.name";
  private static final String MDNS_BACKEND_NAME = "adb.mdns.backend.name2";
  private static final String USE_USER_MANAGED_ADB = "AdbOptionsService.use.user.managed.adb";
  private static final String USER_MANAGED_ADB_PORT = "AdbOptionsService.user.managed.adb.port";
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

  @NotNull
  public AdbServerUsbBackend getAdbServerUsbBackend() {
    String value = PropertiesComponent.getInstance().getValue(USB_BACKEND_NAME, AdbServerUsbBackend.DEFAULT.name());
    try {
      return AdbServerUsbBackend.valueOf(value);
    } catch(IllegalArgumentException e) {
      return AdbServerUsbBackend.DEFAULT;
    }
  }

  @NotNull
  public AdbServerMdnsBackend getAdbServerMdnsBackend() {
    final AdbServerMdnsBackend defaultMdnsBackend;
    if (SystemInfo.isMac) {
      defaultMdnsBackend = AdbServerMdnsBackend.BONJOUR;
    } else {
      defaultMdnsBackend = AdbServerMdnsBackend.OPENSCREEN;
    }

    String value = PropertiesComponent.getInstance().getValue(MDNS_BACKEND_NAME, defaultMdnsBackend.name());
    try {
      return AdbServerMdnsBackend.valueOf(value);
    } catch(IllegalArgumentException e) {
      return defaultMdnsBackend;
    }
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
    props.setValue(USB_BACKEND_NAME, options.getAdbServerUsbBackend().name());
    props.setValue(MDNS_BACKEND_NAME, options.getAdbServerMdnsBackend().name());
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
    private AdbServerUsbBackend myServerBackend;
    private AdbServerMdnsBackend myServerMdnsBackend;
    private boolean myUseUserManagedAdb;
    private int myUserManagedAdbPort;

    private AdbOptionsUpdater(@NotNull AdbOptionsService service) {
      myService = service;
      myServerBackend = service.getAdbServerUsbBackend();
      myServerMdnsBackend = service.getAdbServerMdnsBackend();
      myUseUserManagedAdb = service.shouldUseUserManagedAdb();
      myUserManagedAdbPort = service.getUserManagedAdbPort();
    }

    public AdbServerUsbBackend getAdbServerUsbBackend() {
      return myServerBackend;
    }

    public AdbOptionsUpdater setAdbServerUsbBackend(AdbServerUsbBackend serverBackend) {
      myServerBackend = serverBackend;
      return this;
    }

    public AdbServerMdnsBackend getAdbServerMdnsBackend() {
      return myServerMdnsBackend;
    }
    
    public AdbOptionsUpdater setAdbServerMdnsBackend(AdbServerMdnsBackend serverBackend) {
      myServerMdnsBackend = serverBackend;
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
