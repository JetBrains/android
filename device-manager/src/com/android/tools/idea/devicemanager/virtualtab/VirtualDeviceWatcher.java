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
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeAvdManagers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.EventListenerList;
import org.jetbrains.android.AndroidPluginDisposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    myAlarm = new Alarm();
    Disposer.register(AndroidPluginDisposable.Companion.getApplicationInstance(), myAlarm);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, this);
  }

  @UiThread
  @Override
  public void applicationActivated(@NotNull IdeFrame ideFrame) {
    // TODO
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
  private synchronized void snapshotAvds() {
    // TODO
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private synchronized @NotNull Map<@NotNull String, @NotNull AvdInfo> getCurrentAvds() {
    try {
      myAvdManager.reloadAvds();
      Map<String, AvdInfo> idToAvdInfo = new HashMap<>();
      Arrays.stream(myAvdManager.getAllAvds()).forEach(avdInfo -> idToAvdInfo.put(avdInfo.getId(), avdInfo));
      return idToAvdInfo;
    }
    catch (AndroidLocationsException e) {
      throw new RuntimeException(e);
    }
  }

}
