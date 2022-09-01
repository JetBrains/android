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

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.event.EventListenerList;
import org.jetbrains.annotations.NotNull;

final class ProcessManager implements IDeviceChangeListener {
  private Map<@NotNull Key, @NotNull State> myKeyToStateMap;
  private final @NotNull EventListenerList myListeners;

  private final @NotNull NewSetKeyToStateMapFutureCallback myNewSetKeyToStateMapFutureCallback;
  private final @NotNull Callable<@NotNull AvdManagerConnection> myGetDefaultAvdManagerConnection;

  @UiThread
  ProcessManager() {
    this(ProcessManager::newSetKeyToStateMapFutureCallback, AvdManagerConnection::getDefaultAvdManagerConnection);
  }

  @UiThread
  @VisibleForTesting
  ProcessManager(@NotNull NewSetKeyToStateMapFutureCallback newSetKeyToStateMapFutureCallback,
                 @NotNull Callable<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection) {
    myListeners = new EventListenerList();

    myNewSetKeyToStateMapFutureCallback = newSetKeyToStateMapFutureCallback;
    myGetDefaultAvdManagerConnection = getDefaultAvdManagerConnection;
  }

  @UiThread
  @VisibleForTesting
  static @NotNull FutureCallback<@NotNull Map<@NotNull Key, @NotNull State>> newSetKeyToStateMapFutureCallback(@NotNull ProcessManager manager) {
    return new DeviceManagerFutureCallback<>(ProcessManager.class, manager::setKeyToStateMap);
  }

  @UiThread
  private void setKeyToStateMap(@NotNull Map<@NotNull Key, @NotNull State> keyToStateMap) {
    myKeyToStateMap = keyToStateMap;
    fire(() -> new ProcessManagerEvent(this), ProcessManagerListener::allStatesChanged);
  }

  @UiThread
  private void fire(@NotNull Supplier<@NotNull ProcessManagerEvent> newProcessManagerEvent,
                    @NotNull BiConsumer<@NotNull ProcessManagerListener, @NotNull ProcessManagerEvent> consumer) {
    Object[] listeners = myListeners.getListenerList();
    ProcessManagerEvent event = null;

    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] != ProcessManagerListener.class) {
        continue;
      }

      if (event == null) {
        event = newProcessManagerEvent.get();
      }

      consumer.accept((ProcessManagerListener)listeners[i + 1], event);
    }
  }

  @UiThread
  void addProcessManagerListener(@NotNull ProcessManagerListener listener) {
    myListeners.add(ProcessManagerListener.class, listener);
  }

  /**
   * Called by the event dispatch and the device list monitor threads
   */
  @AnyThread
  void init() {
    // noinspection UnstableApiUsage
    FluentFuture.from(DeviceManagerFutures.appExecutorServiceSubmit(myGetDefaultAvdManagerConnection))
      .transform(ProcessManager::collectKeyToStateMap, AppExecutorUtil.getAppExecutorService())
      .addCallback(myNewSetKeyToStateMapFutureCallback.apply(this), EdtExecutorService.getInstance());
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull Map<@NotNull Key, @NotNull State> collectKeyToStateMap(@NotNull AvdManagerConnection connection) {
    return connection.getAvds(true).stream()
      .collect(Collectors.toMap(avd -> new VirtualDevicePath(avd.getId()), avd -> State.valueOf(connection.isAvdRunning(avd))));
  }

  @VisibleForTesting
  interface NewSetKeyToStateMapFutureCallback {
    @NotNull FutureCallback<@NotNull Map<@NotNull Key, @NotNull State>> apply(@NotNull ProcessManager manager);
  }

  enum State {
    STOPPED, LAUNCHED;

    /**
     * Called by an application pool thread
     */
    @WorkerThread
    private static @NotNull State valueOf(boolean online) {
      if (online) {
        return LAUNCHED;
      }

      return STOPPED;
    }
  }

  /**
   * Called by the device list monitor and the device client monitor threads
   */
  @WorkerThread
  @Override
  public void deviceChanged(@NotNull IDevice device, int mask) {
    if (!device.isEmulator()) {
      return;
    }

    if ((mask & IDevice.CHANGE_STATE) == 0) {
      return;
    }

    DeviceState state = device.getState();

    if (state == null) {
      return;
    }

    switch (state) {
      case OFFLINE:
      case ONLINE:
        init();
        break;
      default:
        break;
    }
  }

  @VisibleForTesting
  @NotNull Object getKeyToStateMap() {
    return myKeyToStateMap;
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
  }
}
