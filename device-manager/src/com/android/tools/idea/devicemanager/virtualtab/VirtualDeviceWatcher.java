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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.Alarm.ThreadToUse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
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
  @NotNull
  private final Supplier<Optional<AvdManager>> myGetAvdManager;

  private final @NotNull EventListenerList myListeners;
  private final @NotNull Alarm myAlarm;

  @UiThread
  @SuppressWarnings("unused")
  private VirtualDeviceWatcher() {
    this(VirtualDeviceWatcher::getAvdManager);
  }

  @UiThread
  @VisibleForTesting
  VirtualDeviceWatcher(@NotNull Supplier<Optional<AvdManager>> getAvdManager) {
    myGetAvdManager = getAvdManager;
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

  /**
   * Called by the alarm thread
   */
  @WorkerThread
  @NotNull
  private static Optional<AvdManager> getAvdManager() {
    try {
      return Optional.ofNullable(IdeAvdManagers.INSTANCE.getAvdManager(AndroidSdks.getInstance().tryToChooseSdkHandler()));
    }
    catch (AndroidLocationsException exception) {
      Logger.getInstance(VirtualDeviceWatcher.class).warn("Unable to get AvdManager for VirtualDeviceWatcher", exception);
      return Optional.empty();
    }
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
   * Called by the alarm thread
   */
  @WorkerThread
  private void snapshotAvds() {
    Application application = ApplicationManager.getApplication();

    if (!application.isActive()) {
      return;
    }

    myGetAvdManager.get()
      .flatMap(VirtualDeviceWatcher::getAllAvds)
      .ifPresent(avds -> fireVirtualDevicesChanged(application, avds));
  }

  /**
   * Called by the alarm thread
   */
  @WorkerThread
  private void fireVirtualDevicesChanged(@NotNull Application application, @NotNull Iterable<AvdInfo> avds) {
    application.invokeLater(() -> fireVirtualDevicesChanged(avds));
  }

  /**
   * Called by the alarm thread
   */
  @WorkerThread
  @NotNull
  private static Optional<Iterable<AvdInfo>> getAllAvds(@NotNull AvdManager manager) {
    try {
      manager.reloadAvds();
      return Optional.of(new ArrayList<>(Arrays.asList(manager.getAllAvds())));
    }
    catch (AndroidLocationsException exception) {
      Logger.getInstance(VirtualDeviceWatcher.class).warn("Unable to reload AvdManager", exception);
      return Optional.empty();
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
