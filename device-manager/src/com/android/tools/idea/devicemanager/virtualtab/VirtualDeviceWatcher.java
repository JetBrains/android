/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.prefs.AndroidLocationsException;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeAvdManagers;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.Alarm.ThreadToUse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.event.EventListenerList;
import org.jetbrains.android.AndroidPluginDisposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The purpose of this class is to watch for changes to AVDs. Most operations within Studio on the AVDs are already tracked by Device
 * Manager, but if the user manually deletes files outside of Studio, we also need to update Device Manager. We save the current AVDs when
 * Studio loses focus, and then check the differences when Studio regains focus.
 */
@Service
public final class VirtualDeviceWatcher implements ApplicationActivationListener {
  private final @NotNull AvdManager myAvdManager;
  private final @NotNull EventListenerList myListeners;

  private final @NotNull Alarm myAlarm;

  @UiThread
  @SuppressWarnings("unused")
  private VirtualDeviceWatcher() throws AndroidLocationsException {
    this(getAvdManagerInstance());
  }

  @UiThread
  @VisibleForTesting
  VirtualDeviceWatcher(@NotNull AvdManager avdManager) {
    myAvdManager = avdManager;
    myListeners = new EventListenerList();
    myAlarm = new Alarm(ThreadToUse.POOLED_THREAD, AndroidPluginDisposable.Companion.getApplicationInstance());

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, this);
  }

  @UiThread
  @Override
  public void applicationActivated(@NotNull IdeFrame ideFrame) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(this::snapshotAvds, Duration.ofSeconds(1).toMillis());
  }

  @UiThread
  private static @NotNull AvdManager getAvdManagerInstance() throws AndroidLocationsException {
    AvdManager manager = IdeAvdManagers.INSTANCE.getAvdManager(AndroidSdks.getInstance().tryToChooseSdkHandler());
    assert manager != null;

    return manager;
  }

  @UiThread
  static @NotNull VirtualDeviceWatcher getInstance() {
    return ApplicationManager.getApplication().getService(VirtualDeviceWatcher.class);
  }

  @UiThread
  void addVirtualDeviceWatcherListener(@NotNull VirtualDeviceWatcherListener listener) {
    myListeners.add(VirtualDeviceWatcherListener.class, listener);
  }

  @UiThread
  void removeVirtualDeviceWatcherListener(@NotNull VirtualDeviceWatcherListener listener) {
    myListeners.remove(VirtualDeviceWatcherListener.class, listener);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private void snapshotAvds() {
    Application application = ApplicationManager.getApplication();

    if (!application.isActive()) {
      return;
    }

    Iterable<AvdInfo> avds = getCurrentAvds();
    application.invokeLater(() -> fireVirtualDevicesChanged(avds));
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull Iterable<AvdInfo> getCurrentAvds() {
    try {
      myAvdManager.reloadAvds();
      return new ArrayList<>(Arrays.asList(myAvdManager.getAllAvds()));
    }
    catch (AndroidLocationsException e) {
      throw new RuntimeException(e);
    }
  }

  @UiThread
  private void fireVirtualDevicesChanged(@NotNull Iterable<AvdInfo> avds) {
    EventListenerLists.fire(myListeners,
                            VirtualDeviceWatcherListener::virtualDevicesChanged,
                            VirtualDeviceWatcherListener.class,
                            () -> new VirtualDeviceWatcherEvent(this, avds));
  }
}
